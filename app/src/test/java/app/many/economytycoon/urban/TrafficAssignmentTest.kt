package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TrafficAssignmentTest {
    private val p = UrbanParams()

    @Test fun `gravity pull halves at fifteen minutes`() {
        assertEquals(Scale.PPM, TrafficAssignment.gravityPpm(0L))
        assertEquals(500_000L, TrafficAssignment.gravityPpm(900L))
        assertEquals(Scale.PPM, TrafficAssignment.gravityPpm(-50L))
        assertTrue(TrafficAssignment.gravityPpm(3_600L) < 100_000L)
    }

    @Test fun `every worker gets exactly one destination`() {
        val net = UrbanTestSupport.gridNetwork()
        val ds = UrbanTestSupport.commuterDistricts()
        val walk = TravelTimes.walk(net.geometry, p)
        val workers = LongArray(9) { ds[it].residents * p.participationPpm / Scale.PPM }
        val jobs = LongArray(9) { ds[it].jobs }
        val trips = TrafficAssignment.tripMatrix(9, workers, jobs, TravelTimes.car(net, p), TravelTimes.transit(net, p, Scale.PPM), walk)
        for (o in 0 until 9) assertEquals(workers[o], (0 until 9).sumOf { trips[o * 9 + it] })
    }

    @Test fun `nobody commutes to districts without jobs`() {
        val net = UrbanTestSupport.gridNetwork()
        val ds = UrbanTestSupport.districts()
        ds[0].residents = 3_000L
        ds[8].jobs = 5_000L
        val walk = TravelTimes.walk(net.geometry, p)
        val trips = TrafficAssignment.tripMatrix(9, LongArray(9) { if (it == 0) 1_000L else 0L }, LongArray(9) { ds[it].jobs }, TravelTimes.car(net, p), TravelTimes.transit(net, p, Scale.PPM), walk)
        assertEquals(1_000L, trips[0 * 9 + 8])
    }

    @Test fun `an empty city has an empty report`() {
        val r = TrafficAssignment.assign(UrbanTestSupport.gridNetwork(), UrbanTestSupport.districts(), p)
        assertEquals(0L, r.commuters)
        assertEquals(0L, r.averageCommuteSeconds)
        assertEquals(ModeShares(0, 0, Scale.PPM), r.modeShares)
        assertEquals(Scale.PPM, r.roadDelayPpm)
        assertEquals(0L, r.congestionIndexPpm)
    }

    @Test fun `the report counts commuters and mode shares add up`() {
        val ds = UrbanTestSupport.commuterDistricts()
        val r = TrafficAssignment.assign(UrbanTestSupport.gridNetwork(), ds, p)
        assertEquals(ds.sumOf { it.residents * p.participationPpm / Scale.PPM }, r.commuters)
        assertEquals(r.commuters * 2L, r.dailyTrips)
        assertEquals(Scale.PPM, r.modeShares.car + r.modeShares.transit + r.modeShares.walk)
        assertTrue(r.averageCommuteSeconds > 0L)
        assertEquals(9, r.districtCommuteSeconds.size)
        assertEquals(81, r.bestSeconds.size)
    }

    @Test fun `flows are written into the network and are never negative`() {
        val net = UrbanTestSupport.gridNetwork()
        val r = TrafficAssignment.assign(net, UrbanTestSupport.commuterDistricts(), p)
        assertTrue(net.roads().sumOf { it.flowVph } > 0L)
        for (road in net.roads()) {
            assertTrue(road.flowVph >= 0L)
            assertEquals(road.flowVph, r.roadFlows.getValue(road.id))
        }
    }

    @Test fun `narrow roads are more congested than wide ones`() {
        val narrow = UrbanTestSupport.gridNetwork(RoadClass.LOCAL, 1)
        val wide = UrbanTestSupport.gridNetwork(RoadClass.HIGHWAY, 3)
        val a = TrafficAssignment.assign(narrow, UrbanTestSupport.commuterDistricts(9_000L), p)
        val b = TrafficAssignment.assign(wide, UrbanTestSupport.commuterDistricts(9_000L), p)
        assertTrue(a.roadDelayPpm > b.roadDelayPpm)
        assertTrue(a.averageCommuteSeconds > b.averageCommuteSeconds)
    }

    @Test fun `a metro line shifts commuters onto transit`() {
        val without = UrbanTestSupport.gridNetwork(RoadClass.LOCAL, 1)
        val with = UrbanTestSupport.gridNetwork(RoadClass.LOCAL, 1)
        UrbanTestSupport.activeLine(with, 1, "Metro", TransitMode.METRO, listOf(0, 1, 2), headway = 180, fare = Money.cents(50))
        UrbanTestSupport.activeLine(with, 2, "Metro 2", TransitMode.METRO, listOf(3, 4, 5), headway = 180, fare = Money.cents(50))
        UrbanTestSupport.activeLine(with, 3, "Metro 3", TransitMode.METRO, listOf(6, 7, 8), headway = 180, fare = Money.cents(50))
        val ds = { UrbanTestSupport.commuterDistricts(9_000L, 6_000L) }
        val a = TrafficAssignment.assign(without, ds(), p)
        val b = TrafficAssignment.assign(with, ds(), p)
        assertEquals(0L, a.modeShares.transit)
        assertTrue(b.modeShares.transit > 0L)
        assertTrue(b.lineDailyRiders.values.sum() > 0L)
        assertTrue(with.lines().all { it.peakLoad >= 0L })
        assertTrue(b.modeShares.car < a.modeShares.car)
    }

    @Test fun `tolled roads count their vehicles`() {
        val net = UrbanTestSupport.gridNetwork()
        for (r in net.roads()) net.setToll(r.id, Money.cents(5))
        val r = TrafficAssignment.assign(net, UrbanTestSupport.commuterDistricts(), p)
        assertTrue(r.tollVehicles.values.sum() > 0L)
        assertTrue(r.tollVehicles.keys.all { net.road(it)!!.toll.isPositive })
    }

    @Test fun `accessibility is higher where jobs are close`() {
        val net = UrbanTestSupport.gridNetwork()
        val ds = UrbanTestSupport.districts()
        ds[4].jobs = 50_000L
        ds[4].residents = 5_000L
        val r = TrafficAssignment.assign(net, ds, p)
        assertTrue(r.accessibilityPermille[4] > r.accessibilityPermille[0])
        assertTrue(r.accessibilityPermille.all { it in 0..1000 })
    }

    @Test fun `the result does not depend on how many times it is run`() {
        val a = TrafficAssignment.assign(UrbanTestSupport.gridNetwork(), UrbanTestSupport.commuterDistricts(), p)
        val b = TrafficAssignment.assign(UrbanTestSupport.gridNetwork(), UrbanTestSupport.commuterDistricts(), p)
        assertEquals(a.roadFlows, b.roadFlows)
        assertEquals(a.averageCommuteSeconds, b.averageCommuteSeconds)
        assertEquals(a.modeShares, b.modeShares)
        assertEquals(a.bestSeconds.toList(), b.bestSeconds.toList())
    }

    @Test fun `more iterations settle towards a stable answer`() {
        val net = { UrbanTestSupport.gridNetwork(RoadClass.LOCAL, 1) }
        val ds = { UrbanTestSupport.commuterDistricts(9_000L, 6_000L) }
        val eight = TrafficAssignment.assign(net(), ds(), p.copy(assignmentIterations = 8)).averageCommuteSeconds
        val twelve = TrafficAssignment.assign(net(), ds(), p.copy(assignmentIterations = 12)).averageCommuteSeconds
        val four = TrafficAssignment.assign(net(), ds(), p).averageCommuteSeconds
        assertTrue(Math.abs(twelve - eight) <= Math.abs(eight - four) + eight / 20)
    }

    @Test fun `road delay weights by traffic`() {
        val net = UrbanTestSupport.gridNetwork()
        assertEquals(Scale.PPM, TrafficAssignment.roadDelay(net))
        val roads = net.roads()
        roads[0].flowVph = roads[0].capacityVph
        roads[1].flowVph = 10L
        val delay = TrafficAssignment.roadDelay(net)
        assertTrue(delay > Scale.PPM)
        assertTrue(delay < Congestion.delayFactorPpm(Scale.PPM))
    }

    @Test fun `the district count must match the network`() {
        try { TrafficAssignment.assign(UrbanTestSupport.gridNetwork(), UrbanTestSupport.districts().take(4), p); fail() } catch (e: IllegalArgumentException) { }
    }
}
