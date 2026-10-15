package app.many.economytycoon.regulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegulationServiceTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val s = e.regulation
        val country = e.world.countries().first()
        init { e.regulationRunner.ensureGovernments(e.tick) }
        val gov get() = s.government(country.id)!!
        fun current(l: Lever) = l.valueIn(e.world.country(country.id))
        fun propose(l: Lever, raise: Boolean, tick: Long = 0): Outcome<Law> {
            val c = e.world.country(country.id)
            return s.propose(c, l, s.target(l, l.valueIn(c), raise, gov.baselineWage)!!, Tick(tick))
        }
        fun random(seed: Long = 1) = e.random.stream("regulation.test", seed)
    }

    @Test fun everyCountryGetsOneGovernmentAndKeepsIt() {
        val r = Rig()
        assertEquals(r.e.world.countries().size, r.s.governments().size)
        val before = r.s.governments().map { it.stance to it.nextElection }
        r.e.regulationRunner.ensureGovernments(Tick(500))
        assertEquals(before, r.s.governments().map { it.stance to it.nextElection })
        assertTrue(r.s.governments().all { it.nextElection.value > 0 && it.approvalPpm == 550_000L && it.terms == 1 })
    }

    @Test fun targetsStayInsideTheAllowedRange() {
        val r = Rig()
        for (l in Lever.values()) {
            val range = r.s.range(l, r.gov.baselineWage)
            for (raise in listOf(true, false)) {
                val to = r.s.target(l, r.current(l), raise, r.gov.baselineWage)
                if (to != null) { assertTrue("$l $raise", to in range); assertEquals(raise, to > r.current(l)) }
            }
        }
        assertNull(r.s.target(Lever.TARIFF, 200_000L, true, 40L))
        assertNull(r.s.target(Lever.SALES_TAX, 0L, false, 40L))
        assertEquals(30_000L, r.s.target(Lever.TARIFF, 20_000L, true, 40L))
    }

    @Test fun theMinimumWageMovesInTenthsAndNeverBelowOne() {
        val r = Rig()
        assertEquals(4L, r.s.stepFor(Lever.MINIMUM_WAGE, 40L))
        assertEquals(1L, r.s.stepFor(Lever.MINIMUM_WAGE, 3L))
    }

    @Test fun aProposalRecordsWhereTheRuleStandsAndWhenParliamentVotes() {
        val r = Rig()
        val law = (r.propose(Lever.CORPORATE_TAX, true, tick = 10) as Outcome.Ok).value
        assertEquals(r.current(Lever.CORPORATE_TAX), law.from)
        assertEquals(law.from + 20_000L, law.to)
        assertEquals(Tick(10L + r.s.params.debateDays), law.voteAt)
        assertEquals(LawStatus.PROPOSED, law.status)
        assertTrue(law.raises && law.isOpen)
        assertEquals(r.gov.stance, law.proposedBy)
    }

    @Test fun twoLawsOnTheSameRuleOrTooManyLawsAreRefused() {
        val r = Rig()
        r.propose(Lever.CORPORATE_TAX, true)
        val same = r.propose(Lever.CORPORATE_TAX, false) as Outcome.Rejected
        assertEquals(Rules.WRONG_STATE, same.rejection.rule)
        r.propose(Lever.SALES_TAX, true)
        val third = r.propose(Lever.PAYROLL_TAX, true) as Outcome.Rejected
        assertEquals(Rules.LIMIT_EXCEEDED, third.rejection.rule)
    }

    @Test fun aLawOutsideTheRangeOrUnchangedIsRefused() {
        val r = Rig(); val c = r.e.world.country(r.country.id)
        assertEquals(Rules.INVALID_ARGUMENT, (r.s.propose(c, Lever.SALES_TAX, 900_000L, Tick(0)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_ARGUMENT, (r.s.propose(c, Lever.SALES_TAX, c.salesTaxPpm, Tick(0)) as Outcome.Rejected).rejection.rule)
    }

    @Test fun lobbyingAddsToTheRightPoolAndNeedsAnOpenLaw() {
        val r = Rig(); val law = (r.propose(Lever.PAYROLL_TAX, true) as Outcome.Ok).value
        val a = CompanyId(1); val b = CompanyId(2)
        r.s.lobby(law.id, a, true, Money.units(5_000)); r.s.lobby(law.id, a, true, Money.units(2_000)); r.s.lobby(law.id, b, false, Money.units(1_000))
        assertEquals(Money.units(7_000), law.backedBy(a)); assertEquals(Money.units(1_000), law.opposedBy(b))
        assertEquals(Money.units(7_000), law.supportPool); assertEquals(Money.units(1_000), law.opposePool)
        assertEquals(Rules.INVALID_ARGUMENT, (r.s.lobby(law.id, b, true, Money.units(1)) as Outcome.Rejected).rejection.rule)
        r.s.vote(Tick(100)) { r.random() }
        assertEquals(Rules.WRONG_STATE, (r.s.lobby(law.id, b, true, Money.units(5_000)) as Outcome.Rejected).rejection.rule)
    }

    @Test fun lobbySwingIsBoundedAndTipsTowardsTheBiggerSide() {
        val r = Rig(); val law = (r.propose(Lever.PAYROLL_TAX, true) as Outcome.Ok).value
        assertEquals(0L, r.s.lobbySwing(law))
        r.s.lobby(law.id, CompanyId(1), true, Money.units(60_000))
        assertEquals(r.s.params.maxLobbySwingPpm / 2, r.s.lobbySwing(law))
        r.s.lobby(law.id, CompanyId(2), false, Money.units(60_000))
        assertEquals(0L, r.s.lobbySwing(law))
        r.s.lobby(law.id, CompanyId(2), false, Money.units(10_000_000))
        assertTrue(r.s.lobbySwing(law) < 0 && r.s.lobbySwing(law) >= -r.s.params.maxLobbySwingPpm)
    }

    @Test fun moneyOnOneSideMakesTheVoteGoThatWay() {
        val r = Rig()
        val up = (r.propose(Lever.SALES_TAX, true) as Outcome.Ok).value
        val down = (r.propose(Lever.PAYROLL_TAX, true) as Outcome.Ok).value
        r.s.lobby(up.id, CompanyId(1), true, Money.units(500_000))
        r.s.lobby(down.id, CompanyId(1), false, Money.units(500_000))
        val events = r.s.vote(Tick(up.voteAt.value)) { r.random(3) }
        assertEquals(2, events.size)
        assertEquals(LawStatus.PASSED, up.status); assertEquals(LawStatus.REJECTED, down.status)
        assertTrue(events[0] is RegulationEvent.Passed || events[1] is RegulationEvent.Passed)
        assertEquals(Tick(up.voteAt.value + r.s.params.noticeDays), up.effectiveAt)
        assertTrue(up.supportScorePpm >= 500_000L && down.supportScorePpm < 500_000L)
    }

    @Test fun noVoteHappensBeforeTheDebateEnds() {
        val r = Rig(); val law = (r.propose(Lever.TARIFF, true, tick = 0) as Outcome.Ok).value
        assertTrue(r.s.vote(Tick(law.voteAt.value - 1)) { r.random() }.isEmpty())
        assertTrue(law.isOpen)
    }

    @Test fun passedLawsBecomeDueAfterTheNoticePeriodAndAreMarkedOnce() {
        val r = Rig(); val law = (r.propose(Lever.SALES_TAX, true) as Outcome.Ok).value
        r.s.lobby(law.id, CompanyId(1), true, Money.units(500_000))
        r.s.vote(law.voteAt) { r.random() }
        assertTrue(r.s.due(law.voteAt).isEmpty())
        assertEquals(listOf(law), r.s.due(law.effectiveAt!!))
        assertTrue(r.s.markEnacted(law, law.effectiveAt!!) is RegulationEvent.Enacted)
        assertTrue(r.s.due(law.effectiveAt!!).isEmpty())
        assertEquals(listOf(law), r.s.enacted(r.country.id))
    }

    @Test fun everyStanceHasSomethingItWantsAndBusinessNeverRaisesCorporateTax() {
        val r = Rig(); val calm = Mood(50_000L, 600_000L, 800_000L)
        for (st in Stance.values()) assertTrue(st.name, r.s.candidates(st, calm).isNotEmpty())
        assertFalse(r.s.candidates(Stance.BUSINESS, calm).any { it.lever == Lever.CORPORATE_TAX && it.raise })
        assertTrue(r.s.candidates(Stance.LABOUR, calm).any { it.lever == Lever.MINIMUM_WAGE && it.raise })
        assertTrue(r.s.candidates(Stance.GREEN, calm).all { it.raise })
    }

    @Test fun aCentristReactsToTheMood() {
        val r = Rig()
        val jobless = r.s.candidates(Stance.CENTRIST, Mood(200_000L, 600_000L, 800_000L))
        assertTrue(jobless.any { it.lever == Lever.PAYROLL_TAX && !it.raise })
        val sad = r.s.candidates(Stance.CENTRIST, Mood(30_000L, 300_000L, 800_000L))
        assertTrue(sad.any { it.lever == Lever.MINIMUM_WAGE && it.raise })
        val dirty = r.s.candidates(Stance.CENTRIST, Mood(30_000L, 700_000L, 800_000L, pollutionPpm = 800_000L))
        assertTrue(dirty.any { it.lever == Lever.ENVIRONMENT && it.raise })
    }

    @Test fun chooseSkipsRulesAlreadyBeingDebatedAndStopsAtTheLimit() {
        val r = Rig(); val calm = Mood(50_000L, 600_000L, 800_000L)
        val c = r.e.world.country(r.country.id)
        r.gov.stance = Stance.GREEN
        val first = r.s.choose(c, calm, r.random())!!
        assertEquals(Lever.ENVIRONMENT, first.first)
        r.s.propose(c, first.first, first.second, Tick(0))
        val second = r.s.choose(c, calm, r.random())
        assertNotNull(second); assertTrue(second!!.first != Lever.ENVIRONMENT)
        r.s.propose(c, second.first, second.second, Tick(0))
        assertNull(r.s.choose(c, calm, r.random()))
    }

    @Test fun choiceDependsOnlyOnTheRandomStream() {
        val a = Rig(); val b = Rig(); val calm = Mood(50_000L, 600_000L, 800_000L)
        repeat(8) {
            assertEquals(a.s.choose(a.e.world.country(a.country.id), calm, a.random(it.toLong())), b.s.choose(b.e.world.country(b.country.id), calm, b.random(it.toLong())))
        }
    }

    @Test fun approvalFollowsJobsHappinessAndStability() {
        val r = Rig()
        val good = r.s.approvalFor(Mood(30_000L, 800_000L, 900_000L)); val bad = r.s.approvalFor(Mood(250_000L, 300_000L, 300_000L))
        assertTrue(good > 600_000L && bad < 350_000L)
        assertEquals(0L, r.s.approvalFor(Mood(1_000_000L, 0L, 0L)))
        assertTrue(r.s.approvalFor(Mood(0L, 1_000_000L, 1_000_000L)) <= 1_000_000L)
    }

    @Test fun approvalDriftsGraduallyTowardsTheMood() {
        val r = Rig(); val m = Mood(250_000L, 200_000L, 300_000L)
        val before = r.gov.approvalPpm
        r.s.updateApproval(r.country.id, m)
        val after = r.gov.approvalPpm
        assertTrue(after < before && after > r.s.approvalFor(m))
        repeat(60) { r.s.updateApproval(r.country.id, m) }
        assertTrue(r.gov.approvalPpm - r.s.approvalFor(m) < 20_000L)
    }

    @Test fun aPopularGovernmentIsReturnedAndAnUnpopularOneReplaced() {
        val r = Rig(); val calm = Mood(50_000L, 600_000L, 800_000L)
        val g = r.gov
        g.approvalPpm = 900_000L
        val t = g.nextElection
        val kept = r.s.elections(t, { calm }) { r.random(5) }.filterIsInstance<RegulationEvent.Election>().first { it.country == r.country.id }
        assertEquals(kept.from, kept.to); assertEquals(2, g.terms)
        assertEquals(Tick(t.value + r.s.params.electionEvery), g.nextElection)
        g.approvalPpm = 100_000L
        val old = g.stance
        val lost = r.s.elections(g.nextElection, { calm }) { r.random(6) }.filterIsInstance<RegulationEvent.Election>().first { it.country == r.country.id }
        assertTrue(lost.from == old && lost.to != old); assertEquals(1, g.terms); assertEquals(550_000L, g.approvalPpm)
    }

    @Test fun noElectionBeforeItsDay() {
        val r = Rig()
        assertTrue(r.s.elections(Tick(r.gov.nextElection.value - 1), { Mood(0, 0, 0) }) { r.random() }.none { it.country == r.country.id })
    }

    @Test fun hardTimesPushVotersTowardsTheMatchingParty() {
        val r = Rig(); val tally = HashMap<Stance, Int>()
        repeat(300) { tally.merge(r.s.nextStance(Stance.CENTRIST, Mood(300_000L, 600_000L, 800_000L), r.random(it.toLong())), 1, Int::plus) }
        assertEquals(0, tally[Stance.CENTRIST] ?: 0)
        assertTrue(tally.getValue(Stance.BUSINESS) > tally.getValue(Stance.GREEN))
    }

    @Test fun finesAreCountedPerFacilityInsideTheWindow() {
        val r = Rig(); val c = CompanyId(4); val f = FacilityId(8)
        fun fine(day: Long) = Fine(c, f, r.country.id, Tick(day), 900_000L, 600_000L, Money.units(100), false)
        r.s.recordFine(fine(10)); r.s.recordFine(fine(100))
        r.s.recordFine(Fine(c, FacilityId(9), r.country.id, Tick(100), 900_000L, 600_000L, Money.units(100), false))
        assertEquals(2, r.s.recentFines(c, f, Tick(150)))
        assertEquals(1, r.s.recentFines(c, f, Tick(10L + r.s.params.fineWindowDays)))
        assertEquals(3, r.s.finesOf(c).size)
    }

    @Test fun fineHistoryIsBounded() {
        val r = Rig()
        repeat(r.s.params.maxFineHistory + 25) { r.s.recordFine(Fine(CompanyId(1), FacilityId(1), r.country.id, Tick(it.toLong()), 1, 0, Money.ZERO, false)) }
        assertEquals(r.s.params.maxFineHistory, r.s.fines().size)
        assertEquals(25L, r.s.fines().first().tick.value)
    }

    @Test fun finishedLawsAreTrimmedButOpenAndPassedOnesStay() {
        val r = Rig(); val c = r.e.world.country(r.country.id)
        r.gov.stance = Stance.CENTRIST
        var tick = 0L
        repeat(r.s.params.historyLength + 10) {
            val l = (r.s.propose(c, Lever.TARIFF, Lever.TARIFF.valueIn(c) + 10_000L, Tick(tick)) as Outcome.Ok).value
            r.s.vote(l.voteAt) { r.random(it.value) }
            if (l.status == LawStatus.PASSED) { l.status = LawStatus.REJECTED }
            tick += 30
        }
        val open = (r.s.propose(c, Lever.SALES_TAX, c.salesTaxPpm + 20_000L, Tick(tick)) as Outcome.Ok).value
        r.s.prune(Tick(tick))
        assertEquals(r.s.params.historyLength + 1, r.s.laws().size)
        assertTrue(r.s.laws().contains(open))
    }

    @Test fun theHashChangesWithEveryKindOfState() {
        val r = Rig()
        fun h() = app.many.economytycoon.core.CanonicalHasher().also { r.s.hashInto(it) }.finish()
        val base = h()
        val law = (r.propose(Lever.TARIFF, true) as Outcome.Ok).value
        val withLaw = h(); assertTrue(withLaw != base)
        r.s.lobby(law.id, CompanyId(1), true, Money.units(2_000))
        val withLobby = h(); assertTrue(withLobby != withLaw)
        r.s.recordFine(Fine(CompanyId(1), FacilityId(1), r.country.id, Tick(1), 1, 0, Money.ZERO, false))
        assertTrue(h() != withLobby)
    }
}
