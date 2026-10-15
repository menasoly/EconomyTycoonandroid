package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapTest {
    @Test fun aNewGameHasMoneyRivalsAndATradeBoardPerCountry() {
        val sim = SimTestSupport.sim()
        val e = sim.economy
        assertTrue(e.finance.ledger.cash(e.finance.households).raw > 0)
        assertTrue(e.finance.ledger.cash(e.finance.bank).raw > 0)
        assertEquals(4, e.companies.ofKind(CompanyKind.AI).size)
        assertTrue(e.companies.ofKind(CompanyKind.STATE).size >= e.world.countries().size)
        for (c in e.world.countries()) assertTrue(e.tradeBoard.boardOf(c.id) != null)
    }

    @Test fun everyRivalStartsWithAStaffedFacilityAndRules() {
        val e = SimTestSupport.sim().economy
        for (c in e.companies.ofKind(CompanyKind.AI)) {
            val facilities = e.production.facilitiesOf(c.id)
            assertEquals(1, facilities.size)
            assertTrue(e.workforce.cohortsOf(c.id).isNotEmpty())
            assertTrue(e.companies.policies.sellRules(c.id).isNotEmpty())
            assertTrue(e.competitors.isAi(c.id))
        }
    }

    @Test fun bootstrapLeavesTheBooksBalancedAndConsistent() {
        val e = SimTestSupport.sim().economy
        assertEquals(emptyList<String>(), Invariants.check(e))
    }

    @Test fun importDepotsAreStocked() {
        val e = SimTestSupport.sim().economy
        val goods = e.tradeBoard.importedGoods
        assertTrue(goods.isNotEmpty())
        val region = e.world.regions().first()
        for (g in goods) {
            val board = e.tradeBoard.carrierOf(region.country, g)!!
            val depot = e.production.facilitiesOf(board).first { it.region == region.id }
            assertTrue(depot.inventory.quantity(g).raw > 0)
        }
    }

    @Test fun sameSeedGivesTheSameStartingHash() {
        assertEquals(SimTestSupport.sim(5).hash(), SimTestSupport.sim(5).hash())
        assertTrue(SimTestSupport.sim(5).hash() != SimTestSupport.sim(6).hash())
        assertTrue(Money.ZERO.raw == 0L)
    }
}
