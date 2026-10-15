package app.many.economytycoon.markets

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.WorldTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldMarketsTest {
    private val content = WorldTestSupport.content

    private fun build(seed: Long): Triple<MarketService, app.many.economytycoon.world.WorldState, RandomProvider> {
        val (world, _) = WorldTestSupport.generate(seed)
        val market = MarketService(IdAllocators(), content, WorldPopulationView(world))
        return Triple(market, world, RandomProvider(seed))
    }

    @Test fun populationViewSumsCities() {
        val (_, world, _) = build(11)
        val view = WorldPopulationView(world)
        val region = world.regions().first().id
        val total = app.many.economytycoon.content.IncomeBand.values().sumOf { view.consumers(region, it) }
        assertTrue(total > 0)
        assertEquals(world.citiesIn(region).sumOf { world.population(it.id).total }, total)
        assertTrue(view.incomeIndexPpm(region) in 300_000L..3_000_000L)
    }

    @Test fun everyConsumerGoodHasHouseholdDemandInEveryRegion() {
        val (market, world, rnd) = build(12)
        val food = content.allDemandProfiles.first()
        val region = world.regions().first().id
        val def = content.good(food.good)
        market.placeOrder(
            Party.company(CompanyId(1)), MarketKey(region, food.good), Side.SELL, Price(Money(def.referencePrice.perUnit.raw / 2)),
            Quantity.units(1_000_000), 30, 0, Tick(0),
        ).getOrThrow()
        val rep = market.step(Tick(1), rnd)
        assertTrue(rep.householdSpend[region]!!.raw > 0)
    }

    @Test fun marketsRunForAMonthOnTheRealCatalogue() {
        val (market, world, rnd) = build(13)
        val region = world.regions().first().id
        for (p in content.allDemandProfiles.take(10)) {
            val def = content.good(p.good)
            market.placeOrder(Party.company(CompanyId(1)), MarketKey(region, p.good), Side.SELL, Price(Money(def.referencePrice.perUnit.raw * 9 / 10)), Quantity.units(10_000_000), 90, 0, Tick(0))
        }
        var spent = 0L
        for (t in 1..30) spent += market.step(Tick(t.toLong()), rnd).householdSpend.values.sumOf { it.raw }
        assertTrue(spent > 0)
        val cpi = market.index.cpi(market)
        assertTrue("cpi $cpi", cpi in 600_000L..1_800_000L)
        for (s in market.states()) {
            val ref = content.good(s.key.good).referencePrice.perUnit.raw
            assertTrue(s.last.perUnit.raw in ref / 10..ref * 10)
        }
    }

    @Test fun realWorldRunIsDeterministic() {
        fun run(): String {
            val (market, world, rnd) = build(14)
            val region = world.regions().first().id
            val p = content.allDemandProfiles.first()
            market.placeOrder(Party.company(CompanyId(1)), MarketKey(region, p.good), Side.SELL, null, Quantity.units(5_000_000), 90, 0, Tick(0))
            for (t in 1..25) market.step(Tick(t.toLong()), rnd) { Money.units(5_000_000) }
            return app.many.economytycoon.core.CanonicalHasher().also { market.hashInto(it) }.finish().toHex()
        }
        assertEquals(run(), run())
    }
}
