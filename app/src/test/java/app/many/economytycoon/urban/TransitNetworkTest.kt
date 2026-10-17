package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitNetworkTest {
    private fun net() = TransitNetwork(UrbanTestSupport.geometry())
    private fun rule(o: app.many.economytycoon.core.Outcome<*>) = o.rejectionOrNull()?.rule

    @Test fun `a road joins two districts with a length from the map`() {
        val n = net()
        val r = n.addRoad(LinkId(1), 4, 1, RoadClass.ARTERIAL, 2).getOrThrow()
        assertEquals(1, r.a)
        assertEquals(4, r.b)
        assertEquals(n.geometry.distanceMeters(1, 4), r.lengthMeters)
        assertEquals(2_000L, r.capacityVph)
        assertTrue(r.freeFlowSeconds > 0L)
        assertEquals(r, n.roadBetween(4, 1))
    }

    @Test fun `bad roads are rejected`() {
        val n = net()
        assertEquals(Rules.UNKNOWN_ENTITY, rule(n.addRoad(LinkId(1), 0, 99, RoadClass.LOCAL, 1)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(n.addRoad(LinkId(1), 2, 2, RoadClass.LOCAL, 1)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(n.addRoad(LinkId(1), 0, 1, RoadClass.LOCAL, 0)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(n.addRoad(LinkId(1), 0, 1, RoadClass.LOCAL, 9)))
        n.addRoad(LinkId(1), 0, 1, RoadClass.LOCAL, 1).getOrThrow()
        assertEquals(Rules.DUPLICATE, rule(n.addRoad(LinkId(2), 1, 0, RoadClass.HIGHWAY, 2)))
        assertEquals(1, n.roads().size)
    }

    @Test fun `upgrades may grow a road but not shrink it`() {
        val n = net()
        n.addRoad(LinkId(1), 0, 1, RoadClass.ARTERIAL, 2)
        val r = n.upgradeRoad(LinkId(1), RoadClass.HIGHWAY, 3).getOrThrow()
        assertEquals(3 * 1_900L, r.capacityVph)
        assertEquals(Rules.INVALID_ARGUMENT, rule(n.upgradeRoad(LinkId(1), RoadClass.LOCAL, 3)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(n.upgradeRoad(LinkId(1), RoadClass.HIGHWAY, 2)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(n.upgradeRoad(LinkId(9), RoadClass.HIGHWAY, 3)))
        assertEquals(3, n.road(LinkId(1))?.lanes)
    }

    @Test fun `tolls cannot be negative`() {
        val n = net()
        n.addRoad(LinkId(1), 0, 1, RoadClass.ARTERIAL, 2)
        assertEquals(Money.units(3), n.setToll(LinkId(1), Money.units(3)).getOrThrow().toll)
        assertEquals(Rules.INVALID_ARGUMENT, rule(n.setToll(LinkId(1), Money.units(-1))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(n.setToll(LinkId(5), Money.ZERO)))
    }

    @Test fun `a connected road grid is reported as connected`() {
        assertTrue(UrbanTestSupport.gridNetwork().roadsConnected())
        val partial = net()
        partial.addRoad(LinkId(1), 0, 1, RoadClass.LOCAL, 1)
        assertFalse(partial.roadsConnected())
    }

    @Test fun `grid roads reach every neighbour`() {
        val n = UrbanTestSupport.gridNetwork()
        assertEquals(12, n.roads().size)
        assertEquals(2, n.roadsAt(0).size)
        assertEquals(4, n.roadsAt(4).size)
        assertNull(n.roadBetween(0, 8))
    }

    @Test fun `a line is validated before it is planned`() {
        val n = net()
        fun plan(name: String = "A", stops: List<Int> = listOf(0, 1), headway: Int = 300, fare: Money = Money.units(2)) =
            n.planLine(TransitLineId(1), name, TransitMode.BUS, stops, headway, fare, false)
        assertEquals(Rules.INVALID_ARGUMENT, rule(plan(name = " ")))
        assertEquals(Rules.INVALID_ARGUMENT, rule(plan(stops = listOf(0))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(plan(stops = listOf(0, 40))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(plan(stops = listOf(0, 0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(plan(stops = listOf(0, 1, 0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(plan(headway = 10)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(plan(headway = 99_999)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(plan(fare = Money.units(-1))))
        assertTrue(plan().isOk)
        assertEquals(Rules.NAME_TAKEN, rule(plan()))
    }

    @Test fun `rail and metro always get their own right of way`() {
        val n = net()
        assertTrue(n.planLine(TransitLineId(1), "M", TransitMode.METRO, listOf(0, 4), 240, Money.units(2), false).getOrThrow().dedicatedLane)
        assertFalse(n.planLine(TransitLineId(2), "B", TransitMode.BUS, listOf(0, 4), 240, Money.units(1), false).getOrThrow().dedicatedLane)
        assertTrue(n.planLine(TransitLineId(3), "B2", TransitMode.BUS, listOf(0, 4), 240, Money.units(1), true).getOrThrow().dedicatedLane)
    }

    @Test fun `line length adds up the legs`() {
        val n = net()
        val l = n.planLine(TransitLineId(1), "Cross", TransitMode.BUS, listOf(0, 1, 4), 300, Money.units(1), false).getOrThrow()
        assertEquals(n.geometry.distanceMeters(0, 1) + n.geometry.distanceMeters(1, 4), l.lengthMeters)
        assertEquals(LineStatus.PLANNED, l.status)
    }

    @Test fun `a planned line cannot enter service before it is built`() {
        val n = net()
        n.planLine(TransitLineId(1), "A", TransitMode.BUS, listOf(0, 1), 300, Money.units(1), false)
        assertEquals(Rules.WRONG_STATE, rule(n.setStatus(TransitLineId(1), LineStatus.ACTIVE)))
        assertTrue(n.setStatus(TransitLineId(1), LineStatus.UNDER_CONSTRUCTION).isOk)
        assertTrue(n.setStatus(TransitLineId(1), LineStatus.ACTIVE).isOk)
        assertEquals(1, n.activeLines().size)
        assertTrue(n.setStatus(TransitLineId(1), LineStatus.SUSPENDED).isOk)
        assertTrue(n.activeLines().isEmpty())
        assertEquals(Rules.UNKNOWN_ENTITY, rule(n.setStatus(TransitLineId(7), LineStatus.ACTIVE)))
    }

    @Test fun `headway and fare changes are validated`() {
        val n = net()
        n.planLine(TransitLineId(1), "A", TransitMode.BUS, listOf(0, 1), 300, Money.units(1), false)
        assertEquals(120, n.setHeadway(TransitLineId(1), 120).getOrThrow().headwaySeconds)
        assertEquals(Rules.INVALID_ARGUMENT, rule(n.setHeadway(TransitLineId(1), 30)))
        assertEquals(Money.ZERO, n.setFare(TransitLineId(1), Money.ZERO).getOrThrow().fare)
        assertEquals(Rules.INVALID_ARGUMENT, rule(n.setFare(TransitLineId(1), Money.units(-2))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(n.setHeadway(TransitLineId(3), 120)))
    }

    @Test fun `shorter headways need more vehicles and cost more to run`() {
        val n = net()
        val slow = UrbanTestSupport.activeLine(n, 1, "Slow", TransitMode.BUS, listOf(0, 1, 2), headway = 900)
        val fast = UrbanTestSupport.activeLine(n, 2, "Fast", TransitMode.BUS, listOf(0, 1, 2), headway = 300)
        assertEquals(4L, slow.vehiclesPerHour)
        assertEquals(12L, fast.vehiclesPerHour)
        assertEquals(12L * 70L, fast.capacityPerHour)
        assertEquals(3L, fast.monthlyOperatingCost().raw / slow.monthlyOperatingCost().raw)
    }

    @Test fun `metro costs far more to build than a bus line of the same length`() {
        val n = net()
        val bus = n.planLine(TransitLineId(1), "B", TransitMode.BUS, listOf(0, 1), 300, Money.units(1), false).getOrThrow()
        val metro = n.planLine(TransitLineId(2), "M", TransitMode.METRO, listOf(0, 1), 300, Money.units(1), false).getOrThrow()
        assertTrue(metro.buildCost() > bus.buildCost() * 50L)
    }

    @Test fun `total operating cost counts only active lines`() {
        val n = net()
        val a = UrbanTestSupport.activeLine(n, 1, "A", TransitMode.BUS, listOf(0, 1))
        n.planLine(TransitLineId(2), "B", TransitMode.BUS, listOf(1, 2), 300, Money.units(1), false)
        assertEquals(a.monthlyOperatingCost(), n.monthlyOperatingCost())
    }

    @Test fun `loads are reported against capacity`() {
        val n = net()
        val l = UrbanTestSupport.activeLine(n, 1, "A", TransitMode.BUS, listOf(0, 1), headway = 600)
        l.peakLoad = l.capacityPerHour / 2
        assertEquals(500_000L, l.loadRatioPpm)
        assertEquals(6L, l.vehiclesPerHour)
    }

    @Test fun `road flow sets the level of service and drive time`() {
        val n = net()
        val r = n.addRoad(LinkId(1), 0, 1, RoadClass.ARTERIAL, 1).getOrThrow()
        val free = r.currentSeconds()
        assertEquals(LevelOfService.A, r.levelOfService)
        r.flowVph = 1_500L
        assertEquals(LevelOfService.F, r.levelOfService)
        assertTrue(r.currentSeconds() > free)
        assertEquals(1, r.other(0) - 0)
        assertTrue(r.connects(1, 0))
    }

    @Test fun `hash covers roads and lines`() {
        fun h(n: TransitNetwork) = CanonicalHasher().also { n.hashInto(it) }.finish()
        val a = UrbanTestSupport.gridNetwork()
        val b = UrbanTestSupport.gridNetwork()
        assertEquals(h(a), h(b))
        b.roads().first().flowVph = 5L
        assertTrue(h(a) != h(b))
        val c = UrbanTestSupport.gridNetwork()
        UrbanTestSupport.activeLine(c, 1, "A", TransitMode.BUS, listOf(0, 1))
        assertTrue(h(a) != h(c))
    }

    @Test fun `lines serving a district are only the active ones`() {
        val n = net()
        UrbanTestSupport.activeLine(n, 1, "A", TransitMode.BUS, listOf(0, 1))
        n.planLine(TransitLineId(2), "B", TransitMode.BUS, listOf(1, 2), 300, Money.units(1), false)
        assertEquals(listOf(TransitLineId(1)), n.linesServing(1).map { it.id })
        assertTrue(n.linesServing(2).isEmpty())
    }
}
