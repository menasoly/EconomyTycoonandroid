package app.many.economytycoon.reporting

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.IncomeStatement

/** How a year went, in one word. */
enum class Verdict(val label: String) {
    RECORD("Best year yet"),
    GROWTH("Growing"),
    STEADY("Steady"),
    SLIP("Slipping"),
    LOSS("Loss-making"),
    QUIET("Quiet"),
}

/**
 * One company's year, worked out from the ledger: what it earned, what it owned at the start and end, and how
 * that compares with the year before. A report for the year still under way has [partial] set.
 */
class AnnualReport(
    val company: CompanyId,
    val name: String,
    /** Zero for the first year of the game. */
    val index: Int,
    val from: Tick,
    val to: Tick,
    val partial: Boolean,
    val income: IncomeStatement,
    val opening: BalanceSheet,
    val closing: BalanceSheet,
    val prior: AnnualReport?,
) {
    val calendarYear: Int get() = Calendar.START_YEAR + index
    val days: Long get() = to.value - from.value

    val revenue: Money get() = income.revenue
    val netIncome: Money get() = income.netIncome
    val netWorthChange: Money get() = closing.netWorth - opening.netWorth
    val cashChange: Money get() = closing.cash - opening.cash
    val debtChange: Money get() = closing.totalDebt - opening.totalDebt

    /** Net income over revenue, in parts per million; null in a year without sales. */
    val netMarginPpm: Long? get() = if (revenue.raw <= 0L) null else mulDiv(netIncome.raw, 1_000_000L, revenue.raw)

    val grossMarginPpm: Long? get() = if (revenue.raw <= 0L) null else mulDiv(income.grossProfit.raw, 1_000_000L, revenue.raw)

    /** Net income over the average of opening and closing equity; null when equity is not positive on average. */
    val returnOnEquityPpm: Long? get() {
        val average = (opening.totalEquity.raw + closing.totalEquity.raw) / 2L
        if (average <= 0L) return null
        return mulDiv(netIncome.raw, 1_000_000L, average)
    }

    /** Change in revenue against the year before, in parts per million; null without a year to compare with. */
    val revenueGrowthPpm: Long? get() {
        val p = prior ?: return null
        if (p.revenue.raw <= 0L) return null
        return mulDiv(revenue.raw - p.revenue.raw, 1_000_000L, p.revenue.raw)
    }

    val netIncomeChange: Money? get() = prior?.let { netIncome - it.netIncome }

    /** True when no year before earned more. */
    val bestNetIncomeSoFar: Boolean get() {
        var p = prior
        if (netIncome.raw <= 0L) return false
        while (p != null) {
            if (p.netIncome >= netIncome) return false
            p = p.prior
        }
        return true
    }

    val verdict: Verdict get() = when {
        revenue.raw <= 0L && netIncome.raw == 0L -> Verdict.QUIET
        netIncome.raw < 0L -> Verdict.LOSS
        bestNetIncomeSoFar && prior != null -> Verdict.RECORD
        (revenueGrowthPpm ?: 0L) >= GROWTH_PPM -> Verdict.GROWTH
        (revenueGrowthPpm ?: 0L) <= -GROWTH_PPM -> Verdict.SLIP
        else -> Verdict.STEADY
    }

    companion object {
        /** Revenue must move by this much to count as growth or decline. */
        const val GROWTH_PPM = 50_000L
    }
}
