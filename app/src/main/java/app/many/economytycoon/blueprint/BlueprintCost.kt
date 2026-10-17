package app.many.economytycoon.blueprint

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.audit.RecipeEconomics
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.facilityprofit.ProfitMath
import app.many.economytycoon.production.ProductionParams

/** What one line is expected to earn per day at reference prices. */
data class LineEconomy(
    val index: Int,
    val recipe: RecipeId?,
    val recipeName: String?,
    /** Profit per day after recipe inputs, labour and energy and the line's machine upkeep. */
    val profitPerDay: Money,
    /** Combined throughput of the machines the recipe needs, ppm of 1.0. */
    val throughputPpm: Long,
    /** Why the line earns nothing, if it does not. */
    val idleReason: String?,
)

/** Capital needed to build a blueprint and what it is expected to earn. All money values are exact fixed-point. */
data class CostEstimate(
    val facilityName: String,
    val buildCost: Money,
    val buildMaterials: Money,
    val extraLines: Money,
    val machines: Money,
    val storage: Money,
    val upkeepPerDay: Money,
    val overheadStaffPerDay: Money,
    val machineUpkeepPerDay: Money,
    /** Profit of the running lines before machine upkeep, building upkeep and overhead staff. */
    val grossPerDay: Money,
    val lineEconomies: List<LineEconomy>,
    val buildTicks: Int,
) {
    val capital: Money get() = buildCost + buildMaterials + extraLines + machines + storage
    val netPerDay: Money get() = grossPerDay - upkeepPerDay - overheadStaffPerDay - machineUpkeepPerDay

    /** Days to earn the capital back at the estimated pace; null when the set-up does not pay. */
    val paybackDays: Long? get() = ProfitMath.paybackDays(netPerDay, capital)
    val annualReturnPpm: Long get() = ProfitMath.annualReturnPpm(netPerDay, capital)
    val isProfitable: Boolean get() = netPerDay.raw > 0
}

/** Estimates cost and payback of a blueprint from content definitions alone; no game state is read. */
class CostEstimator(
    private val content: ContentRepository,
    private val production: ProductionParams = ProductionParams(),
) {
    /** Null when the facility type no longer exists. */
    fun estimate(spec: BlueprintSpec): CostEstimate? {
        val type = content.facilityTypeOrNull(spec.facilityType) ?: return null
        val materials = type.buildInputs.sumOf { RecipeEconomics.valueOf(content, it) }
        val lineCost = mulDiv(type.buildCost.raw, production.lineBuildCostPpm, 1_000_000L)
        val extraLines = lineCost * (spec.lines.size - 1).coerceAtLeast(0)
        val storage = mulDiv(type.buildCost.raw, EXPANSION_COST_PPM, 1_000_000L) * spec.storageExpansions
        var machineCost = 0L
        var machineUpkeep = 0L
        var gross = 0L
        val economies = ArrayList<LineEconomy>()
        for ((i, line) in spec.lines.withIndex()) {
            var lineUpkeep = 0L
            for (m in line.machines) {
                val def = content.machineTypeOrNull(m) ?: continue
                machineCost += def.cost.raw
                lineUpkeep += def.maintenanceCostPerTick.raw
            }
            machineUpkeep += lineUpkeep
            economies += economy(i, line, lineUpkeep)
            if (line.enabled) gross += economies.last().profitPerDay.raw + lineUpkeep
        }
        val overhead = type.overheadStaff.sumOf { content.role(it.role).baseWage.raw * it.headcount }
        return CostEstimate(
            facilityName = type.name, buildCost = type.buildCost, buildMaterials = Money(materials), extraLines = Money(extraLines),
            machines = Money(machineCost), storage = Money(storage), upkeepPerDay = type.upkeepPerTick, overheadStaffPerDay = Money(overhead),
            machineUpkeepPerDay = Money(machineUpkeep), grossPerDay = Money(gross), lineEconomies = economies, buildTicks = type.buildTicks,
        )
    }

    private fun economy(index: Int, line: LineSpec, upkeep: Long): LineEconomy {
        val rid = line.recipe ?: return LineEconomy(index, null, null, Money.ZERO, 0L, "no recipe")
        val recipe = content.recipeOrNull(rid) ?: return LineEconomy(index, rid, null, Money.ZERO, 0L, "recipe no longer exists")
        var through = 1_000_000L
        for (m in recipe.machines) {
            val def = content.machineTypeOrNull(m)
            if (m !in line.machines || def == null) {
                return LineEconomy(index, rid, recipe.name, Money(-upkeep), 0L, "needs ${def?.name ?: m.key}")
            }
            through = mulDiv(through, def.throughputPpm, 1_000_000L)
        }
        if (!line.enabled) return LineEconomy(index, rid, recipe.name, Money(-upkeep), through, "switched off")
        val perDay = RecipeEconomics.of(content, recipe).profitPerLineDay
        return LineEconomy(index, rid, recipe.name, Money(mulDiv(perDay, through, 1_000_000L) - upkeep), through, null)
    }

    companion object {
        /** A storage expansion costs a fifth of the facility's build price. */
        const val EXPANSION_COST_PPM = 200_000L
    }
}
