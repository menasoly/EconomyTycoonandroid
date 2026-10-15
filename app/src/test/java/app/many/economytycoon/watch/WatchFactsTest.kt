package app.many.economytycoon.watch

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchFactsTest {
    private val sim by lazy { SimTestSupport.sim(13L, 4).also { it.advance(35) } }
    private val e get() = sim.economy

    @Test fun factsReadTheLiveCompany() {
        val c = e.companies.active().first()
        val f = EconomyWatchFacts(e, c.id)
        assertEquals(e.tick.value, f.day)
        assertEquals(e.finance.statements.balance(c.owner, e.tick).cash, f.cash)
        assertTrue(f.idleFacilities >= 0)
    }

    @Test fun pricesComeFromTheMarketsOrAreMissing() {
        val f = EconomyWatchFacts(e, e.companies.active().first().id)
        val state = e.markets.states().first()
        assertEquals(e.markets.price(state.key).perUnit, f.price(state.key.region, state.key.good))
        assertNull(f.price(RegionId(9_999), GoodId("no_such_good")))
    }

    @Test fun unlistedCompaniesHaveNoStockMove() {
        val unlisted = e.companies.active().firstOrNull { !e.exchange.isListed(it.id) } ?: return
        assertNull(EconomyWatchFacts(e, unlisted.id).stockMovePpm(unlisted.id))
    }

    @Test fun listedCompaniesReportAMove() {
        val l = e.exchange.listings().firstOrNull() ?: return
        assertTrue(EconomyWatchFacts(e, l.issuer).stockMovePpm(l.issuer) != null)
    }

    @Test fun namesFallBackForUnknowns() {
        val n = EconomyWatchNames(e)
        assertEquals("no such good", n.good(GoodId("no_such_good")))
        assertEquals("region 9999", n.region(RegionId(9_999)))
        assertEquals("company 9999", n.company(CompanyId(9_999)))
        assertEquals(e.world.regions().first().name, n.region(e.world.regions().first().id))
    }

    @Test fun anUnknownCompanyReadsAsEmpty() {
        val f = EconomyWatchFacts(e, CompanyId(9_999))
        assertEquals(0, f.idleFacilities)
        assertEquals(app.many.economytycoon.core.Money.ZERO, f.cash)
    }

    @Test fun aRuleBookRunsAgainstALiveCompany() {
        val c = e.companies.active().first()
        val b = RuleBook()
        b.add(WatchRule.Cash(app.many.economytycoon.core.Money.units(1_000_000_000)))
        val hits = b.evaluate(EconomyWatchFacts(e, c.id), EconomyWatchNames(e))
        assertEquals(1, hits.size)
        assertEquals(e.tick.value, hits.single().day)
    }
}
