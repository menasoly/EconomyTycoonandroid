package app.many.economytycoon.tourism

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Season
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.world.ClimateZone

/**
 * Things that happen to tourism from outside. Each kind has a full-strength effect; a shock's severity scales how far
 * the effect departs from normal. The ordinal is part of the save format.
 */
enum class ShockKind(
    val label: String,
    /** Multiplier on arrivals at full severity, ppm of normal. */
    val arrivalsPpm: Long,
    /** Drop in how safe the place feels, ppm. */
    val safetyDropPpm: Long,
    /** Share of normal staff that can work, ppm. */
    val staffPpm: Long,
    /** Extra cancellations on every booking, ppm. */
    val cancelExtraPpm: Long,
    /** Share of normal transport capacity that remains, ppm. */
    val transportPpm: Long,
    /** Pull of an event on the destination, ppm; zero for bad news. */
    val eventPpm: Long,
    val isBad: Boolean,
) {
    DISEASE("Disease outbreak", 450_000L, 300_000L, 700_000L, 250_000L, 800_000L, 0L, true),
    STRIKE("Strike", 850_000L, 0L, 400_000L, 80_000L, 600_000L, 0L, true),
    SAFETY_SCARE("Safety scare", 600_000L, 600_000L, 1_000_000L, 200_000L, 900_000L, 0L, true),
    WEATHER("Severe weather", 700_000L, 100_000L, 850_000L, 150_000L, 500_000L, 0L, true),
    FESTIVAL("Festival", 1_350_000L, 0L, 1_000_000L, 0L, 1_000_000L, 800_000L, false),
    CONFERENCE("Conference", 1_150_000L, 0L, 1_000_000L, 0L, 1_000_000L, 600_000L, false),
    TRANSPORT_DISRUPTION("Transport disruption", 800_000L, 0L, 1_000_000L, 100_000L, 350_000L, 0L, true);

    /** Multiplier a shock of this kind has on the arrivals of [segment], before severity. */
    fun segmentBiasPpm(segment: VisitorSegment): Long = when (this) {
        CONFERENCE -> if (segment == VisitorSegment.BUSINESS) 1_000_000L else 250_000L
        FESTIVAL -> when (segment) {
            VisitorSegment.FAMILY, VisitorSegment.LUXURY -> 1_000_000L
            VisitorSegment.BUDGET -> 800_000L
            VisitorSegment.BUSINESS -> 100_000L
        }
        STRIKE -> if (segment == VisitorSegment.BUSINESS) 1_000_000L else 600_000L
        else -> 1_000_000L
    }
}

/** A shock at work. A shock with neither destination nor origin touches every destination. */
class Shock(
    val id: ShockId,
    val kind: ShockKind,
    val destination: DestinationId?,
    val origin: OriginId?,
    val start: Tick,
    val durationDays: Int,
    val severityPpm: Long,
    val label: String,
) {
    init {
        require(durationDays >= 1) { "a shock lasts at least a day" }
        require(severityPpm in 1..1_000_000L) { "severity is between 0 and 1,000,000" }
    }

    val end: Tick get() = start + durationDays.toLong()
    fun activeAt(tick: Tick): Boolean = tick >= start && tick < end
    fun touches(dest: DestinationId): Boolean = destination == null || destination == dest

    /** Fraction of the shock still ahead, ppm; used by news and by recovery. */
    fun remainingPpm(tick: Tick): Long = if (tick >= end) 0L else if (tick <= start) 1_000_000L else mulDiv((end - tick), 1_000_000L, durationDays.toLong())

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putInt(kind.ordinal).putLong(destination?.value ?: -1L).putLong(origin?.value ?: -1L)
        h.putLong(start.value).putInt(durationDays).putLong(severityPpm).putString(label)
    }
}

/** The combined effect of the shocks active on a date. */
data class ShockEffects(
    val arrivalsPpm: LongArray,
    val safetyDropPpm: Long,
    val staffPpm: Long,
    val cancelExtraPpm: Long,
    val transportPpm: Long,
    val eventPpm: Long,
) {
    fun arrivals(segment: VisitorSegment): Long = arrivalsPpm[segment.ordinal]

    override fun equals(other: Any?): Boolean = other is ShockEffects && arrivalsPpm.contentEquals(other.arrivalsPpm) &&
        safetyDropPpm == other.safetyDropPpm && staffPpm == other.staffPpm && cancelExtraPpm == other.cancelExtraPpm &&
        transportPpm == other.transportPpm && eventPpm == other.eventPpm

    override fun hashCode(): Int = arrivalsPpm.contentHashCode() * 31 + safetyDropPpm.hashCode()

    companion object {
        val NONE = ShockEffects(LongArray(VisitorSegment.COUNT) { 1_000_000L }, 0L, 1_000_000L, 0L, 1_000_000L, 0L)

        /** Scales a multiplier toward one by the severity. */
        private fun scaled(full: Long, severity: Long): Long = 1_000_000L + mulDiv(full - 1_000_000L, severity, 1_000_000L)

        /** The effect of one shock. */
        fun of(shock: Shock): ShockEffects {
            val k = shock.kind
            val sev = shock.severityPpm
            val arrivals = LongArray(VisitorSegment.COUNT) { i ->
                val bias = k.segmentBiasPpm(VisitorSegment.values()[i])
                scaled(k.arrivalsPpm, mulDiv(sev, bias, 1_000_000L))
            }
            return ShockEffects(
                arrivals, mulDiv(k.safetyDropPpm, sev, 1_000_000L), scaled(k.staffPpm, sev), mulDiv(k.cancelExtraPpm, sev, 1_000_000L),
                scaled(k.transportPpm, sev), mulDiv(k.eventPpm, sev, 1_000_000L),
            )
        }

        /** Multipliers multiply, drops and extras add (capped at one), event strengths take the largest. */
        fun combine(effects: List<ShockEffects>): ShockEffects {
            if (effects.isEmpty()) return NONE
            val arrivals = LongArray(VisitorSegment.COUNT) { 1_000_000L }
            var safety = 0L
            var staff = 1_000_000L
            var cancel = 0L
            var transport = 1_000_000L
            var event = 0L
            for (e in effects) {
                for (i in arrivals.indices) arrivals[i] = mulDiv(arrivals[i], e.arrivalsPpm[i], 1_000_000L)
                safety = (safety + e.safetyDropPpm).coerceAtMost(1_000_000L)
                staff = mulDiv(staff, e.staffPpm, 1_000_000L)
                cancel = (cancel + e.cancelExtraPpm).coerceAtMost(1_000_000L)
                transport = mulDiv(transport, e.transportPpm, 1_000_000L)
                event = maxOf(event, e.eventPpm)
            }
            return ShockEffects(arrivals, safety, staff, cancel, transport, event)
        }
    }
}

/** Seeded generation of random shocks and the fixed yearly festival. Pure given the stream. */
object ShockGenerator {
    /** Rolls are made once a week per destination. */
    const val ROLL_EVERY_DAYS = 7L

    /** Chance per weekly roll, ppm, of each kind that can start at random. */
    fun chancePpm(kind: ShockKind, zone: ClimateZone, season: Season): Long = when (kind) {
        ShockKind.DISEASE -> if (season == Season.WINTER) 3_000L else 1_200L
        ShockKind.STRIKE -> if (season == Season.AUTUMN || season == Season.SPRING) 5_000L else 2_500L
        ShockKind.SAFETY_SCARE -> 1_500L
        ShockKind.CONFERENCE -> if (season == Season.SUMMER) 4_000L else 9_000L
        ShockKind.TRANSPORT_DISRUPTION -> if (zone == ClimateZone.COLD && season == Season.WINTER) 12_000L else 3_000L
        ShockKind.WEATHER, ShockKind.FESTIVAL -> 0L
    }

    private val RANDOM_KINDS = listOf(
        ShockKind.DISEASE, ShockKind.STRIKE, ShockKind.SAFETY_SCARE, ShockKind.CONFERENCE, ShockKind.TRANSPORT_DISRUPTION,
    )

    private fun durationFor(kind: ShockKind, rnd: DeterministicRandom): Int = when (kind) {
        ShockKind.DISEASE -> 30 + rnd.nextInt(60)
        ShockKind.STRIKE -> 3 + rnd.nextInt(12)
        ShockKind.SAFETY_SCARE -> 14 + rnd.nextInt(30)
        ShockKind.CONFERENCE -> 3 + rnd.nextInt(4)
        ShockKind.TRANSPORT_DISRUPTION -> 2 + rnd.nextInt(6)
        ShockKind.WEATHER -> 3 + rnd.nextInt(10)
        ShockKind.FESTIVAL -> 7
    }

    /**
     * Rolls for new shocks at a destination on [tick]. Returns the kinds, durations and severities to create; the caller
     * allocates ids. Nothing happens except on roll days, and a kind that is already active is not repeated.
     */
    fun roll(tick: Tick, zone: ClimateZone, active: Set<ShockKind>, rnd: DeterministicRandom): List<Triple<ShockKind, Int, Long>> {
        if (tick.value % ROLL_EVERY_DAYS != 0L) return emptyList()
        val season = Calendar.season(tick)
        val out = ArrayList<Triple<ShockKind, Int, Long>>()
        for (kind in RANDOM_KINDS) {
            // The draw is made for every kind, active or not, so the stream does not depend on what is going on.
            val hit = rnd.nextLong(1_000_000L) < chancePpm(kind, zone, season)
            val duration = durationFor(kind, rnd)
            val severity = 300_000L + rnd.nextLong(600_000L)
            if (hit && kind !in active) out += Triple(kind, duration, severity)
        }
        return out
    }

    /** Whether the destination's yearly festival is running on [tick], and with what pull (ppm). */
    fun festivalPpm(festivalMonth: Int, festivalDays: Int, tick: Tick): Long {
        if (festivalMonth !in 1..Calendar.MONTHS_PER_YEAR) return 0L
        val date = Calendar.date(tick)
        if (date.month != festivalMonth || date.day > festivalDays) return 0L
        return ShockKind.FESTIVAL.eventPpm
    }

    /** Arrival multiplier of the festival for a segment on [tick], ppm. */
    fun festivalArrivalsPpm(segment: VisitorSegment, festivalMonth: Int, festivalDays: Int, tick: Tick): Long =
        if (festivalPpm(festivalMonth, festivalDays, tick) == 0L) 1_000_000L
        else 1_000_000L + mulDiv(ShockKind.FESTIVAL.arrivalsPpm - 1_000_000L, ShockKind.FESTIVAL.segmentBiasPpm(segment), 1_000_000L)
}
