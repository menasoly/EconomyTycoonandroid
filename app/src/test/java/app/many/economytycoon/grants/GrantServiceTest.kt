package app.many.economytycoon.grants

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantServiceTest {
    private val params = GrantParams(applyDays = 5, holdDays = 20, maxOpenPerCountry = 2, maxApplicationsPerCompany = 2, keepClosedDays = 30)
    private val s = GrantService(IdAllocators(), params)
    private val country = CountryId(1)
    private val a = CompanyId(11)
    private val b = CompanyId(12)

    private fun jobs(budget: Long = 100_000, rate: Long = 100, cap: Long = 50_000, tick: Long = 0) =
        (s.announce(country, GrantKind.JOBS, null, Money.units(rate), Money.units(cap), Money.units(budget), Tick(tick)) as Outcome.Ok).value

    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule

    @Test fun announcingSetsTheWindows() {
        val g = jobs(tick = 10)
        assertEquals(GrantStatus.OPEN, g.status)
        assertEquals(Tick(15), g.applyUntil)
        assertEquals(Tick(35), g.endsAt)
        assertEquals(listOf(g), s.open())
    }

    @Test fun programmesNeedSensibleTerms() {
        assertEquals(Rules.INVALID_ARGUMENT, rule(s.announce(country, GrantKind.JOBS, null, Money.ZERO, Money.units(1), Money.units(1), Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(s.announce(country, GrantKind.FACILITIES, null, Money.units(1), Money.units(1), Money.units(1), Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(s.announce(country, GrantKind.JOBS, IndustryFamily.MINING, Money.units(1), Money.units(1), Money.units(1), Tick(0))))
    }

    @Test fun aCountryCapsItsOpenProgrammes() {
        jobs(); jobs()
        assertEquals(Rules.LIMIT_EXCEEDED, rule(s.announce(country, GrantKind.JOBS, null, Money.units(1), Money.units(1), Money.units(1), Tick(0))))
        assertTrue(s.announce(CountryId(2), GrantKind.JOBS, null, Money.units(1), Money.units(1), Money.units(1), Tick(0)) is Outcome.Ok)
    }

    @Test fun applyingRecordsTheBaseline() {
        val g = jobs()
        val app = (s.apply(g.id, a, 40, Tick(1)) as Outcome.Ok).value
        assertEquals(40L, app.baseline)
        assertEquals(app, g.applicationOf(a))
    }

    @Test fun aCompanyAppliesOnlyOnce() {
        val g = jobs()
        s.apply(g.id, a, 1, Tick(1))
        assertEquals(Rules.DUPLICATE, rule(s.apply(g.id, a, 1, Tick(2))))
    }

    @Test fun applicationsStopWhenTheWindowCloses() {
        val g = jobs()
        assertEquals(Rules.WRONG_STATE, rule(s.apply(g.id, a, 1, Tick(5))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(s.apply(app.many.economytycoon.core.GrantId(99), a, 1, Tick(1))))
    }

    @Test fun aCompanyCannotJuggleTooManyApplications() {
        val g1 = jobs(); val g2 = jobs()
        s.announce(CountryId(2), GrantKind.JOBS, null, Money.units(1), Money.units(1), Money.units(1), Tick(0))
        val g3 = s.open().last()
        s.apply(g1.id, a, 1, Tick(1)); s.apply(g2.id, a, 1, Tick(1))
        assertEquals(Rules.LIMIT_EXCEEDED, rule(s.apply(g3.id, a, 1, Tick(1))))
    }

    @Test fun withdrawingFreesTheSlot() {
        val g = jobs()
        s.apply(g.id, a, 1, Tick(1))
        assertTrue(s.withdraw(g.id, a) is Outcome.Ok)
        assertNull(g.applicationOf(a))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(s.withdraw(g.id, a)))
    }

    @Test fun aProgrammeNobodyJoinedEndsWhenTheWindowCloses() {
        val g = jobs()
        assertEquals(emptyList<GrantEvent>(), s.closeApplications(Tick(4)))
        val ev = s.closeApplications(Tick(5))
        assertEquals(listOf<GrantEvent>(GrantEvent.NoApplicants(g.id, Tick(5))), ev)
        assertEquals(GrantStatus.NO_APPLICANTS, g.status)
        assertEquals(listOf(g), s.history())
    }

    @Test fun aProgrammeWithApplicantsMovesOnToMeasuring() {
        val g = jobs()
        s.apply(g.id, a, 1, Tick(1))
        s.closeApplications(Tick(5))
        assertEquals(GrantStatus.ACTIVE, g.status)
        assertEquals(listOf(g), s.active())
    }

    private fun settleWith(gains: Map<CompanyId, Long>, tick: Long = 25, treasury: Money = Money.units(10_000_000)): List<GrantEvent> =
        s.settle(Tick(tick), { _, app -> app.baseline + (gains[app.company] ?: 0L) }, { _, _, owed -> Money.min(owed, treasury) })

    @Test fun nothingIsPaidBeforeTheMeasuringPeriodEnds() {
        val g = jobs()
        s.apply(g.id, a, 0, Tick(1)); s.closeApplications(Tick(5))
        assertEquals(emptyList<GrantEvent>(), settleWith(mapOf(a to 10), tick = 24))
        assertEquals(GrantStatus.ACTIVE, g.status)
    }

    @Test fun growthIsPaidAtTheRate() {
        val g = jobs(rate = 100)
        s.apply(g.id, a, 5, Tick(1)); s.closeApplications(Tick(5))
        val ev = settleWith(mapOf(a to 20))
        assertEquals(Money.units(2_000), g.applicationOf(a)!!.paid)
        assertEquals(20L, g.applicationOf(a)!!.gain)
        assertTrue(ev.any { it is GrantEvent.Settled && it.company == a && it.paid == Money.units(2_000) })
        assertTrue(ev.last() is GrantEvent.Closed)
        assertEquals(GrantStatus.PAID, g.status)
        assertEquals(Money.units(2_000), g.paidTotal)
    }

    @Test fun shrinkingEarnsNothing() {
        val g = jobs()
        s.apply(g.id, a, 50, Tick(1)); s.closeApplications(Tick(5))
        s.settle(Tick(25), { _, _ -> 30L }, { _, _, owed -> owed })
        assertEquals(0L, g.applicationOf(a)!!.gain)
        assertEquals(Money.ZERO, g.applicationOf(a)!!.paid)
    }

    @Test fun thePerCompanyCapApplies() {
        val g = jobs(rate = 1_000, cap = 5_000)
        s.apply(g.id, a, 0, Tick(1)); s.closeApplications(Tick(5))
        settleWith(mapOf(a to 100))
        assertEquals(Money.units(5_000), g.applicationOf(a)!!.paid)
    }

    @Test fun anOverspentBudgetIsSharedProRata() {
        val g = jobs(budget = 6_000, rate = 100, cap = 50_000)
        s.apply(g.id, a, 0, Tick(1)); s.apply(g.id, b, 0, Tick(1)); s.closeApplications(Tick(5))
        settleWith(mapOf(a to 60, b to 30))
        // Owed 6,000 and 3,000 against a 6,000 budget: two thirds each of what they asked.
        assertEquals(Money.units(4_000), g.applicationOf(a)!!.paid)
        assertEquals(Money.units(2_000), g.applicationOf(b)!!.paid)
        assertTrue(g.paidTotal <= g.budget)
    }

    @Test fun aShortTreasuryPaysWhatItCan() {
        val g = jobs(rate = 100)
        s.apply(g.id, a, 0, Tick(1)); s.closeApplications(Tick(5))
        settleWith(mapOf(a to 20), treasury = Money.units(500))
        assertEquals(Money.units(500), g.applicationOf(a)!!.paid)
    }

    @Test fun committedCountsRunningBudgetsOnly() {
        val g = jobs(budget = 100_000); jobs(budget = 50_000)
        assertEquals(Money.units(150_000), s.committed(country))
        s.apply(g.id, a, 0, Tick(1)); s.closeApplications(Tick(5))
        settleWith(emptyMap())
        assertEquals(Money.ZERO, s.committed(country))
    }

    @Test fun receivedByAddsUpAcrossProgrammes() {
        val g1 = jobs(rate = 100); val g2 = jobs(rate = 50)
        s.apply(g1.id, a, 0, Tick(1)); s.apply(g2.id, a, 0, Tick(1)); s.closeApplications(Tick(5))
        settleWith(mapOf(a to 10))
        assertEquals(Money.units(1_500), s.receivedBy(a))
        assertEquals(Money.ZERO, s.receivedBy(b))
    }

    @Test fun oldFinishedProgrammesAreForgotten() {
        jobs(); s.closeApplications(Tick(5))
        s.prune(Tick(30))
        assertEquals(1, s.all().size)
        s.prune(Tick(40))
        assertEquals(0, s.all().size)
    }

    @Test fun endingACompanyDropsItsApplications() {
        val g = jobs()
        s.apply(g.id, a, 0, Tick(1)); s.apply(g.id, b, 0, Tick(1))
        s.endAllOf(a)
        assertNull(g.applicationOf(a))
        assertTrue(g.applicationOf(b) != null)
    }

    @Test fun theHashChangesWithState() {
        fun hash() = CanonicalHasher().also { s.hashInto(it) }.finish().toHex()
        val empty = hash()
        val g = jobs()
        val withProgramme = hash()
        s.apply(g.id, a, 0, Tick(1))
        assertTrue(empty != withProgramme && withProgramme != hash())
    }
}
