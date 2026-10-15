package app.many.economytycoon.unions

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnionServiceTest {
    private val s = UnionService(IdAllocators())
    private val company = CompanyId(1)
    private val region = RegionId(1)
    private fun union(members: Long = 40, tick: Long = 0): Union = (s.form(company, region, members, Tick(tick)) as Outcome.Ok).value

    @Test fun unhappyOrUnderpaidGroupsOfEnoughPeopleWantAUnion() {
        assertTrue(s.wantsUnion(400_000L, 1_000_000L, 40))
        assertTrue(s.wantsUnion(700_000L, 800_000L, 40))
        assertFalse(s.wantsUnion(700_000L, 1_000_000L, 40))
        assertFalse(s.wantsUnion(100_000L, 500_000L, s.params.minMembers - 1))
    }

    @Test fun aUnionStartsModestAndHasOneChapterPerGroup() {
        val u = union(30)
        assertEquals(s.params.startStrengthPpm, u.strengthPpm); assertEquals(30L, u.members); assertTrue(u.isActive)
        assertEquals(u, s.forGroup(company, region)); assertEquals(listOf(u), s.ofCompany(company))
        assertEquals(Rules.DUPLICATE, (s.form(company, region, 30, Tick(1)) as Outcome.Rejected).rejection.rule)
        assertTrue(s.form(company, RegionId(2), 30, Tick(1)) is Outcome.Ok)
        assertEquals(Rules.INVALID_QUANTITY, (s.form(CompanyId(2), region, 3, Tick(1)) as Outcome.Rejected).rejection.rule)
    }

    @Test fun targetStrengthRisesWithUnhappinessAndFallsWithContentment() {
        val unhappy = s.targetStrength(250_000L, 800_000L); val content = s.targetStrength(850_000L, 1_100_000L)
        assertTrue(unhappy > 700_000L && content < 300_000L)
        assertTrue(s.targetStrength(0L, 0L) <= 1_000_000L); assertTrue(s.targetStrength(1_000_000L, 2_000_000L) >= 0L)
    }

    @Test fun strengthMovesAtenthOfTheWayAndKeepsMemberCount() {
        val u = union()
        val before = u.strengthPpm
        s.updateStrength(u, 1_000_000L, 55)
        assertEquals(before + (1_000_000L - before) / 10, u.strengthPpm); assertEquals(55L, u.members)
        repeat(200) { s.updateStrength(u, 0L, 55) }
        assertTrue(u.strengthPpm < 10_000L)
        assertTrue(s.shouldDisband(u))
    }

    @Test fun disbandingKeepsTheRecordButFreesTheGroup() {
        val u = union(); s.disband(u, Tick(40))
        assertFalse(u.isActive); assertEquals(Tick(40), u.disbandedAt)
        assertNull(s.forGroup(company, region)); assertTrue(s.active().isEmpty()); assertEquals(1, s.all().size)
        assertTrue(s.form(company, region, 30, Tick(50)) is Outcome.Ok)
    }

    @Test fun stronglyOrganisedAndUnderpaidWorkersAskForMore() {
        val weak = s.demandedRaise(100_000L, 1_000_000L); val strong = s.demandedRaise(900_000L, 1_000_000L)
        val underpaid = s.demandedRaise(500_000L, 600_000L); val paid = s.demandedRaise(500_000L, 1_000_000L)
        assertTrue(strong > weak && underpaid > paid)
        assertTrue(weak >= s.params.baseRaisePpm && s.demandedRaise(1_000_000L, 0L) <= s.params.maxRaisePpm)
    }

    @Test fun roundsComeEveryQuarterOnlyWhenNothingIsPending() {
        val u = union(tick = 0)
        assertFalse(s.isRoundDue(u, Tick(s.params.roundEvery - 1L)))
        assertTrue(s.isRoundDue(u, Tick(s.params.roundEvery.toLong())))
        val d = s.makeDemand(u, 40_000L, Tick(90))
        assertFalse(s.isRoundDue(u, Tick(200)))
        assertEquals(Tick(90L + s.params.responseDays), d.deadline); assertEquals(Tick(90), u.lastRound)
        assertEquals(d, s.openDemand(u.id)); assertEquals(listOf(d), s.openDemandsFor(company))
    }

    @Test fun noRoundDuringAStrikeOrAfterDisbanding() {
        val u = union(); val d = s.makeDemand(u, 40_000L, Tick(0)); s.struck(d, u, Tick(85), 10, 0L)
        assertFalse(s.isRoundDue(u, Tick(92)))
        assertTrue(s.isRoundDue(u, Tick(95)))
        s.disband(u, Tick(100))
        assertFalse(s.isRoundDue(u, Tick(500)))
    }

    @Test fun strongerUnionsHoldOutForMoreAndStrikeMoreOften() {
        assertTrue(s.acceptableShare(900_000L) > s.acceptableShare(100_000L))
        assertEquals(850_000L, s.acceptableShare(900_000L)); assertEquals(450_000L, s.acceptableShare(100_000L))
        assertTrue(s.strikeChance(1_000_000L) > s.strikeChance(0L))
        assertEquals(100_000L, s.strikeChance(0L)); assertEquals(800_000L, s.strikeChance(1_000_000L))
    }

    @Test fun strikesLastWithinTheConfiguredSpan() {
        val r = RandomProvider(4).stream("t", 0)
        val days = List(200) { s.strikeLength(r) }
        assertTrue(days.all { it in s.params.strikeMinDays..s.params.strikeMinDays + s.params.strikeSpanDays })
        assertTrue(days.toSet().size > 5)
    }

    @Test fun answersAreChecked() {
        val u = union(); val d = s.makeDemand(u, 60_000L, Tick(0))
        assertNull(s.checkResponse(d, ResponseKind.ACCEPT, 0L)); assertNull(s.checkResponse(d, ResponseKind.REFUSE, 0L))
        assertNull(s.checkResponse(d, ResponseKind.COUNTER, 30_000L))
        assertEquals(Rules.INVALID_ARGUMENT, s.checkResponse(d, ResponseKind.COUNTER, 0L)!!.rule)
        assertEquals(Rules.INVALID_ARGUMENT, s.checkResponse(d, ResponseKind.COUNTER, 60_000L)!!.rule)
        s.accepted(d, u, Tick(1), 60_000L, DemandStatus.ACCEPTED)
        assertEquals(Rules.WRONG_STATE, s.checkResponse(d, ResponseKind.ACCEPT, 0L)!!.rule)
    }

    @Test fun acceptingRecordsTheRaiseAndCountsAWin() {
        val u = union(); val d = s.makeDemand(u, 50_000L, Tick(0))
        s.accepted(d, u, Tick(3), 40_000L, DemandStatus.SETTLED)
        assertEquals(DemandStatus.SETTLED, d.status); assertEquals(40_000L, d.agreedPpm); assertEquals(Tick(3), d.resolvedAt)
        assertEquals(1, u.raisesWon); assertNull(s.openDemand(u.id))
    }

    @Test fun aStrikeStrengthensTheUnionAndBackingDownWeakensIt() {
        val u = union(); val a = s.makeDemand(u, 50_000L, Tick(0)); val before = u.strengthPpm
        s.struck(a, u, Tick(2), 9, 20_000L)
        assertEquals(DemandStatus.STRUCK, a.status); assertEquals(9, a.strikeDays); assertEquals(Tick(11), u.strikeUntil); assertEquals(1, u.strikes)
        assertEquals(before + s.params.strikeStrengthGainPpm, u.strengthPpm)
        val b = s.makeDemand(u, 50_000L, Tick(100)); val mid = u.strengthPpm
        s.backedDown(b, u, Tick(101), 0L)
        assertEquals(DemandStatus.BACKED_DOWN, b.status); assertEquals(mid - s.params.backDownStrengthLossPpm, u.strengthPpm)
        s.clearStrike(u); assertFalse(u.onStrike(Tick(5)))
    }

    @Test fun strengthStaysWithinBounds() {
        val u = union(); u.strengthPpm = 990_000L
        s.struck(s.makeDemand(u, 50_000L, Tick(0)), u, Tick(1), 5, 0L)
        assertEquals(1_000_000L, u.strengthPpm)
        u.strengthPpm = 20_000L
        s.backedDown(s.makeDemand(u, 50_000L, Tick(100)), u, Tick(101), 0L)
        assertEquals(0L, u.strengthPpm)
    }

    @Test fun oldHistoryIsTrimmedButOpenDemandsStay() {
        val u = union()
        repeat(s.params.historyLength + 12) { i -> val d = s.makeDemand(u, 30_000L, Tick(i * 100L)); s.accepted(d, u, Tick(i * 100L + 1), 30_000L, DemandStatus.ACCEPTED) }
        val open = s.makeDemand(u, 30_000L, Tick(99_999))
        s.prune()
        assertTrue(s.demandsOf(u.id).size <= s.params.historyLength + 1)
        assertNotNull(s.demand(open.id)); assertTrue(open.isOpen)
    }

    @Test fun theHashCoversUnionsAndDemands() {
        fun h() = app.many.economytycoon.core.CanonicalHasher().also { s.hashInto(it) }.finish()
        val u = union(); val base = h()
        val d = s.makeDemand(u, 40_000L, Tick(90)); val withDemand = h()
        assertTrue(withDemand != base)
        s.accepted(d, u, Tick(92), 40_000L, DemandStatus.ACCEPTED)
        assertTrue(h() != withDemand)
    }
}
