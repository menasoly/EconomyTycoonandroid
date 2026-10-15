package app.many.economytycoon.forecast

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money

/** The company's position on one day of a trial run. */
data class ForecastPoint(val day: Long, val cash: Money, val netWorth: Money, val debt: Money, val revenue: Money)

/** Why a trial run stopped before its last day. */
enum class EndReason(val label: String) {
    COMPLETED("Ran to the end"),
    COMPANY_GONE("The company no longer exists"),
    NOT_ACTIVE("The company stopped trading"),
}

/** What the company's accounts look like over a stretch of days, as one possible future. */
class Forecast(
    val company: CompanyId,
    val startDay: Long,
    val points: List<ForecastPoint>,
    val ended: EndReason,
    /** Commands tried at the start, with the reason each was refused, or null if it went through. */
    val actions: List<ActionResult> = emptyList(),
) {
    val first: ForecastPoint? get() = points.firstOrNull()
    val last: ForecastPoint? get() = points.lastOrNull()
    val days: Long get() = (last?.day ?: startDay) - startDay

    val lowestCash: ForecastPoint? get() = points.minByOrNull { it.cash.raw }
    val highestNetWorth: ForecastPoint? get() = points.maxByOrNull { it.netWorth.raw }

    /** First day cash falls below zero, or null if it never does. */
    val firstOverdrawn: Long? get() = points.firstOrNull { it.cash.raw < 0L }?.day

    val netWorthChange: Money get() = (last?.netWorth ?: Money.ZERO) - (first?.netWorth ?: Money.ZERO)
    val cashChange: Money get() = (last?.cash ?: Money.ZERO) - (first?.cash ?: Money.ZERO)

    /** Average change in net worth per day over the run; zero for a run of no length. */
    val netWorthPerDay: Money get() = if (days <= 0L) Money.ZERO else Money(netWorthChange.raw / days)

    /** Revenue earned over the run, taken from the final cumulative figure. */
    val revenueOverRun: Money get() = (last?.revenue ?: Money.ZERO) - (first?.revenue ?: Money.ZERO)

    val refused: List<ActionResult> get() = actions.filter { it.refusal != null }
}

data class ActionResult(val description: String, val refusal: String?) {
    val accepted: Boolean get() = refusal == null
}

/** A trial run set beside the run in which nothing is done. */
class Comparison(val baseline: Forecast, val scenario: Forecast) {
    val netWorthDifference: Money get() = (scenario.last?.netWorth ?: Money.ZERO) - (baseline.last?.netWorth ?: Money.ZERO)
    val cashDifference: Money get() = (scenario.last?.cash ?: Money.ZERO) - (baseline.last?.cash ?: Money.ZERO)
    val debtDifference: Money get() = (scenario.last?.debt ?: Money.ZERO) - (baseline.last?.debt ?: Money.ZERO)

    /** Day on which the scenario overtakes doing nothing after having been behind, if it ever does. */
    val breakEvenDay: Long? get() {
        for (i in scenario.points.indices) {
            val b = baseline.points.getOrNull(i) ?: break
            val s = scenario.points[i]
            if (i > 0 && s.netWorth > b.netWorth && scenario.points[i - 1].netWorth < baseline.points[i - 1].netWorth) return s.day
        }
        return null
    }

    val worthIt: Boolean get() = netWorthDifference.raw > 0L

    /** The scenario is worse in the short run but better at the end, or the other way about. */
    val changesSign: Boolean get() {
        val mid = scenario.points.size / 2
        val b = baseline.points.getOrNull(mid) ?: return false
        val s = scenario.points.getOrNull(mid) ?: return false
        return (s.netWorth > b.netWorth) != worthIt
    }
}
