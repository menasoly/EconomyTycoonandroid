package app.many.economytycoon.content.audit

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainCostTest {
    private val chain = ChainCost(AuditFixture.content)

    @Test fun aGoodNothingMakesCostsItsReferencePrice() {
        assertEquals(Money.units(5).raw, chain.perUnit(GoodId("mystery")))
    }

    @Test fun aMadeGoodCostsItsIngredientsPlusLabor() {
        // Ore: two workers for a day make ten units, so two units of money each.
        assertEquals(Money.units(2).raw, chain.perUnit(GoodId("ore")))
        // A bar takes two ore (4) and a day of one worker (10).
        assertEquals(Money.units(14).raw, chain.perUnit(GoodId("bar")))
    }

    @Test fun priceOverCostShowsWhereMoneyIsMade() {
        assertEquals(5_000_000L, chain.priceToCostPpm(GoodId("ore")))
        assertTrue(chain.priceToCostPpm(GoodId("bar")) in 2_800_000L..2_900_000L)
        assertEquals(250_000L, chain.priceToCostPpm(GoodId("junk")))
    }

    @Test fun aCycleDoesNotLoopForever() {
        val a = chain.perUnit(GoodId("loop_a"))
        assertTrue(a > 0L)
    }

    @Test fun costsAreRememberedBetweenAsks() {
        assertEquals(chain.perUnit(GoodId("bar")), chain.perUnit(GoodId("bar")))
    }

    @Test fun shippedContentChainsAllFinish() {
        val shipped = ChainCost(app.many.economytycoon.simulation.SimTestSupport.content)
        for (g in app.many.economytycoon.simulation.SimTestSupport.content.allGoods) assertTrue(g.id.key, shipped.perUnit(g.id) >= 0L)
    }
}
