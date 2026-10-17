package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.core.splitLargestRemainder
import app.many.economytycoon.world.Point

/**
 * A place visitors come from. [outboundPerDay] is the number of people who start a trip on an average day and are
 * willing to consider the destinations of this world; the rest of their travel goes elsewhere. [mixPpm] says how
 * those trips divide between the segments.
 */
class OriginMarket(
    val id: OriginId,
    val name: String,
    val country: CountryId?,
    val location: Point,
) {
    var outboundPerDay: Long = 0L
        internal set

    /** Spending power relative to the reference, ppm. Richer markets send more luxury travel (reflected in [mixPpm]). */
    var incomePpm: Long = 1_000_000L
        internal set

    var mixPpm: LongArray = longArrayOf(400_000L, 300_000L, 200_000L, 100_000L)
        internal set

    internal fun configure(outbound: Long, income: Long, mix: LongArray) {
        require(outbound >= 0L) { "outbound trips cannot be negative" }
        require(mix.size == VisitorSegment.COUNT && mix.all { it >= 0L } && mix.sum() == 1_000_000L) { "the segment mix must sum to one" }
        outboundPerDay = outbound; incomePpm = income; mixPpm = mix.copyOf()
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putString(name).putLong(country?.value ?: -1L).putInt(location.x).putInt(location.y)
        h.putLong(outboundPerDay).putLong(incomePpm)
        for (m in mixPpm) h.putLong(m)
    }
}

/** Visitors of one segment from one market on one date. */
data class FlowCell(val origin: OriginId, val segment: VisitorSegment, val persons: Long)

/** What the flow model needs to know about a destination on a date. */
data class FlowCandidate(
    val destination: DestinationId,
    val country: CountryId,
    val location: Point,
    /** Appeal after price for each segment, ppm. */
    val valuePpm: LongArray,
    /** Shock and festival multipliers on arrivals for each segment, ppm of normal. */
    val arrivalsPpm: LongArray,
)

/** Pure model of who travels where. */
object VisitorFlows {
    /** Weight of travelling somewhere else instead, against a destination's squared appeal; see [allocate]. */
    const val OUTSIDE_WEIGHT_PPM = 250_000L

    /** Travel within the home country is easier, this much more attractive. */
    const val DOMESTIC_BONUS_PPM = 1_300_000L

    /** Distance decay: 1,000,000 at no distance, one half at the segment's tolerance. */
    fun distanceFactorPpm(distanceUnits: Int, toleranceUnits: Int): Long =
        mulDiv(1_000_000L, toleranceUnits.toLong(), toleranceUnits.toLong() + distanceUnits.coerceAtLeast(0).toLong())

    /** Trips a market starts on [tick] in [segment], before choosing a destination. */
    fun pool(origin: OriginMarket, segment: VisitorSegment, tick: Tick, originShockPpm: Long = 1_000_000L): Long {
        var n = mulDiv(origin.outboundPerDay, origin.mixPpm[segment.ordinal], 1_000_000L)
        n = mulDiv(n, Seasonality.monthIndexPpm(segment, tick), 1_000_000L)
        n = mulDiv(n, Seasonality.normalisedWeekdayPpm(segment, tick), 1_000_000L)
        return mulDiv(n, originShockPpm.coerceAtLeast(0L), 1_000_000L)
    }

    /** The weight with which a destination competes for the trips of one market and segment. */
    fun weight(origin: OriginMarket, segment: VisitorSegment, c: FlowCandidate): Long {
        val value = c.valuePpm[segment.ordinal].coerceAtLeast(0L)
        var w = mulDiv(value, value, 1_000_000L)
        val distance = origin.location.distanceTo(c.location)
        w = mulDiv(w, distanceFactorPpm(distance, segment.profile.distanceToleranceUnits), 1_000_000L)
        if (origin.country != null && origin.country == c.country) w = mulDiv(w, DOMESTIC_BONUS_PPM, 1_000_000L)
        return w
    }

    /**
     * Shares every market's trips out between the candidates and the outside option, exactly: the persons sent to
     * all destinations plus those staying away add up to the pool. Arrival multipliers (shocks, festivals) are applied
     * afterwards, so they change who arrives without moving trips between destinations. The result lists the cells of
     * each destination in market and segment order.
     */
    fun allocate(
        origins: List<OriginMarket>, candidates: List<FlowCandidate>, tick: Tick,
        originShockPpm: (OriginId) -> Long = { 1_000_000L },
    ): Map<DestinationId, List<FlowCell>> {
        val out = java.util.TreeMap<DestinationId, MutableList<FlowCell>>()
        for (c in candidates) out[c.destination] = ArrayList()
        if (candidates.isEmpty()) return out
        for (o in origins) {
            for (s in VisitorSegment.values()) {
                val pool = pool(o, s, tick, originShockPpm(o.id))
                if (pool <= 0L) continue
                val weights = LongArray(candidates.size + 1)
                for ((i, c) in candidates.withIndex()) weights[i] = weight(o, s, c)
                weights[candidates.size] = OUTSIDE_WEIGHT_PPM
                val parts = splitLargestRemainder(pool, weights)
                for ((i, c) in candidates.withIndex()) {
                    val n = mulDiv(parts[i], c.arrivalsPpm[s.ordinal].coerceAtLeast(0L), 1_000_000L)
                    if (n > 0L) out.getValue(c.destination) += FlowCell(o.id, s, n)
                }
            }
        }
        return out
    }

    /** Persons of [segment] in a list of cells. */
    fun persons(cells: List<FlowCell>, segment: VisitorSegment): Long = cells.filter { it.segment == segment }.sumOf { it.persons }

    fun total(cells: List<FlowCell>): Long = cells.sumOf { it.persons }
}
