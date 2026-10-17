package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
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

class ProviderServiceTest {
    private val rig = ServiceRig()
    private val svc = ProviderService(rig.state, rig.taxonomy, rig.markets, rig.training, 5L)
    private val owner = CompanyId(1)
    private val region = RegionId(1)
    private val skill = DefaultTaxonomy.MACHINING
    private val cash = Money.units(1_000_000)

    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule

    private fun provider(name: String = "Tech Institute"): TrainingProvider = (svc.open(owner, region, name, Tick(0), cash) as Outcome.Ok).value.first
    private fun course(p: TrainingProvider = provider(), kind: CourseKind = CourseKind.CERTIFICATE, seats: Long = 200L, tuition: Long = 800L): Course =
        (svc.addCourse(owner, p.id, "Machining ${kind.label}", skill, kind, seats, Money.units(tuition), rig.world) as Outcome.Ok).value

    private fun fillPool(n: Long = 20_000L) = rig.markets.release(region, skill, SkillDistribution.around(250_000L, 80_000L, n))

    private fun run(from: Long, to: Long, step: Long = 30L): ProviderReport {
        val intents = ArrayList<app.many.economytycoon.finance.SettlementIntent>(); val events = ArrayList<TalentEvent>()
        var t = from
        while (t <= to) { val r = svc.advance(Tick(t), rig.world); intents += r.intents; events += r.events; t += step }
        return ProviderReport(intents, events)
    }

    @Test fun openingNeedsNameCashAndRoom() {
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.open(owner, region, " ", Tick(0), cash)))
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(svc.open(owner, region, "X", Tick(0), Money.units(10))))
        provider("A")
        assertEquals(Rules.NAME_TAKEN, rule(svc.open(CompanyId(2), region, "a", Tick(0), cash)))
        provider("B"); provider("C")
        assertEquals(Rules.LIMIT_EXCEEDED, rule(svc.open(owner, region, "D", Tick(0), cash)))
    }

    @Test fun openingProducesTheSetupPayment() {
        val (p, intents) = (svc.open(owner, region, "Tech", Tick(0), cash) as Outcome.Ok).value
        assertEquals(svc.params.setupCost, intents.single().total)
        assertEquals(svc.params.setupCost, p.costs)
        assertEquals(Accreditation.NONE, p.accreditation)
    }

    @Test fun coursesAreValidated() {
        val p = provider()
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.addCourse(owner, p.id, " ", skill, CourseKind.EVENING, 10, Money.units(100), rig.world)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.addCourse(owner, p.id, "x", SkillId("underwater_basketry"), CourseKind.EVENING, 10, Money.units(100), rig.world)))
        assertEquals(Rules.INVALID_QUANTITY, rule(svc.addCourse(owner, p.id, "x", skill, CourseKind.EVENING, 0, Money.units(100), rig.world)))
        assertEquals(Rules.INVALID_PRICE, rule(svc.addCourse(owner, p.id, "x", skill, CourseKind.EVENING, 10, Money.units(1), rig.world)))
        assertEquals(Rules.INVALID_PRICE, rule(svc.addCourse(owner, p.id, "x", skill, CourseKind.EVENING, 10, Money.units(900_000), rig.world)))
        assertEquals(Rules.NOT_OWNER, rule(svc.addCourse(CompanyId(2), p.id, "x", skill, CourseKind.EVENING, 10, Money.units(100), rig.world)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.addCourse(owner, ProviderId(77), "x", skill, CourseKind.EVENING, 10, Money.units(100), rig.world)))
        repeat(svc.params.maxCoursesPerProvider) { course(p) }
        assertEquals(Rules.LIMIT_EXCEEDED, rule(svc.addCourse(owner, p.id, "x", skill, CourseKind.EVENING, 10, Money.units(100), rig.world)))
    }

    @Test fun studentsEnrolAndPayTuition() {
        fillPool(); val c = course()
        val before = rig.markets.poolSize(region, skill)
        val r = run(30, 30)
        assertTrue(c.students > 0L)
        assertEquals(before - c.students, rig.markets.poolSize(region, skill))
        val tuition = r.intents.single { it.memo == "course tuition" }
        assertEquals(Money.units(800) * c.students, tuition.total)
        assertEquals(c.students, c.enrolledTotal)
    }

    @Test fun capacityLimitsEnrolment() {
        fillPool(200_000L); val c = course(seats = 15L)
        run(30, 90)
        assertTrue(c.students <= 15L)
    }

    @Test fun priceKeepsStudentsAway() {
        fillPool()
        val cheap = course(provider("Cheap"), tuition = 100L)
        val dear = course(provider("Dear"), tuition = 5_000L, seats = 200L)
        run(30, 30)
        assertTrue(cheap.enrolledTotal > dear.enrolledTotal)
    }

    @Test fun noJobSeekersNoStudents() {
        val c = course(); run(30, 120)
        assertEquals(0L, c.enrolledTotal)
    }

    @Test fun graduatesAreBetterAndGoBackToTheMarket() {
        fillPool(); val c = course(kind = CourseKind.EVENING, seats = 100L)
        run(30, 30)
        val inCourse = c.students
        val meanBefore = rig.markets.pool(region, skill).mean()
        run(60, 150)
        assertTrue(c.completedTotal in 1L..inCourse)
        val p = svc.provider(c.provider)!!
        assertEquals(c.completedTotal, p.graduates)
        assertTrue(p.meanGainPpm > 0L)
        assertTrue(rig.markets.pool(region, skill).mean() >= meanBefore - 5_000L)
    }

    @Test fun peopleAreConservedThroughACourse() {
        fillPool(5_000L); val c = course(kind = CourseKind.EVENING, seats = 100L)
        val start = rig.markets.poolSize(region, skill)
        run(30, 400)
        assertEquals(start, rig.markets.poolSize(region, skill) + c.students)
    }

    @Test fun teachingSpendBuildsQualityAndCostsMoney() {
        fillPool(); val p = provider(); course(p)
        svc.setTeaching(owner, p.id, 1_800_000L)
        val before = p.qualityPpm
        val r = run(30, 360)
        assertTrue(p.qualityPpm > before)
        assertTrue(r.intents.any { it.memo == "instructors" })
        val lean = provider("Lean"); course(lean); svc.setTeaching(owner, lean.id, 200_000L)
        run(390, 720)
        assertTrue(p.qualityPpm > lean.qualityPpm)
    }

    @Test fun teachingLevelIsRangeChecked() {
        val p = provider()
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.setTeaching(owner, p.id, 10L)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.setTeaching(owner, p.id, 9_000_000L)))
        assertEquals(Rules.NOT_OWNER, rule(svc.setTeaching(CompanyId(2), p.id, 1_000_000L)))
    }

    @Test fun accreditationNeedsMerit() {
        val p = provider()
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.accredit(owner, p.id, Accreditation.NONE, Tick(0), cash)))
        assertEquals(Rules.PREREQUISITE_MISSING, rule(svc.accredit(owner, p.id, Accreditation.PREMIER, Tick(0), cash)))
        p.qualityPpm = 400_000L
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(svc.accredit(owner, p.id, Accreditation.BASIC, Tick(0), Money.units(5))))
        val (_, intents) = (svc.accredit(owner, p.id, Accreditation.BASIC, Tick(0), cash) as Outcome.Ok).value
        assertEquals(Accreditation.BASIC.annualFee, intents.single().total)
        assertEquals(Rules.DUPLICATE, rule(svc.accredit(owner, p.id, Accreditation.BASIC, Tick(5), cash)))
    }

    @Test fun accreditationRaisesTrust() {
        fillPool()
        val plain = course(provider("Plain")); val bright = provider("Bright"); val bc = course(bright)
        bright.qualityPpm = 800_000L; bright.reputationPpm = 700_000L
        svc.accredit(owner, bright.id, Accreditation.BASIC, Tick(0), cash)
        run(30, 30)
        assertTrue(bc.enrolledTotal > plain.enrolledTotal)
    }

    @Test fun accreditationLapsesWhenQualityFalls() {
        val p = provider(); course(p)
        p.qualityPpm = 600_000L
        svc.accredit(owner, p.id, Accreditation.BASIC, Tick(0), cash)
        svc.setTeaching(owner, p.id, svc.params.minTeachingPpm)
        p.qualityPpm = 100_000L
        val r = run(30, 420)
        assertEquals(Accreditation.NONE, p.accreditation)
        assertTrue(r.events.any { it is TalentEvent.AccreditationChanged })
    }

    @Test fun accreditationIsRenewedWhenStillEarned() {
        val p = provider(); course(p)
        p.qualityPpm = 800_000L; svc.setTeaching(owner, p.id, 2_000_000L)
        svc.accredit(owner, p.id, Accreditation.BASIC, Tick(0), cash)
        val r = run(30, 400)
        assertEquals(Accreditation.BASIC, p.accreditation)
        assertTrue(r.intents.any { it.memo == "accreditation renewal" })
    }

    @Test fun employersBuyPlacesAndTheProviderEarns() {
        val c = course(seats = 50L); val p = svc.provider(c.provider)!!
        val key = TalentTestSupport.key(company = 2L, role = "machinist")
        val fact = TalentTestSupport.fact(key, 100)
        val r = svc.buySeats(CompanyId(2), c.id, fact, 10L, null, Money.units(500_000), Tick(0))
        assertTrue(r is Outcome.Ok)
        val started = (r as Outcome.Ok).value
        assertEquals(svc.corporatePrice(c) * 10L, started.intents.single().total)
        assertEquals(40L, svc.freeSeats(c))
        assertEquals(svc.corporatePrice(c) * 10L, p.revenue)
        assertTrue(svc.corporatePrice(c) < c.tuition)
    }

    @Test fun employerPlacesRespectCapacityRegionAndState() {
        val c = course(seats = 5L)
        val fact = TalentTestSupport.fact(TalentTestSupport.key(company = 2L, role = "machinist"), 100)
        assertEquals(Rules.INSUFFICIENT_CAPACITY, rule(svc.buySeats(CompanyId(2), c.id, fact, 6L, null, Money.units(500_000), Tick(0))))
        val away = TalentTestSupport.fact(TalentTestSupport.key(company = 2L, role = "machinist", region = 2L), 100)
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.buySeats(CompanyId(2), c.id, away, 1L, null, Money.units(500_000), Tick(0))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.buySeats(CompanyId(2), CourseId(99), fact, 1L, null, Money.units(500_000), Tick(0))))
        svc.closeCourse(owner, c.id)
        assertEquals(Rules.WRONG_STATE, rule(svc.buySeats(CompanyId(2), c.id, fact, 1L, null, Money.units(500_000), Tick(0))))
    }

    @Test fun employerSeatsCrowdOutStudents() {
        fillPool(100_000L); val c = course(seats = 30L)
        val fact = TalentTestSupport.fact(TalentTestSupport.key(company = 2L, role = "machinist"), 400)
        svc.buySeats(CompanyId(2), c.id, fact, 25L, null, Money.units(500_000), Tick(0))
        run(30, 30)
        assertTrue(c.students <= 5L)
    }

    @Test fun closedCoursesTakeNoNewStudents() {
        fillPool(); val c = course()
        assertTrue(svc.closeCourse(owner, c.id) is Outcome.Ok)
        assertEquals(Rules.WRONG_STATE, rule(svc.closeCourse(owner, c.id)))
        run(30, 90)
        assertEquals(0L, c.enrolledTotal)
    }

    @Test fun closingAProviderReturnsItsStudents() {
        fillPool(5_000L); val p = provider(); val c = course(p)
        val total = rig.markets.poolSize(region, skill)
        run(30, 30)
        assertTrue(c.students > 0L)
        assertTrue(svc.close(owner, p.id, Tick(31)) is Outcome.Ok)
        assertEquals(total, rig.markets.poolSize(region, skill))
        assertEquals(Rules.WRONG_STATE, rule(svc.close(owner, p.id, Tick(32))))
        assertTrue(svc.providersOf(owner).isEmpty())
    }

    @Test fun rankingFavoursQualityAndResults() {
        val a = provider("A"); val b = provider("B")
        a.qualityPpm = 800_000L; a.reputationPpm = 700_000L
        b.qualityPpm = 300_000L
        val table = svc.ranking(region)
        assertEquals(listOf(a.id, b.id), table.map { it.provider })
        assertEquals(listOf(1, 2), table.map { it.rank })
        assertTrue(table[0].scorePpm > table[1].scorePpm)
        assertTrue(svc.ranking(RegionId(2)).isEmpty())
    }

    @Test fun rankingTiesBreakById() {
        val a = provider("A"); val b = provider("B")
        assertEquals(listOf(a.id, b.id), svc.ranking().map { it.provider })
    }

    @Test fun resultsBuildReputation() {
        fillPool(); val c = course(kind = CourseKind.CERTIFICATE, seats = 400L); val p = svc.provider(c.provider)!!
        p.qualityPpm = 800_000L
        svc.setTeaching(owner, p.id, 1_800_000L)
        run(30, 900)
        assertTrue(p.graduates > 0L)
        assertTrue(p.reputationPpm > 400_000L)
        assertNotNull(svc.course(c.id))
    }

    @Test fun businessesHaveRevenueAndCosts() {
        fillPool(); val c = course(); val p = svc.provider(c.provider)!!
        run(30, 360)
        assertTrue(p.revenue.raw > 0L && p.costs > svc.params.setupCost)
        assertFalse(p.closed)
    }

    @Test fun sameSeedSameHistory() {
        fun history(): app.many.economytycoon.core.StateHash {
            val r = ServiceRig(); val s = ProviderService(r.state, r.taxonomy, r.markets, r.training, 5L)
            r.markets.release(region, skill, SkillDistribution.around(250_000L, 80_000L, 20_000L))
            val p = (s.open(owner, region, "T", Tick(0), cash) as Outcome.Ok).value.first
            s.addCourse(owner, p.id, "c", skill, CourseKind.BOOTCAMP, 200, Money.units(800), r.world)
            var t = 30L
            while (t <= 720L) { s.advance(Tick(t), r.world); t += 30L }
            return r.state.stateHash()
        }
        assertEquals(history(), history())
    }
}
