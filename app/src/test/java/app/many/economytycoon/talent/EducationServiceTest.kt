package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EducationServiceTest {
    private val state = TalentState()
    private val params = EducationParams()
    private val svc = EducationService(state, TalentTestSupport.taxonomy, params)
    private val world = FixedTalentWorld.standard()
    private val region = RegionId(1)
    private val country = CountryId(1)
    private val owner = CompanyId(7)
    private val par: (RegionId, SkillId) -> Long = { _, _ -> Ppm.ONE }

    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule

    private fun openUni(capacity: Long = 200L, tuition: Long = 2_500L, spend: Long = 4_500L, fields: List<SkillId> = listOf(DefaultTaxonomy.ENGINEERING)): Institution =
        (svc.open(owner, country, region, InstitutionKind.UNIVERSITY, "Test U", capacity, Money.units(tuition), Money.units(spend), fields, Tick(0), Money.units(90)) as Outcome.Ok).value.institution

    private fun publicSchool(capacity: Long = 4_000L): Institution =
        (svc.registerPublic(country, region, InstitutionKind.SCHOOL, "Public school", capacity, Money.units(1_200), emptyList(), Tick(0)) as Outcome.Ok).value

    private fun run(from: Long, to: Long, premium: (RegionId, SkillId) -> Long = par): List<EducationReport> =
        (from..to).map { svc.advance(Tick(it), world, premium) }

    @Test fun openingChargesTheBuildingsAndRegisters() {
        val r = (svc.open(owner, country, region, InstitutionKind.UNIVERSITY, "Test U", 100L, Money.units(2_500), Money.units(4_500), listOf(DefaultTaxonomy.ENGINEERING), Tick(5), Money.units(90)) as Outcome.Ok).value
        assertEquals(1, r.intents.size)
        assertEquals(Money.units(900_000), r.intents[0].total)
        assertEquals(owner, r.institution.owner)
        assertEquals(listOf(r.institution), svc.ownedBy(owner))
        assertFalse(r.institution.isPublic)
    }

    @Test fun buildCostScalesWithLocalPay() {
        val cheap = svc.buildCost(InstitutionKind.VOCATIONAL, 100L, Money.units(45))
        val dear = svc.buildCost(InstitutionKind.VOCATIONAL, 100L, Money.units(90))
        assertEquals(dear.raw / 2, cheap.raw)
    }

    @Test fun openingNeedsSaneArguments() {
        fun open(name: String = "X", cap: Long = 100L, tuition: Long = 1L, fields: List<SkillId> = listOf(DefaultTaxonomy.ENGINEERING), kind: InstitutionKind = InstitutionKind.UNIVERSITY) =
            svc.open(owner, country, region, kind, name, cap, Money.units(tuition), Money.units(1), fields, Tick(0), Money.units(90))
        assertEquals(Rules.INVALID_ARGUMENT, rule(open(name = " ")))
        assertEquals(Rules.INVALID_QUANTITY, rule(open(cap = 5L)))
        assertEquals(Rules.INVALID_QUANTITY, rule(open(cap = 10_000_000L)))
        assertEquals(Rules.INVALID_PRICE, rule(open(tuition = -1L)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(open(fields = emptyList())))
        assertEquals(Rules.DUPLICATE, rule(open(fields = listOf(DefaultTaxonomy.DESIGN, DefaultTaxonomy.DESIGN))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(open(fields = listOf(DefaultTaxonomy.MACHINING))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(open(fields = listOf(SkillId("alchemy")))))
        assertTrue(open() is Outcome.Ok)
    }

    @Test fun aCompanyMayOnlyRunSoManyInstitutions() {
        repeat(params.maxInstitutionsPerCompany) { openUni() }
        assertEquals(Rules.LIMIT_EXCEEDED, rule(svc.open(owner, country, region, InstitutionKind.UNIVERSITY, "More", 100L, Money.ZERO, Money.ZERO, listOf(DefaultTaxonomy.DESIGN), Tick(0), Money.units(90))))
    }

    @Test fun schoolsAlwaysTeachGeneralWork() {
        val s = (svc.open(owner, country, region, InstitutionKind.SCHOOL, "Private school", 100L, Money.units(100), Money.units(1_200), emptyList(), Tick(0), Money.units(90)) as Outcome.Ok).value.institution
        assertEquals(listOf(DefaultTaxonomy.GENERAL), s.fields)
    }

    @Test fun onlyTheOwnerChangesTheTerms() {
        val u = openUni()
        assertEquals(Rules.NOT_OWNER, rule(svc.setTuition(u.id, CompanyId(8), Money.units(1))))
        assertEquals(Rules.NOT_OWNER, rule(svc.setSpend(u.id, CompanyId(8), Money.units(1))))
        assertEquals(Rules.NOT_OWNER, rule(svc.setFields(u.id, CompanyId(8), listOf(DefaultTaxonomy.DESIGN))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.setTuition(InstitutionId(99), owner, Money.units(1))))
        assertEquals(Rules.INVALID_PRICE, rule(svc.setTuition(u.id, owner, Money.units(-1))))
        assertTrue(svc.setTuition(u.id, owner, Money.units(3_000)) is Outcome.Ok)
        assertEquals(Money.units(3_000), u.tuition)
        assertTrue(svc.setFields(u.id, owner, listOf(DefaultTaxonomy.DESIGN, DefaultTaxonomy.ANALYSIS)) is Outcome.Ok)
        assertEquals(listOf(DefaultTaxonomy.ANALYSIS, DefaultTaxonomy.DESIGN), u.fields)
    }

    @Test fun expansionOpensSeatsAfterTheBuildingTime() {
        val u = openUni(capacity = 100L)
        val r = (svc.expand(u.id, owner, 50L, Tick(0), Money.units(90)) as Outcome.Ok).value
        assertEquals(Tick(360), r.readyAt)
        assertEquals(1, r.intents.size)
        assertEquals(100L, u.capacity)
        assertEquals(50L, u.underConstruction)
        val reports = run(1, 360)
        assertEquals(150L, u.capacity)
        assertEquals(0L, u.underConstruction)
        assertTrue(reports.flatMap { it.events }.any { it is TalentEvent.CapacityReady && it.seats == 50L })
    }

    @Test fun expansionHasLimits() {
        val u = openUni(capacity = 100L)
        assertEquals(Rules.INVALID_QUANTITY, rule(svc.expand(u.id, owner, 0L, Tick(0), Money.units(90))))
        assertEquals(Rules.LIMIT_EXCEEDED, rule(svc.expand(u.id, owner, params.maxSeatsPerInstitution, Tick(0), Money.units(90))))
    }

    @Test fun closingDropsTheStudentsAndBlocksChanges() {
        val u = openUni()
        u.intakes.add(Intake(0L, DefaultTaxonomy.ENGINEERING, 80L, 800_000L))
        assertTrue(svc.close(u.id, owner, Tick(10)) is Outcome.Ok)
        assertFalse(u.isOpen)
        assertEquals(80L, u.droppedTotal)
        assertEquals(0L, u.enrolled)
        assertEquals(Rules.WRONG_STATE, rule(svc.setTuition(u.id, owner, Money.units(1))))
        assertTrue(svc.ownedBy(owner).isEmpty())
        assertTrue(svc.inRegion(region).isEmpty())
    }

    @Test fun theIntakeDayEnrolsStudentsUpToTheSeats() {
        val school = publicSchool(capacity = 2_000L)
        world.r(1).youth = 1_000L
        val reports = run(0, params.intakeDayOfYear.toLong())
        // 850 of the 1000 young people want school; all fit
        assertEquals(850L, school.enrolled)
        assertEquals(0L, school.unmetLastIntake)
        assertTrue(reports.flatMap { it.events }.none { it is TalentEvent.PlacesShort })
    }

    @Test fun tooFewSeatsLeavePeopleOutAndRaiseAnEvent() {
        val school = publicSchool(capacity = 400L)
        val reports = run(0, params.intakeDayOfYear.toLong())
        assertEquals(400L, school.enrolled)
        assertEquals(450L, school.unmetLastIntake)
        val ev = reports.flatMap { it.events }.filterIsInstance<TalentEvent.PlacesShort>().single()
        assertEquals(450L, ev.unmet)
        assertEquals(InstitutionKind.SCHOOL, ev.kind)
    }

    @Test fun competingCollegesShareStudentsByQualityAndPrice() {
        val good = openUni(capacity = 1_000L, tuition = 500L)
        good.qualityPpm = 800_000L
        val poor = (svc.open(CompanyId(9), country, region, InstitutionKind.UNIVERSITY, "Cheap U", 1_000L, Money.units(500), Money.units(500), listOf(DefaultTaxonomy.ENGINEERING), Tick(0), Money.units(90)) as Outcome.Ok).value.institution
        poor.qualityPpm = 200_000L
        state.leavers.getOrPut(region) { SkillDistribution() }.addAll(SkillDistribution.around(300_000L, 100_000L, 1_000L))
        run(0, params.intakeDayOfYear.toLong())
        assertTrue(good.enrolled > poor.enrolled)
        assertEquals(260L, good.enrolled + poor.enrolled)
    }

    @Test fun collegeLeaversAreTheBestOfTheSchoolLeavers() {
        openUni(capacity = 1_000L, tuition = 0L)
        val leavers = state.leavers.getOrPut(region) { SkillDistribution() }
        leavers.addAll(SkillDistribution.around(300_000L, 150_000L, 1_000L))
        val meanBefore = leavers.mean()
        val reports = run(0, params.intakeDayOfYear.toLong())
        val rest = reports.flatMap { it.graduations }.single { it.skill == DefaultTaxonomy.GENERAL }
        assertEquals(740L, rest.count)
        assertTrue(rest.levelPpm < meanBefore)
        assertTrue(state.leavers.getValue(region).isEmpty)
    }

    @Test fun leaversWithNoCollegeAllJoinTheLabourPool() {
        state.leavers.getOrPut(region) { SkillDistribution() }.addAll(SkillDistribution.around(300_000L, 100_000L, 500L))
        val reports = run(0, params.intakeDayOfYear.toLong())
        val grad = reports.flatMap { it.graduations }.single()
        assertEquals(InstitutionId(0), grad.institution)
        assertEquals(500L, grad.count)
        assertEquals(DefaultTaxonomy.GENERAL, grad.skill)
    }

    @Test fun classesGraduateAfterTheirDurationAtAQualityLevel() {
        val u = openUni(capacity = 500L)
        u.qualityPpm = 500_000L
        u.intakes.add(Intake(params.intakeDayOfYear.toLong(), DefaultTaxonomy.ENGINEERING, 100L, 800_000L))
        val finish = params.intakeDayOfYear.toLong() + 1_440L
        val reports = run(finish - 2L, finish)
        val g = reports.flatMap { it.graduations }.single()
        assertEquals(80L, g.count)
        assertEquals(DefaultTaxonomy.ENGINEERING, g.skill)
        assertEquals(700_000L, g.levelPpm)
        assertEquals(80L, u.graduatesTotal)
        assertEquals(20L, u.droppedTotal)
        // the class joins the stock, and the year's retirements take 2.5% of it on the same day
        assertEquals(78L, svc.qualifications(region).of(InstitutionKind.UNIVERSITY))
    }

    @Test fun dropoutsHappenMonthlyAndSumToThePlan() {
        val u = openUni(capacity = 500L)
        val intake = Intake(240L, DefaultTaxonomy.ENGINEERING, 100L, 800_000L)
        u.intakes.add(intake)
        run(241, 240 + 1_440 - 3)
        assertTrue(intake.enrolled in 80L..81L)
        assertEquals(intake.enrolled, u.enrolled)
    }

    @Test fun anEarlyMonthStillHasMostOfTheClass() {
        val u = openUni(capacity = 500L)
        val intake = Intake(240L, DefaultTaxonomy.ENGINEERING, 100L, 800_000L)
        u.intakes.add(intake)
        run(241, 300)
        assertTrue(intake.enrolled in 97L..100L)
    }

    @Test fun qualityDriftsTowardWhatSpendingBuys() {
        val rich = openUni(spend = 9_000L)
        val poor = (svc.open(CompanyId(9), country, region, InstitutionKind.UNIVERSITY, "Poor U", 100L, Money.ZERO, Money.units(500), listOf(DefaultTaxonomy.DESIGN), Tick(0), Money.units(90)) as Outcome.Ok).value.institution
        run(1, 720)
        assertTrue(rich.qualityPpm > 550_000L)
        assertTrue(poor.qualityPpm < 250_000L)
    }

    @Test fun privateInstitutionsBillTuitionAndPayTeachers() {
        val u = openUni(tuition = 2_400L, spend = 1_200L)
        u.intakes.add(Intake(0L, DefaultTaxonomy.ENGINEERING, 120L, 900_000L))
        val intents = svc.advance(Tick(30), world, par).intents
        assertEquals(2, intents.size)
        val tuition = intents.single { it.key.startsWith("talent:tuition") }
        val teaching = intents.single { it.key.startsWith("talent:teaching") }
        assertEquals(Money.units(2_400).scale(u.enrolled, 12L), tuition.total)
        assertEquals(Money.units(1_200).scale(u.enrolled, 12L), teaching.total)
        assertTrue(u.revenueYear.isPositive && u.costYear.isPositive)
    }

    @Test fun publicInstitutionsAreFundedByTheGovernment() {
        val s = publicSchool(capacity = 1_000L)
        s.intakes.add(Intake(0L, DefaultTaxonomy.GENERAL, 600L, 900_000L))
        val intents = svc.advance(Tick(30), world, par).intents
        assertEquals(1, intents.size)
        assertTrue(intents[0].key.startsWith("talent:public"))
        assertEquals(Money.units(1_200).scale(s.enrolled, 12L), intents[0].total)
    }

    @Test fun anIntakeKeepsItsCompletionRateFromTheDayItStarted() {
        val u = openUni(capacity = 300L, tuition = 0L)
        state.leavers.getOrPut(region) { SkillDistribution() }.addAll(SkillDistribution.around(300_000L, 100_000L, 1_000L))
        u.qualityPpm = 500_000L
        run(0, params.intakeDayOfYear.toLong())
        val intake = u.intakes.single()
        u.qualityPpm = 900_000L
        assertEquals(EducationDemand.completionPpm(InstitutionKind.UNIVERSITY, 500_000L, Ppm.ratio(260L, 300L), params), intake.completionPpm)
    }

    @Test fun qualificationsRetireWithTheWorkforce() {
        val stock = QualificationStock()
        state.qualifications[region] = stock
        stock.byKind[InstitutionKind.UNIVERSITY.ordinal] = 40_000L
        run(0, params.intakeDayOfYear.toLong())
        assertEquals(39_000L, stock.of(InstitutionKind.UNIVERSITY))
    }

    @Test fun educationIndexWeighsDegreesMoreThanSchooling() {
        val q = QualificationStock()
        q.byKind[0] = 1_000L
        val a = q.indexPpm(1_000L)
        q.byKind[2] = 1_000L
        val b = q.indexPpm(1_000L)
        assertEquals(350_000L, a)
        assertEquals(Ppm.ONE, b)
        assertEquals(0L, q.indexPpm(0L))
    }

    @Test fun regionsWithoutInstitutionsAreSkipped() {
        val reports = run(0, params.intakeDayOfYear.toLong())
        assertTrue(reports.all { it.graduations.isEmpty() })
    }

    @Test fun theSameStepsGiveTheSameHash() {
        fun build(): TalentState {
            val st = TalentState(); val s = EducationService(st, TalentTestSupport.taxonomy, params)
            s.registerPublic(country, region, InstitutionKind.SCHOOL, "S", 2_000L, Money.units(1_200), emptyList(), Tick(0))
            s.open(owner, country, region, InstitutionKind.UNIVERSITY, "U", 100L, Money.units(100), Money.units(2_000), listOf(DefaultTaxonomy.DESIGN), Tick(0), Money.units(90))
            for (t in 0L..1_000L) s.advance(Tick(t), world, par)
            return st
        }
        assertEquals(build().stateHash(), build().stateHash())
        assertNotNull(build().stateHash())
    }
}
