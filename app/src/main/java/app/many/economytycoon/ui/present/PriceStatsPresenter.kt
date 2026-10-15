package app.many.economytycoon.ui.present

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.pricestats.PriceMath
import app.many.economytycoon.pricestats.PriceStats
import app.many.economytycoon.pricestats.RangeZone
import app.many.economytycoon.pricestats.Trend
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class PriceStatsModel(
    val headline: String,
    val tone: Tone,
    val facts: List<Fact>,
    val notes: List<String>,
)

data class MoverRow(val good: GoodId, val name: String, val change: String, val tone: Tone, val volatility: String)

object PriceStatsPresenter {
    /** Fewest days of trading needed before the figures mean anything. */
    const val MIN_DAYS = 10

    fun trendWord(t: Trend): String = when (t) {
        Trend.RISING -> "rising"
        Trend.FALLING -> "falling"
        Trend.FLAT -> "steady"
    }

    fun trendTone(t: Trend): Tone = when (t) {
        Trend.RISING -> Tone.WARN
        Trend.FALLING -> Tone.INFO
        Trend.FLAT -> Tone.NEUTRAL
    }

    fun zoneWord(z: RangeZone): String = when (z) {
        RangeZone.NEAR_LOW -> "near the bottom of its recent range"
        RangeZone.MIDDLE -> "in the middle of its recent range"
        RangeZone.NEAR_HIGH -> "near the top of its recent range"
    }

    fun volatilityWord(ppm: Long): String = when {
        ppm < 10_000L -> "very calm"
        ppm < 30_000L -> "calm"
        ppm < 80_000L -> "lively"
        else -> "wild"
    }

    fun strengthWord(strength: Int): String = when {
        strength >= 70 -> "mostly climbing"
        strength <= 30 -> "mostly sliding"
        else -> "balanced"
    }

    fun headline(st: PriceStats): String =
        "Price is ${trendWord(st.trend)} and ${zoneWord(st.zone)}; day to day it moves in a ${volatilityWord(st.volatilityPpm)} way."

    fun notes(st: PriceStats, closes: LongArray): List<String> {
        val out = ArrayList<String>()
        if (st.drawdownPpm >= 200_000L) out += "It fell ${Formatting.percentPpm(st.drawdownPpm)} from its peak at the worst point."
        if (st.zone == RangeZone.NEAR_HIGH && st.trend == Trend.RISING) out += "Buyers are paying near the highest price of the period; selling now earns more than usual."
        if (st.zone == RangeZone.NEAR_LOW && st.trend == Trend.FALLING) out += "Sellers are getting near the lowest price of the period; holding stock back may pay."
        val strength = PriceMath.strength(closes)
        if (strength != null && (strength >= 70 || strength <= 30)) out += "Recent days have been ${strengthWord(strength)}."
        if (st.volatilityPpm >= 80_000L) out += "Swings this large make fixed-price contracts risky for one side."
        return out
    }

    fun build(session: GameSession, good: GoodId): PriceStatsModel? {
        val closes = session.reports.market.averageHistory(good).map { it.raw }.toLongArray()
        if (closes.size < MIN_DAYS) return null
        val st = PriceMath.stats(closes) ?: return null
        val money = session.settings::money
        val strength = PriceMath.strength(closes)
        val facts = ArrayList<Fact>()
        facts += Fact("Lowest", money(Money(st.low)))
        facts += Fact("Highest", money(Money(st.high)))
        facts += Fact("Average", money(Money(st.mean)))
        facts += Fact("Over ${closes.size} days", Formatting.signedPercentPpm(st.changePpm), MarketPresenter.changeTone(st.changePpm))
        facts += Fact("Daily swing", Formatting.percentPpm(st.volatilityPpm), if (st.volatilityPpm >= 80_000L) Tone.WARN else Tone.NEUTRAL, "Typical size of one day's move")
        facts += Fact("Days up", Formatting.percentPpm(st.upDaysPpm))
        facts += Fact("Worst fall", Formatting.percentPpm(st.drawdownPpm), if (st.drawdownPpm >= 200_000L) Tone.WARN else Tone.NEUTRAL)
        facts += Fact("Since the high", Formatting.plural(PriceMath.daysSinceHigh(closes).toLong(), "day"))
        if (strength != null) facts += Fact("Recent momentum", strengthWord(strength))
        return PriceStatsModel(headline(st), trendTone(st.trend), facts, notes(st, closes))
    }

    /** Goods whose price moved most over their recorded history, biggest first. */
    fun movers(session: GameSession, limit: Int = 8): List<MoverRow> {
        val rows = ArrayList<Triple<MoverRow, Long, Long>>()
        for (quote in session.reports.market.board()) {
            val closes = session.reports.market.averageHistory(quote.good).map { it.raw }.toLongArray()
            if (closes.size < MIN_DAYS) continue
            val st = PriceMath.stats(closes) ?: continue
            rows += Triple(MoverRow(quote.good, quote.name, Formatting.signedPercentPpm(st.changePpm), MarketPresenter.changeTone(st.changePpm), Formatting.percentPpm(st.volatilityPpm)), kotlin.math.abs(st.changePpm), quote.good.key.hashCode().toLong())
        }
        return rows.sortedWith(compareByDescending<Triple<MoverRow, Long, Long>> { it.second }.thenBy { it.first.name }).take(limit).map { it.first }
    }
}
