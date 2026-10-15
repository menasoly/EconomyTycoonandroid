package app.many.economytycoon.ui.present

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.planner.ChainPlan
import app.many.economytycoon.planner.PlanNode
import app.many.economytycoon.planner.Sourcing
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

/** One line of the production tree, indented by [depth]. */
data class ChainLine(val good: GoodId, val depth: Int, val title: String, val detail: String, val make: Boolean)

data class PlannerModel(
    val tiles: List<Tile>,
    val tree: List<ChainLine>,
    val buildings: List<Fact>,
    val purchases: List<Fact>,
    val costs: List<Fact>,
    val verdict: String,
    val verdictTone: Tone,
)

/** Turns a chain plan into text the planner screen can show. */
object PlannerPresenter {
    val rates: List<Long> = listOf(1L, 10L, 50L, 200L)
    const val NOTHING = "Pick a product to see what it takes to make it."

    fun verdict(plan: ChainPlan): Pair<String, Tone> {
        val t = plan.totals
        return when {
            t.facilityCount == 0 -> "Nothing to build: this product is bought, not made." to Tone.NEUTRAL
            t.profitPerDay.raw <= 0L -> "At reference prices this chain loses money. Buy more of the inputs, or wait for better prices." to Tone.BAD
            t.marginPpm < 100_000L -> "A thin margin: small price moves could turn it into a loss." to Tone.WARN
            else -> "A healthy margin at reference prices." to Tone.GOOD
        }
    }

    fun lines(node: PlanNode, depth: Int = 0): List<ChainLine> {
        val amount = Formatting.quantity(node.perDay)
        val line = when (node.sourcing) {
            Sourcing.MAKE -> ChainLine(node.good, depth, "${node.name}, $amount a day", "Make with ${node.recipe?.name ?: "a recipe"}, ${Formatting.plural(node.linesNeeded, "line")}", true)
            Sourcing.BUY -> ChainLine(node.good, depth, "${node.name}, $amount a day", "Buy", false)
        }
        return listOf(line) + node.children.flatMap { lines(it, depth + 1) }
    }

    fun build(session: GameSession, plan: ChainPlan?): PlannerModel {
        if (plan == null) return PlannerModel(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), NOTHING, Tone.NEUTRAL)
        val t = plan.totals
        val money = session.settings::money
        val (text, tone) = verdict(plan)
        val tiles = listOf(
            Tile("Profit a day", money(t.profitPerDay), if (t.profitPerDay.raw > 0L) Tone.GOOD else Tone.BAD),
            Tile("Build cost", money(t.capital), note = t.paybackDays?.let { "Pays back in ${Formatting.plural(it, "day")}" } ?: "Never pays back"),
            Tile("Buildings", t.facilityCount.toString()),
            Tile("Staff", t.headcount.toString()),
        )
        val buildings = t.facilities.map { Fact(it.name, "${it.count} for ${Formatting.plural(it.lines, "line")}", hint = money(it.capital)) }
        val purchases = t.purchases.map { Fact(it.name, "${Formatting.quantity(it.perDay)} a day", hint = money(it.costPerDay) + " a day") }
        val costs = listOf(
            Fact("Revenue", money(t.revenuePerDay)),
            Fact("Materials", money(t.materialsPerDay)),
            Fact("Wages", money(t.labourPerDay + t.overheadPerDay)),
            Fact("Energy", money(t.energyPerDay)),
            Fact("Upkeep", money(t.upkeepPerDay)),
            Fact("Margin", Formatting.percentPpm(t.marginPpm), if (t.marginPpm > 0L) Tone.GOOD else Tone.BAD),
        )
        return PlannerModel(tiles, lines(plan.root), buildings, purchases, costs, text, tone)
    }

    fun plan(session: GameSession, good: GoodId?, perDay: Long, buy: Set<GoodId>): ChainPlan? =
        good?.let { session.planChain(it, Quantity.units(perDay), buy) }
}
