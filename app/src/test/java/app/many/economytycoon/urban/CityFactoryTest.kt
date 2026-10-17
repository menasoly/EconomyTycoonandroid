package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CityFactoryTest {
    private val T = UrbanTestSupport

    private fun create(seed: Long = 7L, pop: Long = 150_000L, id: Long = 1L, p: UrbanParams = T.params): CityPlan {
        val state = UrbanState(p, seed)
        return CityFactory.create(state, CityId(id), T.country, "Testville", T.inputs(pop))
    }

    @Test fun `rings count steps from the centre`() {
        assertEquals(0, CityFactory.ring(4, 3))
        assertEquals(1, CityFactory.ring(0, 3))
        assertEquals(1, CityFactory.ring(1, 3))
        assertEquals(0, CityFactory.ring(0, 1))
    }

    @Test fun `a city has somewhere to live, something to work in, and a little green`() {
        for (seed in 1L..25L) {
            val plan = create(seed)
            val zones = plan.districts.map { it.zone }
            assertTrue("seed $seed", zones.any { it == ZoneUse.RESIDENTIAL || it == ZoneUse.MIXED })
            assertTrue(ZoneUse.INDUSTRIAL in zones)
            assertTrue(ZoneUse.GREEN in zones)
        }
    }

    @Test fun `districts have unique ids, names and sensible limits`() {
        val plan = create()
        assertEquals(9, plan.districts.map { it.id }.toSet().size)
        assertEquals(9, plan.districts.map { it.name }.toSet().size)
        for (d in plan.districts) {
            assertEquals(DensityRules.defaultsFor(d.zone), d.rules)
            assertTrue(d.floorArea <= d.maxFloorArea)
            assertEquals(plan.geometry.districtAreaSqm, d.landSqm)
        }
    }

    @Test fun `the centre is the old town and protected`() {
        val plan = create()
        assertTrue(plan.districts[4].heritage)
        assertTrue(plan.districts.filter { it.index != 4 }.none { it.heritage })
    }

    @Test fun `old stock sits nearer the centre`() {
        val plan = create()
        val centre = plan.districts[4].stock.averageAgeYears
        val edge = plan.districts[0].stock.averageAgeYears
        assertTrue(centre >= edge)
    }

    @Test fun `stock scales with the population`() {
        val small = create(pop = 30_000L).floorArea
        val big = create(pop = 600_000L).floorArea
        assertTrue(big > small * 5)
    }

    @Test fun `housing holds the people and jobs roughly match the workers`() {
        for (pop in listOf(25_000L, 150_000L, 800_000L)) {
            val plan = create(pop = pop)
            val cap = plan.districts.sumOf { Zoning.capacity(it.zone, it.floorArea, T.params) }
            assertTrue("capacity $cap for $pop", cap >= pop)
            val workers = pop * T.params.participationPpm / Scale.PPM
            val jobs = plan.districts.sumOf { Zoning.jobs(it.zone, it.floorArea, T.params) }
            assertTrue("jobs $jobs for $workers", jobs in workers / 2..workers * 2)
        }
    }

    @Test fun `the road network is connected and a bus line crosses the city`() {
        val plan = create()
        assertTrue(plan.network.roadsConnected())
        val bus = plan.network.lines().single()
        assertEquals("Crosstown bus", bus.name)
        assertEquals(LineStatus.ACTIVE, bus.status)
        assertEquals(3, bus.stops.size)
        assertTrue(plan.network.roadsAt(4).all { it.roadClass == RoadClass.ARTERIAL })
    }

    @Test fun `the council is seeded to the configured size`() {
        assertEquals(9, create().council.seats.size)
        assertEquals(5, create(p = T.params.copy(councilSize = 5)).council.seats.size)
    }

    @Test fun `the starting budget scales with the people`() {
        assertEquals(Money.units(20) * 100_000L, create(pop = 100_000L).budget.cash)
        assertEquals(Money.ZERO, create().budget.debt)
    }

    @Test fun `the same seed and city make the same plan`() {
        val a = create(5L); val b = create(5L)
        assertEquals(a.districts.map { it.zone }, b.districts.map { it.zone })
        assertEquals(a.districts.map { it.floorArea }, b.districts.map { it.floorArea })
        assertEquals(a.council.seats.map { it.ideology }, b.council.seats.map { it.ideology })
    }

    @Test fun `different cities get different layouts`() {
        val layouts = (1L..8L).map { create(7L, id = it).districts.map { d -> d.zone } }.toSet()
        assertTrue(layouts.size > 1)
    }

    @Test fun `smaller grids work too`() {
        val plan = create(p = UrbanParams(gridSize = 8, blockSize = 4))
        assertEquals(4, plan.districts.size)
        assertTrue(plan.network.roadsConnected())
    }

    @Test fun `a larger grid works too`() {
        val plan = create(pop = 400_000L, p = UrbanParams(gridSize = 20, blockSize = 4))
        assertEquals(25, plan.districts.size)
        assertTrue(plan.network.roadsConnected())
    }
}
