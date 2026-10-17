package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

const val UNREACHABLE = -1L

/** Shortest drive between every pair of districts at the current traffic, plus the roads used. */
class CarTable(val n: Int, private val seconds: LongArray, private val prevLink: Array<RoadLink?>) {
    /** Generalised seconds (driving time plus tolls expressed as time); [UNREACHABLE] if no road connects them. */
    fun seconds(o: Int, d: Int): Long = seconds[o * n + d]

    /** Roads of the best route from [o] to [d], in driving order. */
    fun path(o: Int, d: Int): List<RoadLink> {
        if (o == d || seconds(o, d) == UNREACHABLE) return emptyList()
        val out = ArrayList<RoadLink>()
        var at = d
        while (at != o) {
            val link = prevLink[o * n + at] ?: return emptyList()
            out += link
            at = link.other(at)
        }
        out.reverse()
        return out
    }
}

/** One ride on one line, from one stop to another. */
data class TransitLeg(val line: TransitLineId, val from: Int, val to: Int)

/** Shortest public transport journey between every pair of districts, including waiting and transfers. */
class TransitTable(val n: Int, private val seconds: LongArray, private val legs: Array<List<TransitLeg>>) {
    fun seconds(o: Int, d: Int): Long = seconds[o * n + d]
    fun legs(o: Int, d: Int): List<TransitLeg> = legs[o * n + d]
    fun transfers(o: Int, d: Int): Int = (legs(o, d).size - 1).coerceAtLeast(0)
}

/** Travel time tables for each way of getting around, built from the network at its current load. */
object TravelTimes {
    /** Seconds a driver spends finding parking and reaching the destination on foot. */
    const val PARKING_SECONDS = 420L
    private const val TRANSFER_WALK_SECONDS = 120L
    private const val BOARDING_SECONDS = 30L

    /** Seconds one unit of money is worth, from the value of time. */
    fun secondsPerUnit(params: UrbanParams): Long = 3600L / params.fareUnitsPerHourOfTime.coerceAtLeast(1L)

    /** A fare or toll expressed as seconds of travel time. */
    fun moneyAsSeconds(m: Money, params: UrbanParams): Long = mulDiv(m.raw, secondsPerUnit(params), Money.SCALE)

    /** Walking time in seconds; within a district it is half a block. */
    fun walk(geometry: CityGeometry, params: UrbanParams): LongArray {
        val n = geometry.districtCount
        val speed = params.walkSpeedMetersPerSec.coerceAtLeast(1L)
        val out = LongArray(n * n)
        val within = geometry.blockSize * geometry.cellMeters / 2L
        for (o in 0 until n) for (d in 0 until n) {
            out[o * n + d] = (if (o == d) within else geometry.distanceMeters(o, d)) / speed
        }
        return out
    }

    /** Seconds to walk to and from a stop: a quarter of a block each way. */
    fun accessSeconds(geometry: CityGeometry, params: UrbanParams): Long =
        geometry.blockSize * geometry.cellMeters / 4L / params.walkSpeedMetersPerSec.coerceAtLeast(1L)

    fun car(network: TransitNetwork, params: UrbanParams): CarTable {
        val n = network.geometry.districtCount
        val seconds = LongArray(n * n) { UNREACHABLE }
        val prev = arrayOfNulls<RoadLink>(n * n)
        val weight = HashMap<LinkId, Long>()
        for (r in network.roads()) weight[r.id] = r.currentSeconds() + moneyAsSeconds(r.toll, params)
        for (o in 0 until n) {
            val dist = LongArray(n) { Long.MAX_VALUE }
            val done = BooleanArray(n)
            dist[o] = 0L
            while (true) {
                var best = -1
                for (i in 0 until n) if (!done[i] && dist[i] != Long.MAX_VALUE && (best == -1 || dist[i] < dist[best])) best = i
                if (best == -1) break
                done[best] = true
                for (r in network.roadsAt(best)) {
                    val next = r.other(best)
                    val cand = dist[best] + (weight[r.id] ?: 0L)
                    if (cand < dist[next] || (cand == dist[next] && !done[next] && prev[o * n + next]?.let { r.id < it.id } == true)) {
                        dist[next] = cand
                        prev[o * n + next] = r
                    }
                }
            }
            for (d in 0 until n) if (dist[d] != Long.MAX_VALUE) seconds[o * n + d] = dist[d]
        }
        return CarTable(n, seconds, prev)
    }

    /** In-vehicle seconds along the segment of [line] between consecutive stops [from] and [to], at its current crowding. */
    fun segmentSeconds(line: TransitLine, from: Int, to: Int, geometry: CityGeometry, params: UrbanParams, roadDelayPpm: Long): Long {
        val dist = geometry.distanceMeters(from, to)
        val speed = when (line.mode) {
            TransitMode.BUS -> mulDiv(params.carFreeFlowMetersPerSec, params.busSpeedFactorPpm, Scale.PPM)
            TransitMode.METRO -> params.metroSpeedMetersPerSec
            TransitMode.RAIL -> params.railSpeedMetersPerSec
        }.coerceAtLeast(1L)
        var t = dist / speed + line.mode.dwellSeconds
        if (line.mode == TransitMode.BUS && !line.dedicatedLane) t = mulDiv(t, roadDelayPpm, Scale.PPM)
        return mulDiv(t, Congestion.crowdingFactorPpm(line.loadRatioPpm), Scale.PPM)
    }

    /**
     * Public transport journey times over the active lines. Boarding costs half a headway of waiting; changing lines costs
     * a short walk and the next wait. [roadDelayPpm] is the average slowdown of roads, which buses without their own lane
     * share.
     */
    fun transit(network: TransitNetwork, params: UrbanParams, roadDelayPpm: Long): TransitTable {
        val geometry = network.geometry
        val n = geometry.districtCount
        val lines = network.activeLines()
        val m = lines.size
        val seconds = LongArray(n * n) { UNREACHABLE }
        val legsOut = Array(n * n) { emptyList<TransitLeg>() }
        if (m == 0) return TransitTable(n, seconds, legsOut)
        val access = accessSeconds(geometry, params)
        // state index: district * (m + 1) + k, where k == m means "on foot / between lines"
        val stride = m + 1
        val states = n * stride
        val seg = HashMap<Triple<Int, Int, Int>, Long>()
        for ((k, line) in lines.withIndex()) {
            for ((x, y) in line.stops.zipWithNext()) {
                val t = segmentSeconds(line, x, y, geometry, params, roadDelayPpm)
                seg[Triple(k, x, y)] = t
                seg[Triple(k, y, x)] = t
            }
        }
        for (o in 0 until n) {
            val dist = LongArray(states) { Long.MAX_VALUE }
            val prev = IntArray(states) { -1 }
            val done = BooleanArray(states)
            dist[o * stride + m] = access
            while (true) {
                var best = -1
                for (i in 0 until states) if (!done[i] && dist[i] != Long.MAX_VALUE && (best == -1 || dist[i] < dist[best])) best = i
                if (best == -1) break
                done[best] = true
                val d = best / stride
                val k = best % stride
                fun relax(to: Int, cost: Long) {
                    val cand = dist[best] + cost
                    if (cand < dist[to]) { dist[to] = cand; prev[to] = best }
                }
                if (k == m) {
                    for ((li, line) in lines.withIndex()) if (d in line.stops) relax(d * stride + li, line.headwaySeconds / 2L + BOARDING_SECONDS)
                } else {
                    val line = lines[k]
                    val at = line.stops.indexOf(d)
                    if (at > 0) relax(line.stops[at - 1] * stride + k, seg.getValue(Triple(k, d, line.stops[at - 1])))
                    if (at < line.stops.size - 1) relax(line.stops[at + 1] * stride + k, seg.getValue(Triple(k, d, line.stops[at + 1])))
                    relax(d * stride + m, TRANSFER_WALK_SECONDS)
                }
            }
            for (dest in 0 until n) {
                if (dest == o) continue
                var bestState = -1
                for (k in 0 until m) {
                    val s = dest * stride + k
                    if (dist[s] != Long.MAX_VALUE && (bestState == -1 || dist[s] < dist[bestState])) bestState = s
                }
                if (bestState == -1) continue
                seconds[o * n + dest] = dist[bestState] + access
                legsOut[o * n + dest] = legsFrom(prev, bestState, lines, stride, m)
            }
        }
        return TransitTable(n, seconds, legsOut)
    }

    private fun legsFrom(prev: IntArray, end: Int, lines: List<TransitLine>, stride: Int, m: Int): List<TransitLeg> {
        val chain = ArrayList<Int>()
        var s = end
        while (s != -1) { chain += s; s = prev[s] }
        chain.reverse()
        val legs = ArrayList<TransitLeg>()
        var current = -1
        var from = 0
        var last = 0
        for (st in chain) {
            val d = st / stride
            val k = st % stride
            if (k == m) {
                if (current != -1) { legs += TransitLeg(lines[current].id, from, last); current = -1 }
                continue
            }
            if (current != k) {
                if (current != -1) legs += TransitLeg(lines[current].id, from, last)
                current = k
                from = d
            }
            last = d
        }
        if (current != -1) legs += TransitLeg(lines[current].id, from, last)
        return legs.filter { it.from != it.to }
    }
}
