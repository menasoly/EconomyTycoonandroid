package app.many.economytycoon.facilityprofit

import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RecipeId

/** What one production line earns at today's prices. */
data class LinePerformance(
    val line: LineId,
    val recipe: RecipeId?,
    val recipeName: String?,
    val enabled: Boolean,
    val utilisationPpm: Long,
    val outputValue: Money,
    val inputCost: Money,
    val labour: Money,
    val energy: Money,
    val defectPpm: Long,
    val durationDays: Int,
) {
    val profitPerBatch: Money get() = ProfitMath.profitPerBatch(outputValue, defectPpm, inputCost, labour, energy)
    val costPerBatch: Money get() = inputCost + labour + energy
    val marginPpm: Long get() = ProfitMath.marginPpm(profitPerBatch, costPerBatch)

    /** At the utilisation seen so far. */
    val profitPerDay: Money get() = if (recipe == null || !enabled) Money.ZERO else ProfitMath.perDay(profitPerBatch, durationDays, utilisationPpm)

    /** If it ran without a pause. */
    val potentialPerDay: Money get() = if (recipe == null) Money.ZERO else ProfitMath.perDay(profitPerBatch, durationDays, 1_000_000L)

    val idle: Boolean get() = recipe == null || !enabled
    val losing: Boolean get() = !idle && profitPerBatch.raw < 0L
}

/** A better use of a line than the one it has now. */
data class Switch(val facility: FacilityId, val line: LineId, val from: String?, val to: RecipeId, val toName: String, val gainPerDay: Money)

data class FacilityPerformance(
    val facility: FacilityId,
    val name: String,
    val typeName: String,
    val regionName: String,
    val lines: List<LinePerformance>,
    val upkeepPerDay: Money,
    val overheadPerDay: Money,
    val capital: Money,
) {
    val grossPerDay: Money get() = Money(lines.sumOf { it.profitPerDay.raw })
    val potentialPerDay: Money get() = Money(lines.sumOf { it.potentialPerDay.raw })
    val netPerDay: Money get() = grossPerDay - upkeepPerDay - overheadPerDay
    val idleLines: Int get() = lines.count { it.idle }
    val losingLines: Int get() = lines.count { it.losing }
    val bestLine: LinePerformance? get() = lines.filter { !it.idle }.maxByOrNull { it.profitPerDay.raw }
    val worstLine: LinePerformance? get() = lines.filter { !it.idle }.minByOrNull { it.profitPerDay.raw }
    val annualReturnPpm: Long get() = ProfitMath.annualReturnPpm(netPerDay, capital)
    val paybackDays: Long? get() = ProfitMath.paybackDays(netPerDay, capital)

    /** Money left on the table each day because lines are paused or blocked. */
    val unusedPerDay: Money get() = Money(lines.sumOf { (it.potentialPerDay.raw - it.profitPerDay.raw).coerceAtLeast(0L) })
}

data class PortfolioPerformance(val facilities: List<FacilityPerformance>, val switches: List<Switch>) {
    val netPerDay: Money get() = Money(facilities.sumOf { it.netPerDay.raw })
    val capital: Money get() = Money(facilities.sumOf { it.capital.raw })
    val unusedPerDay: Money get() = Money(facilities.sumOf { it.unusedPerDay.raw })
    val annualReturnPpm: Long get() = ProfitMath.annualReturnPpm(netPerDay, capital)
    val ranked: List<FacilityPerformance> get() = facilities.sortedWith(compareByDescending<FacilityPerformance> { it.netPerDay.raw }.thenBy { it.facility.value })
    val losers: List<FacilityPerformance> get() = facilities.filter { it.netPerDay.raw < 0L }.sortedBy { it.netPerDay.raw }
    val potentialGainPerDay: Money get() = Money(switches.sumOf { it.gainPerDay.raw })
}
