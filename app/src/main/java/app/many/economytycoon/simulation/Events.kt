package app.many.economytycoon.simulation

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick

/** Something that happens to the economy from outside the players' and companies' decisions. */
sealed class EconomicEvent {
    abstract val title: String

    /** Price pressure on a good everywhere; positive means scarcity. */
    data class SupplyShock(val good: GoodId, val ppm: Long, override val title: String) : EconomicEvent()
    /** Lasting change in how much of a good households want, ppm around 1,000,000. */
    data class DemandShift(val good: GoodId, val ppm: Long, override val title: String) : EconomicEvent()
    /** Mood of a country's economy swings. */
    data class ConfidenceShock(val country: CountryId, val ppm: Long, override val title: String) : EconomicEvent()
    /** Ends a demand shift by restoring neutral demand. */
    data class DemandRestored(val good: GoodId, override val title: String) : EconomicEvent()
}

class ScheduledEvent(val sequence: Long, val tick: Tick, val event: EconomicEvent)

/** Events waiting for their tick, applied in schedule order. */
class EventQueue {
    private val pending = java.util.TreeMap<Pair<Long, Long>, ScheduledEvent>(compareBy({ it.first }, { it.second }))
    private var seq = 0L
    val history = ArrayList<ScheduledEvent>()
    private val shifts = java.util.TreeMap<GoodId, Long>()

    /** Lasting demand multiplier set by events for a good; 1,000,000 means no change. */
    fun demandShift(good: GoodId): Long = shifts[good] ?: 1_000_000L
    fun setDemandShift(good: GoodId, ppm: Long) { if (ppm == 1_000_000L) shifts.remove(good) else shifts[good] = ppm }
    internal fun shiftEntries(): List<Pair<GoodId, Long>> = shifts.map { it.key to it.value }
    internal fun restoreShifts(list: List<Pair<GoodId, Long>>) { shifts.clear(); for ((g, v) in list) shifts[g] = v }

    fun schedule(at: Tick, event: EconomicEvent): ScheduledEvent {
        val s = ScheduledEvent(seq++, at, event)
        pending[at.value to s.sequence] = s
        return s
    }

    fun due(tick: Tick): List<ScheduledEvent> {
        val out = ArrayList<ScheduledEvent>()
        while (pending.isNotEmpty() && pending.firstKey().first <= tick.value) out += pending.pollFirstEntry().value
        history += out
        while (history.size > MAX_HISTORY) history.removeAt(0)
        return out
    }

    fun pendingEvents(): List<ScheduledEvent> = pending.values.toList()

    internal fun restore(next: Long, list: List<ScheduledEvent>) {
        pending.clear(); history.clear(); seq = next
        for (s in list) pending[s.tick.value to s.sequence] = s
    }

    internal fun nextSequence() = seq

    fun hashInto(h: CanonicalHasher) {
        h.putLong(seq).putInt(shifts.size)
        for ((g, v) in shifts) h.putString(g.key).putLong(v)
        h.putInt(pending.size)
        for (s in pending.values) h.putLong(s.sequence).putLong(s.tick.value).putString(s.event.javaClass.simpleName).putString(s.event.title)
    }

    companion object { const val MAX_HISTORY = 100 }
}

/** Draws occasional shocks from a dedicated stream so a seed always has the same history. */
class EventGenerator(private val e: Economy, private val intervalTicks: Long = 30L) {
    private val goods: List<GoodId> by lazy { e.content.allDemandProfiles.map { it.good }.sortedBy { it.key } }

    fun generate(tick: Tick, queue: EventQueue) {
        if (tick.value == 0L || tick.value % intervalTicks != 0L) return
        val rnd = e.random.stream("events", tick.value)
        if (!rnd.chance(Rate(450_000L))) return
        val countries = e.world.countries().sortedBy { it.id }
        when (rnd.nextInt(4)) {
            0 -> if (goods.isNotEmpty()) {
                val g = goods[rnd.nextInt(goods.size)]
                val ppm = rnd.nextInRange(80_000L, 260_000L)
                queue.schedule(tick, EconomicEvent.SupplyShock(g, ppm, "Supply disruption hits ${e.content.good(g).name}"))
            }
            1 -> if (goods.isNotEmpty()) {
                val g = goods[rnd.nextInt(goods.size)]
                val up = rnd.chance(Rate(500_000L))
                val ppm = if (up) 1_000_000L + rnd.nextInRange(80_000L, 250_000L) else 1_000_000L - rnd.nextInRange(60_000L, 200_000L)
                queue.schedule(tick, EconomicEvent.DemandShift(g, ppm, if (up) "Fad lifts ${e.content.good(g).name}" else "Tastes turn from ${e.content.good(g).name}"))
                queue.schedule(Tick(tick.value + 20 + rnd.nextInt(40)), EconomicEvent.DemandRestored(g, "Demand for ${e.content.good(g).name} normalises"))
            }
            2 -> if (countries.isNotEmpty()) {
                val c = countries[rnd.nextInt(countries.size)]
                val ppm = rnd.nextInRange(-120_000L, 120_000L)
                queue.schedule(tick, EconomicEvent.ConfidenceShock(c.id, ppm, if (ppm >= 0) "Optimism spreads in ${c.name}" else "Nerves fray in ${c.name}"))
            }
            else -> Unit
        }
    }
}
