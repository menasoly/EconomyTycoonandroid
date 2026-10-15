package app.many.economytycoon.analysis

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.IncomeStatement

/**
 * How hard a company's assets and working capital work. Turnover is in ppm (1,000,000 means once a year);
 * the day counts say how long money sits in stock, in customers' hands or owed to suppliers. A value is null
 * when its denominator is zero, because "no sales" and "no stock" are different from "nought days".
 */
data class Efficiency(
    val assetTurnoverPpm: Long?,
    val fixedAssetTurnoverPpm: Long?,
    val inventoryDays: Long?,
    val receivableDays: Long?,
    val payableDays: Long?,
) {
    /** Days between paying suppliers and being paid: stock days plus collection days less payment days. */
    val cashCycleDays: Long? get() {
        val i = inventoryDays ?: return null
        val r = receivableDays ?: 0L
        val p = payableDays ?: 0L
        return i + r - p
    }

    companion object {
        private const val PPM = 1_000_000L

        private fun ratio(n: Money, d: Money): Long? = if (d.raw <= 0L) null else mulDiv(n.raw, PPM, d.raw).coerceIn(-1_000_000_000L, 1_000_000_000L)

        private fun days(balance: Money, flow: Money, periodDays: Long): Long? =
            if (flow.raw <= 0L) null else mulDiv(balance.raw.coerceAtLeast(0L), periodDays, flow.raw)

        /** [periodDays] is the length of the income statement's window; flows are annualised to a 360-day year. */
        fun of(i: IncomeStatement, b: BalanceSheet, periodDays: Long, yearDays: Long = 360L): Efficiency {
            val p = periodDays.coerceAtLeast(1L)
            val revenue = Money(mulDiv(i.revenue.raw, yearDays, p))
            return Efficiency(
                assetTurnoverPpm = ratio(revenue, b.totalAssets),
                fixedAssetTurnoverPpm = ratio(revenue, b.fixedAssetsNet),
                inventoryDays = days(b.inventory, i.cogs, p),
                receivableDays = days(b.receivables, i.revenue, p),
                payableDays = days(b.payables, i.cogs, p),
            )
        }
    }
}
