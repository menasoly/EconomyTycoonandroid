package app.many.economytycoon.tourism

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Season
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.ClimateTable
import app.many.economytycoon.world.ClimateZone

/**
 * Pure functions for the calendar side of demand: how comfortable a climate is, how many people feel like travelling
 * in a given month, which days of the week they travel on, and how bookings arrive ahead of the stay.
 */
object Seasonality {
    /** Booking horizon in days. A stay is first bookable [HORIZON] - 1 days ahead; the last bucket also covers earlier bookings. */
    const val HORIZON = 14

    private val OUTBOUND = mapOf(
        VisitorSegment.BUDGET to longArrayOf(700, 750, 850, 950, 1050, 1250, 1450, 1450, 1050, 850, 750, 900),
        VisitorSegment.FAMILY to longArrayOf(700, 700, 800, 1000, 950, 1200, 1500, 1550, 900, 800, 700, 1200),
        VisitorSegment.BUSINESS to longArrayOf(900, 1000, 1150, 1150, 1150, 1050, 800, 650, 1150, 1250, 1150, 600),
        VisitorSegment.LUXURY to longArrayOf(950, 900, 950, 1050, 1000, 1050, 1250, 1150, 1050, 1000, 800, 850),
    )

    /** How warm the weather feels on a scale where 0 is mild: cooling demand minus heating demand, ppm. */
    fun warmth(zone: ClimateZone, season: Season): Long {
        val p = ClimateTable.profile(zone, season)
        return p.cooling - p.heating
    }

    /** How pleasant the climate is for [segment] in [season]: 1,000,000 when it is exactly the preferred warmth. */
    fun comfortPpm(zone: ClimateZone, season: Season, segment: VisitorSegment): Long {
        val diff = Math.abs(warmth(zone, season) - segment.profile.idealWarmthPpm)
        return (1_000_000L - diff * 55L / 100L).coerceIn(0L, 1_000_000L)
    }

    /** Share of the year's travel that [segment] undertakes in [month] (1..12) relative to an average month, ppm. */
    fun monthIndexPpm(segment: VisitorSegment, month: Int): Long {
        require(month in 1..Calendar.MONTHS_PER_YEAR) { "month out of range" }
        return (OUTBOUND.getValue(segment)[month - 1]) * 1_000L
    }

    fun monthIndexPpm(segment: VisitorSegment, tick: Tick): Long = monthIndexPpm(segment, Calendar.date(tick).month)

    /** Weekend days are the last two of the seven-day week. */
    fun isWeekend(tick: Tick): Boolean = Calendar.dayOfWeek(tick) >= 5

    fun weekdayPpm(segment: VisitorSegment, tick: Tick): Long =
        if (isWeekend(tick)) segment.profile.weekendPpm else segment.profile.weekdayPpm

    /** Mean of the weekday factors over a full week; used to keep the weekly average at one. */
    fun weekAveragePpm(segment: VisitorSegment): Long = (segment.profile.weekdayPpm * 5L + segment.profile.weekendPpm * 2L) / 7L

    /** Weekday factor scaled so that a whole week averages exactly its monthly level. */
    fun normalisedWeekdayPpm(segment: VisitorSegment, tick: Tick): Long =
        weekdayPpm(segment, tick) * 1_000_000L / weekAveragePpm(segment)

    /** Share of the requests for a stay that arrive on the day that is [lead] days ahead of it. */
    fun bookingShare(segment: VisitorSegment, lead: Int): Long = segment.profile.bookingCurve[lead.coerceIn(0, HORIZON - 1)]

    /**
     * Share of a stay's eventual bookings that were already made when [lead] days remain. At lead 0 this is everything
     * except the same-day requests; at the horizon it is zero.
     */
    fun bookedBeforeLeadPpm(segment: VisitorSegment, lead: Int): Long {
        val c = segment.profile.bookingCurve
        var sum = 0L
        for (k in lead.coerceIn(0, HORIZON - 1) + 1 until HORIZON) sum += c[k]
        return sum
    }

    /** The same pickup expectation for a blend of segments given as ppm weights in segment order. */
    fun bookedBeforeLeadPpm(mixPpm: LongArray, lead: Int): Long {
        var total = 0L
        var weight = 0L
        for (s in VisitorSegment.values()) {
            val w = mixPpm.getOrElse(s.ordinal) { 0L }
            total += w * bookedBeforeLeadPpm(s, lead)
            weight += w
        }
        return if (weight <= 0L) 0L else total / weight
    }

    /** The warm months: a convenience for tests and screens, the months with the highest combined index. */
    fun peakMonths(segment: VisitorSegment, count: Int = 3): List<Int> =
        (1..Calendar.MONTHS_PER_YEAR).sortedWith(compareByDescending<Int> { monthIndexPpm(segment, it) }.thenBy { it }).take(count).sorted()
}
