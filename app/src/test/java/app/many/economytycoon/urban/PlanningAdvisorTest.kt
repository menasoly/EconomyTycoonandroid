package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningAdvisorTest {
    private val T = UrbanTestSupport
    private val p = T.params

    private fun advise(plan: CityPlan, now: Long = 0): List<Insight> {
        val analysis = UrbanAnalysis.analyse(plan, p)
        return PlanningAdvisor.advise(plan, analysis, Liveability.score(plan, analysis, p), p, Tick(now))
    }

    private fun calm(): CityPlan = T.plan(T.withCity()).also { plan ->
        plan.budget.restore(plan.budget.rates, Money.units(900_000_000), Money.ZERO, 40_000L, Money.ZERO, 0, plan.budget.levelsList(), plan.budget.qualityList())
    }

    private fun has(list: List<Insight>, category: InsightCategory, severity: InsightSeverity? = null, text: String? = null) =
        list.any { it.category == category && (severity == null || it.severity == severity) && (text == null || it.title.contains(text)) }

    @Test fun `ratios are written with two decimals`() {
        assertEquals("2.40", PlanningAdvisor.ratioText(2400))
        assertEquals("0.05", PlanningAdvisor.ratioText(50))
        assertEquals("12.00", PlanningAdvisor.ratioText(12000))
    }

    @Test fun `every city gets at least the overall remark`() {
        val list = advise(calm())
        assertTrue(has(list, InsightCategory.OVERALL, InsightSeverity.INFO, "Weakest point"))
        assertTrue(list.all { it.title.isNotBlank() && it.detail.isNotBlank() })
    }

    @Test fun `insights are sorted most severe first and are repeatable`() {
        val plan = calm()
        plan.inputs = plan.inputs.copy(population = plan.inputs.population * 2)
        val a = advise(plan); val b = advise(plan)
        assertEquals(a, b)
        for ((x, y) in a.zipWithNext()) assertTrue(x.severity >= y.severity)
    }

    @Test fun `people without homes is a critical housing problem`() {
        val plan = calm()
        plan.inputs = plan.inputs.copy(population = plan.districts.sumOf { Zoning.capacity(it.zone, it.floorArea, p) } * 2)
        val list = advise(plan)
        assertTrue(has(list, InsightCategory.HOUSING, InsightSeverity.CRITICAL, "have no home"))
    }

    @Test fun `a tight market is a warning pointing at the dearest district`() {
        val plan = calm()
        val cap = plan.districts.sumOf { Zoning.capacity(it.zone, it.floorArea, p) }
        plan.inputs = plan.inputs.copy(population = cap)
        for (d in plan.districts) d.residents = Zoning.capacity(d.zone, d.floorArea, p)
        plan.districts.first { it.residentialArea > 0 }.rentPerSqm = Money.units(99)
        val list = advise(plan)
        val tight = list.firstOrNull { it.title == "Housing is very tight" }
        if (tight != null) assertEquals(plan.districts.first { it.rentPerSqm == Money.units(99) }.index, tight.district)
    }

    @Test fun `districts with many empty homes are noted`() {
        val plan = calm()
        val d = plan.districts.first { it.residentialArea > 0L }
        d.vacancyPpm = 300_000L
        assertTrue(advise(plan).any { it.district == d.index && it.title.contains("too many empty homes") })
    }

    @Test fun `a district built to its limit with scarce homes suggests a variance`() {
        val plan = calm()
        val d = plan.districts.first { it.zone == ZoneUse.RESIDENTIAL }
        d.stock.addNew(d.maxFloorArea - d.floorArea)
        d.vacancyPpm = 5_000L
        val i = advise(plan).first { it.district == d.index && it.title.contains("built to its limit") }
        assertEquals(InsightCategory.ZONING, i.category)
        assertTrue(i.suggestion!!.contains("variance"))
    }

    @Test fun `protests are critical and gentrification a warning`() {
        val plan = calm()
        plan.districts[0].backlashPpm = 800_000L
        val d = plan.districts[1]
        d.restoreRentHistory(listOf(Money.units(10))); d.rentPerSqm = Money.units(14)
        val list = advise(plan)
        assertTrue(has(list, InsightCategory.EQUITY, InsightSeverity.CRITICAL, "Protests"))
        assertTrue(has(list, InsightCategory.EQUITY, InsightSeverity.WARNING, "is gentrifying"))
    }

    @Test fun `jammed roads and crowded lines are flagged`() {
        val plan = calm()
        plan.traffic = plan.traffic.copy(congestionPpm = 500_000L)
        val road = plan.network.roads().first()
        road.flowVph = road.capacityVph * 2
        val line = plan.network.activeLines().first()
        line.peakLoad = line.capacityPerHour
        val list = advise(plan)
        assertTrue(has(list, InsightCategory.TRANSPORT, InsightSeverity.WARNING, "Roads are congested"))
        assertTrue(has(list, InsightCategory.TRANSPORT, InsightSeverity.WARNING, "is jammed"))
        assertTrue(has(list, InsightCategory.TRANSPORT, InsightSeverity.CRITICAL, "is overcrowded"))
    }

    @Test fun `an empty bus is noted as little used`() {
        val plan = calm()
        val line = plan.network.activeLines().first()
        line.peakLoad = line.capacityPerHour / 20
        line.monthlyRiders = 500L
        assertTrue(has(advise(plan), InsightCategory.TRANSPORT, InsightSeverity.INFO, "little used"))
    }

    @Test fun `big areas without service are mentioned`() {
        val plan = calm()
        plan.network.activeLines().forEach { it.status = LineStatus.SUSPENDED }
        for (d in plan.districts) d.residents = 1_000L
        assertTrue(has(advise(plan), InsightCategory.TRANSPORT, InsightSeverity.NOTICE, "no public transport"))
    }

    @Test fun `money trouble is reported in order of gravity`() {
        val plan = calm()
        plan.budget.restore(plan.budget.rates, Money.units(10), Money.units(900_000_000), 40_000L, Money.units(1_000_000), 8, plan.budget.levelsList(), plan.budget.qualityList())
        val list = advise(plan)
        assertTrue(has(list, InsightCategory.BUDGET, InsightSeverity.WARNING, "short of cash"))
        assertTrue(has(list, InsightCategory.BUDGET, InsightSeverity.WARNING, "Deficit for 8 months"))
        assertTrue(has(list, InsightCategory.BUDGET, InsightSeverity.CRITICAL, "Credit is poor"))
    }

    @Test fun `unfunded services are critical`() {
        val plan = calm()
        plan.budget.cash = Money.ZERO
        plan.budget.settleMonth(BudgetInputs(100, Money.ZERO, Money.ZERO, 0, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO), Money.ZERO, p.copy(debtLimitMonthsOfRevenue = 0))
        assertTrue(has(advise(plan), InsightCategory.BUDGET, InsightSeverity.CRITICAL, "underfunded"))
    }

    @Test fun `neighbouring factory and houses clash`() {
        val plan = calm()
        plan.districts[0].zone = ZoneUse.INDUSTRIAL
        plan.districts[1].zone = ZoneUse.RESIDENTIAL
        assertTrue(advise(plan).any { it.category == InsightCategory.ZONING && it.title.contains("clash") })
    }

    @Test fun `too little green space is noted`() {
        val plan = calm()
        for (d in plan.districts.filter { it.zone == ZoneUse.GREEN }) d.zone = ZoneUse.RESIDENTIAL
        assertTrue(has(advise(plan), InsightCategory.ZONING, InsightSeverity.NOTICE, "Little green space"))
    }

    @Test fun `politics notes hostile councils, angry voters and opposed petitions`() {
        val plan = calm()
        for (s in plan.council.seats) s.ideology = -300
        plan.council.moodAgainstGrowth = 800
        for (d in plan.districts) d.vacancyPpm = 5_000L
        val d = plan.districts.first { it.zone == ZoneUse.RESIDENTIAL }
        val pet = plan.petitions.file(PetitionId(1), d, PetitionKind.REZONE, null, ZoneUse.INDUSTRIAL, 0, 0, Money.ZERO, Tick(0), p).getOrThrow()
        pet.status = PetitionStatus.HEARING
        pet.staffScore = -700
        val list = advise(plan)
        assertTrue(has(list, InsightCategory.POLITICS, InsightSeverity.WARNING, "Voters are angry"))
        assertTrue(has(list, InsightCategory.POLITICS, InsightSeverity.INFO, "Planners oppose"))
    }

    @Test fun `stalled and overrunning projects are listed`() {
        val plan = calm()
        val a = plan.projects.start(CapitalProjectId(1), ProjectKind.SCHOOL, "School", 0, null, null, 0, Money.units(1_000_000), Tick(0), false).getOrThrow()
        val b = plan.projects.start(CapitalProjectId(2), ProjectKind.PARK, "Park", 1, null, null, 100, Money.units(1_000_000), Tick(0), false).getOrThrow()
        a.status = ProjectStatus.STALLED; a.stalledDays = 12
        b.budget = Money.units(1_500_000)
        val list = advise(plan)
        assertTrue(has(list, InsightCategory.PROJECTS, InsightSeverity.CRITICAL, "School has stopped"))
        assertTrue(has(list, InsightCategory.PROJECTS, InsightSeverity.WARNING, "Park is 50% over budget"))
    }

    @Test fun `dirty air and crime in populated districts are reported`() {
        val plan = calm()
        for (d in plan.districts) d.residents = 500L
        plan.districts[0].zone = ZoneUse.INDUSTRIAL
        plan.districts[1].zone = ZoneUse.INDUSTRIAL
        plan.districts[3].zone = ZoneUse.INDUSTRIAL
        plan.districts[0].stock.addNew(300_000L); plan.districts[1].stock.addNew(300_000L); plan.districts[3].stock.addNew(300_000L)
        plan.budget.boostQuality(Service.SANITATION, -500)
        val list = advise(plan)
        assertTrue(list.any { it.category == InsightCategory.ENVIRONMENT && it.title.startsWith("Air in") })
    }

    @Test fun `a healthy well funded city raises no critical flags`() {
        val list = advise(calm())
        assertFalse(list.any { it.severity == InsightSeverity.CRITICAL && it.category in listOf(InsightCategory.BUDGET, InsightCategory.PROJECTS) })
    }
}
