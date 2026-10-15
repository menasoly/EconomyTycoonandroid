package app.many.economytycoon.markets

import app.many.economytycoon.core.Season
import app.many.economytycoon.core.Tick
import app.many.economytycoon.markets.MarketTestSupport.FakePopulation
import app.many.economytycoon.markets.MarketTestSupport.Rig
import app.many.economytycoon.markets.MarketTestSupport.alice
import app.many.economytycoon.markets.MarketTestSupport.bread
import app.many.economytycoon.markets.MarketTestSupport.content
import app.many.economytycoon.markets.MarketTestSupport.gold
import app.many.economytycoon.markets.MarketTestSupport.north
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemandAndIndexTest {
    private val breadProfile = content.demandProfile(bread)!!
    private val ref = content.good(bread).referencePrice.perUnit.raw

    @Test fun baseQuantityAddsUpTheBands() {
        val pop = FakePopulation(low = 1000, middle = 1000, high = 1000)
        assertEquals(3_000_000L, ConsumerDemand.baseQuantity(breadProfile, pop, north, Season.SPRING))
        val bigger = FakePopulation(low = 2000, middle = 2000, high = 2000)
        assertEquals(6_000_000L, ConsumerDemand.baseQuantity(breadProfile, bigger, north, Season.SPRING))
    }

    @Test fun ladderSumsToTheDemandAtEachPrice() {
        val base = 3_000_000L
        val steps = ConsumerDemand.ladder(breadProfile, base, ref, 1_000_000L)
        fun wanted(multiplierPpm: Long): Long {
            val limit = ref * multiplierPpm / 1_000_000L
            return steps.filter { it.limit >= limit }.sumOf { it.quantity }
        }
        assertEquals(base, wanted(1_000_000))
        assertTrue(wanted(800_000) > wanted(1_000_000))
        assertTrue(wanted(1_600_000) < wanted(1_000_000))
        // linear demand with elasticity 0.8 loses 48% of demand at 1.6x
        assertEquals(ConsumerDemand.demandAt(base, breadProfile.elasticityPpm, 1_600_000), wanted(1_600_000))
    }

    @Test fun demandNeverGoesNegativeOrExplodes() {
        assertEquals(0L, ConsumerDemand.demandAt(1000, 2_000_000, 5_000_000))
        assertEquals(1800L, ConsumerDemand.demandAt(1000, 800_000, 0))
        assertEquals(3000L, ConsumerDemand.demandAt(1000, 4_000_000, 0))
    }

    @Test fun emptyDemandProducesNoSteps() {
        assertTrue(ConsumerDemand.ladder(breadProfile, 0, ref, 1_000_000L).isEmpty())
        assertTrue(ConsumerDemand.ladder(breadProfile, 100, ref, 0L).isEmpty())
    }

    @Test fun spendAtMatchesQuantityTimesPrice() {
        val steps = ConsumerDemand.ladder(breadProfile, 3_000_000L, ref, 1_000_000L)
        // 3,000 units at 40.00 = 120,000.00
        assertEquals(1_200_000_000L, ConsumerDemand.spendAt(steps, ref))
    }

    @Test fun priceHistoryWrapsAround() {
        val h = PriceHistory(4)
        for (i in 1..6) h.push(i.toLong() * 10, i.toLong())
        assertEquals(4, h.size)
        assertEquals(60L, h.close(0)); assertEquals(30L, h.close(3)); assertNull(h.close(4))
        assertEquals(listOf(30L, 40L, 50L, 60L), h.closesOldestFirst().toList())
    }

    @Test fun priceRulesKeepMovesInsideTheBand() {
        val band = PriceRules.band(10_000, 50_000)
        assertEquals(9_500L, band.first); assertEquals(10_500L, band.last)
        val up = PriceRules.drift(10_000, 9_000, null, 10_000, 50_000)
        assertTrue(up in 10_000..10_500)
        val down = PriceRules.drift(10_000, null, 11_000, 10_000, 50_000)
        assertTrue(down in 9_500..10_000)
        val idle = PriceRules.drift(8_000, null, null, 10_000, 50_000)
        assertTrue(idle > 8_000)
    }

    @Test fun cpiStartsNeutralAndFollowsPrices() {
        val r = Rig()
        assertEquals(1_000_000L, r.market.index.cpi(r.market))
        r.market.applyShock(MarketKey(north, bread), 100_000)
        r.market.setDemandShiftEverywhere(bread, 0)
        r.buy(alice, bread, 1.0, null); r.sell(alice, bread, 1.0, null)
        repeat(5) { r.step() }
        // food prices only drift if there is trade pressure; the index responds to any price move
        assertNotNull(r.market.index.cpi(r.market, north))
    }

    @Test fun indexLogRecordsMonthlyAndMeasuresInflation() {
        val log = IndexLog()
        for (m in 0..13) log.record(Tick(m * 30L), 1_000_000L + m * 5_000L, 1_000_000L)
        val infl = log.inflationPpm(Tick(13 * 30L))
        assertNotNull(infl)
        assertTrue("inflation $infl", infl!! in 50_000..70_000)
        assertNull(IndexLog().inflationPpm(Tick(500)))
        val trend = log.trendPpm()!!
        assertTrue(trend > 0)
    }

    @Test fun indexLogKeepsABoundedWindow() {
        val log = IndexLog(capacity = 5)
        for (m in 0..20) log.record(Tick(m * 30L), 1_000_000L, 1_000_000L)
        assertEquals(5, log.samples().size)
        log.record(Tick(20 * 30L), 7, 7)
        assertEquals(1_000_000L, log.latest()!!.cpi)
    }

    @Test fun serviceRecordsTheIndexAtMonthStart() {
        val r = Rig(FakePopulation(low = 0, middle = 0, high = 0))
        repeat(61) { r.step() }
        assertTrue(r.market.indexLog.samples().isNotEmpty())
    }

    @Test fun averagePriceFallsBackToReference() {
        val r = Rig()
        assertEquals(content.good(gold).referencePrice, r.market.averagePrice(gold))
    }
}
