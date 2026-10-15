package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.unions.DemandStatus
import app.many.economytycoon.unions.ResponseKind
import app.many.economytycoon.unions.Union
import app.many.economytycoon.unions.UnionEvent
import app.many.economytycoon.workforce.Cohort
import app.many.economytycoon.workforce.CohortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnionRunnerTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val run = e.unionRunner
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val role = e.content.allRoles.first().id
        val company = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Labour Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        fun staff(who: CompanyId = company, heads: Long = 60, morale: Long = 300_000L, facility: Long = 1): Cohort {
            val c = e.workforce.ensure(CohortKey(who, FacilityId(facility), role, region))
            c.headcount = heads; c.moralePpm = morale; c.target = heads
            return c
        }
        /** The next day on which the company is looked over for organising. */
        fun reviewDay(after: Long): Long { var t = after + 1; while ((t + company.value) % e.unions.params.reviewEvery != 0L) t++; return t }
        fun organise(max: Int = 80): Union {
            var t = 0L
            repeat(max) {
                t = reviewDay(t)
                run.run(Tick(t))
                e.unions.forGroup(company, region)?.let { return it }
            }
            error("no union formed")
        }
        fun demand(u: Union, raise: Long = 60_000L, tick: Long = 100) = e.unions.makeDemand(u, raise, Tick(tick))
    }

    @Test fun unhappyWorkersOrganiseAndTheirTeamsAreMarked() {
        val r = Rig(); val c = r.staff()
        val u = r.organise()
        assertEquals(r.company, u.company); assertEquals(r.region, u.region); assertEquals(60L, u.members)
        assertTrue(c.unionized)
        assertTrue(r.e.unions.active().size == 1)
    }

    @Test fun contentWellPaidWorkersDoNot() {
        val r = Rig(); r.staff(morale = 800_000L)
        var t = 0L
        repeat(40) { t = r.reviewDay(t); r.run.run(Tick(t)) }
        assertNull(r.e.unions.forGroup(r.company, r.region))
    }

    @Test fun aSmallTeamIsTooSmallToOrganise() {
        val r = Rig(); r.staff(heads = 5)
        var t = 0L
        repeat(40) { t = r.reviewDay(t); r.run.run(Tick(t)) }
        assertTrue(r.e.unions.all().isEmpty())
    }

    @Test fun contentmentErodesAUnionUntilItDisbandsAndClearsTheFlag() {
        val r = Rig(); val c = r.staff(); val u = r.organise()
        c.moralePpm = 950_000L
        var t = 0L; var gone = false
        repeat(80) {
            t = r.reviewDay(maxOf(t, u.formedAt.value)); val events = r.run.run(Tick(t))
            if (events.any { it is UnionEvent.Disbanded }) gone = true
            c.moralePpm = 950_000L
        }
        assertTrue(gone); assertFalse(u.isActive); assertFalse(c.unionized)
        assertNull(r.e.unions.forGroup(r.company, r.region))
    }

    @Test fun anAngryWorkforceKeepsAUnionStrong() {
        val r = Rig(); val c = r.staff(morale = 150_000L); val u = r.organise()
        var t = u.formedAt.value
        repeat(30) { t = r.reviewDay(t); r.run.run(Tick(t)); c.moralePpm = 150_000L }
        assertTrue(u.isActive && u.strengthPpm > 700_000L)
    }

    @Test fun layingEveryoneOffEndsTheUnion() {
        val r = Rig(); val c = r.staff(); val u = r.organise()
        c.headcount = 0
        val events = r.run.run(Tick(r.reviewDay(u.formedAt.value)))
        assertTrue(events.any { it is UnionEvent.Disbanded && it.union == u.id })
        assertFalse(u.isActive)
    }

    @Test fun aWageRoundIsDemandedEveryQuarter() {
        val r = Rig(); r.staff(); val u = r.organise()
        val day = u.formedAt.value + r.e.unions.params.roundEvery
        assertTrue(r.run.run(Tick(day - 1)).none { it is UnionEvent.Demanded })
        val events = r.run.run(Tick(day))
        val d = r.e.unions.openDemand(u.id)!!
        assertTrue(events.any { it is UnionEvent.Demanded && it.demand == d.id })
        assertTrue(d.raisePpm in r.e.unions.params.baseRaisePpm..r.e.unions.params.maxRaisePpm)
        assertEquals(Tick(day + r.e.unions.params.responseDays), d.deadline)
        assertTrue(r.run.run(Tick(day + 1)).none { it is UnionEvent.Demanded })
    }

    @Test fun acceptingRaisesEveryonesWageAndLiftsMorale() {
        val r = Rig(); val a = r.staff(facility = 1); val b = r.staff(facility = 2)
        val u = r.organise(); val d = r.demand(u, 50_000L)
        val wa = a.wage; val wb = b.wage; val ma = a.moralePpm
        val event = (r.run.respond(r.company, d.id, ResponseKind.ACCEPT, 0L, Tick(101)) as Outcome.Ok).value
        assertTrue(event is UnionEvent.Agreed && event.raisePpm == 50_000L)
        assertEquals(Money(wa.raw + wa.raw * 50_000L / 1_000_000L), a.wage); assertEquals(Money(wb.raw + wb.raw * 50_000L / 1_000_000L), b.wage)
        assertEquals(ma + r.e.unions.params.dealMoraleBoostPpm, a.moralePpm)
        assertEquals(DemandStatus.ACCEPTED, d.status); assertEquals(1, u.raisesWon)
        assertEquals(Rules.WRONG_STATE, (r.run.respond(r.company, d.id, ResponseKind.ACCEPT, 0L, Tick(102)) as Outcome.Rejected).rejection.rule)
    }

    @Test fun aRaiseNeverTakesAWageBelowTheLegalMinimum() {
        val r = Rig(); val c = r.staff(); val u = r.organise()
        r.e.laborMarkets.setMinimumWage(r.region, Money(c.wage.raw * 3))
        r.run.respond(r.company, r.demand(u, 30_000L).id, ResponseKind.ACCEPT, 0L, Tick(101))
        assertEquals(Money(c.wage.raw), r.e.laborMarkets.minimumWage(r.region))
    }

    @Test fun aGenerousCounterIsSettledAtTheCounterNotTheDemand() {
        val r = Rig(); val c = r.staff(); val u = r.organise(); u.strengthPpm = 100_000L
        val d = r.demand(u, 80_000L); val wage = c.wage
        val event = (r.run.respond(r.company, d.id, ResponseKind.COUNTER, 50_000L, Tick(101)) as Outcome.Ok).value
        assertTrue(event is UnionEvent.Agreed && event.raisePpm == 50_000L)
        assertEquals(DemandStatus.SETTLED, d.status); assertEquals(50_000L, d.agreedPpm)
        assertEquals(Money(wage.raw + wage.raw * 50_000L / 1_000_000L), c.wage)
    }

    @Test fun aStingyCounterIsTurnedIntoAStrikeOrAClimbdown() {
        val r = Rig(); val c = r.staff(); val u = r.organise(); u.strengthPpm = 900_000L
        val d = r.demand(u, 80_000L); val wage = c.wage
        val event = (r.run.respond(r.company, d.id, ResponseKind.COUNTER, 5_000L, Tick(101)) as Outcome.Ok).value
        assertTrue(event is UnionEvent.Struck || event is UnionEvent.BackedDown)
        assertEquals(wage, c.wage); assertEquals(5_000L, d.counterPpm)
        assertEquals(if (event is UnionEvent.Struck) DemandStatus.STRUCK else DemandStatus.BACKED_DOWN, d.status)
    }

    @Test fun refusingSometimesBringsAStrikeAndSometimesNot() {
        val r = Rig(); val c = r.staff(); val u = r.organise()
        var struck = 0; var down = 0
        repeat(40) { i ->
            u.strengthPpm = 500_000L
            val d = r.demand(u, 50_000L, tick = 1_000L + i * 100)
            when (val ev = (r.run.respond(r.company, d.id, ResponseKind.REFUSE, 0L, Tick(1_001L + i * 100)) as Outcome.Ok).value) {
                is UnionEvent.Struck -> { struck++; assertTrue(ev.days in r.e.unions.params.strikeMinDays..r.e.unions.params.strikeMinDays + r.e.unions.params.strikeSpanDays) }
                is UnionEvent.BackedDown -> down++
                else -> error("unexpected $ev")
            }
        }
        assertTrue("struck $struck", struck >= 5); assertTrue("down $down", down >= 5)
        assertTrue(u.strikes == struck)
    }

    @Test fun aStrikeStopsWorkUntilItEndsAndThenTheUnionIsToldSo() {
        val r = Rig(); val c = r.staff(); val u = r.organise(); u.strengthPpm = 1_000_000L
        var ev: UnionEvent
        var tick = 2_000L
        do {
            val d = r.demand(u, 50_000L, tick = tick)
            ev = (r.run.respond(r.company, d.id, ResponseKind.REFUSE, 0L, Tick(tick + 1)) as Outcome.Ok).value
            tick += 100
        } while (ev !is UnionEvent.Struck)
        val until = u.strikeUntil!!
        assertTrue(c.onStrike(Tick(until.value - 1))); assertTrue(u.onStrike(Tick(until.value - 1)))
        assertFalse(c.onStrike(until))
        assertTrue(r.run.run(Tick(until.value - 1)).none { it is UnionEvent.StrikeEnded })
        assertTrue(r.run.run(until).any { it is UnionEvent.StrikeEnded })
        assertNull(u.strikeUntil)
    }

    @Test fun settlingDuringAStrikeEndsIt() {
        val r = Rig(); val c = r.staff(); val u = r.organise()
        c.strikeUntilForTest(10_000)
        r.run.respond(r.company, r.demand(u, 40_000L).id, ResponseKind.ACCEPT, 0L, Tick(101))
        assertFalse(c.onStrike(Tick(150)))
    }

    @Test fun anUnansweredDemandIsRefusedAtTheDeadline() {
        val r = Rig(); r.staff(); val u = r.organise(); val d = r.demand(u, 50_000L)
        assertTrue(r.run.run(Tick(d.deadline.value - 1)).none { it is UnionEvent.Struck || it is UnionEvent.BackedDown })
        assertTrue(d.isOpen)
        val events = r.run.run(d.deadline)
        assertFalse(d.isOpen)
        assertTrue(events.any { it is UnionEvent.Struck || it is UnionEvent.BackedDown })
    }

    @Test fun onlyTheEmployerCanAnswer() {
        val r = Rig(); r.staff(); val u = r.organise(); val d = r.demand(u)
        val other = CompanyId((r.sim.execute(Actor.Player(2), Command.FoundCompany("Other Works", r.country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        assertEquals(Rules.NOT_OWNER, (r.run.respond(other, d.id, ResponseKind.ACCEPT, 0L, Tick(101)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, (r.run.respond(r.company, 999, ResponseKind.ACCEPT, 0L, Tick(101)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_ARGUMENT, (r.run.respond(r.company, d.id, ResponseKind.COUNTER, d.raisePpm, Tick(101)) as Outcome.Rejected).rejection.rule)
        assertTrue(d.isOpen)
    }

    @Test fun theCostOfARaiseIsAMonthOfTheExtraPay() {
        val r = Rig(); val c = r.staff(); val u = r.organise()
        val payroll = c.wage.raw * 60
        assertEquals(Money(payroll * 30 * 50_000L / 1_000_000L), r.run.monthlyCost(u, 50_000L))
        c.headcount = 0
        assertEquals(Money.ZERO, r.run.monthlyCost(u, 50_000L))
    }

    @Test fun windingUpACompanyDisbandsItsUnions() {
        val r = Rig(); val c = r.staff(); val u = r.organise(); val d = r.demand(u)
        r.run.forget(r.company, Tick(120))
        assertFalse(u.isActive); assertFalse(c.unionized); assertFalse(d.isOpen)
    }

    @Test fun theCommandGoesThroughTheExecutor() {
        val r = Rig(); r.staff(); val u = r.organise(); val d = r.demand(u, 50_000L)
        val ok = r.sim.execute(Actor.Player(1), Command.RespondDemand(r.company, d.id, ResponseKind.ACCEPT))
        assertTrue(ok.toString(), ok is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.Player(1), Command.RespondDemand(r.company, d.id, ResponseKind.ACCEPT)) is Outcome.Rejected)
        assertNotNull(r.e.unions.demand(d.id))
    }

    private fun Cohort.strikeUntilForTest(tick: Long) { strikeUntil = tick }
}
