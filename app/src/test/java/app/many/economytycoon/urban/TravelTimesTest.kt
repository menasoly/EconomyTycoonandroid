package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelTimesTest {
    private val p = UrbanParams()
    private val g = UrbanTestSupport.geometry()

    @Test fun `walking within a district takes half a block`() {
        val w = TravelTimes.walk(g, p)
        assertEquals(g.blockSize * g.cellMeters / 2L, w[0])
        assertEquals(g.distanceMeters(0, 1), w[0 * 9 + 1])
        assertEquals(w[2 * 9 + 5], w[5 * 9 + 2])
    }

    @Test fun `money converts to time by the value of time`() {
        assertEquals(3600L / p.fareUnitsPerHourOfTime, TravelTimes.secondsPerUnit(p))
        assertEquals(TravelTimes.secondsPerUnit(p) * 3, TravelTimes.moneyAsSeconds(Money.units(3), p))
        assertEquals(0L, TravelTimes.moneyAsSeconds(Money.ZERO, p))
    }

    @Test fun `a driver reaches every district over a connected grid`() {
        val t = TravelTimes.car(UrbanTestSupport.gridNetwork(), p)
        for (o in 0 until 9) for (d in 0 until 9) assertTrue("$o->$d", t.seconds(o, d) != UNREACHABLE)
        assertEquals(0L, t.seconds(3, 3))
    }

    @Test fun `car times are symmetric on an unloaded network`() {
        val t = TravelTimes.car(UrbanTestSupport.gridNetwork(), p)
        for (o in 0 until 9) for (d in 0 until 9) assertEquals(t.seconds(o, d), t.seconds(d, o))
    }

    @Test fun `the best path follows the grid and its time is the sum of its roads`() {
        val net = UrbanTestSupport.gridNetwork()
        val t = TravelTimes.car(net, p)
        val path = t.path(0, 8)
        assertEquals(4, path.size)
        assertEquals(path.sumOf { it.currentSeconds() }, t.seconds(0, 8))
        assertTrue(t.path(2, 2).isEmpty())
    }

    @Test fun `districts without a connecting road are unreachable`() {
        val net = TransitNetwork(g)
        net.addRoad(LinkId(1), 0, 1, RoadClass.LOCAL, 1)
        val t = TravelTimes.car(net, p)
        assertEquals(UNREACHABLE, t.seconds(0, 5))
        assertTrue(t.path(0, 5).isEmpty())
        assertTrue(t.seconds(0, 1) > 0L)
    }

    @Test fun `congestion and tolls lengthen the drive`() {
        val net = UrbanTestSupport.gridNetwork()
        val free = TravelTimes.car(net, p).seconds(0, 1)
        net.roadBetween(0, 1)?.flowVph = 5_000L
        val jammed = TravelTimes.car(net, p).seconds(0, 1)
        assertTrue(jammed >= free)
        assertTrue(net.roadBetween(0, 1)!!.currentSeconds() > UrbanTestSupport.gridNetwork().roadBetween(0, 1)!!.currentSeconds())
        val tolled = UrbanTestSupport.gridNetwork()
        tolled.setToll(tolled.roadBetween(0, 1)!!.id, Money.units(1))
        assertTrue(TravelTimes.car(tolled, p).seconds(0, 1) > free || TravelTimes.car(tolled, p).path(0, 1).size > 1)
    }

    @Test fun `a driver goes around an expensive toll road when there is time to gain`() {
        val net = UrbanTestSupport.gridNetwork()
        net.setToll(net.roadBetween(0, 1)!!.id, Money.units(500))
        val path = TravelTimes.car(net, p).path(0, 1)
        assertTrue(path.size > 1)
        assertTrue(path.none { it.toll.isPositive })
    }

    @Test fun `without lines there is no transit`() {
        val t = TravelTimes.transit(UrbanTestSupport.gridNetwork(), p, Scale.PPM)
        assertEquals(UNREACHABLE, t.seconds(0, 1))
        assertTrue(t.legs(0, 1).isEmpty())
    }

    @Test fun `a line connects its stops with waiting and access included`() {
        val net = UrbanTestSupport.gridNetwork()
        val line = UrbanTestSupport.activeLine(net, 1, "A", TransitMode.METRO, listOf(0, 1, 2), headway = 300)
        val t = TravelTimes.transit(net, p, Scale.PPM)
        val ride = t.seconds(0, 2)
        val access = TravelTimes.accessSeconds(g, p)
        assertTrue(ride > 2 * access + line.headwaySeconds / 2)
        assertEquals(listOf(TransitLeg(line.id, 0, 2)), t.legs(0, 2))
        assertEquals(0, t.transfers(0, 2))
        assertEquals(UNREACHABLE, t.seconds(0, 8))
        assertEquals(UNREACHABLE, t.seconds(0, 0))
    }

    @Test fun `a transfer costs a walk and a second wait`() {
        val net = UrbanTestSupport.gridNetwork()
        UrbanTestSupport.activeLine(net, 1, "A", TransitMode.METRO, listOf(0, 1), headway = 300)
        UrbanTestSupport.activeLine(net, 2, "B", TransitMode.METRO, listOf(1, 2), headway = 300)
        val t = TravelTimes.transit(net, p, Scale.PPM)
        assertEquals(1, t.transfers(0, 2))
        assertTrue(t.seconds(0, 2) > t.seconds(0, 1) + t.seconds(1, 2) - 2 * TravelTimes.accessSeconds(g, p) - 1L)
    }

    @Test fun `rarer service means longer journeys`() {
        val frequent = UrbanTestSupport.gridNetwork()
        UrbanTestSupport.activeLine(frequent, 1, "A", TransitMode.BUS, listOf(0, 1, 2), headway = 120)
        val rare = UrbanTestSupport.gridNetwork()
        UrbanTestSupport.activeLine(rare, 1, "A", TransitMode.BUS, listOf(0, 1, 2), headway = 1_800)
        assertTrue(TravelTimes.transit(rare, p, Scale.PPM).seconds(0, 2) > TravelTimes.transit(frequent, p, Scale.PPM).seconds(0, 2))
    }

    @Test fun `buses in traffic slow down but dedicated lanes do not`() {
        val net = UrbanTestSupport.gridNetwork()
        val shared = net.planLine(TransitLineId(1), "S", TransitMode.BUS, listOf(0, 1), 300, Money.units(1), false).getOrThrow()
        val lane = net.planLine(TransitLineId(2), "L", TransitMode.BUS, listOf(0, 1), 300, Money.units(1), true).getOrThrow()
        val calm = TravelTimes.segmentSeconds(shared, 0, 1, g, p, Scale.PPM)
        val jam = TravelTimes.segmentSeconds(shared, 0, 1, g, p, 1_600_000L)
        assertTrue(jam > calm)
        assertEquals(TravelTimes.segmentSeconds(lane, 0, 1, g, p, Scale.PPM), TravelTimes.segmentSeconds(lane, 0, 1, g, p, 1_600_000L))
    }

    @Test fun `crowded lines are slower`() {
        val net = UrbanTestSupport.gridNetwork()
        val l = UrbanTestSupport.activeLine(net, 1, "A", TransitMode.METRO, listOf(0, 1))
        val empty = TravelTimes.segmentSeconds(l, 0, 1, g, p, Scale.PPM)
        l.peakLoad = l.capacityPerHour * 2
        assertTrue(TravelTimes.segmentSeconds(l, 0, 1, g, p, Scale.PPM) > empty)
    }

    @Test fun `the metro is faster than the bus over the same distance`() {
        val net = UrbanTestSupport.gridNetwork()
        val bus = UrbanTestSupport.activeLine(net, 1, "B", TransitMode.BUS, listOf(0, 3))
        val metro = UrbanTestSupport.activeLine(net, 2, "M", TransitMode.METRO, listOf(0, 3))
        assertTrue(TravelTimes.segmentSeconds(metro, 0, 3, g, p, Scale.PPM) < TravelTimes.segmentSeconds(bus, 0, 3, g, p, Scale.PPM))
    }

    @Test fun `tables are repeatable`() {
        val net = UrbanTestSupport.gridNetwork()
        UrbanTestSupport.activeLine(net, 1, "A", TransitMode.RAIL, listOf(0, 4, 8))
        val a = TravelTimes.transit(net, p, Scale.PPM)
        val b = TravelTimes.transit(net, p, Scale.PPM)
        for (o in 0 until 9) for (d in 0 until 9) {
            assertEquals(a.seconds(o, d), b.seconds(o, d))
            assertEquals(a.legs(o, d), b.legs(o, d))
        }
    }
}
