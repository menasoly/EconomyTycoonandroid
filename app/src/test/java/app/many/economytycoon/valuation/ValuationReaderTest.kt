package app.many.economytycoon.valuation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuationReaderTest {
    private val sim by lazy { SimTestSupport.sim(9L, 4).also { it.advance(40) } }

    @Test fun unknownCompaniesHaveNoInput() {
        assertEquals(null, ValuationReader(sim.economy).input(CompanyId(9_999)))
        assertEquals(null, ValuationReader(sim.economy).summary(CompanyId(9_999)))
    }

    @Test fun everyActiveCompanyCanBeValued() {
        val r = ValuationReader(sim.economy)
        for (c in sim.economy.companies.active()) {
            val s = r.summary(c.id)
            assertTrue("${c.name}", s != null)
            assertTrue(s!!.low <= s.high)
            assertTrue(s.equity.raw >= 0L)
        }
    }

    @Test fun inputsMatchTheBalanceSheet() {
        val e = sim.economy
        val c = e.companies.active().first()
        val i = ValuationReader(e).input(c.id)!!
        val b = e.finance.statements.balance(c.owner, e.tick)
        assertEquals(b.cash, i.cash)
        assertEquals(b.totalDebt, i.debt)
        assertEquals(b.totalEquity, i.bookEquity)
    }

    @Test fun listedCompaniesCarryTheirMarketPrice() {
        val e = sim.economy
        for (l in e.exchange.listings()) {
            val i = ValuationReader(e).input(l.issuer)
            if (i != null) assertEquals(l.price, i.marketPrice)
        }
    }

    @Test fun historyNeverExceedsTheWindow() {
        val r = ValuationReader(sim.economy)
        for (c in sim.economy.companies.active()) assertTrue(r.input(c.id)!!.historyDays <= ValuationReader.WINDOW_DAYS)
    }
}
