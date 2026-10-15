package app.many.economytycoon.analysis

import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.IncomeStatement

enum class Driver(val label: String) {
    MARGIN("profit margin"),
    TURNOVER("asset turnover"),
    LEVERAGE("borrowing"),
}

/**
 * Splits return on equity into three causes: how much of each sale is kept (margin), how many sales each
 * unit of assets makes (turnover) and how much of the assets were paid for with other people's money
 * (leverage). The three multiply to the return, so a weak return always has an identifiable culprit.
 */
data class DuPont(
    val marginPpm: Long,
    val turnoverPpm: Long,
    val leveragePpm: Long,
    val returnOnEquityPpm: Long,
) {
    /** The factor furthest below a neutral reference, i.e. the first thing to fix. Null when all three are healthy. */
    fun weakest(): Driver? {
        val shortfalls = listOf(
            Driver.MARGIN to (REFERENCE_MARGIN - marginPpm) * PPM / REFERENCE_MARGIN,
            Driver.TURNOVER to (REFERENCE_TURNOVER - turnoverPpm) * PPM / REFERENCE_TURNOVER,
            Driver.LEVERAGE to (leveragePpm - REFERENCE_LEVERAGE_MAX) * PPM / REFERENCE_LEVERAGE_MAX,
        )
        val worst = shortfalls.maxByOrNull { it.second } ?: return null
        return if (worst.second > 0L) worst.first else null
    }

    companion object {
        private const val PPM = 1_000_000L
        const val REFERENCE_MARGIN = 80_000L
        const val REFERENCE_TURNOVER = 800_000L
        const val REFERENCE_LEVERAGE_MAX = 3_000_000L
        /** Null when the books cannot support it: no revenue, no assets or no equity. */
        fun of(i: IncomeStatement, b: BalanceSheet, periodDays: Long, yearDays: Long = 360L): DuPont? {
            val p = periodDays.coerceAtLeast(1L)
            val revenue = i.revenue.raw
            val assets = b.totalAssets.raw
            val equity = b.totalEquity.raw
            if (revenue <= 0L || assets <= 0L || equity <= 0L) return null
            val annualRevenue = mulDiv(revenue, yearDays, p)
            val margin = mulDiv(i.netIncome.raw, PPM, revenue)
            val turnover = mulDiv(annualRevenue, PPM, assets)
            val leverage = mulDiv(assets, PPM, equity)
            val roe = mulDiv(mulDiv(margin, turnover, PPM), leverage, PPM)
            return DuPont(margin, turnover, leverage, roe)
        }
    }
}

/** Convenience for showing the three factors next to each other. */
fun DuPont.describe(): String = "margin ${marginPpm / 10_000}%, turnover ${turnoverPpm / 10_000}%, leverage ${leveragePpm / 10_000}%"
