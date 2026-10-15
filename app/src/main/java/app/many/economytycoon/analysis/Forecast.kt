package app.many.economytycoon.analysis

import app.many.economytycoon.core.Money

/** A projected value with a range around it. */
data class Projection(val day: Long, val expected: Money, val low: Money, val high: Money)

/**
 * Carries a recorded series forward along its fitted line. The range is the series' own typical distance from
 * the line, widening the further out the projection reaches, so a jumpy history gives a wide cone.
 * This is a rough guide for the player, not a prediction the simulation obeys.
 */
class Forecast private constructor(
    private val fit: LineFit,
    private val lastStep: Long,
    private val daysPerStep: Long,
    private val lastDay: Long,
) {
    val fitPpm: Long get() = fit.fitPpm
    val trendPerDay: Money get() = Money(fit.slopeScaled / LineFit.SCALE / daysPerStep)

    /** True when the line explains enough of the history to be worth showing. */
    val reliable: Boolean get() = fit.fitPpm >= RELIABLE_FIT_PPM && fit.points >= MIN_POINTS

    fun at(daysAhead: Long): Projection {
        val steps = lastStep + Math.max(0L, daysAhead) / daysPerStep
        val centre = fit.at(steps)
        val widen = 1L + daysAhead / (daysPerStep * 20L)
        val band = fit.spread * 2L * widen
        return Projection(lastDay + daysAhead, Money(centre), Money(centre - band), Money(centre + band))
    }

    /** Days from now until the line crosses [level]; null when it never does going forward. */
    fun daysUntil(level: Money): Long? {
        val slope = fit.slopeScaled
        if (slope == 0L) return null
        val now = fit.at(lastStep)
        val gap = level.raw - now
        if (gap == 0L) return 0L
        if ((gap > 0L) != (slope > 0L)) return null
        val steps = Math.floorDiv(Math.abs(gap) * LineFit.SCALE, Math.abs(slope))
        return steps * daysPerStep
    }

    companion object {
        const val RELIABLE_FIT_PPM = 600_000L
        const val MIN_POINTS = 6

        /** [samples] are evenly spaced [daysPerStep] days apart and end at day [lastDay]. */
        fun of(samples: List<Money>, daysPerStep: Long, lastDay: Long): Forecast? {
            val fit = Series.fit(samples.map { it.raw }) ?: return null
            return Forecast(fit, (samples.size - 1).toLong(), daysPerStep.coerceAtLeast(1L), lastDay)
        }
    }
}
