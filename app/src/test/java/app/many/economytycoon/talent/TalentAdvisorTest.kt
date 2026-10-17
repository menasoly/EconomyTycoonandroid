package app.many.economytycoon.talent

import app.many.economytycoon.advisor.Topic
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TalentAdvisorTest {
    private val co = CompanyId(1)
    private val key = TalentTestSupport.key()
    private fun codes(f: TalentAdvisorFacts) = TalentAdvisor(Scenario().system).advise(f).map { it.code }

    @Test fun aQuietCompanyHearsNothing() {
        assertEquals(emptyList<String>(), codes(TalentAdvisorFacts(co, 100L)))
    }

    @Test fun everyInsightIsAboutPeople() {
        val f = TalentAdvisorFacts(
            co, 100L, shortages = listOf(ShortageRow(RegionId(1), DefaultTaxonomy.MACHINING, ShortageKind.QUANTITY, 0, 10, 3_000_000L, 1_500_000L, 0L, 5L)),
            weakCohorts = listOf(WeakCohort(key, 700_000L, DefaultTaxonomy.MACHINING, false)),
            cheapCohorts = listOf(CheapCohort(key, 800_000L, 300_000L, false)),
            vacantPosts = listOf(OpenPost("CEO", 40L, true)), unreadyPosts = listOf(UnreadyPost("CFO", 0, 0L)),
            offersAgainstUs = 2, openings = 30L, headcount = 100L, brandAppealPpm = 300_000L, brainDrainPpm = 9_000L,
            providerLapsingDays = 30L, bondedHeadsExpiringSoon = 6L,
        )
        val all = TalentAdvisor(Scenario().system).advise(f)
        assertEquals(TalentRules.all.size, all.size)
        assertTrue(all.all { it.topic == Topic.PEOPLE && it.code.startsWith("talent-") })
        assertEquals(all.map { it.code }.toSet().size, all.size)
    }

    @Test fun suggestionsComeMostPressingFirst() {
        val f = TalentAdvisorFacts(co, 100L, vacantPosts = listOf(OpenPost("CEO", 10L, false)), offersAgainstUs = 1, bondedHeadsExpiringSoon = 3L)
        val list = TalentAdvisor(Scenario().system).advise(f)
        assertEquals(list.sortedWith(compareByDescending<app.many.economytycoon.advisor.Insight> { it.priority }.thenBy { it.code }), list)
        assertEquals("talent-vacant-post", list.first().code)
    }

    @Test fun limitsApply() {
        val f = TalentAdvisorFacts(co, 100L, vacantPosts = listOf(OpenPost("CEO", 10L, false)), offersAgainstUs = 1, bondedHeadsExpiringSoon = 3L)
        assertEquals(2, TalentAdvisor(Scenario().system).advise(f, 2).size)
    }

    @Test fun counterPolicyAdviceNeedsAnApproach() {
        assertFalse("talent-no-counter" in codes(TalentAdvisorFacts(co, 1L)))
        assertTrue("talent-no-counter" in codes(TalentAdvisorFacts(co, 1L, offersAgainstUs = 1)))
        assertFalse("talent-no-counter" in codes(TalentAdvisorFacts(co, 1L, offersAgainstUs = 1, hasCounterPolicy = true)))
    }

    @Test fun retainedTeamsAreNotFlightRisks() {
        val kept = TalentAdvisorFacts(co, 1L, cheapCohorts = listOf(CheapCohort(key, 800_000L, 300_000L, true)))
        assertFalse("talent-flight-risk" in codes(kept))
    }

    @Test fun trainedTeamsAreNotSkillGaps() {
        val f = TalentAdvisorFacts(co, 1L, weakCohorts = listOf(WeakCohort(key, 700_000L, DefaultTaxonomy.MACHINING, true)))
        assertFalse("talent-skill-gap" in codes(f))
    }

    @Test fun brandAdviceStopsOnceACampaignRuns() {
        val base = TalentAdvisorFacts(co, 1L, openings = 20L, headcount = 100L, brandAppealPpm = 300_000L)
        assertTrue("talent-weak-brand" in codes(base))
        assertFalse("talent-weak-brand" in codes(base.copy(recruitingCampaigns = 1)))
        assertFalse("talent-unfilled" in codes(base.copy(recruitingCampaigns = 1)))
    }

    @Test fun readyCompaniesAreNotWarnedAboutSuccession() {
        val sc = Scenario(); sc.run(5)
        sc.system.succession.openPosition(co, RoleId("executive"), RegionId(1), "CEO")
        val advisor = TalentAdvisor(sc.system)
        val facts = advisor.facts(co, Tick(sc.day), sc.inputs(), sc.world)
        assertEquals(listOf("CEO"), facts.vacantPosts.map { it.title })
    }

    @Test fun factsAreGatheredFromTheSystem() {
        val sc = Scenario(); sc.run(90)
        sc.workforce.rows.getValue(sc.machinists1).let { it.wage = Money.units(60); it.morale = 200_000L }
        sc.system.poaching.setCounterPolicy(CompanyId(2), false, 100_000L)
        sc.system.execute(TalentCommand.SubmitOffer(co, sc.machinists1, PoachTarget.Group(sc.machinists2, 10L), Money.units(120), Money.ZERO, CovenantStance.RESPECT), Tick(sc.day), sc.inputs(), sc.world)
        val victim = TalentAdvisor(sc.system).facts(CompanyId(2), Tick(sc.day), sc.inputs(), sc.world)
        assertEquals(1, victim.offersAgainstUs)
        val own = TalentAdvisor(sc.system).facts(co, Tick(sc.day), sc.inputs(), sc.world)
        assertTrue(own.cheapCohorts.any { it.key == sc.machinists1 })
        assertTrue(own.shortages.isNotEmpty())
        assertEquals(own.headcount, sc.workforce.rows.filterKeys { it.company == co }.values.sumOf { it.heads })
    }

    @Test fun adviceFromTheLiveSystemIsOrderedAndRepeatable() {
        val sc = Scenario(); sc.run(120)
        val a = TalentAdvisor(sc.system).advise(co, Tick(sc.day), sc.inputs(), sc.world)
        val b = TalentAdvisor(sc.system).advise(co, Tick(sc.day), sc.inputs(), sc.world)
        assertEquals(a, b)
        assertTrue(a.zipWithNext().all { (x, y) -> x.priority >= y.priority })
    }
}
