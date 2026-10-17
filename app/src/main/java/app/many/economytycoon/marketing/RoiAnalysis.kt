package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

enum class RoiVerdict(val label: String) {
    TOO_EARLY("Too early to judge"),
    PAID_BACK("Paid for itself"),
    PAYING_BACK("On the way to paying back"),
    LOSING("Costing more than it earns"),
}

/** The lift a campaign bought, measured against what the product sold before it. */
data class RoiReport(
    val days: Int,
    val spend: Money,
    val baselinePerDay: Money,
    val actualRevenue: Money,
    val incrementalRevenue: Money,
    val incrementalProfit: Money,
    /** Average daily revenue against baseline, ppm above it (negative when sales fell). */
    val liftPpm: Long,
    /** Net gain over spend, as a share of spend. */
    val roiPpm: Long,
    /** First day (counting from one) on which cumulative profit from the lift covered cumulative spend, if it ever did. */
    val paybackDay: Int?,
    val verdict: RoiVerdict,
) {
    val netProfit: Money get() = incrementalProfit - spend
}

/** Looks back at what advertising earned. */
object RoiAnalysis {
    /** Days of history needed before a verdict is more than a guess. */
    const val MIN_DAYS = 7

    /** Mean of the sales before a campaign began, used as the baseline. Null when there is no history. */
    fun baseline(before: List<Money>): Money? =
        if (before.isEmpty()) null else Money(before.sumOf { it.raw } / before.size)

    /**
     * Compares [revenueByDay] with [baselinePerDay] over the same days as [spendByDay]. [marginPpm] turns extra
     * revenue into extra gross profit. The two series must be the same length.
     */
    fun analyse(spendByDay: List<Money>, revenueByDay: List<Money>, baselinePerDay: Money, marginPpm: Long): RoiReport {
        require(spendByDay.size == revenueByDay.size) { "spend and revenue cover different days" }
        val days = spendByDay.size
        var spend = Money.ZERO
        var actual = Money.ZERO
        var cumProfit = 0L
        var cumSpend = 0L
        var payback: Int? = null
        for (d in 0 until days) {
            spend = spend + spendByDay[d]
            actual = actual + revenueByDay[d]
            cumSpend += spendByDay[d].raw
            cumProfit += mulDiv(revenueByDay[d].raw - baselinePerDay.raw, marginPpm, Ppm.ONE)
            if (payback == null && cumSpend > 0L && cumProfit >= cumSpend) payback = d + 1
        }
        val expected = baselinePerDay * days.toLong()
        val incremental = actual - expected
        val profit = Money(mulDiv(incremental.raw, marginPpm, Ppm.ONE))
        val lift = if (expected.raw <= 0L) 0L else mulDiv(incremental.raw, Ppm.ONE, expected.raw)
        val roi = if (spend.raw <= 0L) 0L else mulDiv((profit - spend).raw, Ppm.ONE, spend.raw)
        val verdict = when {
            days < MIN_DAYS -> RoiVerdict.TOO_EARLY
            payback != null && profit >= spend -> RoiVerdict.PAID_BACK
            profit.raw > 0L && profit.raw * 2L >= spend.raw -> RoiVerdict.PAYING_BACK
            else -> RoiVerdict.LOSING
        }
        return RoiReport(days, spend, baselinePerDay, actual, incremental, profit, lift, roi, payback, verdict)
    }

    /** Days a steady daily profit of [dailyProfit] takes to cover [spendToCover], or null if it never will. */
    fun paybackDays(spendToCover: Money, dailyProfit: Money): Int? {
        if (dailyProfit.raw <= 0L) return null
        val days = (spendToCover.raw + dailyProfit.raw - 1L) / dailyProfit.raw
        return if (days > Int.MAX_VALUE) null else days.toInt()
    }

    /** Spend split by channel for a set of campaigns, in channel order, as the campaigns were actually billed. */
    fun spendByChannel(campaigns: List<Campaign>): Map<Channel, Money> {
        val out = java.util.EnumMap<Channel, Money>(Channel::class.java)
        for (c in campaigns) out[c.channel] = (out[c.channel] ?: Money.ZERO) + c.netSpend
        return Channel.ordered.filter { out.containsKey(it) }.associateWith { out.getValue(it) }
    }
}
