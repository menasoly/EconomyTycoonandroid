package app.many.economytycoon.trade

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeFinderTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false).also { s -> s.advance(20) }
    private val e = sim.economy
    private val wheat = GoodId("wheat")
    private val finder = TradeFinder(e)

    private fun pin(region: Int, price: Long) {
        val r = e.world.regions().sortedBy { it.id }[region]
        e.markets.applyShock(MarketKey(r.id, wheat), 0L)
        e.markets.state(MarketKey(r.id, wheat))!!.let { s -> s.record(Price(Money.units(price)), Quantity.ZERO, e.tick.value); s.record(Price(Money.units(price)), Quantity.ZERO, e.tick.value) }
    }

    @Test fun anUnknownGoodHasNoLegs() {
        assertEquals(emptyList<TradeLeg>(), finder.forGood(GoodId("no_such_good")))
    }

    @Test fun legsNeverStartAndEndInTheSameRegion() {
        for (leg in finder.forGood(wheat)) assertTrue(leg.from != leg.to)
    }

    @Test fun everyLegSellsHigherThanItBuys() {
        for (leg in finder.forGood(wheat)) assertTrue(leg.sellPrice.raw > leg.buyPrice.raw)
    }

    @Test fun aWidePriceGapGivesAProfitableLeg() {
        pin(0, 5); pin(1, 500)
        val leg = finder.forGood(wheat, Quantity.units(10)).first()
        assertTrue("profit ${leg.netProfit}", leg.isProfitable)
        assertEquals(Quantity.units(10), leg.quantity)
    }

    @Test fun legsAreBestFirstByProfit() {
        pin(0, 5); pin(1, 300)
        val profits = finder.forGood(wheat).map { it.netProfit.raw }
        assertEquals(profits.sortedDescending(), profits)
    }

    @Test fun scanOnlyKeepsProfitableLegsAndRespectsThePerGoodLimit() {
        pin(0, 5); pin(1, 400)
        val legs = finder.scan(perGood = 2)
        assertTrue(legs.all { it.isProfitable })
        assertTrue(legs.groupBy { it.good }.values.all { it.size <= 2 })
        assertEquals(legs.map { it.netProfit.raw }.sortedDescending(), legs.map { it.netProfit.raw })
    }

    @Test fun carriageGrowsWithTheShipment() {
        pin(0, 5); pin(1, 500)
        val small = finder.forGood(wheat, Quantity.units(10)).first()
        val big = finder.forGood(wheat, Quantity.units(1_000)).first { it.from == small.from && it.to == small.to }
        assertTrue(big.freight.raw > small.freight.raw)
    }

    @Test fun theScanDoesNotChangeTheEconomy() {
        val before = sim.hash()
        finder.scan()
        assertEquals(before, sim.hash())
    }
}
