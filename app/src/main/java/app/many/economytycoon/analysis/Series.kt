package app.many.economytycoon.analysis

import app.many.economytycoon.core.mulDiv
import java.math.BigInteger

/** A straight line fitted through a series, with how well it fits. */
data class LineFit(
    /** Change per step, in the series' own units times [SCALE]. */
    val slopeScaled: Long,
    /** Fitted value at step zero, in the series' units. */
    val intercept: Long,
    /** Share of the variation the line explains, in ppm (0 to 1,000,000). */
    val fitPpm: Long,
    /** Typical distance of the points from the line, in the series' units. */
    val spread: Long,
    val points: Int,
) {
    val slope: Long get() = slopeScaled / SCALE

    /** The line's value at [step], counting steps from the first sample. */
    fun at(step: Long): Long = intercept + Math.floorDiv(slopeScaled * step, SCALE)

    companion object { const val SCALE = 1_000L }
}

/** Plain statistics over series of whole numbers. Everything is integer arithmetic so results repeat exactly. */
object Series {
    fun mean(values: List<Long>): Long = if (values.isEmpty()) 0L else sum(values).divide(BigInteger.valueOf(values.size.toLong())).toLong()

    fun sum(values: List<Long>): BigInteger = values.fold(BigInteger.ZERO) { acc, v -> acc.add(BigInteger.valueOf(v)) }

    fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else Math.floorDiv(s[mid - 1] + s[mid], 2L)
    }

    /** The value below which [ppm] of the sample falls, by nearest rank. */
    fun percentile(values: List<Long>, ppm: Long): Long {
        if (values.isEmpty()) return 0L
        val s = values.sorted()
        val rank = ((ppm.coerceIn(0L, 1_000_000L) * (s.size - 1) + 500_000L) / 1_000_000L).toInt()
        return s[rank]
    }

    /** Where [value] stands among [values]: the share at or below it, with ties counting half. */
    fun rankPpm(values: List<Long>, value: Long): Long {
        if (values.isEmpty()) return 500_000L
        val below = values.count { it < value }
        val same = values.count { it == value }
        return (below * 2L + same) * 500_000L / values.size
    }

    /** Mean of each trailing window of [window] samples; shorter at the start so the lengths match. */
    fun movingAverage(values: List<Long>, window: Int): List<Long> {
        require(window >= 1) { "window must be at least 1" }
        val out = ArrayList<Long>(values.size)
        var running = 0L
        for (i in values.indices) {
            running += values[i]
            if (i >= window) running -= values[i - window]
            out += running / minOf(i + 1, window)
        }
        return out
    }

    /** Mean distance from the mean, as ppm of the mean's size. Zero for a flat or empty series. */
    fun volatilityPpm(values: List<Long>): Long {
        if (values.size < 2) return 0L
        val m = mean(values)
        val dev = mean(values.map { Math.abs(it - m) })
        val base = maxOf(Math.abs(m), 1L)
        return mulDiv(dev, 1_000_000L, base).coerceAtMost(1_000_000_000L)
    }

    /** Growth from the first value to the last in ppm; null when the start is zero or there is one value. */
    fun growthPpm(values: List<Long>): Long? {
        if (values.size < 2) return null
        val first = values.first()
        if (first == 0L) return null
        return mulDiv(values.last() - first, 1_000_000L, Math.abs(first)).coerceIn(-1_000_000_000L, 1_000_000_000L)
    }

    /** Length of the run of rises (positive) or falls (negative) that ends the series. */
    fun streak(values: List<Long>): Int {
        if (values.size < 2) return 0
        var n = 0
        var dir = 0
        for (i in values.size - 1 downTo 1) {
            val d = values[i].compareTo(values[i - 1])
            if (d == 0) break
            if (dir == 0) dir = d else if (d != dir) break
            n++
        }
        return n * dir
    }

    /** Least-squares line through the points taken one step apart; null with fewer than two points. */
    fun fit(values: List<Long>): LineFit? {
        val n = values.size
        if (n < 2) return null
        val bn = BigInteger.valueOf(n.toLong())
        var sx = BigInteger.ZERO; var sy = BigInteger.ZERO; var sxy = BigInteger.ZERO; var sxx = BigInteger.ZERO
        for (i in 0 until n) {
            val x = BigInteger.valueOf(i.toLong()); val y = BigInteger.valueOf(values[i])
            sx = sx.add(x); sy = sy.add(y); sxy = sxy.add(x.multiply(y)); sxx = sxx.add(x.multiply(x))
        }
        val denom = bn.multiply(sxx).subtract(sx.multiply(sx))
        val slopeNum = bn.multiply(sxy).subtract(sx.multiply(sy))
        val slopeScaled = slopeNum.multiply(BigInteger.valueOf(LineFit.SCALE)).divide(denom)
        // intercept = (sy - slope*sx) / n, with slope carried scaled
        val interceptNum = sy.multiply(BigInteger.valueOf(LineFit.SCALE)).subtract(slopeScaled.multiply(sx))
        val intercept = interceptNum.divide(bn.multiply(BigInteger.valueOf(LineFit.SCALE)))
        val slope = slopeScaled.toLong()
        val icpt = intercept.toLong()
        val fit0 = LineFit(slope, icpt, 0L, 0L, n)
        var ssRes = BigInteger.ZERO; var ssTot = BigInteger.ZERO
        val m = mean(values)
        var absRes = 0L
        for (i in 0 until n) {
            val r = values[i] - fit0.at(i.toLong())
            ssRes = ssRes.add(BigInteger.valueOf(r).pow(2))
            ssTot = ssTot.add(BigInteger.valueOf(values[i] - m).pow(2))
            absRes += Math.abs(r)
        }
        val fitPpm = if (ssTot.signum() == 0) 1_000_000L
        else BigInteger.valueOf(1_000_000L).subtract(ssRes.multiply(BigInteger.valueOf(1_000_000L)).divide(ssTot)).toLong().coerceIn(0L, 1_000_000L)
        return LineFit(slope, icpt, fitPpm, absRes / n, n)
    }
}
