package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveabilityTest {
    private fun scoreOf(plan: CityPlan) = Liveability.score(plan, UrbanAnalysis.analyse(plan, UrbanTestSupport.params), UrbanTestSupport.params)

    @Test fun `weights add up to a whole`() {
        assertEquals(1000, LiveabilityFactor.values().sumOf { it.weightPermille })
    }

    @Test fun `a fresh city scores inside the range with every factor bounded`() {
        val plan = UrbanTestSupport.plan(UrbanTestSupport.withCity())
        val s = scoreOf(plan)
        assertTrue(s.index in 0..1000)
        for (f in LiveabilityFactor.values()) assertTrue("$f", s.of(f) in 0..1000)
        assertEquals(LiveabilityFactor.values().toSet(), s.asMap().keys)
    }

    @Test fun `the index is the weighted mean of the factors`() {
        val s = scoreOf(UrbanTestSupport.plan(UrbanTestSupport.withCity()))
        val total = LiveabilityFactor.values().sumOf { s.of(it) * it.weightPermille }
        assertEquals(total / 1000, s.index)
    }

    @Test fun `strongest and weakest factors are consistent`() {
        val s = scoreOf(UrbanTestSupport.plan(UrbanTestSupport.withCity()))
        assertTrue(LiveabilityFactor.values().all { s.of(s.strongest()) >= s.of(it) })
        assertTrue(LiveabilityFactor.values().all { s.of(s.weakest()) <= s.of(it) })
    }

    @Test fun `underfunded services lower the services factor`() {
        val svc = UrbanTestSupport.withCity()
        val plan = UrbanTestSupport.plan(svc)
        val before = scoreOf(plan).of(LiveabilityFactor.SERVICES)
        plan.budget.boostQuality(Service.EDUCATION, -400)
        plan.budget.boostQuality(Service.HEALTH, -400)
        assertTrue(scoreOf(plan).of(LiveabilityFactor.SERVICES) < before)
    }

    @Test fun `more green land raises the green factor`() {
        val plan = UrbanTestSupport.plan(UrbanTestSupport.withCity())
        val before = scoreOf(plan).of(LiveabilityFactor.GREEN)
        val target = plan.districts.first { it.zone != ZoneUse.GREEN }
        target.zone = ZoneUse.GREEN
        assertTrue(scoreOf(plan).of(LiveabilityFactor.GREEN) >= before)
    }

    @Test fun `high unemployment lowers the jobs factor`() {
        val plan = UrbanTestSupport.plan(UrbanTestSupport.withCity())
        val calm = scoreOf(plan).of(LiveabilityFactor.JOBS)
        plan.inputs = plan.inputs.copy(unemploymentPpm = 200_000L)
        assertTrue(scoreOf(plan).of(LiveabilityFactor.JOBS) < calm)
    }

    @Test fun `ranking orders cities by index with ties to the lower id`() {
        val a = CityId(2); val b = CityId(1); val c = CityId(3)
        val mk = { idx: Int -> LiveabilityScore(idx, IntArray(8) { it * 100 }) }
        val ranked = Liveability.rank(mapOf(a to mk(600), b to mk(600), c to mk(800)))
        assertEquals(listOf(c, b, a), ranked.map { it.city })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
        assertEquals(LiveabilityFactor.JOBS, ranked[0].best)
        assertEquals(LiveabilityFactor.HOUSING, ranked[0].worst)
    }

    @Test fun `an empty ranking is empty`() {
        assertTrue(Liveability.rank(emptyMap()).isEmpty())
        assertNull(Liveability.leader(emptyMap(), LiveabilityFactor.SAFETY))
    }

    @Test fun `the leader on a factor is the city with the best score there`() {
        val low = LiveabilityScore(500, IntArray(8) { 300 })
        val high = LiveabilityScore(400, IntArray(8) { 300 }.also { it[LiveabilityFactor.SAFETY.ordinal] = 900 })
        assertEquals(CityId(2), Liveability.leader(mapOf(CityId(1) to low, CityId(2) to high), LiveabilityFactor.SAFETY))
        assertEquals(CityId(1), Liveability.leader(mapOf(CityId(1) to low, CityId(2) to high), LiveabilityFactor.GREEN))
    }

    @Test fun `a richer city with more services outranks a poor one`() {
        val svc = UrbanTestSupport.service()
        svc.addCity(CityId(1), UrbanTestSupport.country, "Rich", UrbanTestSupport.inputs(150_000L, Money.units(140), 250_000L), Tick(0)).getOrThrow()
        svc.addCity(CityId(2), UrbanTestSupport.country, "Poor", UrbanTestSupport.inputs(150_000L, Money.units(40), 700_000L), Tick(0)).getOrThrow()
        val poorPlan = UrbanTestSupport.plan(svc, CityId(2))
        for (s in Service.values()) poorPlan.budget.setLevel(s, 100)
        repeat(10) { poorPlan.budget.settleMonth(BudgetInputs(10_000, Money.ZERO, Money.ZERO, 0, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO), Money.ZERO, UrbanTestSupport.params.copy(debtLimitMonthsOfRevenue = 0)) }
        svc.refreshScores()
        assertTrue(svc.score(CityId(1))!!.of(LiveabilityFactor.SERVICES) > svc.score(CityId(2))!!.of(LiveabilityFactor.SERVICES))
        assertEquals(2, svc.ranking().size)
    }
}
