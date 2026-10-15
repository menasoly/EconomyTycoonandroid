package app.many.economytycoon.cashflow

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashForecasterTest {
    private val sim by lazy { SimTestSupport.sim(21L, 4).also { it.advance(35) } }
    private val e get() = sim.economy

    @Test fun unknownCompaniesHaveNoForecast() {
        assertEquals(null, CashForecaster(e).outlook(CompanyId(9_999)))
        assertEquals(null, CashForecaster(e).basis(CompanyId(9_999)))
    }

    @Test fun everyActiveCompanyGetsAnOutlook() {
        val f = CashForecaster(e)
        for (c in e.companies.active()) {
            val o = f.outlook(c.id)
            assertTrue(c.name, o != null)
            assertEquals(CashForecaster.DEFAULT_HORIZON, o!!.expected.points.size)
        }
    }

    @Test fun startingCashMatchesTheBalanceSheet() {
        val c = e.companies.active().first()
        val b = CashForecaster(e).basis(c.id)!!
        assertEquals(e.finance.statements.balance(c.owner, e.tick).cash, b.cash)
    }

    @Test fun windowNeverExceedsTheHistory() {
        val b = CashForecaster(e).basis(e.companies.active().first().id)!!
        assertTrue(b.windowDays in 1..CashForecaster.WINDOW_DAYS)
    }

    @Test fun lumpsAreAllFutureLoanPayments() {
        val f = CashForecaster(e)
        for (c in e.companies.active()) for (l in f.basis(c.id)!!.lumps) {
            assertTrue(l.day >= e.tick.value)
            assertTrue(l.amount.raw < 0L)
        }
    }

    @Test fun aNewLoanShowsUpAsDatedPayments() {
        val s = SimTestSupport.sim(22L, 2).also { it.advance(5) }
        val c = s.economy.companies.active().first()
        s.economy.finance.mint(s.economy.tick, s.economy.finance.bank, Money.units(5_000_000), "test liquidity")
        val before = CashForecaster(s.economy).basis(c.id)!!.lumps.size
        s.economy.finance.borrow(s.economy.tick, c.owner, Money.units(100_000), 90)
        val after = CashForecaster(s.economy).basis(c.id)!!.lumps
        assertTrue(after.size > before)
    }

    @Test fun outlookScenariosAreOrderedAtTheEnd() {
        val f = CashForecaster(e)
        for (c in e.companies.active()) {
            val o = f.outlook(c.id)!!
            assertTrue(o.cautious.end <= o.expected.end && o.expected.end <= o.hopeful.end)
        }
    }

    @Test fun shorterHorizonsGiveFewerPoints() {
        val o = CashForecaster(e).outlook(e.companies.active().first().id, horizon = 30)!!
        assertEquals(30, o.expected.points.size)
    }
}
