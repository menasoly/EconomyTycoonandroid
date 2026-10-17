package app.many.economytycoon.planner

import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RoleId

/** Where a good in the chain comes from. */
enum class Sourcing(val label: String) {
    /** The company makes it in its own facilities. */
    MAKE("Make"),
    /** It is bought on the market at its going price. */
    BUY("Buy"),
}

/**
 * One good in a production chain and what it takes to supply it.
 * [perDay] is the amount the chain needs each day, in whole and fractional units.
 */
data class PlanNode(
    val good: GoodId,
    val name: String,
    val perDay: Quantity,
    val sourcing: Sourcing,
    val recipe: RecipeDef?,
    /** Batches of the recipe run per day, in parts per million of one batch. */
    val batchesPerDayPpm: Long,
    val children: List<PlanNode>,
    /** Other goods the recipe makes at the same time, per day. */
    val byproducts: List<Pair<GoodId, Quantity>> = emptyList(),
) {
    /** Production lines kept fully busy to hold this rate. Zero for bought goods. */
    val linesNeeded: Long
        get() {
            val r = recipe ?: return 0L
            val work = batchesPerDayPpm * r.durationTicks
            return (work + ONE_PPM - 1) / ONE_PPM
        }

    fun walk(): List<PlanNode> = listOf(this) + children.flatMap { it.walk() }

    val depth: Int get() = 1 + (children.maxOfOrNull { it.depth } ?: 0)

    companion object { const val ONE_PPM = 1_000_000L }
}

/** A good the chain buys on the market, summed over every place it is used. */
data class Purchase(val good: GoodId, val name: String, val perDay: Quantity, val costPerDay: Money)

/** Facilities of one type the chain needs. */
data class FacilityNeed(val type: FacilityTypeId, val name: String, val count: Int, val lines: Long, val capital: Money)

/** What the whole chain costs to build and run, and what it earns, at reference prices. */
data class PlanTotals(
    val facilities: List<FacilityNeed>,
    val purchases: List<Purchase>,
    val staff: List<Pair<RoleId, Long>>,
    val capital: Money,
    val revenuePerDay: Money,
    val materialsPerDay: Money,
    val labourPerDay: Money,
    val energyPerDay: Money,
    val upkeepPerDay: Money,
    val overheadPerDay: Money,
    val energyUnitsPerDay: Quantity,
) {
    val operatingPerDay: Money get() = materialsPerDay + labourPerDay + energyPerDay + upkeepPerDay + overheadPerDay
    val profitPerDay: Money get() = revenuePerDay - operatingPerDay
    val facilityCount: Int get() = facilities.sumOf { it.count }
    val headcount: Long get() = staff.sumOf { it.second }

    /** Days to earn back the buildings; null when the chain does not pay. */
    val paybackDays: Long?
        get() = if (profitPerDay.raw <= 0L) null else (capital.raw + profitPerDay.raw - 1) / profitPerDay.raw

    /** Profit over operating cost, ppm; zero when nothing is spent. */
    val marginPpm: Long get() = if (operatingPerDay.raw <= 0L) 0L else profitPerDay.raw * 1_000_000L / operatingPerDay.raw
}

/** A chain for one product with its tree and totals. */
data class ChainPlan(val product: GoodId, val perDay: Quantity, val root: PlanNode, val totals: PlanTotals) {
    val depth: Int get() = root.depth
    val goodsMade: List<PlanNode> get() = root.walk().filter { it.sourcing == Sourcing.MAKE }
    val goodsBought: List<PlanNode> get() = root.walk().filter { it.sourcing == Sourcing.BUY }
}
