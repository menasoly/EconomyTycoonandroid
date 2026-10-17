package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.talent.TalentTestSupport.fact
import app.many.economytycoon.talent.TalentTestSupport.inputs
import app.many.economytycoon.talent.TalentTestSupport.key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingServiceTest {
    private val rig = ServiceRig()
    private val svc = rig.training
    private val k = key(role = "machinist")
    private val company = CompanyId(1)
    private val cash = Money.units(1_000_000)

    private fun staffed(n: Long = 40L) = fact(k, n).also { rig.skills.reconcile(it, rig.world) }

    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule

    private fun start(f: CohortFact = fact(k, 40L), kind: ProgrammeKind = ProgrammeKind.ON_THE_JOB, seats: Long = 10L, days: Int = 90,
                      clause: RetentionClause? = null, skill: SkillId = DefaultTaxonomy.MACHINING, available: Money = cash, tick: Long = 0L) =
        svc.start(company, f, skill, kind, seats, days, clause, available, Tick(tick))

    private fun ok(o: Outcome<StartedProgramme>) = (o as Outcome.Ok).value

    private fun run(from: Long, to: Long, f: CohortFact): List<TrainingReport> = (from..to).map { svc.advance(Tick(it), inputs(it, f)) }

    @Test fun startingRegistersAnActiveProgramme() {
        val f = staffed()
        val s = ok(start(f))
        assertTrue(s.programme.isActive)
        assertEquals(Tick(90), s.programme.endTick)
        assertEquals(listOf(s.programme), svc.activeOf(company))
        assertEquals(svc.quote(ProgrammeKind.ON_THE_JOB, 90, f), s.programme.costPerHead)
        assertTrue(s.intents.isEmpty())
    }

    @Test fun startingChecksTheCohortTheSkillAndTheSize() {
        val f = staffed()
        assertEquals(Rules.NOT_OWNER, rule(svc.start(CompanyId(2), f, DefaultTaxonomy.MACHINING, ProgrammeKind.ON_THE_JOB, 5L, 30, null, cash, Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(start(f, skill = DefaultTaxonomy.ENGINEERING)))
        assertEquals(Rules.INVALID_QUANTITY, rule(start(f, seats = 0L)))
        assertEquals(Rules.INVALID_QUANTITY, rule(start(f, seats = 39L)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(start(f, days = 5)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(start(f, days = 5_000)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.start(company, fact(key(role = "astronaut"), 10L), DefaultTaxonomy.GENERAL, ProgrammeKind.ON_THE_JOB, 1L, 30, null, cash, Tick(0))))
    }

    @Test fun onlySomeKindsCarryAClause() {
        val f = staffed()
        val clause = RetentionClause(180, 500_000L)
        assertEquals(Rules.INVALID_ARGUMENT, rule(start(f, kind = ProgrammeKind.ON_THE_JOB, clause = clause)))
        assertTrue(start(f, kind = ProgrammeKind.CERTIFICATION, days = 60, clause = clause) is Outcome.Ok)
    }

    @Test fun programmeCountsAreLimited() {
        val f = staffed(100L)
        ok(start(f, seats = 5L)); ok(start(f, seats = 5L))
        assertEquals(Rules.LIMIT_EXCEEDED, rule(start(f, seats = 5L)))
        val other = key(role = "driver")
        val of = fact(other, 100L)
        for (i in 0 until 4) ok(svc.start(company, fact(key(role = "machinist", facility = (i + 1).toLong()), 100L), DefaultTaxonomy.MACHINING, ProgrammeKind.ON_THE_JOB, 5L, 30, null, cash, Tick(0)))
        assertEquals(Rules.LIMIT_EXCEEDED, rule(svc.start(company, of, DefaultTaxonomy.DRIVING, ProgrammeKind.ON_THE_JOB, 5L, 30, null, cash, Tick(0))))
    }

    @Test fun aCompanyMustAffordAMonthOfRunningCosts() {
        val f = staffed()
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(start(f, available = Money.units(100))))
        assertTrue(start(f, available = svc.quote(ProgrammeKind.ON_THE_JOB, 90, f) * 10L) is Outcome.Ok)
    }

    @Test fun inHouseCostIsPaidWeeklyAndAddsUpExactly() {
        val f = staffed()
        val p = ok(start(f, seats = 10L, days = 90)).programme
        val reports = run(1, 91, f)
        val total = Money.sum(reports.flatMap { it.intents }.map { it.total })
        assertEquals(p.totalCost, total)
        assertEquals(p.totalCost, p.paid)
        assertTrue(reports.count { it.intents.isNotEmpty() } >= 12)
    }

    @Test fun monthlyGainsRaiseTheCohortAndStopAtTheEnd() {
        val f = staffed()
        val set = rig.skills.get(k)!!
        set.dist(DefaultTaxonomy.MACHINING)!!.also { it.clear(); it.addAt(425_000L, 40L) }
        val before = rig.skills.meanLevel(k)!!
        val p = ok(start(f, seats = 40L - 8L, days = 90)).programme
        run(1, 91, f)
        val gained = rig.skills.meanLevel(k)!! - before
        assertTrue(gained > 20_000L)
        assertTrue(p.gainDelivered > 0L)
        val after = rig.skills.meanLevel(k)!!
        run(92, 200, f)
        assertEquals(after, rig.skills.meanLevel(k)!!)
    }

    @Test fun completionEmitsAnEventAndFreesTheTrainingShare() {
        val f = staffed()
        ok(start(f, seats = 10L, days = 30))
        val reports = run(1, 31, f)
        val done = reports.flatMap { it.events }.filterIsInstance<TalentEvent.ProgrammeCompleted>().single()
        assertEquals(10L, done.trainees)
        val shares = reports.flatMap { it.effects }.filterIsInstance<WorkforceEffect.TrainingShare>()
        // ten of forty people away, each for 15% of their time
        assertEquals(37_500L, shares.first().ppm)
        assertEquals(0L, shares.last().ppm)
        assertTrue(shares.first().ppm > 0L)
    }

    @Test fun trainingShareFollowsSeatsAndWorkLoss() {
        val f = staffed()
        ok(start(f, kind = ProgrammeKind.CERTIFICATION, seats = 10L, days = 60))
        assertEquals(125_000L, svc.trainingShare(k, 40L))
        assertEquals(0L, svc.trainingShare(k, 0L))
        assertEquals(0L, svc.trainingShare(key(role = "driver"), 40L))
    }

    @Test fun certificatesAreAwardedByCertificationCourses() {
        val f = staffed()
        ok(start(f, kind = ProgrammeKind.CERTIFICATION, seats = 10L, days = 60))
        run(1, 61, f)
        assertEquals(250_000L, rig.skills.get(k)!!.certifiedShare(DefaultTaxonomy.MACHINING))
        ok(start(f, kind = ProgrammeKind.ON_THE_JOB, seats = 10L, days = 30, tick = 62L))
        run(62, 100, f)
        assertEquals(250_000L, rig.skills.get(k)!!.certifiedShare(DefaultTaxonomy.MACHINING))
    }

    @Test fun reimbursementIsPaidInOneLumpWhenTheCourseEnds() {
        val f = staffed()
        val p = ok(start(f, kind = ProgrammeKind.TUITION_REIMBURSEMENT, seats = 8L, days = 540)).programme
        val reports = run(1, 541, f)
        val paying = reports.filter { it.intents.isNotEmpty() }
        assertEquals(1, paying.size)
        assertEquals(p.totalCost, paying[0].intents.single().total)
        assertTrue(paying[0].events.any { it is TalentEvent.ProgrammeCompleted })
    }

    @Test fun providerFeesAreSettledUpFrontToTheProvider() {
        val f = staffed()
        val s = ok(svc.start(company, f, DefaultTaxonomy.MACHINING, ProgrammeKind.CERTIFICATION, 6L, 60, null, cash, Tick(0),
            provider = ProviderId(3), course = CourseId(4), fee = Money.units(1_500), payee = CompanyId(9)))
        assertEquals(1, s.intents.size)
        assertEquals(Money.units(9_000), s.intents[0].total)
        assertEquals(Money.units(1_500), s.programme.costPerHead)
        val later = run(1, 61, f).flatMap { it.intents }
        assertTrue(later.isEmpty())
    }

    @Test fun cancellingStopsPaymentsAndGains() {
        val f = staffed()
        val p = ok(start(f)).programme
        assertEquals(Rules.NOT_OWNER, rule(svc.cancel(p.id, CompanyId(2), Tick(1))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.cancel(ProgrammeId(99), company, Tick(1))))
        assertTrue(svc.cancel(p.id, company, Tick(10)) is Outcome.Ok)
        assertEquals(Rules.WRONG_STATE, rule(svc.cancel(p.id, company, Tick(11))))
        val reports = run(11, 120, f)
        assertTrue(reports.all { it.intents.isEmpty() })
        assertFalse(p.isActive)
    }

    @Test fun aVanishedCohortCancelsItsProgrammes() {
        val f = staffed()
        val p = ok(start(f)).programme
        val report = svc.advance(Tick(5), inputs(5L, fact(k, 0L)))
        assertEquals(ProgrammeStatus.CANCELLED, p.status)
        assertTrue(report.events.any { it is TalentEvent.ProgrammeCancelled })
    }

    @Test fun leaversRepayUnderARetentionClause() {
        val f = staffed()
        val clause = RetentionClause(360, 800_000L)
        val p = ok(start(f, kind = ProgrammeKind.CERTIFICATION, seats = 20L, days = 60, clause = clause)).programme
        run(1, 61, f)
        assertEquals(20L, p.bondedHeads)
        assertTrue(svc.bondedShare(k, 40L, Tick(70)) == 500_000L)
        // ten of the forty leave on day 100; half of them were bonded trainees
        val r = svc.onDepartures(k, 10L, 40L, Tick(100))
        assertEquals(5L, 20L - p.bondedHeads)
        val owed = r.intents.single()
        val served = 100L - 60L
        val share = Ppm.ratio(360L - served, 360L)
        val expected = Money(Ppm.of(Ppm.of(p.costPerHead.raw, 800_000L), share)) * 5L
        assertEquals(expected, owed.total)
        assertEquals(expected, p.clawedBack)
        assertTrue(r.events.single() is TalentEvent.ClawbackCharged)
    }

    @Test fun noRepaymentAfterTheBondEnds() {
        val f = staffed()
        val p = ok(start(f, kind = ProgrammeKind.CERTIFICATION, seats = 20L, days = 60, clause = RetentionClause(100, 500_000L))).programme
        run(1, 61, f)
        val r = svc.onDepartures(k, 10L, 40L, Tick(200))
        assertTrue(r.intents.isEmpty())
        assertEquals(20L, p.bondedHeads)
        assertFalse(svc.bondedShare(k, 40L, Tick(200)) > 0L)
    }

    @Test fun departuresWithoutBondedTraineesCostNothing() {
        val f = staffed()
        ok(start(f))
        run(1, 100, f)
        assertTrue(svc.onDepartures(k, 10L, 40L, Tick(120)).intents.isEmpty())
        assertTrue(svc.onDepartures(k, 0L, 40L, Tick(120)).intents.isEmpty())
        assertTrue(svc.onDepartures(k, 5L, 0L, Tick(120)).intents.isEmpty())
    }

    @Test fun smallDeparturesAccumulateInTheCarryUntilWholePeopleLeave() {
        val f = staffed()
        val p = ok(start(f, kind = ProgrammeKind.CERTIFICATION, seats = 10L, days = 60, clause = RetentionClause(360, 800_000L))).programme
        run(1, 61, f)
        var charged = 0
        for (day in 70L..76L) if (svc.onDepartures(k, 1L, 40L, Tick(day)).intents.isNotEmpty()) charged++
        // each leaver is a quarter of a bonded head; seven leavers send about one to repay
        assertEquals(1, charged)
        assertEquals(9L, p.bondedHeads)
    }

    @Test fun oldEndedProgrammesArePruned() {
        val f = staffed()
        val p = ok(start(f, days = 30)).programme
        run(1, 31, f)
        svc.prune(Tick(200))
        assertTrue(svc.get(p.id) != null)
        svc.prune(Tick(2_000))
        assertNull(svc.get(p.id))
    }

    @Test fun statesWithTheSameHistoryHashEqual() {
        fun build(): TalentState {
            val r = ServiceRig(); val f = fact(k, 40L); r.skills.reconcile(f, r.world)
            r.training.start(company, f, DefaultTaxonomy.MACHINING, ProgrammeKind.APPRENTICESHIP, 10L, 200, RetentionClause(90, 500_000L), cash, Tick(0))
            for (t in 1L..250L) r.training.advance(Tick(t), inputs(t, f))
            return r.state
        }
        assertEquals(build().stateHash(), build().stateHash())
    }
}
