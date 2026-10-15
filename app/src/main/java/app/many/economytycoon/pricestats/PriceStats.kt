package app.many.economytycoon.pricestats

import kotlin.math.abs
import kotlin.math.sqrt

/** How the price has been moving, judged from its recent closes. */
enum class Trend { RISING, FALLING, FLAT }

/** Where the latest close sits in the recent range. */
enum class RangeZone { NEAR_LOW, MIDDLE, NEAR_HIGH }

/** Summary of a series of closing prices, in raw money units. Built only for display, never fed back into the economy. */
data class PriceStats(
    val count: Int,
    val last: Long,
    val low: Long,
    val high: Long,
    val mean: Long,
    /** Standard deviation of daily changes as ppm of the price. */
    val volatilityPpm: Long,
    /** Change from first to last close, signed ppm. */
    val changePpm: Long,
    /** Largest fall from a previous peak, ppm of that peak (never negative). */
    val drawdownPpm: Long,
    /** Position of the last close inside the low-to-high range, 0..1,000,000. */
    val rangePositionPpm: Long,
    val trend: Trend,
    /** Share of days the price rose, ppm of days with a change. */
    val upDaysPpm: Long,
) {
    val zone: RangeZone
        get() = when {
            rangePositionPpm <= ZONE_EDGE -> RangeZone.NEAR_LOW
            rangePositionPpm >= 1_000_000L - ZONE_EDGE -> RangeZone.NEAR_HIGH
            else -> RangeZone.MIDDLE
        }

    /** The spread between low and high as ppm of the mean. */
    val spreadPpm: Long get() = if (mean <= 0L) 0L else (high - low) * 1_000_000L / mean

    companion object {
        const val ZONE_EDGE = 200_000L
        const val MIN_POINTS = 3
        /** Net movement smaller than this over the window counts as flat. */
        const val FLAT_BAND_PPM = 15_000L
    }
}

object PriceMath {
    /** Statistics for closes ordered oldest first, or null when there are too few points to say anything. */
    fun stats(closes: LongArray): PriceStats? {
        if (closes.size < PriceStats.MIN_POINTS) return null
        val first = closes.first(); val last = closes.last()
        var low = Long.MAX_VALUE; var high = Long.MIN_VALUE; var sum = 0L
        for (c in closes) { if (c < low) low = c; if (c > high) high = c; sum += c }
        val mean = sum / closes.size
        val change = if (first <= 0L) 0L else (last - first) * 1_000_000L / first
        val position = if (high == low) 500_000L else (last - low) * 1_000_000L / (high - low)
        val trend = when {
            change > PriceStats.FLAT_BAND_PPM -> Trend.RISING
            change < -PriceStats.FLAT_BAND_PPM -> Trend.FALLING
            else -> Trend.FLAT
        }
        return PriceStats(
            count = closes.size, last = last, low = low, high = high, mean = mean,
            volatilityPpm = volatility(closes), changePpm = change, drawdownPpm = drawdown(closes),
            rangePositionPpm = position, trend = trend, upDaysPpm = upDays(closes),
        )
    }

    /** Standard deviation of one-day relative changes, ppm. */
    fun volatility(closes: LongArray): Long {
        val moves = relativeMoves(closes)
        if (moves.size < 2) return 0L
        val mean = moves.average()
        val variance = moves.sumOf { (it - mean) * (it - mean) } / (moves.size - 1)
        return sqrt(variance).toLong()
    }

    /** Relative one-day changes in ppm; days where the previous close was not positive are skipped. */
    fun relativeMoves(closes: LongArray): List<Double> {
        val out = ArrayList<Double>(closes.size)
        for (i in 1 until closes.size) {
            val prev = closes[i - 1]
            if (prev > 0L) out += (closes[i] - prev).toDouble() * 1_000_000.0 / prev
        }
        return out
    }

    fun drawdown(closes: LongArray): Long {
        var peak = Long.MIN_VALUE; var worst = 0L
        for (c in closes) {
            if (c > peak) peak = c
            if (peak > 0L) worst = maxOf(worst, (peak - c) * 1_000_000L / peak)
        }
        return worst
    }

    fun upDays(closes: LongArray): Long {
        var up = 0; var moved = 0
        for (i in 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            if (d != 0L) { moved++; if (d > 0L) up++ }
        }
        return if (moved == 0) 0L else up * 1_000_000L / moved
    }

    /** Simple moving average over the last [window] closes, or null when the series is shorter. */
    fun movingAverage(closes: LongArray, window: Int): Long? {
        require(window > 0) { "window must be positive" }
        if (closes.size < window) return null
        var s = 0L
        for (i in closes.size - window until closes.size) s += closes[i]
        return s / window
    }

    /** The whole moving-average line; entries before a full window are averages of what exists so far. */
    fun movingAverageLine(closes: LongArray, window: Int): LongArray {
        require(window > 0) { "window must be positive" }
        val out = LongArray(closes.size)
        var s = 0L
        for (i in closes.indices) {
            s += closes[i]
            if (i >= window) s -= closes[i - window]
            out[i] = s / minOf(i + 1, window)
        }
        return out
    }

    /**
     * Relative strength of recent gains against losses over [window] days, 0..100. 50 means balanced, above 70 the
     * price has mostly been climbing, below 30 mostly falling. Null when there is no movement to measure.
     */
    fun strength(closes: LongArray, window: Int = 14): Int? {
        if (closes.size < 2) return null
        val from = maxOf(1, closes.size - window)
        var gain = 0L; var loss = 0L
        for (i in from until closes.size) {
            val d = closes[i] - closes[i - 1]
            if (d > 0L) gain += d else loss -= d
        }
        if (gain == 0L && loss == 0L) return null
        return (100L * gain / (gain + loss)).toInt()
    }

    /** Days since the latest close was the highest in the series, 0 when it is the high. */
    fun daysSinceHigh(closes: LongArray): Int {
        if (closes.isEmpty()) return 0
        var at = 0
        for (i in closes.indices) if (closes[i] >= closes[at]) at = i
        return closes.size - 1 - at
    }

    fun daysSinceLow(closes: LongArray): Int {
        if (closes.isEmpty()) return 0
        var at = 0
        for (i in closes.indices) if (closes[i] <= closes[at]) at = i
        return closes.size - 1 - at
    }

    /** How alike two series move day to day, -1,000,000..1,000,000, over their shared recent length; null if too short or still. */
    fun correlationPpm(a: LongArray, b: LongArray): Long? {
        val n = minOf(a.size, b.size)
        if (n < PriceStats.MIN_POINTS) return null
        val x = relativeMoves(a.copyOfRange(a.size - n, a.size))
        val y = relativeMoves(b.copyOfRange(b.size - n, b.size))
        val m = minOf(x.size, y.size)
        if (m < 2) return null
        val xs = x.takeLast(m); val ys = y.takeLast(m)
        val mx = xs.average(); val my = ys.average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in 0 until m) { val dx = xs[i] - mx; val dy = ys[i] - my; sxy += dx * dy; sxx += dx * dx; syy += dy * dy }
        if (sxx == 0.0 || syy == 0.0) return null
        return (sxy / sqrt(sxx * syy) * 1_000_000.0).toLong().coerceIn(-1_000_000L, 1_000_000L)
    }

    /** Whether a price is stretched away from its average by more than [bandPpm]. */
    fun stretched(last: Long, average: Long, bandPpm: Long): Boolean = average > 0L && abs(last - average) * 1_000_000L / average > bandPpm
}
