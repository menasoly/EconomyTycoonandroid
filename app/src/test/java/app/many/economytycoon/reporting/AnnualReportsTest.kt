package app.many.economytycoon.reporting

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnualReportsTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val me: CompanyId = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Yearly Works", country.id, null, Money.units(800_000))) as Outcome.Ok).value.created!!)
    private val books = AnnualReports(e)

    private fun runTo(day: Long) { sim.advance((day - e.tick.value).toInt()) }

    @Test fun yearsAreCountedInThreeHundredSixtyDays() {
        assertEquals(0, books.completedYears())
        runTo(361)
        assertEquals(1, books.completedYears())
        assertEquals(Tick(360), books.yearEnd(0))
        assertEquals(Tick(360), books.yearStart(1))
    }

    @Test fun theYearInProgressIsMarkedPartial() {
        runTo(40)
        val r = books.report(me, 0)!!
        assertTrue(r.partial)
        assertEquals(Tick(40), r.to)
        assertEquals(40L, r.days)
    }

    @Test fun aFinishedYearMatchesTheLedgerForThatPeriod() {
        e.production.grantFacility(me, FacilityTypeId("grain_farm"), e.world.regionsOf(country.id).first().id, null, null, null, e.tick)
        runTo(400)
        val r = books.report(me, 0)!!
        val owner = e.companies.require(me).owner
        assertFalse(r.partial)
        assertEquals(Tick(360), r.to)
        assertEquals(e.finance.statements.income(owner, Tick(0), Tick(360)), r.income)
        assertEquals(e.finance.statements.balance(owner, Tick(360)), r.closing)
        assertEquals(e.finance.statements.balance(owner, Tick(0)), r.opening)
    }

    @Test fun reportsAreRebuiltIdenticallyEachTime() {
        runTo(380)
        val a = books.report(me, 0)!!
        val b = books.report(me, 0)!!
        assertEquals(a.income, b.income)
        assertEquals(a.closing, b.closing)
    }

    @Test fun historyListsEveryYearFromFoundingOldestFirst() {
        runTo(400)
        val h = books.history(me)
        assertEquals(listOf(0, 1), h.map { it.index })
        assertFalse(h[0].partial)
        assertTrue(h[1].partial)
        assertEquals(h[0].netIncome, h[1].prior!!.netIncome)
    }

    @Test fun latestCompleteSkipsTheYearInProgress() {
        runTo(400)
        assertEquals(0, books.latestComplete(me)!!.index)
        assertNull(AnnualReports(SimTestSupport.sim(5, 0).economy).latestComplete(CompanyId(9_999)))
    }

    @Test fun anUnknownCompanyHasNoHistory() {
        assertTrue(books.history(CompanyId(9_999)).isEmpty())
        assertNull(books.report(CompanyId(9_999), 0))
    }

    @Test fun negativeYearsDoNotExist() {
        assertNull(books.report(me, -1))
    }

    @Test fun theTableRanksByNetWorthAndLeavesOutTheState() {
        runTo(380)
        val table = books.table(0)
        assertTrue(table.isNotEmpty())
        assertEquals(table.map { it.netWorth.raw }.sortedDescending(), table.map { it.netWorth.raw })
        assertTrue(table.any { it.company == me })
        assertEquals(1 + table.indexOfFirst { it.company == me }, books.rank(me, 0))
        assertNull(books.rank(CompanyId(9_999), 0))
    }
}
