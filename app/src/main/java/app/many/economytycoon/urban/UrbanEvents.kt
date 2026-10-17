package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

enum class UrbanEventKind(val label: String, val needsDistrict: Boolean) {
    GROWTH_BOOM("Growth boom", false),
    FLOOD("Flood", true),
    EARTHQUAKE("Earthquake", false),
    FIRE("Major fire", true),
    FACTORY_CLOSURE("Factory closure", true),
    TRANSIT_STRIKE("Transit strike", false),
    GENTRIFICATION_BACKLASH("Gentrification backlash", true),
}

/** Something that happened to a city and may still be going on. */
class UrbanEvent(
    val id: UrbanEventId,
    val kind: UrbanEventKind,
    /** District hit, or -1 when the event concerns the whole city. */
    val district: Int,
    val startedAt: Tick,
    val endsAt: Tick,
    val magnitudePermille: Int,
    /** Lines stopped by a strike, restored when it ends. */
    val lines: List<TransitLineId> = emptyList(),
) {
    var damageSqm: Long = 0L
        internal set
    var ended: Boolean = false
        internal set

    fun isActive(now: Tick): Boolean = !ended && now >= startedAt && now < endsAt

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putInt(kind.ordinal).putInt(district).putLong(startedAt.value).putLong(endsAt.value).putInt(magnitudePermille)
        h.putInt(lines.size)
        for (l in lines) h.putLong(l.value)
        h.putLong(damageSqm).putBoolean(ended)
    }
}

/** Event history and the effects that are in force. */
class EventBook {
    private val events = java.util.TreeMap<UrbanEventId, UrbanEvent>()

    fun all(): List<UrbanEvent> = events.values.toList()
    fun get(id: UrbanEventId): UrbanEvent? = events[id]
    fun active(now: Tick): List<UrbanEvent> = events.values.filter { it.isActive(now) }
    fun activeOfKind(kind: UrbanEventKind, now: Tick): List<UrbanEvent> = active(now).filter { it.kind == kind }
    fun isActive(kind: UrbanEventKind, district: Int, now: Tick): Boolean = activeOfKind(kind, now).any { it.district == district }
    fun recent(limit: Int): List<UrbanEvent> = events.values.sortedByDescending { it.startedAt }.take(limit)

    internal fun add(e: UrbanEvent) { events[e.id] = e }
    internal fun restore(e: UrbanEvent) { events[e.id] = e }
    internal fun clear() { events.clear() }

    /** Forgets events that ended long ago. */
    internal fun prune(now: Tick, keepDays: Long) {
        events.values.removeAll { it.ended && now.value - it.endsAt.value > keepDays }
    }

    /** Appetite of developers: 1.0 normally, higher during a boom. */
    fun boomPpm(now: Tick): Long = Scale.PPM + activeOfKind(UrbanEventKind.GROWTH_BOOM, now).sumOf { it.magnitudePermille * 1000L }

    /** How well assets are kept available, permille: floods, earthquakes and strikes take it down. */
    fun availabilityPermille(now: Tick): Int {
        var lost = 0
        for (e in active(now)) {
            lost += when (e.kind) {
                UrbanEventKind.FLOOD, UrbanEventKind.EARTHQUAKE -> e.magnitudePermille / 2
                UrbanEventKind.TRANSIT_STRIKE -> 250
                else -> 0
            }
        }
        return (1000 - lost).coerceIn(300, 1000)
    }

    /** Building in a district is frozen while protests are going on there. */
    fun developmentFrozen(district: Int, now: Tick): Boolean = isActive(UrbanEventKind.GENTRIFICATION_BACKLASH, district, now)

    fun hashInto(h: CanonicalHasher) {
        h.putInt(events.size)
        for (e in events.values) e.hashInto(h)
    }
}

/** Conditions that make events more or less likely this month. */
data class EventClimate(
    val coastal: Boolean,
    val fireQualityPermille: Int,
    val sanitationQualityPermille: Int,
    val hasIndustry: Boolean,
    val hasTransit: Boolean,
)

/**
 * Rolls the random events of a month. Exactly three draws are taken for each kind of event every time, whether or not
 * the event happens, so the random stream stays aligned however the city is doing.
 */
object EventGenerator {
    const val BOOM_PPM = 12_000L
    const val FLOOD_PPM = 6_000L
    const val QUAKE_PPM = 1_500L
    const val FIRE_PPM = 8_000L
    const val CLOSURE_PPM = 5_000L
    const val STRIKE_PPM = 6_000L

    /** Anger above which a district's protests turn into a movement. */
    const val BACKLASH_THRESHOLD_PPM = 650_000L

    fun roll(
        now: Tick, districts: List<District>, climate: EventClimate, active: EventBook, random: DeterministicRandom, nextId: () -> UrbanEventId,
        lines: List<TransitLine>,
    ): List<UrbanEvent> {
        val out = ArrayList<UrbanEvent>()
        for (kind in UrbanEventKind.values()) {
            if (kind == UrbanEventKind.GENTRIFICATION_BACKLASH) continue // not random: see backlash()
            val occurrence = random.nextRate().ppm
            val pick = random.nextLong(Long.MAX_VALUE)
            val magnitude = random.nextInRange(0L, 1000L).toInt()
            var chance = when (kind) {
                UrbanEventKind.GROWTH_BOOM -> BOOM_PPM
                UrbanEventKind.FLOOD -> if (climate.coastal) FLOOD_PPM * 2 else FLOOD_PPM
                UrbanEventKind.EARTHQUAKE -> QUAKE_PPM
                UrbanEventKind.FIRE -> mulDiv(FIRE_PPM, 1000L - climate.fireQualityPermille * 6L / 10L, 1000L)
                UrbanEventKind.FACTORY_CLOSURE -> if (climate.hasIndustry) CLOSURE_PPM else 0L
                UrbanEventKind.TRANSIT_STRIKE -> if (climate.hasTransit) STRIKE_PPM else 0L
                else -> 0L
            }
            if (kind == UrbanEventKind.GROWTH_BOOM && active.activeOfKind(kind, now).isNotEmpty()) chance = 0L
            if (kind == UrbanEventKind.TRANSIT_STRIKE && active.activeOfKind(kind, now).isNotEmpty()) chance = 0L
            if (occurrence >= chance) continue
            val district = if (kind.needsDistrict) pickDistrict(kind, districts, pick) else -1
            if (kind.needsDistrict && district < 0) continue
            val days = when (kind) {
                UrbanEventKind.GROWTH_BOOM -> 180 + magnitude * 180 / 1000
                UrbanEventKind.FLOOD -> 40 + magnitude * 50 / 1000
                UrbanEventKind.EARTHQUAKE -> 60 + magnitude * 120 / 1000
                UrbanEventKind.FIRE -> 20 + magnitude * 20 / 1000
                UrbanEventKind.FACTORY_CLOSURE -> 90
                UrbanEventKind.TRANSIT_STRIKE -> 5 + magnitude * 15 / 1000
                else -> 30
            }
            val strikeLines = if (kind == UrbanEventKind.TRANSIT_STRIKE) lines.filter { it.isActive }.map { it.id } else emptyList()
            out += UrbanEvent(nextId(), kind, district, now, Tick(now.value + days), if (kind == UrbanEventKind.GROWTH_BOOM) 300 + magnitude * 500 / 1000 else magnitude, strikeLines)
        }
        return out
    }

    /** Chooses the district for a kind of event; older and worse-kept stock burns and floods more readily. */
    private fun pickDistrict(kind: UrbanEventKind, districts: List<District>, pick: Long): Int {
        val weights = LongArray(districts.size) { i ->
            val d = districts[i]
            when (kind) {
                UrbanEventKind.FACTORY_CLOSURE -> if (d.zone == ZoneUse.INDUSTRIAL && d.floorArea > 0L) 1L + d.floorArea / 1000L else 0L
                UrbanEventKind.FLOOD -> if (d.floorArea > 0L) 1L + d.floorArea / 2000L else 0L
                else -> if (d.floorArea > 0L) 1L + (Scale.PPM - d.stock.averageConditionPpm) / 1000L + d.floorArea / 4000L else 0L
            }
        }
        val total = weights.sum()
        if (total == 0L) return -1
        var roll = pick % total
        for (i in weights.indices) {
            if (roll < weights[i]) return i
            roll -= weights[i]
        }
        return weights.lastIndex
    }

    /** Districts whose anger has crossed the threshold and that are not already protesting. */
    fun backlashes(now: Tick, districts: List<District>, book: EventBook, nextId: () -> UrbanEventId): List<UrbanEvent> {
        val out = ArrayList<UrbanEvent>()
        for (d in districts) {
            if (d.backlashPpm < BACKLASH_THRESHOLD_PPM) continue
            if (book.isActive(UrbanEventKind.GENTRIFICATION_BACKLASH, d.index, now)) continue
            val magnitude = (d.backlashPpm / 1000L).toInt().coerceIn(0, 1000)
            out += UrbanEvent(nextId(), UrbanEventKind.GENTRIFICATION_BACKLASH, d.index, now, Tick(now.value + 90), magnitude)
        }
        return out
    }

    fun describe(e: UrbanEvent, districtName: String?): String = when (e.kind) {
        UrbanEventKind.GROWTH_BOOM -> "A growth boom is drawing builders and newcomers to the city."
        UrbanEventKind.FLOOD -> "Flooding hit ${districtName ?: "the city"}."
        UrbanEventKind.EARTHQUAKE -> "An earthquake damaged buildings across the city."
        UrbanEventKind.FIRE -> "A major fire broke out in ${districtName ?: "the city"}."
        UrbanEventKind.FACTORY_CLOSURE -> "Factories closed in ${districtName ?: "the city"}, leaving workers without jobs."
        UrbanEventKind.TRANSIT_STRIKE -> "Transit workers are on strike; lines are suspended."
        UrbanEventKind.GENTRIFICATION_BACKLASH -> "Residents of ${districtName ?: "a district"} are protesting rising rents and displacement."
    }
}
