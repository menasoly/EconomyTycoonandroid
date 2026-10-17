package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanOverlayTest {
    private val T = UrbanTestSupport

    private fun overlays(svc: UrbanService = T.withCity(), now: Long = 0): List<UrbanOverlay> {
        val plan = T.plan(svc)
        return UrbanOverlays.buildAll(plan, UrbanAnalysis.analyse(plan, T.params), T.params, Tick(now))
    }

    @Test fun `ramps mix colours channel by channel`() {
        assertEquals(0xFF000000.toInt(), Ramps.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0L))
        assertEquals(0xFFFFFFFF.toInt(), Ramps.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), Scale.PPM))
        assertEquals(0xFF7F7F7F.toInt(), Ramps.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 500_000L))
        assertEquals(0xFFFFFFFF.toInt(), Ramps.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 9_000_000L))
    }

    @Test fun `colours follow the value along the ramp`() {
        val r = Ramps.SEQUENTIAL
        assertEquals(r[0], Ramps.colorFor(0L, 0L, 100L, r))
        assertEquals(r.last(), Ramps.colorFor(100L, 0L, 100L, r))
        assertEquals(r[2], Ramps.colorFor(50L, 0L, 100L, r))
        assertEquals(r[0], Ramps.colorFor(-50L, 0L, 100L, r))
        assertEquals(r.last(), Ramps.colorFor(500L, 0L, 100L, r))
        assertEquals(r[0], Ramps.colorFor(5L, 7L, 7L, r))
    }

    @Test fun `there is one overlay per kind`() {
        val all = overlays()
        assertEquals(OverlayKind.values().toList(), all.map { it.kind })
        assertTrue(all.all { it.city == T.city && it.heat.width == 12 && it.heat.height == 12 })
    }

    @Test fun `the zoning overlay is categorical and matches the districts`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        val o = overlays(svc).first { it.kind == OverlayKind.ZONING }
        for (d in plan.districts) for (c in plan.geometry.cellsOf(d.index)) assertEquals(d.zone.ordinal.toLong(), o.heat.valueAt(c.x, c.y))
        assertEquals(ZoneUse.values().size, o.heat.legend.size)
        assertEquals(Ramps.ZONES[plan.districts[0].zone.ordinal], o.heat.colorAt(0, 0))
        assertEquals(plan.districts[0].zone.ordinal, o.heat.legendIndexAt(0, 0))
    }

    @Test fun `numeric overlays have five legend stops that rise and a range`() {
        for (o in overlays().filter { !it.kind.categorical }) {
            assertEquals(o.kind.name, 5, o.heat.legend.size)
            assertTrue(o.heat.max >= o.heat.min)
            for ((a, b) in o.heat.legend.zipWithNext()) assertTrue(a.value <= b.value)
            assertTrue(o.heat.flat().all { it in o.heat.min..o.heat.max })
        }
    }

    @Test fun `bad-when-high overlays use the warning ramp`() {
        val o = overlays()
        val pollution = o.first { it.kind == OverlayKind.POLLUTION }
        val value = o.first { it.kind == OverlayKind.LAND_VALUE }
        assertEquals(Ramps.WARNING[0], pollution.heat.legend.first().color)
        assertEquals(Ramps.SEQUENTIAL[0], value.heat.legend.first().color)
    }

    @Test fun `legend indexes are always valid`() {
        for (o in overlays()) for (y in 0 until 12) for (x in 0 until 12) assertTrue(o.heat.legendIndexAt(x, y) in o.heat.legend.indices)
    }

    @Test fun `grid data must match the stated size`() {
        try {
            HeatGrid(OverlayKind.RENT, 3, 3, LongArray(8), 0, 1, emptyList(), Ramps.SEQUENTIAL)
            org.junit.Assert.fail()
        } catch (e: IllegalArgumentException) { }
    }

    @Test fun `roads and lines are drawn between district centres`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        val o = overlays(svc).first()
        assertEquals(plan.network.roads().size + plan.network.lines().sumOf { it.stops.size - 1 }, o.lines.size)
        val bus = o.lines.first { it.kind == OverlayLineKind.BUS }
        assertTrue(bus.active)
        assertEquals("Crosstown bus", bus.label)
        val c = plan.geometry.centre(plan.network.lines().first().stops.first())
        assertEquals(c.x * 1000 + 500, bus.fromX)
        assertTrue(o.lines.all { it.loadPpm in 0L..Scale.PPM })
    }

    @Test fun `stations of fast lines are marked and buses are not`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        T.activeLine(plan.network, 90, "Metro", TransitMode.METRO, listOf(0, 4, 8))
        val o = overlays(svc).first()
        val stations = o.markers.filter { it.kind == OverlayMarkerKind.STATION }
        assertEquals(3, stations.size)
        assertTrue(stations.all { it.label.startsWith("Metro") })
    }

    @Test fun `open petitions, works and events get markers`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        plan.budget.restore(plan.budget.rates, Money.units(3_000_000_000), Money.ZERO, 40_000L, Money.ZERO, 0, plan.budget.levelsList(), plan.budget.qualityList())
        val d = plan.districts.first { it.zone == ZoneUse.RESIDENTIAL }
        svc.execute(UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, null), Tick(0)).getOrThrow()
        svc.execute(UrbanCommand.StartProject(T.city, ProjectKind.PARK, "Green lung", d.index, null, null, 0L, false), Tick(0)).getOrThrow()
        plan.events.add(UrbanEvent(UrbanEventId(500), UrbanEventKind.FLOOD, d.index, Tick(0), Tick(50), 300))
        val o = overlays(svc, now = 10).first()
        assertEquals(1, o.markers.count { it.kind == OverlayMarkerKind.PETITION })
        assertEquals(1, o.markers.count { it.kind == OverlayMarkerKind.PROJECT })
        assertEquals(1, o.markers.count { it.kind == OverlayMarkerKind.EVENT })
        assertEquals(0, overlays(svc, now = 80).first().markers.count { it.kind == OverlayMarkerKind.EVENT })
    }

    @Test fun `the overlay knows the district grid and names`() {
        val o = overlays().first()
        assertEquals(4, o.blockSize)
        assertEquals(9, o.districtNames.size)
    }

    @Test fun `building is repeatable and does not change the plan`() {
        val svc = T.withCity()
        val hash = svc.stateHash()
        val a = overlays(svc).map { it.heat.flat().toList() }
        val b = overlays(svc).map { it.heat.flat().toList() }
        assertEquals(a, b)
        assertEquals(hash, svc.stateHash())
        assertFalse(a.isEmpty())
    }
}
