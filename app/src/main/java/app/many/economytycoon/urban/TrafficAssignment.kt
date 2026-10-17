package app.many.economytycoon.urban

import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.core.splitLargestRemainder

/** What the commute looks like across the city after traffic has settled. All trip counts are per day. */
class TrafficReport(
    val commuters: Long,
    val dailyTrips: Long,
    val modeShares: ModeShares,
    val averageCommuteSeconds: Long,
    val districtCommuteSeconds: LongArray,
    /** Generalised best-mode seconds between districts, row-major; the base of accessibility. */
    val bestSeconds: LongArray,
    val accessibilityPermille: IntArray,
    /** Vehicles an hour in the peak, per road. */
    val roadFlows: Map<LinkId, Long>,
    /** Passengers an hour on the fullest segment of each line. */
    val lineLoads: Map<TransitLineId, Long>,
    val lineDailyRiders: Map<TransitLineId, Long>,
    /** Vehicles a day on each tolled road. */
    val tollVehicles: Map<LinkId, Long>,
    /** Average slowdown of roads, ppm over free flow weighted by the traffic they carry. */
    val roadDelayPpm: Long,
) {
    val congestionIndexPpm: Long get() = roadDelayPpm - Scale.PPM
}

/**
 * Settles the morning commute into an equilibrium. Workers choose jobs by a gravity rule over travel cost, then a mode by
 * logit; cars load the roads and riders load the lines; the loads slow the roads and crowd the lines, which changes the
 * costs on the next round. The rounds are averaged (method of successive averages) so the result does not oscillate.
 * The assignment writes the resulting flows into the network's links and lines.
 */
object TrafficAssignment {
    private const val GRAVITY_SECONDS = 900L

    /** Pull of a destination at a given travel cost: 1.0 at zero cost, one half at 15 minutes. */
    fun gravityPpm(seconds: Long): Long {
        val s = seconds.coerceAtLeast(0L)
        val g2 = GRAVITY_SECONDS * GRAVITY_SECONDS
        return mulDiv(Scale.PPM, g2, g2 + s * s)
    }

    private fun segmentKey(line: TransitLineId, segment: Int) = line.value * 64L + segment

    fun assign(network: TransitNetwork, districts: List<District>, params: UrbanParams): TrafficReport {
        val geometry = network.geometry
        val n = geometry.districtCount
        require(districts.size == n) { "one district per node" }
        val walk = TravelTimes.walk(geometry, params)
        val workers = LongArray(n) { mulDiv(districts[it].residents, params.participationPpm, Scale.PPM) }
        val jobs = LongArray(n) { districts[it].jobs }
        val carAvail = LongArray(n) { ModeChoice.carAvailabilityPpm(districts[it].lowIncomeSharePpm) }

        val flows = java.util.TreeMap<LinkId, Long>()
        val segLoads = java.util.TreeMap<Long, Long>()
        for (r in network.roads()) flows[r.id] = 0L
        applyFlows(network, flows, segLoads)

        var car = TravelTimes.car(network, params)
        var transit = TravelTimes.transit(network, params, Scale.PPM)
        for (round in 1..params.assignmentIterations) {
            val trips = tripMatrix(n, workers, jobs, car, transit, walk)
            val newFlows = java.util.TreeMap<LinkId, Long>()
            val newSegs = java.util.TreeMap<Long, Long>()
            for (o in 0 until n) for (d in 0 until n) {
                val t = trips[o * n + d]
                if (t == 0L) continue
                val shares = ModeChoice.shares(ModeCosts(car.seconds(o, d), transit.seconds(o, d), walk[o * n + d]), carAvail[o])
                val peak = mulDiv(t, params.peakHourShareOfTripsPpm, Scale.PPM)
                val vehicles = mulDiv(mulDiv(peak, shares.car, Scale.PPM), 1000L, params.carOccupancyPermille)
                if (vehicles > 0L) for (r in car.path(o, d)) newFlows.merge(r.id, vehicles, Long::plus)
                val riders = mulDiv(peak, shares.transit, Scale.PPM)
                if (riders > 0L) addLegLoads(network, transit.legs(o, d), riders, newSegs)
            }
            averageInto(flows, newFlows, round)
            averageInto(segLoads, newSegs, round)
            applyFlows(network, flows, segLoads)
            car = TravelTimes.car(network, params)
            transit = TravelTimes.transit(network, params, roadDelay(network))
        }
        return report(network, districts, params, walk, workers, jobs, carAvail, car, transit)
    }

    /** Workers per origin spread over destinations by jobs and gravity; every worker gets exactly one destination. */
    fun tripMatrix(n: Int, workers: LongArray, jobs: LongArray, car: CarTable, transit: TransitTable, walk: LongArray): LongArray {
        val out = LongArray(n * n)
        for (o in 0 until n) {
            if (workers[o] == 0L) continue
            val weights = LongArray(n) { d -> jobs[d] * gravityPpm(bestCost(car, transit, walk, n, o, d)) }
            if (weights.sum() == 0L) continue
            val split = splitLargestRemainder(workers[o], weights)
            for (d in 0 until n) out[o * n + d] = split[d]
        }
        return out
    }

    /** Generalised cost of the cheapest mode between two districts. */
    fun bestCost(car: CarTable, transit: TransitTable, walk: LongArray, n: Int, o: Int, d: Int): Long {
        var best = walk[o * n + d]
        val c = car.seconds(o, d)
        if (c != UNREACHABLE && o != d) best = minOf(best, c + TravelTimes.PARKING_SECONDS)
        val t = transit.seconds(o, d)
        if (t != UNREACHABLE) best = minOf(best, t)
        return best
    }

    private fun <K : Comparable<K>> averageInto(current: java.util.TreeMap<K, Long>, fresh: Map<K, Long>, round: Int) {
        val keys = java.util.TreeSet<K>(current.keys)
        keys.addAll(fresh.keys)
        for (k in keys) {
            val old = current[k] ?: 0L
            val new = fresh[k] ?: 0L
            current[k] = old + Math.floorDiv(new - old, round.toLong())
        }
    }

    private fun addLegLoads(network: TransitNetwork, legs: List<TransitLeg>, riders: Long, into: MutableMap<Long, Long>) {
        for (leg in legs) {
            val line = network.line(leg.line) ?: continue
            val a = line.stops.indexOf(leg.from)
            val b = line.stops.indexOf(leg.to)
            if (a < 0 || b < 0) continue
            for (s in minOf(a, b) until maxOf(a, b)) into.merge(segmentKey(line.id, s), riders, Long::plus)
        }
    }

    private fun applyFlows(network: TransitNetwork, flows: Map<LinkId, Long>, segLoads: Map<Long, Long>) {
        for (r in network.roads()) r.flowVph = flows[r.id] ?: 0L
        for (l in network.lines()) {
            var peak = 0L
            for (s in 0 until l.stops.size - 1) peak = maxOf(peak, segLoads[segmentKey(l.id, s)] ?: 0L)
            l.peakLoad = peak
        }
    }

    /** Average slowdown of roads weighted by the vehicles they carry; 1.0 if the roads are empty. */
    fun roadDelay(network: TransitNetwork): Long {
        var weighted = 0L
        var total = 0L
        for (r in network.roads()) {
            if (r.flowVph <= 0L) continue
            weighted += Congestion.delayFactorPpm(r.volumeCapacityPpm) * r.flowVph
            total += r.flowVph
        }
        return if (total == 0L) Scale.PPM else weighted / total
    }

    private fun report(
        network: TransitNetwork, districts: List<District>, params: UrbanParams, walk: LongArray, workers: LongArray, jobs: LongArray,
        carAvail: LongArray, car: CarTable, transit: TransitTable,
    ): TrafficReport {
        val n = network.geometry.districtCount
        val trips = tripMatrix(n, workers, jobs, car, transit, walk)
        var commuters = 0L
        var carTrips = 0L
        var transitTrips = 0L
        var walkTrips = 0L
        var timeSum = 0L
        val districtTime = LongArray(n)
        val districtTrips = LongArray(n)
        val riders = java.util.TreeMap<TransitLineId, Long>()
        val tollVehicles = java.util.TreeMap<LinkId, Long>()
        for (o in 0 until n) for (d in 0 until n) {
            val t = trips[o * n + d]
            if (t == 0L) continue
            val shares = ModeChoice.shares(ModeCosts(car.seconds(o, d), transit.seconds(o, d), walk[o * n + d]), carAvail[o])
            val byCar = mulDiv(t, shares.car, Scale.PPM)
            val byTransit = mulDiv(t, shares.transit, Scale.PPM)
            val byWalk = t - byCar - byTransit
            commuters += t
            carTrips += byCar; transitTrips += byTransit; walkTrips += byWalk
            val carPath = car.path(o, d)
            val driveSeconds = carPath.sumOf { it.currentSeconds() } + TravelTimes.PARKING_SECONDS
            val expected = byCar * driveSeconds + byTransit * maxOf(transit.seconds(o, d), 0L) + byWalk * walk[o * n + d]
            timeSum += expected
            districtTime[o] += expected
            districtTrips[o] += t
            val vehiclesPerDay = byCar * 2L * 1000L / params.carOccupancyPermille
            for (r in carPath) if (r.toll.isPositive) tollVehicles.merge(r.id, vehiclesPerDay, Long::plus)
            if (byTransit > 0L) for (leg in transit.legs(o, d)) riders.merge(leg.line, byTransit * 2L, Long::plus)
        }
        val shares = if (commuters == 0L) ModeShares(0L, 0L, Scale.PPM) else {
            val parts = splitLargestRemainder(Scale.PPM, longArrayOf(carTrips, transitTrips, walkTrips))
            ModeShares(parts[0], parts[1], parts[2])
        }
        val best = LongArray(n * n) { bestCost(car, transit, walk, n, it / n, it % n) }
        val accessibility = IntArray(n) { o ->
            var num = 0L
            var den = 0L
            for (d in 0 until n) {
                val opp = jobs[d] + districts[d].residents / 4L + 1L
                num += opp * gravityPpm(best[o * n + d])
                den += opp * Scale.PPM
            }
            if (den == 0L) 0 else (num * 1000L / den).toInt().coerceIn(0, 1000)
        }
        val flows = java.util.TreeMap<LinkId, Long>()
        for (r in network.roads()) flows[r.id] = r.flowVph
        val loads = java.util.TreeMap<TransitLineId, Long>()
        for (l in network.lines()) loads[l.id] = l.peakLoad
        return TrafficReport(
            commuters = commuters,
            dailyTrips = commuters * 2L,
            modeShares = shares,
            averageCommuteSeconds = if (commuters == 0L) 0L else timeSum / commuters,
            districtCommuteSeconds = LongArray(n) { if (districtTrips[it] == 0L) 0L else districtTime[it] / districtTrips[it] },
            bestSeconds = best,
            accessibilityPermille = accessibility,
            roadFlows = flows,
            lineLoads = loads,
            lineDailyRiders = riders,
            tollVehicles = tollVehicles,
            roadDelayPpm = roadDelay(network),
        )
    }
}
