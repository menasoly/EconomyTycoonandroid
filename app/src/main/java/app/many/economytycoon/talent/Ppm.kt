package app.many.economytycoon.talent

import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** Fixed-point helpers for ratios held as parts per million. Every talent ratio and skill level uses this scale. */
object Ppm {
    const val ONE = 1_000_000L

    /** a x b / 1.0. */
    fun mul(a: Long, b: Long): Long = mulDiv(a, b, ONE, RoundingMode.HALF_EVEN)

    /** [value] scaled by [ppm], rounded half-even. */
    fun of(value: Long, ppm: Long): Long = mulDiv(value, ppm, ONE, RoundingMode.HALF_EVEN)

    /** numerator / denominator as ppm; zero when the denominator is not positive. */
    fun ratio(numerator: Long, denominator: Long): Long =
        if (denominator <= 0L) 0L else mulDiv(numerator, ONE, denominator, RoundingMode.HALF_EVEN)

    fun clamp(v: Long): Long = v.coerceIn(0L, ONE)

    fun clamp(v: Long, lo: Long, hi: Long): Long = v.coerceIn(lo, hi)

    fun lerp(a: Long, b: Long, t: Long): Long = a + mulDiv(b - a, clamp(t), ONE, RoundingMode.HALF_EVEN)

    /** Moves [current] toward [target] by [rate] of the gap, at least one unit, never overshooting. */
    fun approach(current: Long, target: Long, rate: Long): Long {
        val gap = target - current
        if (gap == 0L) return current
        val move = mulDiv(gap, rate.coerceIn(0L, ONE), ONE, RoundingMode.HALF_EVEN)
        val applied = if (move == 0L) (if (gap > 0) 1L else -1L) else move
        return if (gap > 0) minOf(current + applied, target) else maxOf(current + applied, target)
    }

    /** The part of an annual ppm rate that falls on [days] days of a 360-day year. */
    fun perDays(annualPpm: Long, days: Long): Long = mulDiv(annualPpm, days, 360L, RoundingMode.HALF_EVEN)

    fun rate(ppm: Long): Rate = Rate(ppm)

    /** Smooth 0..1 response that rises with [x] between [lo] and [hi] (smoothstep on integers). */
    fun smooth(x: Long, lo: Long, hi: Long): Long {
        if (hi <= lo) return if (x >= hi) ONE else 0L
        val t = clamp(ratio(x - lo, hi - lo))
        val t2 = mul(t, t)
        return clamp(3 * t2 - 2 * mul(t2, t))
    }
}
