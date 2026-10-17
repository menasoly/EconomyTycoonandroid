package app.many.economytycoon.valuation

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate

/** How much a discounted-cash-flow value moves when its two big assumptions change. */
object Sensitivity {
    val DEFAULT_DISCOUNTS: List<Rate> = listOf(Rate(80_000), Rate(100_000), Rate(120_000), Rate(140_000), Rate(160_000))
    val DEFAULT_GROWTHS: List<Rate> = listOf(Rate(0), Rate(20_000), Rate(40_000), Rate(60_000), Rate(80_000))

    /** One row per discount rate, one column per growth rate; null where the maths does not hold or there is no cash flow. */
    fun dcfGrid(i: ValuationInput, a: ValuationAssumptions, discounts: List<Rate> = DEFAULT_DISCOUNTS, growths: List<Rate> = DEFAULT_GROWTHS): List<List<Money?>> =
        discounts.map { d -> growths.map { g -> cell(i, a, d, g) } }

    fun cell(i: ValuationInput, a: ValuationAssumptions, discount: Rate, growth: Rate): Money? {
        if (i.freeCashFlow.raw <= 0L || discount <= a.terminalGrowth) return null
        return Money.max(ValuationMethods.dcfEquity(i, a.copy(discount = discount, growth = growth)), Money.ZERO)
    }

    /**
     * The yearly growth rate at which the cash-flow value equals [target], found by bisection. Null when the target
     * cannot be reached within -20% to +40% a year, or the company has no cash flow to project.
     */
    fun impliedGrowth(i: ValuationInput, a: ValuationAssumptions, target: Money): Rate? {
        if (i.freeCashFlow.raw <= 0L || target.raw <= 0L) return null
        var lo = -200_000L
        var hi = 400_000L
        fun at(g: Long) = ValuationMethods.dcfEquity(i, a.copy(growth = Rate(g)))
        if (at(lo) > target || at(hi) < target) return null
        repeat(40) {
            val mid = (lo + hi) / 2
            if (at(mid) < target) lo = mid else hi = mid
        }
        return Rate(hi)
    }

    /** The largest discount rate (down to the terminal growth rate) at which the value still reaches [target]. */
    fun breakEvenDiscount(i: ValuationInput, a: ValuationAssumptions, target: Money): Rate? {
        if (i.freeCashFlow.raw <= 0L || target.raw <= 0L) return null
        var lo = a.terminalGrowth.ppm + 1_000L
        var hi = 600_000L
        fun at(d: Long) = ValuationMethods.dcfEquity(i, a.copy(discount = Rate(d)))
        if (at(lo) < target || at(hi) > target) return null
        repeat(40) {
            val mid = (lo + hi) / 2
            if (at(mid) >= target) lo = mid else hi = mid
        }
        return Rate(lo)
    }
}
