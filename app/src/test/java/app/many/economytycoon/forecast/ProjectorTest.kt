package app.many.economytycoon.forecast

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectorTest {
    private val sim = Simulation(SimTestSupport.config(9, 2), SimTestSupport.content, bootstrap = true)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val me: CompanyId = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Forecast Works", country.id, null, Money.units(900_000))) as Outcome.Ok).value.created!!)

    @Test fun aForkStartsIdenticalAndStaysApart() {
        sim.advance(5)
        val copy = Projector.fork(sim)
        assertEquals(sim.hash(), copy.hash())
        copy.advance(10)
        assertNotEquals(sim.hash(), copy.hash())
        assertEquals(5L, sim.tick.value)
    }

    @Test fun projectingNeverMovesTheRealGame() {
        sim.advance(3)
        val before = sim.hash()
        Projector.project(sim, me, 30)
        assertEquals(before, sim.hash())
        assertEquals(3L, sim.tick.value)
    }

    @Test fun pointsRunFromTodayInSteps() {
        sim.advance(2)
        val f = Projector.project(sim, me, 20, step = 5)
        assertEquals(listOf(2L, 7L, 12L, 17L, 22L), f.points.map { it.day })
        assertEquals(20L, f.days)
        assertEquals(EndReason.COMPLETED, f.ended)
    }

    @Test fun anUnevenStepStillEndsOnTheLastDay() {
        val f = Projector.project(sim, me, 12, step = 5)
        assertEquals(12L, f.last!!.day - f.startDay)
    }

    @Test fun doingNothingPredictsWhatReallyHappens() {
        sim.advance(4)
        val forecast = Projector.project(sim, me, 25, step = 25)
        sim.advance(25)
        val real = e.finance.statements.balance(e.companies.require(me).owner, e.tick)
        assertEquals(real.cash, forecast.last!!.cash)
        assertEquals(real.netWorth, forecast.last!!.netWorth)
    }

    @Test fun twoProjectionsOfTheSameThingAgree() {
        val a = Projector.project(sim, me, 20)
        val b = Projector.project(sim, me, 20)
        assertEquals(a.points, b.points)
    }

    @Test fun anActionChangesTheOutcome() {
        val region = e.world.regionsOf(country.id).first().id
        val build = Command.Build(me, FacilityTypeId("grain_farm"), region)
        val c = Projector.compare(sim, me, 20, listOf(build))
        assertTrue(c.scenario.actions.single().refusal.toString(), c.scenario.actions.single().accepted)
        assertTrue(c.scenario.first!!.cash < c.baseline.first!!.cash || c.scenario.last!!.cash < c.baseline.last!!.cash)
        assertNotEquals(c.baseline.last!!.cash, c.scenario.last!!.cash)
    }

    @Test fun aRefusedActionIsReportedAndTheRunGoesOn() {
        val region = e.world.regionsOf(country.id).first().id
        val build = Command.Build(me, FacilityTypeId("no_such_facility"), region)
        val f = Projector.project(sim, me, 10, actions = listOf(build))
        assertEquals(1, f.refused.size)
        assertFalse(f.refused.single().accepted)
        assertEquals(EndReason.COMPLETED, f.ended)
    }

    @Test fun anUnknownCompanyGivesAnEmptyRun() {
        val f = Projector.project(sim, CompanyId(9_999), 10)
        assertTrue(f.points.isEmpty())
        assertEquals(EndReason.COMPANY_GONE, f.ended)
        assertEquals(0L, f.days)
    }

    @Test fun lengthsOutsideTheLimitsAreRefused() {
        var failed = 0
        for (d in listOf(0, -3, Projector.MAX_DAYS + 1)) try { Projector.project(sim, me, d) } catch (x: IllegalArgumentException) { failed++ }
        assertEquals(3, failed)
        try { Projector.project(sim, me, 5, step = 0); failed++ } catch (x: IllegalArgumentException) { }
        assertEquals(3, failed)
    }

    @Test fun summariesReadTheEndsOfTheRun() {
        val f = Projector.project(sim, me, 30)
        assertEquals(f.last!!.netWorth - f.first!!.netWorth, f.netWorthChange)
        assertEquals(f.last!!.cash - f.first!!.cash, f.cashChange)
        assertTrue(f.lowestCash!!.cash <= f.first!!.cash)
        assertTrue(f.highestNetWorth!!.netWorth >= f.last!!.netWorth)
        assertEquals(f.netWorthChange.raw / 30, f.netWorthPerDay.raw)
    }

    @Test fun anOverdrawnCompanyIsFlaggedOnItsFirstBadDay() {
        val broke = Forecast(me, 0, listOf(ForecastPoint(0, Money.units(10), Money.units(10), Money.ZERO, Money.ZERO), ForecastPoint(5, Money.units(-1), Money.units(1), Money.ZERO, Money.ZERO)), EndReason.COMPLETED)
        assertEquals(5L, broke.firstOverdrawn)
        assertEquals(null, Forecast(me, 0, broke.points.take(1), EndReason.COMPLETED).firstOverdrawn)
    }

    @Test fun aRunFromSavedBytesMatchesARunFromTheGame() {
        sim.advance(6)
        val bytes = Projector.capture(sim)
        sim.advance(10)
        val later = Projector.project(sim, me, 15)
        val fromBytes = Projector.projectSaved(bytes, sim.content, me, 25)
        assertEquals(later.points.map { it.netWorth }, fromBytes.points.takeLast(later.points.size).map { it.netWorth })
        assertEquals(6L, fromBytes.startDay)
        assertEquals(31L, fromBytes.points.last().day)
    }

    @Test fun savedBytesAreNotChangedByRunningThem() {
        val bytes = Projector.capture(sim)
        val copy = bytes.copyOf()
        Projector.projectSaved(bytes, sim.content, me, 10)
        assertTrue(copy.contentEquals(bytes))
    }
}
