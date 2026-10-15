package app.many.economytycoon.session

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyReportsTest {
    private val sim = SimTestSupport.sim(31L, 4).also { it.advance(60) }
    private val reports = CompanyReports(sim.economy)

    @Test fun everyActiveCompanyHasAHeadline() {
        val active = sim.economy.companies.active()
        assertEquals(active.size, reports.leaderboard().size)
        for (h in reports.leaderboard()) {
            assertTrue(h.name.isNotBlank())
            assertTrue(h.facilities >= 0 && h.employees >= 0)
        }
    }

    @Test fun leaderboardIsOrderedByNetWorth() {
        val worth = reports.leaderboard().map { it.netWorth.raw }
        assertEquals(worth.sortedDescending(), worth)
    }

    @Test fun unknownCompaniesHaveNoReports() {
        assertNull(reports.headline(CompanyId(9_999)))
        assertNull(reports.finance(CompanyId(9_999)))
        assertTrue(reports.facilities(CompanyId(9_999)).isEmpty())
    }

    @Test fun theBalanceSheetInAFinancePackBalances() {
        for (c in sim.economy.companies.active().filter { it.kind == CompanyKind.AI }) {
            val pack = reports.finance(c.id)!!
            assertTrue("company ${c.id}", pack.balance.balances)
            assertEquals(sim.tick, pack.asOf)
            assertTrue(pack.month.from <= pack.month.to)
        }
    }

    @Test fun facilitiesComeWithNamesRegionsAndStock() {
        val owner = sim.economy.companies.active().first { reports.facilities(it.id).isNotEmpty() }
        val rows = reports.facilities(owner.id)
        assertEquals(sim.economy.production.facilitiesOf(owner.id).size, rows.size)
        for (r in rows) {
            assertTrue(r.name.isNotBlank() && r.region.isNotBlank())
            assertTrue(r.conditionPpm in 0..1_000_000)
            for (s in reports.stock(r.id)) assertTrue(s.quantity.raw > 0 && s.name.isNotBlank())
        }
        assertNotNull(rows.firstOrNull())
    }

    @Test fun headcountInRowsMatchesCompanyEmployment() {
        for (c in sim.economy.companies.active()) {
            val rows = reports.facilities(c.id)
            val head = reports.headline(c.id)!!
            assertTrue(rows.sumOf { it.headcount } <= head.employees)
        }
    }
}
