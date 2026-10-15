package app.many.economytycoon.markets

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.IncomeBand
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.VolatilityClass
import app.many.economytycoon.content.contentPack
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

object MarketTestSupport {
    val bread = GoodId("bread"); val flour = GoodId("flour"); val salt = GoodId("salt"); val gold = GoodId("gold")
    val north = RegionId(1); val south = RegionId(2)
    val alice = Party.company(CompanyId(1)); val bob = Party.company(CompanyId(2)); val carol = Party.company(CompanyId(3))

    val content: ContentRepository by lazy {
        ContentRepository.of(listOf(contentPack("markettest", 1, 0) {
            industry("food", "Food", IndustryFamily.FOOD)
            good("bread", "Bread", GoodCategory.CONSUMER, 40.0, VolatilityClass.NORMAL)
            good("salt", "Salt", GoodCategory.CONSUMER, 5.0, VolatilityClass.STABLE)
            good("flour", "Flour", GoodCategory.INTERMEDIATE, 25.0, VolatilityClass.NORMAL)
            good("gold", "Gold", GoodCategory.RAW, 1000.0, VolatilityClass.SPECULATIVE)
            good("loose", "Untradable", GoodCategory.SERVICE, 10.0, tradable = false)
            demand("bread", low = 800, middle = 1000, high = 1200, elasticity = 0.8)
            demand("salt", low = 100, middle = 100, high = 100, elasticity = 0.1, incomeElasticity = 0.2)
        }))
    }

    class FakePopulation(
        private val regions: List<RegionId> = listOf(north),
        var low: Long = 1_000, var middle: Long = 1_000, var high: Long = 1_000,
    ) : PopulationView {
        override fun regions() = regions
        override fun consumers(region: RegionId, band: IncomeBand): Long = when (band) {
            IncomeBand.LOW -> low; IncomeBand.MIDDLE -> middle; IncomeBand.HIGH -> high
        }
    }

    fun price(units: Double) = Price(Money((units * 10_000).toLong()))
    fun qty(units: Double) = Quantity((units * 1000).toLong())

    class Rig(val population: PopulationView = FakePopulation(), seed: Long = 5) {
        val ids = IdAllocators()
        val market = MarketService(ids, content, population)
        val random = RandomProvider(seed)
        var tick = 0L

        fun step(budget: (RegionId) -> Money? = { null }): MarketReport { tick++; return market.step(Tick(tick), random, budget) }

        fun buy(party: Party, good: GoodId, units: Double, limit: Double?, ttl: Int = 10, region: RegionId = north) =
            market.placeOrder(party, MarketKey(region, good), Side.BUY, limit?.let { price(it) }, qty(units), ttl, 0, Tick(tick)).getOrThrow()

        fun sell(party: Party, good: GoodId, units: Double, limit: Double?, ttl: Int = 10, region: RegionId = north, source: Long = 0) =
            market.placeOrder(party, MarketKey(region, good), Side.SELL, limit?.let { price(it) }, qty(units), ttl, source, Tick(tick)).getOrThrow()

        fun hash(): String = CanonicalHasher().also { market.hashInto(it) }.finish().toHex()
    }
}
