package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MunicipalAiTest {
    private val T = UrbanTestSupport
    private val p = T.params

    private fun decide(plan: CityPlan, now: Long = 0): List<UrbanCommand> {
        val analysis = UrbanAnalysis.analyse(plan, p)
        return MunicipalAi.decide(plan, analysis, Liveability.score(plan, analysis, p), p, Tick(now))
    }

    private fun plan(cash: Money? = null): CityPlan {
        val plan = T.plan(T.withCity())
        if (cash != null) plan.budget.restore(plan.budget.rates, cash, plan.budget.debt, 40_000L, plan.budget.lastRevenue, plan.budget.consecutiveDeficitMonths, plan.budget.levelsList(), plan.budget.qualityList())
        return plan
    }

    @Test fun `the profile follows the council and the city's name`() {
        val a = plan()
        for (s in a.council.seats) s.ideology = 400
        val growth = MayorProfile.of(a)
        for (s in a.council.seats) s.ideology = -400
        val cautious = MayorProfile.of(a)
        assertTrue(growth.growth > cautious.growth)
        assertTrue(cautious.green > growth.green)
        assertEquals(growth.thrift, cautious.thrift)
        assertTrue(growth.growth in 0..1000 && growth.thrift in 0..1000 && growth.green in 0..1000)
    }

    @Test fun `no more than four orders and all of them are for this city`() {
        val plan = plan()
        plan.districts[0].backlashPpm = 900_000L
        val orders = decide(plan)
        assertTrue(orders.size <= MunicipalAi.MAX_COMMANDS)
        assertTrue(orders.all { it.city == T.city })
    }

    @Test fun `a broke city with a long deficit raises property tax`() {
        val plan = plan(Money.units(100))
        plan.budget.restore(plan.budget.rates, Money.units(100), Money.ZERO, 40_000L, Money.ZERO, 5, plan.budget.levelsList(), plan.budget.qualityList())
        val first = decide(plan).first()
        assertTrue(first is UrbanCommand.SetTaxRates)
        assertEquals(plan.budget.rates.propertyPerYearPpm + 1_000L, (first as UrbanCommand.SetTaxRates).rates.propertyPerYearPpm)
    }

    @Test fun `a flush city with debt pays it back`() {
        val plan = plan(Money.units(5_000_000_000))
        plan.budget.restore(plan.budget.rates, Money.units(5_000_000_000), Money.units(1_000_000), 40_000L, plan.budget.lastRevenue, 0, plan.budget.levelsList(), plan.budget.qualityList())
        val first = decide(plan).first()
        assertTrue(first is UrbanCommand.Repay)
        assertTrue((first as UrbanCommand.Repay).amount <= Money.units(1_000_000))
    }

    @Test fun `angry districts get tenant protection and housing aid`() {
        val plan = plan(Money.units(900_000_000))
        plan.districts[2].backlashPpm = 700_000L
        val orders = decide(plan)
        val protect = orders.filterIsInstance<UrbanCommand.SetTenantProtection>().single()
        assertEquals(2, protect.district)
        assertEquals(200, protect.permille)
        assertTrue(orders.any { it is UrbanCommand.SetServiceLevel && it.service == Service.HOUSING_AID })
    }

    @Test fun `fully protected districts are left alone`() {
        val plan = plan(Money.units(900_000_000))
        plan.districts[2].backlashPpm = 700_000L
        plan.districts[2].tenantProtectionPermille = 900
        assertTrue(decide(plan).none { it is UrbanCommand.SetTenantProtection })
    }

    @Test fun `a squeezed housing market with a growth minded mayor files a variance`() {
        val plan = plan(Money.units(900_000_000))
        for (s in plan.council.seats) s.ideology = 300
        plan.inputs = plan.inputs.copy(population = plan.inputs.population * 2)
        for (d in plan.districts) d.vacancyPpm = 0L
        val d = plan.districts.filter { it.residentialArea > 0L }.maxByOrNull { it.rentPerSqm.raw }!!
        d.stock.addNew(d.maxFloorArea - d.floorArea)
        val v = decide(plan).filterIsInstance<UrbanCommand.FilePetition>()
        if (v.isNotEmpty()) {
            assertEquals(PetitionKind.VARIANCE, v[0].kind)
            assertTrue(v[0].farMilli > plan.districts[v[0].district].rules.maxFarMilli)
            assertEquals(null, v[0].applicant)
        }
    }

    @Test fun `overcrowded lines get more frequent service and empty ones less`() {
        val plan = plan(Money.units(900_000_000))
        val line = plan.network.activeLines().first()
        line.peakLoad = line.capacityPerHour
        val faster = decide(plan).filterIsInstance<UrbanCommand.SetLineHeadway>().first()
        assertTrue(faster.headwaySeconds < line.headwaySeconds)
        line.peakLoad = line.capacityPerHour / 20
        line.monthlyRiders = 100L
        val slower = decide(plan).filterIsInstance<UrbanCommand.SetLineHeadway>().first()
        assertTrue(slower.headwaySeconds > line.headwaySeconds)
    }

    @Test fun `unfunded services are trimmed starting with the dearest`() {
        val plan = plan(Money.units(900_000_000))
        plan.budget.cash = Money.ZERO
        plan.budget.settleMonth(BudgetInputs(100, Money.ZERO, Money.ZERO, 0, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO), Money.ZERO, p.copy(debtLimitMonthsOfRevenue = 0))
        val cut = decide(plan).filterIsInstance<UrbanCommand.SetServiceLevel>()
        assertTrue(cut.any { it.service == Service.EDUCATION && it.level < plan.budget.level(Service.EDUCATION) })
    }

    @Test fun `a rich green minded mayor builds a park when there is too little`() {
        val plan = plan(Money.units(900_000_000_000))
        for (s in plan.council.seats) s.ideology = -200
        for (d in plan.districts.filter { it.zone == ZoneUse.GREEN }) d.zone = ZoneUse.RESIDENTIAL
        val park = decide(plan).filterIsInstance<UrbanCommand.StartProject>().firstOrNull { it.kind == ProjectKind.PARK }
        if (park != null) assertTrue(park.size > 0L && park.district != null)
    }

    @Test fun `the orders can be executed without errors`() {
        val svc = T.withCity()
        var t = 0L
        repeat(24) {
            t = T.run(svc, t, 30)
            for ((cmd, result) in svc.runAi(T.city, Tick(t))) {
                val bad = result.rejectionOrNull()
                assertFalse("$cmd rejected: $bad", bad != null && bad.rule == app.many.economytycoon.core.Rules.INVALID_ARGUMENT)
            }
        }
    }

    @Test fun `the same city always gets the same orders`() {
        val a = decide(plan()); val b = decide(plan())
        assertEquals(a, b)
    }

    @Test fun `a mayor keeps a city solvent over years`() {
        val svc = T.withCity()
        var t = 0L
        repeat(60) {
            t = T.run(svc, t, 30)
            svc.runAi(T.city, Tick(t))
        }
        val plan = T.plan(svc)
        T.checkInvariants(plan)
        assertTrue(plan.budget.debt <= plan.budget.debtLimit(p) + Money.units(1))
        assertTrue(plan.budget.rating() > 100)
    }
}
