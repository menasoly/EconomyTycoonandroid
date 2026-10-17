package app.many.economytycoon.tourism

import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportTest {
    private val dest = DestinationId(1)
    private val o1 = OriginId(1)
    private val o2 = OriginId(2)

    private fun cells() = listOf(
        FlowCell(o1, VisitorSegment.BUDGET, 600), FlowCell(o1, VisitorSegment.LUXURY, 400), FlowCell(o2, VisitorSegment.FAMILY, 300),
    )

    @Test fun unlimitedTransportKeepsEveryone() {
        val (kept, loads) = TransportCoupling.limit(cells(), dest, UnlimitedTransport, Tick(1), 1_000_000L)
        assertEquals(1_300L, VisitorFlows.total(kept))
        assertTrue(loads.all { it.turnedAway == 0L })
        assertEquals(2, loads.size)
    }

    @Test fun aSeatShortageCutsEverySegmentOfARouteByTheSameShare() {
        val t = FixedTransport(mapOf((o1 to dest) to 500L))
        val (kept, loads) = TransportCoupling.limit(cells(), dest, t, Tick(1), 1_000_000L)
        assertEquals(300L, kept.first { it.segment == VisitorSegment.BUDGET }.persons)
        assertEquals(200L, kept.first { it.segment == VisitorSegment.LUXURY }.persons)
        assertEquals(300L, kept.first { it.origin == o2 }.persons)
        val load = loads.first { it.origin == o1 }
        assertEquals(1_000L, load.wanted)
        assertEquals(500L, load.carried)
        assertEquals(500_000L, load.loadFactorPpm)
    }

    @Test fun carriedNeverExceedsSeats() {
        for (seats in listOf(0L, 1L, 7L, 333L, 999L)) {
            val t = FixedTransport(mapOf((o1 to dest) to seats))
            val (kept, _) = TransportCoupling.limit(cells(), dest, t, Tick(1), 1_000_000L)
            assertTrue(kept.filter { it.origin == o1 }.sumOf { it.persons } <= seats)
        }
    }

    @Test fun aTransportShockTakesSeatsAway() {
        val t = FixedTransport(emptyMap(), default = 1_000L)
        val (_, normal) = TransportCoupling.limit(cells(), dest, t, Tick(1), 1_000_000L)
        val (_, struck) = TransportCoupling.limit(cells(), dest, t, Tick(1), 400_000L)
        assertEquals(0L, normal.first { it.origin == o1 }.turnedAway)
        assertEquals(600L, struck.first { it.origin == o1 }.turnedAway)
    }

    @Test fun noDemandMeansNoRouteLoad() {
        val (kept, loads) = TransportCoupling.limit(emptyList(), dest, UnlimitedTransport, Tick(1), 1_000_000L)
        assertTrue(kept.isEmpty() && loads.isEmpty())
    }

    @Test fun routesComeBackInOriginOrder() {
        val (_, loads) = TransportCoupling.limit(cells().reversed(), dest, UnlimitedTransport, Tick(1), 1_000_000L)
        assertEquals(listOf(o1, o2), loads.map { it.origin })
    }

    @Test fun negativeCapacityCountsAsNone() {
        val t = FixedTransport(mapOf((o1 to dest) to -5L))
        val (kept, _) = TransportCoupling.limit(cells(), dest, t, Tick(1), 1_000_000L)
        assertEquals(0L, kept.filter { it.origin == o1 }.sumOf { it.persons })
    }

    @Test fun loadFactorOfAnEmptyRouteIsZeroNotADivisionByZero() {
        assertEquals(0L, RouteLoad(o1, dest, 0L, 0L).loadFactorPpm)
        assertEquals(1_000_000L, RouteLoad(o1, dest, 10L, 10L).loadFactorPpm)
    }

    @Test fun capacityCanDependOnTheDay() {
        val t = TransportCapacity { _, _, tick -> if (tick.value < 10) 100L else 1_000_000L }
        val early = TransportCoupling.limit(cells(), dest, t, Tick(1), 1_000_000L).first
        val late = TransportCoupling.limit(cells(), dest, t, Tick(11), 1_000_000L).first
        assertTrue(VisitorFlows.total(early) < VisitorFlows.total(late))
    }
}
