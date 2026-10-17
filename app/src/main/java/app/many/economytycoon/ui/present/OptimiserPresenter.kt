package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.marketing.MarketKey
import app.many.economytycoon.marketing.MarketingNames
import app.many.economytycoon.marketing.OptimiserResult
import app.many.economytycoon.marketing.StopReason
import app.many.economytycoon.session.Formatting

data class AllocationRow(val channel: String, val amount: String, val reach: String, val lastReturn: String)

data class MarketPlanRow(val title: String, val spend: String, val extraProfit: String, val tone: Tone, val allocations: List<AllocationRow>)

data class OptimiserModel(
    val tiles: List<Tile>,
    val markets: List<MarketPlanRow>,
    val byChannel: List<Fact>,
    val stop: String,
    val tone: Tone,
    val canApply: Boolean,
)

/** Shows the budget optimiser's plan: where each unit of daily money goes and why it stopped. */
object OptimiserPresenter {
    fun stopTone(r: StopReason): Tone = when (r) {
        StopReason.BUDGET_USED -> Tone.GOOD
        StopReason.DIMINISHING -> Tone.INFO
        StopReason.CONSTRAINED -> Tone.WARN
        StopReason.NO_OPTIONS -> Tone.MUTED
    }

    fun build(result: OptimiserResult, names: MarketingNames, money: (Money) -> String): OptimiserModel {
        val tiles = listOf(
            Tile("Budget", money(result.budget)),
            Tile("Planned spend", money(result.spend)),
            Tile("Left unspent", money(result.leftover), if (result.leftover.raw > 0L) Tone.INFO else Tone.NEUTRAL),
            Tile("Extra profit a day", money(result.netProfit), Tones.bySign(result.netProfit.raw), "after paying for the advertising"),
            Tile("Return", Formatting.ratioTimes(result.returnPpm), if (result.returnPpm >= 1_000_000L) Tone.GOOD else Tone.WARN),
        )
        val keys: List<MarketKey> = result.allocations.map { it.market }.distinct()
        val markets = keys.map { k ->
            val v = result.valueByMarket[k]
            MarketPlanRow(
                title = "${names.good(k.good)} in ${names.region(k.region)}",
                spend = money(v?.spend ?: Money.ZERO),
                extraProfit = money(v?.netProfit ?: Money.ZERO),
                tone = Tones.bySign(v?.netProfit?.raw ?: 0L),
                allocations = result.forMarket(k).map {
                    AllocationRow(it.channel.label, money(it.dailyBudget), Formatting.percentPpm(it.reachPpm), Formatting.ratioTimes(it.lastReturnPpm))
                },
            )
        }
        val byChannel = result.byChannel().map { (ch, m) -> Fact(ch.label, money(m)) }
        return OptimiserModel(tiles, markets, byChannel, result.stopReason.label, stopTone(result.stopReason), result.allocations.isNotEmpty())
    }
}
