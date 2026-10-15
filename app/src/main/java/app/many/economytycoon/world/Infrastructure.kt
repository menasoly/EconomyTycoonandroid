package app.many.economytycoon.world

import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.SegmentId
import app.many.economytycoon.core.mulDiv

/** Speed in distance units per tick and base cost per weight-distance, by mode. */
object ModeProfile {
    fun speed(mode: TransportMode): Int = when (mode) {
        TransportMode.ROAD -> 90
        TransportMode.RAIL -> 140
        TransportMode.SEA -> 60
        TransportMode.AIR -> 600
    }

    /** Raw cost per unit per distance unit. */
    fun costPerUnitDistance(mode: TransportMode): Money = when (mode) {
        TransportMode.ROAD -> Money(300)
        TransportMode.RAIL -> Money(120)
        TransportMode.SEA -> Money(60)
        TransportMode.AIR -> Money(1_800)
    }

    /** Fixed handling cost to change mode, per unit. */
    fun transferCost(from: TransportMode, to: TransportMode): Money = if (from == to) Money.ZERO else Money(400)
    fun transferTicks(from: TransportMode, to: TransportMode): Int = if (from == to) 0 else 1
}

/** One link of the network between two cities. Links are undirected for routing. */
class Segment(
    val id: SegmentId,
    val a: CityId,
    val b: CityId,
    val mode: TransportMode,
    val length: Int,
    capacityPerTick: Quantity,
    quality: Long = 1_000_000L,
) {
    /** Maximum throughput per tick at full quality. */
    var capacity: Quantity = capacityPerTick
        internal set
    var qualityPpm: Long = quality
        internal set
    /** Load placed on the segment in the current tick. */
    var load: Quantity = Quantity.ZERO
        internal set
    var lifetimeLoad: Quantity = Quantity.ZERO
        internal set
    var closedUntil: Long = -1L
        internal set

    fun other(from: CityId): CityId = if (from == a) b else a
    fun isOpen(tick: Long) = tick >= closedUntil

    val effectiveCapacity: Quantity get() = Quantity(mulDiv(capacity.raw, qualityPpm, 1_000_000L))

    /** Congestion multiplier ppm for routing cost: 1.0 until 70% load, then rising steeply. */
    fun congestionPpm(): Long {
        val cap = effectiveCapacity.raw
        if (cap <= 0L) return 10_000_000L
        val u = mulDiv(load.raw, 1_000_000L, cap)
        return if (u <= 700_000L) 1_000_000L else 1_000_000L + (u - 700_000L) * 12
    }

    /** Remaining capacity this tick. */
    fun available(): Quantity = (effectiveCapacity - load).coerceAtLeast(Quantity.ZERO)

    /** Reserves [amount] on the segment, returning how much fit. */
    fun reserve(amount: Quantity): Quantity {
        val fit = Quantity.min(amount, available())
        load += fit
        lifetimeLoad += fit
        return fit
    }

    fun resetLoad() { load = Quantity.ZERO }

    /** Utilization of the segment this tick in ppm of effective capacity. */
    fun utilizationPpm(): Long {
        val cap = effectiveCapacity.raw
        return if (cap <= 0L) 1_000_000L else mulDiv(load.raw, 1_000_000L, cap).coerceAtMost(2_000_000L)
    }

    /** Wear from one tick of use: a small base decay plus extra under heavy load. Call before [resetLoad]. */
    fun wear() {
        qualityPpm = (qualityPpm - 20L - utilizationPpm() * 100L / 1_000_000L).coerceAtLeast(300_000L)
    }

    fun repair(amountPpm: Long) { qualityPpm = (qualityPpm + amountPpm).coerceAtMost(1_000_000L) }

    fun upgrade(extraCapacity: Quantity) { capacity += extraCapacity }

    internal fun restore(capacity: Quantity, quality: Long, closedUntil: Long, lifetime: Quantity) {
        this.capacity = capacity; qualityPpm = quality; this.closedUntil = closedUntil; lifetimeLoad = lifetime
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putQuantity(capacity).putLong(qualityPpm).putLong(closedUntil)
    }
}

/** A computed path through the network. */
data class Route(
    val from: CityId,
    val to: CityId,
    val segments: List<SegmentId>,
    val distance: Int,
    val ticks: Int,
    val costPerUnit: Money,
    val modes: List<TransportMode>,
)

/**
 * The transport network. Routes are found with Dijkstra over (city, arrival mode) states, with
 * deterministic tie-breaking, so the same network always yields the same path.
 */
class TransportNetwork {
    private val segments = java.util.TreeMap<SegmentId, Segment>()
    private val adjacency = java.util.TreeMap<CityId, ArrayList<SegmentId>>()
    private var routeCache = HashMap<Long, Route?>()
    private var version = 0

    fun add(s: Segment) {
        segments[s.id] = s
        adjacency.getOrPut(s.a) { ArrayList() }.add(s.id)
        adjacency.getOrPut(s.b) { ArrayList() }.add(s.id)
        invalidate()
    }

    fun segment(id: SegmentId): Segment? = segments[id]
    fun all(): List<Segment> = segments.values.toList()
    fun touching(city: CityId): List<Segment> = adjacency[city]?.mapNotNull { segments[it] } ?: emptyList()
    fun modesAt(city: CityId): Set<TransportMode> = touching(city).map { it.mode }.toSortedSet()
    fun neighbors(city: CityId): List<CityId> = touching(city).map { it.other(city) }.distinct().sorted()

    fun invalidate() { routeCache = HashMap(); version++ }
    fun resetLoads() { for (s in segments.values) s.resetLoad() }

    /** Applies wear for the tick that just ended and clears loads. */
    fun endTick() { for (s in segments.values) { s.wear(); s.resetLoad() } }

    fun close(id: SegmentId, until: Long) { segments[id]?.closedUntil = until; invalidate() }

    /** Cheapest route by cost weighted with time, or null if the cities are not connected. */
    fun route(from: CityId, to: CityId, tick: Long, allowed: Set<TransportMode> = TransportMode.values().toSet()): Route? {
        if (from == to) return Route(from, to, emptyList(), 0, 0, Money.ZERO, emptyList())
        val cacheable = allowed.size == TransportMode.values().size && tick >= 0 && segments.values.none { it.closedUntil > tick }
        val key = from.value * 1_000_003L + to.value
        if (cacheable) {
            val cached = routeCache[key]
            if (cached != null || routeCache.containsKey(key)) return cached
        }
        val result = search(from, to, tick, allowed)
        if (cacheable) routeCache[key] = result
        return result
    }

    private class State(val city: CityId, val mode: TransportMode?, val cost: Long, val prev: State?, val via: Segment?)

    private fun search(from: CityId, to: CityId, tick: Long, allowed: Set<TransportMode>): Route? {
        data class Key(val city: CityId, val mode: TransportMode?)
        val best = HashMap<Key, Long>()
        val queue = java.util.PriorityQueue<State>(compareBy<State>({ it.cost }, { it.city.value }, { it.mode?.ordinal ?: -1 }))
        val start = State(from, null, 0L, null, null)
        queue.add(start)
        best[Key(from, null)] = 0L
        var goal: State? = null
        while (queue.isNotEmpty()) {
            val cur = queue.poll()
            if (best[Key(cur.city, cur.mode)] != cur.cost) continue
            if (cur.city == to) { goal = cur; break }
            for (seg in touching(cur.city).sortedBy { it.id }) {
                if (seg.mode !in allowed || !seg.isOpen(tick)) continue
                val unitCost = ModeProfile.costPerUnitDistance(seg.mode).raw * seg.length
                val transfer = if (cur.mode != null) ModeProfile.transferCost(cur.mode, seg.mode).raw else 0L
                val timeCost = seg.length.toLong() * 1_000L / ModeProfile.speed(seg.mode) // time valued at 0.1 per tick
                val congestion = seg.congestionPpm()
                val qualityPenalty = 2_000_000L - seg.qualityPpm
                val step = (unitCost * congestion / 1_000_000L) * qualityPenalty / 1_000_000L + transfer + timeCost
                val next = State(seg.other(cur.city), seg.mode, cur.cost + step, cur, seg)
                val k = Key(next.city, next.mode)
                val known = best[k]
                if (known == null || next.cost < known) { best[k] = next.cost; queue.add(next) }
            }
        }
        val g = goal ?: return null
        val chain = ArrayList<State>()
        var s: State? = g
        while (s != null && s.via != null) { chain.add(s); s = s.prev }
        chain.reverse()
        var distance = 0
        var ticks = 0
        var cost = Money.ZERO
        var prevMode: TransportMode? = null
        val modes = ArrayList<TransportMode>()
        for (st in chain) {
            val seg = st.via!!
            distance += seg.length
            ticks += (seg.length + ModeProfile.speed(seg.mode) - 1) / ModeProfile.speed(seg.mode)
            cost += ModeProfile.costPerUnitDistance(seg.mode) * seg.length.toLong()
            if (prevMode != null) {
                cost += ModeProfile.transferCost(prevMode, seg.mode)
                ticks += ModeProfile.transferTicks(prevMode, seg.mode)
            }
            if (seg.mode != prevMode) modes.add(seg.mode)
            prevMode = seg.mode
        }
        return Route(from, to, chain.map { it.via!!.id }, distance, ticks.coerceAtLeast(1), cost, modes)
    }

    /** True when every city in [cities] is mutually reachable. */
    fun connected(cities: List<CityId>): Boolean {
        if (cities.size < 2) return true
        val seen = HashSet<CityId>()
        val stack = ArrayDeque<CityId>()
        stack.add(cities.first()); seen.add(cities.first())
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            for (n in neighbors(c)) if (seen.add(n)) stack.add(n)
        }
        return cities.all { it in seen }
    }

    fun hashInto(h: CanonicalHasher) { for (s in segments.values) s.hashInto(h) }
}
