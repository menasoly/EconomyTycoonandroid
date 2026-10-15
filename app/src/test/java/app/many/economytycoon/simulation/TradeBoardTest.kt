package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeBoardTest {
    private fun fresh(): Economy {
        val sim = SimTestSupport.sim(6, 0)
        return sim.economy
    }

    @Test fun importedGoodsAreInputsNobodyMakesPlusTheConfiguredBasics() {
        val e = fresh()
        val goods = e.tradeBoard.importedGoods.map { it.key }
        assertTrue("water" in goods)
        for (basic in e.config.importAlso) assertTrue(basic in goods)
        for (g in e.tradeBoard.importedGoods) {
            val producible = e.content.recipesProducing(g).isNotEmpty()
            assertTrue(!producible || g.key in e.config.importAlso)
        }
    }

    @Test fun everyStockedGoodHasExactlyOneCarrierPerCountry() {
        val e = fresh()
        for (c in e.world.countries()) for (g in e.tradeBoard.stockedGoods) {
            val carriers = e.tradeBoard.boardsOf(c.id).filter { b ->
                e.companies.policies.sellRules(b).any { it.good == g }
            }
            assertEquals("${g.key} in ${c.name}", 1, carriers.size)
            assertEquals(carriers.single(), e.tradeBoard.carrierOf(c.id, g))
        }
    }

    @Test fun noBoardQuotesMoreThanTheMarketAllowsPerTick() {
        val e = fresh()
        for (b in e.companies.ofKind(CompanyKind.STATE)) {
            assertTrue(e.companies.policies.sellRules(b.id).size <= TradeBoard.ORDERS_PER_TICK + 2)
        }
    }

    @Test fun establishingTwiceChangesNothing() {
        val e = fresh()
        val before = e.companies.all().size
        e.tradeBoard.establish(Tick.ZERO)
        assertEquals(before, e.companies.all().size)
    }

    @Test fun replenishRestoresSoldStockAndBooksItAtTheReferencePrice() {
        val e = fresh()
        val region = e.world.regions().first()
        val water = GoodId("water")
        val board = e.tradeBoard.carrierOf(region.country, water)!!
        val depot = e.production.facilitiesOf(board).first { it.region == region.id }
        val target = depot.inventory.quantity(water)
        depot.inventory.remove(water, Quantity.units(1_000))
        val booksBefore = e.finance.ledger.balance(app.many.economytycoon.finance.LedgerOwner.company(board), app.many.economytycoon.finance.Chart.INVENTORY)
        e.tradeBoard.replenish(Tick(1))
        assertEquals(target, depot.inventory.quantity(water))
        val booksAfter = e.finance.ledger.balance(app.many.economytycoon.finance.LedgerOwner.company(board), app.many.economytycoon.finance.Chart.INVENTORY)
        assertEquals(e.content.good(water).referencePrice.valueOf(Quantity.units(1_000)), booksAfter - booksBefore)
        assertTrue(Money.ZERO <= booksAfter)
    }

    @Test fun boardsPayTheirProfitOutAsDividends() {
        val e = fresh()
        val b = e.companies.ofKind(CompanyKind.STATE).first()
        assertEquals(1_000_000L, e.companies.policies.dividend(b.id).payoutPpm)
    }
}
