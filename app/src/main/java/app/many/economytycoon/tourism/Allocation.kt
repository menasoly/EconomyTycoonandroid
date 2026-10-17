package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.core.splitLargestRemainder

/** Pure rules for deciding which property gets a room request. */
object Allocation {
    /**
     * Hands [requests] to slots in proportion to their weights, never giving a slot more than its capacity. What a
     * full slot cannot take is offered to the others, so the total allocated is as large as the capacities allow. The
     * result is exact and deterministic: ties on the remainder go to the lower index.
     */
    fun waterFill(requests: Long, weights: LongArray, capacities: IntArray): IntArray {
        require(weights.size == capacities.size) { "one weight per capacity" }
        val given = IntArray(weights.size)
        var remaining = requests
        var rounds = 0
        while (remaining > 0L && rounds <= weights.size) {
            rounds++
            val open = weights.indices.filter { weights[it] > 0L && capacities[it] - given[it] > 0 }
            if (open.isEmpty()) break
            val parts = splitLargestRemainder(remaining, LongArray(open.size) { weights[open[it]] })
            var moved = 0L
            for ((k, i) in open.withIndex()) {
                val take = minOf(parts[k], (capacities[i] - given[i]).toLong())
                given[i] += take.toInt()
                moved += take
            }
            if (moved == 0L) break
            remaining -= moved
        }
        return given
    }

    /** 1,000,000 when the stars are what the segment wants, falling 250,000 per star of difference, never below 100,000. */
    fun starMatchPpm(stars: Int, preferred: Int): Long = (1_000_000L - Math.abs(stars - preferred) * 250_000L).coerceAtLeast(100_000L)

    /** How much a price below the fair rate attracts and above repels, scaled by the segment's price sensitivity. */
    fun valueFactorPpm(rate: Money, fair: Money, sensitivityPpm: Long): Long {
        if (rate.raw <= 0L || fair.raw <= 0L) return 1_000_000L
        val ratio = mulDiv(fair.raw, 1_000_000L, rate.raw)
        return (1_000_000L + mulDiv(sensitivityPpm, ratio - 1_000_000L, 1_000_000L)).coerceIn(100_000L, 2_500_000L)
    }

    /** The weight with which a property competes for [segment] guests at a quoted [rate]; larger properties show more. */
    fun propertyWeight(p: Property, segment: VisitorSegment, rate: Money, fair: Money): Long {
        val prof = segment.profile
        val match = starMatchPpm(p.stars, prof.preferredStars)
        val review = 500_000L + p.reviews.scorePpm
        val value = valueFactorPpm(rate, fair, prof.priceSensitivityPpm)
        val quality = mulDiv(match, review, 1_000_000L)
        return mulDiv(quality, value, 1_000_000L) * (p.sellableTotal().toLong() + 1L)
    }

    /** What a standard room of the property would cost a guest at the destination's price level. */
    fun fairRate(p: Property, room: RoomClass, priceLevelPpm: Long): Money {
        val base = AccommodationRules.referenceRack(p.kind, p.stars)
        return Money(mulDiv(mulDiv(base.raw, room.rateMultiplierPpm, 1_000_000L), priceLevelPpm, 1_000_000L))
    }
}

/** Expected occupancy of a property for a future night, used by pricing and the advisor. */
object DemandForecast {
    const val DEFAULT_OCCUPANCY_PPM = 650_000L
    private const val WINDOW_DAYS = 28

    /** Average occupancy of the last weeks; a new property is assumed to do the default. */
    fun recentOccupancyPpm(p: Property): Long {
        val tail = p.days.takeLast(WINDOW_DAYS).filter { it.roomsAvailable > 0 }
        if (tail.isEmpty()) return DEFAULT_OCCUPANCY_PPM
        return tail.sumOf { it.occupancyPpm } / tail.size
    }

    /** How busy [stayDate] normally is against [tick], for a blend of segments, ppm (1,000,000 = same). */
    fun calendarRatioPpm(mixPpm: LongArray, tick: Long, stayDate: Long): Long {
        var now = 0L
        var then = 0L
        for (s in VisitorSegment.values()) {
            val w = mixPpm.getOrElse(s.ordinal) { 0L }
            now += w * Seasonality.monthIndexPpm(s, Tick(tick))
            then += w * Seasonality.monthIndexPpm(s, Tick(stayDate)) / 1_000_000L * Seasonality.normalisedWeekdayPpm(s, Tick(stayDate))
        }
        if (now <= 0L) return 1_000_000L
        return mulDiv(then, 1_000_000L, now).coerceIn(500_000L, 1_600_000L)
    }

    fun baselineOccupancyPpm(p: Property, tick: Long, stayDate: Long, mixPpm: LongArray): Long =
        mulDiv(recentOccupancyPpm(p), calendarRatioPpm(mixPpm, tick, stayDate), 1_000_000L).coerceIn(50_000L, 1_000_000L)

    /** Expected occupancy for a property for each of the next [weeks] weeks starting at [tick]. */
    fun weeklyOccupancyPpm(p: Property, tick: Long, mixPpm: LongArray, weeks: Int): List<Long> =
        (0 until weeks).map { w -> baselineOccupancyPpm(p, tick, tick + w * 7L + 3L, mixPpm) }
}
