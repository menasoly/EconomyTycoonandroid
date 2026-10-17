package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanServiceTest {
    private val T = UrbanTestSupport

    @Test fun `a new city gets nine districts, a network and a first month of figures`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        assertEquals(9, plan.districts.size)
        assertTrue(plan.network.roads().isNotEmpty())
        assertTrue(plan.network.roadsConnected())
        assertTrue(plan.districts.all { it.rentPerSqm.isPositive })
        assertTrue(plan.population > 0L)
        assertEquals(1, plan.liveabilityHistory.size)
        assertNotNull(svc.score(T.city))
        T.checkInvariants(plan)
    }

    @Test fun `cities with the same id, no name or no people are refused`() {
        val svc = T.withCity()
        assertEquals(Rules.DUPLICATE, svc.addCity(T.city, T.country, "Again", T.inputs(), Tick(0)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, svc.addCity(CityId(2), T.country, " ", T.inputs(), Tick(0)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, svc.addCity(CityId(2), T.country, "Empty", T.inputs(0L), Tick(0)).rejectionOrNull()?.rule)
        assertEquals(1, svc.state.size)
    }

    @Test fun `removing a city forgets it`() {
        val svc = T.withCity()
        svc.removeCity(T.city)
        assertEquals(0, svc.state.size)
        assertNull(svc.score(T.city))
        assertNull(svc.outputs(T.city, Tick(0)))
        assertTrue(svc.advice(T.city, Tick(0)).isEmpty())
        assertTrue(svc.aiProposals(T.city, Tick(0)).isEmpty())
        assertTrue(svc.ranking().isEmpty())
    }

    @Test fun `housing holds the population with some room to spare`() {
        for (pop in listOf(20_000L, 150_000L, 900_000L)) {
            val plan = T.plan(T.withCity(population = pop))
            assertTrue("$pop housed ${plan.population}", plan.population >= pop * 95 / 100)
            assertTrue(plan.districts.sumOf { Zoning.capacity(it.zone, it.floorArea, T.params) } >= pop)
        }
    }

    @Test fun `the months pass and history builds up to its limit`() {
        val svc = T.withCity()
        T.run(svc, 0, 30 * 30)
        assertEquals(CityPlan.HISTORY.coerceAtMost(31), T.plan(svc).liveabilityHistory.size)
        assertEquals(Tick(900), T.plan(svc).lastMonthly)
    }

    @Test fun `invariants hold for three years of ordinary life`() {
        val svc = T.withCity()
        var t = 0L
        repeat(36) {
            t = T.run(svc, t, 30)
            T.checkInvariants(T.plan(svc))
        }
    }

    @Test fun `the monthly cycle runs only on month starts`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        val hash = svc.stateHash()
        svc.step(Tick(1), false) { T.inputs() }
        assertEquals(Tick(0), plan.lastMonthly)
        svc.step(Tick(30), true) { T.inputs() }
        assertEquals(Tick(30), plan.lastMonthly)
        assertTrue(hash != svc.stateHash())
    }

    @Test fun `the same seed gives the same history and a different seed a different one`() {
        fun run(seed: Long): String {
            val svc = T.withCity(seed)
            T.run(svc, 0, 24 * 30)
            return svc.stateHash().toHex()
        }
        assertEquals(run(5), run(5))
        assertTrue(run(5) != run(6))
    }

    @Test fun `cities are independent of each other`() {
        val alone = T.withCity()
        val together = T.withCity()
        together.addCity(CityId(2), T.country, "Neighbour", T.inputs(400_000L), Tick(0)).getOrThrow()
        T.run(alone, 0, 6 * 30)
        T.run(together, 0, 6 * 30)
        fun districts(s: UrbanService) = T.plan(s).districts.map { it.rentPerSqm to it.residents }
        assertEquals(districts(alone), districts(together))
    }

    @Test fun `a supplier that has nothing keeps the last inputs`() {
        val svc = T.withCity(population = 90_000L)
        svc.step(Tick(30), true) { null }
        assertEquals(90_000L, T.plan(svc).inputs.population)
        svc.step(Tick(60), true) { T.inputs(120_000L) }
        assertEquals(120_000L, T.plan(svc).inputs.population)
    }

    @Test fun `a growing city builds more and a shrinking one empties`() {
        val svc = T.withCity(population = 60_000L)
        val before = T.plan(svc).districts.sumOf { it.floorArea + it.pipelineArea }
        var t = 0L
        repeat(36) { t = T.run(svc, t, 30, population = 140_000L) }
        assertTrue(T.plan(svc).districts.sumOf { it.floorArea + it.pipelineArea } > before)
        val small = T.withCity(population = 150_000L)
        T.run(small, 0, 120, population = 30_000L)
        assertEquals(30_000L, T.plan(small).population)
        assertTrue(T.plan(small).districts.sumOf { it.vacancyPpm } > T.plan(svc).districts.sumOf { it.vacancyPpm } / 2)
    }

    @Test fun `outputs describe the housing market`() {
        val svc = T.withCity()
        T.run(svc, 0, 60)
        val out = svc.outputs(T.city, Tick(60))!!
        assertTrue(out.housingCapacity >= T.plan(svc).population)
        assertTrue(out.rentIndexPpm > 0L)
        assertTrue(out.boomPpm >= 0L)
        assertEquals(svc.score(T.city)!!.index, out.liveabilityIndex)
    }

    @Test fun `rents rise when the city is short of homes`() {
        val tight = T.withCity(population = 150_000L)
        val loose = T.withCity(population = 150_000L)
        T.run(tight, 0, 12 * 30, population = 260_000L)
        T.run(loose, 0, 12 * 30, population = 60_000L)
        assertTrue(T.plan(tight).districts.sumOf { it.rentPerSqm.raw } > T.plan(loose).districts.sumOf { it.rentPerSqm.raw })
    }

    @Test fun `advice is ordered by severity`() {
        val svc = T.withCity()
        T.run(svc, 0, 90)
        val advice = svc.advice(T.city, Tick(90))
        for ((a, b) in advice.zipWithNext()) assertTrue(a.severity >= b.severity)
    }

    @Test fun `the ranking covers every city once`() {
        val svc = T.withCity()
        svc.addCity(CityId(2), T.country, "Second", T.inputs(80_000L, Money.units(60)), Tick(0)).getOrThrow()
        svc.addCity(CityId(3), T.country, "Third", T.inputs(300_000L, Money.units(120)), Tick(0)).getOrThrow()
        val r = svc.ranking()
        assertEquals(listOf(1, 2, 3), r.map { it.rank })
        assertEquals(setOf(CityId(1), CityId(2), CityId(3)), r.map { it.city }.toSet())
        for ((a, b) in r.zipWithNext()) assertTrue(a.index >= b.index)
    }

    @Test fun `scores can be rebuilt after a load`() {
        val svc = T.withCity()
        val before = svc.score(T.city)!!.index
        svc.refreshScores()
        assertEquals(before, svc.score(T.city)!!.index)
    }

    @Test fun `a lobbying hook is asked about votes`() {
        val svc = T.withCity()
        var asked = 0
        svc.addLobbyingHook(LobbyingHook { _, _, _ -> asked++; 0 })
        val d = T.plan(svc).districts.first { it.zone == ZoneUse.RESIDENTIAL }
        svc.execute(UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, null), Tick(0)).getOrThrow()
        T.run(svc, 0, 60)
        assertTrue(asked >= T.params.councilSize)
    }

    @Test fun `the computer mayor proposes at most four sensible orders and they execute`() {
        val svc = T.withCity()
        var t = 0L
        repeat(18) {
            t = T.run(svc, t, 30)
            val proposals = svc.aiProposals(T.city, Tick(t))
            assertTrue(proposals.size <= MunicipalAi.MAX_COMMANDS)
            val results = svc.runAi(T.city, Tick(t))
            assertEquals(proposals, results.map { it.first })
            T.checkInvariants(T.plan(svc))
        }
    }

    @Test fun `commands for an unplanned city are rejected`() {
        val svc = T.service()
        assertEquals(Rules.UNKNOWN_ENTITY, svc.execute(UrbanCommand.SetTaxRates(CityId(9), TaxRates()), Tick(0)).rejectionOrNull()?.rule)
        assertFalse(svc.execute(UrbanCommand.Borrow(CityId(9), Money.units(5)), Tick(0)).isOk)
    }
}
