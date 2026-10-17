package app.many.economytycoon.marketing

import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv

/** Shared fixed-point helpers for ratios held in parts per million. */
internal object Ppm {
    const val ONE = Rate.ONE_PPM

    fun mul(a: Long, b: Long): Long = mulDiv(a, b, ONE)

    fun clamp(v: Long): Long = v.coerceIn(0L, ONE)

    /** a/b as ppm, zero when [b] is not positive. */
    fun ratio(a: Long, b: Long): Long = if (b <= 0L) 0L else mulDiv(a, ONE, b)

    /** The probability that at least one of independent exposures lands: 1 - product(1 - p). */
    fun union(parts: LongArray): Long {
        var miss = ONE
        for (p in parts) miss = mul(miss, ONE - clamp(p))
        return ONE - miss
    }

    /** [value] less [ratePpm] of itself, but by at least one when anything is left, so small stocks still fade to nothing. */
    fun shrink(value: Long, ratePpm: Long): Long {
        if (value <= 0L || ratePpm <= 0L) return value
        return value - mul(value, ratePpm).coerceAtLeast(1L)
    }

    /** One step of [now] toward [target] by [ratePpm] of the gap, moving at least one unit so it always arrives. */
    fun approach(now: Long, target: Long, ratePpm: Long): Long {
        if (now == target || ratePpm <= 0L) return now
        val step = mul(target - now, ratePpm)
        return if (step != 0L) now + step else now + (if (target > now) 1L else -1L)
    }

    /** Value after [days] days of exponential decay at [ratePpm] per day, by repeated multiplication. */
    fun decay(value: Long, ratePpm: Long, days: Int): Long {
        var v = value
        repeat(days.coerceAtLeast(0)) { v = shrink(v, ratePpm) }
        return v
    }
}
