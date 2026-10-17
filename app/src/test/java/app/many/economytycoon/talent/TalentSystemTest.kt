package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TalentSystemTest {
    private val sc = Scenario()
    private val sys = sc.system
    private val co = CompanyId(1)

    private fun exec(cmd: TalentCommand, tick: Long = 0L): Outcome<TalentResult> = sys.execute(cmd, Tick(tick), sc.workforce.inputs(tick), sc.world)
    private fun ok(o: Outcome<TalentResult>): TalentResult = (o as Outcome.Ok).value
    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule

    @Test fun anIdleDayWithNoPeopleIsQuiet() {
        val bare = TalentSystem(TalentState(), sc.taxonomy, 1L)
        assertTrue(bare.advance(Tick(1), sc.world, TalentInputs(Tick(1), emptyList())).isQuiet)
    }

    @Test fun institutionsAreOpenedAndChanged() {
        val r = ok(exec(TalentCommand.OpenInstitution(co, CountryId(1), RegionId(1), InstitutionKind.VOCATIONAL, "Tech College", 300L, Money.units(900), Money.units(1_500), listOf(DefaultTaxonomy.MACHINING))))
        assertEquals("Tech College", r.note)
        assertEquals(1, r.intents.size)
        val id = sys.education.ownedBy(co).single().id
        assertTrue(exec(TalentCommand.SetInstitutionTuition(co, id, Money.units(500))) is Outcome.Ok)
        assertTrue(exec(TalentCommand.SetInstitutionSpend(co, id, Money.units(2_500))) is Outcome.Ok)
        assertTrue(exec(TalentCommand.SetInstitutionFields(co, id, listOf(DefaultTaxonomy.MACHINING, DefaultTaxonomy.ELECTRICAL))) is Outcome.Ok)
        assertEquals(1, ok(exec(TalentCommand.ExpandInstitution(co, id, 100L))).intents.size)
        assertTrue(exec(TalentCommand.CloseInstitution(co, id)) is Outcome.Ok)
        assertEquals(Rules.UNKNOWN_ENTITY, rule(exec(TalentCommand.ExpandInstitution(co, InstitutionId(999), 10L))))
    }

    @Test fun trainingIsStartedAndCancelled() {
        val start = exec(TalentCommand.StartTraining(co, sc.machinists1, DefaultTaxonomy.MACHINING, ProgrammeKind.ON_THE_JOB, 10L, 90))
        assertTrue(start is Outcome.Ok)
        val id = sys.training.activeOf(co).single().id
        assertEquals(Rules.UNKNOWN_ENTITY, rule(exec(TalentCommand.StartTraining(co, TalentTestSupport.key(company = 1L, role = "cook"), DefaultTaxonomy.COOKING, ProgrammeKind.ON_THE_JOB, 1L, 30))))
        assertTrue(exec(TalentCommand.CancelTraining(co, id)) is Outcome.Ok)
        assertEquals(Rules.WRONG_STATE, rule(exec(TalentCommand.CancelTraining(co, id))))
    }

    @Test fun providerCommandsFlowThrough() {
        ok(exec(TalentCommand.OpenProvider(co, RegionId(1), "Night School")))
        val p = sys.providers.providersOf(co).single()
        val c = exec(TalentCommand.AddCourse(co, p.id, "Lathe basics", DefaultTaxonomy.MACHINING, CourseKind.EVENING, 40L, Money.units(300)))
        assertEquals("Lathe basics", ok(c).note)
        val course = p.courseList().single()
        assertTrue(exec(TalentCommand.SetCourseTuition(co, course.id, Money.units(350))) is Outcome.Ok)
        assertTrue(exec(TalentCommand.SetCourseSeats(co, course.id, 60L)) is Outcome.Ok)
        assertTrue(exec(TalentCommand.SetTeaching(co, p.id, 1_200_000L)) is Outcome.Ok)
        assertEquals(Rules.PREREQUISITE_MISSING, rule(exec(TalentCommand.AccreditProvider(co, p.id, Accreditation.PREMIER))))
        val buy = exec(TalentCommand.BuyCourseSeats(CompanyId(2), course.id, sc.machinists2.copy(region = RegionId(1)), 5L))
        assertTrue(buy is Outcome.Ok)
        assertTrue(exec(TalentCommand.CloseCourse(co, course.id)) is Outcome.Ok)
    }

    @Test fun poachingCommandsFlowThrough() {
        assertTrue(exec(TalentCommand.SetCounterPolicy(CompanyId(2), true, 200_000L)) is Outcome.Ok)
        val sign = exec(TalentCommand.SignNonCompete(CompanyId(2), sc.machinists2, null, 12, Money.units(3_000)))
        assertTrue(sign.also { assertTrue(ok(it).events.single() is TalentEvent.CovenantSigned) } is Outcome.Ok)
        val covenant = sys.poaching.covenants(CompanyId(2)).single()
        val blocked = exec(TalentCommand.SubmitOffer(co, sc.machinists1, PoachTarget.Group(sc.machinists2, 10L), Money.units(130), Money.ZERO, CovenantStance.RESPECT))
        assertEquals(Rules.REGULATION, rule(blocked))
        assertTrue(exec(TalentCommand.ReleaseNonCompete(CompanyId(2), covenant.id)) is Outcome.Ok)
        val offer = exec(TalentCommand.SubmitOffer(co, sc.machinists1, PoachTarget.Group(sc.machinists2, 10L), Money.units(130), Money.ZERO, CovenantStance.RESPECT))
        assertTrue(ok(offer).events.single() is TalentEvent.PoachOffered)
        assertTrue(exec(TalentCommand.GrantRetention(CompanyId(2), sc.machinists2, 500_000L, 90)) is Outcome.Ok)
    }

    @Test fun campaignAndLadderCommandsFlowThrough() {
        val launch = exec(TalentCommand.LaunchCampaign(co, RegionId(1), RoleId("machinist"), Money.units(20_000), 90))
        assertTrue(ok(launch).events.single() is TalentEvent.CampaignStarted)
        val id = sys.brand.activeCampaigns(co).single().id
        assertTrue(exec(TalentCommand.CancelCampaign(co, id)) is Outcome.Ok)
        val rungs = listOf(Rung(sc.machinists1, 0L, 0L, 0L), Rung(sc.engineers1, 360L, 450_000L, 100_000L))
        assertEquals("Shop to design", ok(exec(TalentCommand.CreateLadder(co, "Shop to design", rungs))).note)
        val ladder = sys.careers.laddersOf(co).single()
        assertTrue(exec(TalentCommand.SetLadderEnabled(co, ladder.id, false)) is Outcome.Ok)
        assertTrue(exec(TalentCommand.RemoveLadder(co, ladder.id)) is Outcome.Ok)
    }

    @Test fun successionCommandsFlowThrough() {
        val exec = RoleId("executive")
        val pos = ok(exec(TalentCommand.OpenPosition(co, exec, RegionId(1), "CEO")))
        assertEquals("CEO", pos.note)
        val positionId = sys.succession.positionsOf(co).single().id
        ok(exec(TalentCommand.HirePerson(co, "Ada", exec, RegionId(1), Money.units(300), 50)))
        ok(exec(TalentCommand.HirePerson(co, "Bo", RoleId("manager"), RegionId(1), Money.units(200), 40)))
        val ada = sys.succession.peopleOf(co).first { it.name == "Ada" }
        val bo = sys.succession.peopleOf(co).first { it.name == "Bo" }
        assertTrue(exec(TalentCommand.AppointPerson(co, positionId, ada.id)) is Outcome.Ok)
        assertTrue(exec(TalentCommand.NameSuccessor(co, positionId, bo.id, DevelopmentKind.MENTORING)) is Outcome.Ok)
        assertTrue(exec(TalentCommand.SetDevelopment(co, positionId, bo.id, DevelopmentKind.STRETCH)) is Outcome.Ok)
        assertTrue(exec(TalentCommand.RemoveSuccessor(co, positionId, bo.id)) is Outcome.Ok)
    }

    @Test fun immigrationPolicyIsValidated() {
        assertEquals(Rules.UNKNOWN_ENTITY, rule(exec(TalentCommand.SetImmigrationPolicy(CountryId(99), 500_000L, 5_000L, false))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(exec(TalentCommand.SetImmigrationPolicy(CountryId(1), 2_000_000L, 5_000L, false))))
        assertTrue(exec(TalentCommand.SetImmigrationPolicy(CountryId(1), 300_000L, 4_000L, true)) is Outcome.Ok)
        assertEquals(300_000L, sys.migration.policy(CountryId(1)).opennessPpm)
        assertTrue(TalentCommand.SetImmigrationPolicy(CountryId(1), 0L, 0L, false).actor == null)
    }

    @Test fun commandsLeaveTheStateSound() {
        exec(TalentCommand.OpenProvider(co, RegionId(1), "Night School"))
        exec(TalentCommand.OpenPosition(co, RoleId("executive"), RegionId(1), "CEO"))
        assertEquals(emptyList<String>(), TalentInvariants.violations(sys.state, sc.taxonomy))
    }

    @Test fun cohortsAreFollowedAndTheirPicturesMatchHeadcounts() {
        val facts = sc.workforce.inputs(0).cohorts
        sc.step()
        assertEquals(emptyList<String>(), TalentInvariants.violations(sys.state, sc.taxonomy, facts))
    }

    @Test fun cohortsThatDisappearReturnTheirPeopleToThePool() {
        sc.step()
        val primary = sc.taxonomy.profile(sc.machinists2.role)!!.primary
        val before = sys.markets.poolSize(sc.machinists2.region, primary)
        sc.workforce.rows.remove(sc.machinists2)
        sc.step()
        assertEquals(null, sys.skills.get(sc.machinists2))
        assertTrue(sys.markets.poolSize(sc.machinists2.region, primary) >= before + 100L - 10L)
    }

    @Test fun marketsAreUpdatedMonthly() {
        repeat(31) { sc.step() }
        val primary = sc.taxonomy.profile(sc.machinists1.role)!!.primary
        assertTrue(sys.markets.market(sc.machinists1.region, primary) != null)
    }

    @Test fun reportsStayQuietBetweenEvents() {
        val r = sc.step()
        assertEquals(Tick(0), r.tick)
        assertFalse(r.effects.any { it is WorkforceEffect.Transfer })
    }

    @Test fun differentSeedsCanDiverge() {
        val a = Scenario(1L); val b = Scenario(2L)
        for (s in listOf(a, b)) {
            s.run(20)
            s.system.execute(TalentCommand.HirePerson(CompanyId(1), "Ada", RoleId("manager"), RegionId(1), Money.units(200), 40), Tick(s.day), s.inputs(), s.world)
            s.system.execute(TalentCommand.SubmitOffer(CompanyId(1), s.machinists1, PoachTarget.Group(s.machinists2, 20L), Money.units(140), Money.ZERO, CovenantStance.RESPECT), Tick(s.day), s.inputs(), s.world)
            s.run(400)
        }
        assertNotEquals(a.system.stateHash(), b.system.stateHash())
    }
}
