package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyService
import app.many.economytycoon.competitors.MarketOutlook
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.macro.MacroView
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.markets.MarketService
import app.many.economytycoon.production.PriceView
import app.many.economytycoon.production.ResourceAccess
import app.many.economytycoon.research.ResearchFunding
import app.many.economytycoon.workforce.LaborMarkets
import app.many.economytycoon.workforce.WorkforceService
import app.many.economytycoon.world.WorldState
import app.many.economytycoon.workforce.MarketKey as LaborKey

/** Extraction from the world's natural deposits. */
class WorldResources(private val world: WorldState) : ResourceAccess {
    override fun extract(deposit: DepositId, good: GoodId, wanted: Quantity): Quantity {
        val d = world.deposits.get(deposit) ?: return Quantity.ZERO
        if (d.good != good) return Quantity.ZERO
        return d.extract(wanted)
    }
}

/** Production's view of prices: the going average across regions. */
class MarketPrices(private val markets: MarketService) : PriceView {
    override fun price(good: GoodId): Price = markets.averagePrice(good)
}

/** Research points are what a company's research department has accumulated. */
class DepartmentFunding(private val companies: CompanyService) : ResearchFunding {
    override fun available(company: CompanyId): Long = companies.progress(company).researchPoints
    override fun spend(company: CompanyId, points: Long): Boolean = companies.progress(company).spendResearch(points)
}

/** What the macro layer reads of the economy. */
class WorldMacroView(
    private val world: WorldState,
    private val markets: MarketService,
    private val clock: () -> Tick,
) : MacroView {
    override fun countries(): List<CountryId> = world.countries().map { it.id }.sorted()

    override fun inflationPpm(country: CountryId): Long =
        markets.indexLog.inflationPpm(clock()) ?: markets.indexLog.trendPpm() ?: 20_000L

    override fun unemploymentPpm(country: CountryId): Long {
        val cities = world.citiesOf(country)
        val labor = cities.sumOf { world.population(it.id).laborForce }
        if (labor <= 0L) return 0L
        val unemployed = cities.sumOf { world.population(it.id).unemployed }
        return mulDiv(unemployed, 1_000_000L, labor)
    }

    override fun population(country: CountryId): Long = world.populationOf(country)

    override fun averageWage(country: CountryId): Money {
        val cities = world.citiesOf(country)
        val heads = cities.sumOf { world.population(it.id).employed }
        if (heads <= 0L) return Money.ZERO
        return Money(cities.sumOf { world.population(it.id).averageWage.raw * world.population(it.id).employed } / heads)
    }
}

/** What AI companies see of prices, wages and scarcity. */
class WorldOutlook(
    private val content: ContentRepository,
    private val markets: MarketService,
    private val labor: LaborMarkets,
    private val world: WorldState,
    private val grid: Grid,
) : MarketOutlook {
    override fun price(region: RegionId, good: GoodId): Price = markets.price(MarketKey(region, good))

    override fun scarcityPpm(region: RegionId, good: GoodId): Long {
        val s = markets.state(MarketKey(region, good)) ?: return 1_000_000L
        val unmet = s.unmetDemand.raw; val unsold = s.unsoldSupply.raw
        val denom = s.lastVolume.raw + unmet + unsold + 1L
        return (1_000_000L + mulDiv(unmet - unsold, 1_000_000L, denom)).coerceIn(200_000L, 3_000_000L)
    }

    override fun wage(role: RoleId, region: RegionId): Money = labor.marketWage(LaborKey(region, role))

    override fun unemploymentPpm(region: RegionId): Long {
        val cities = world.citiesIn(region)
        val l = cities.sumOf { world.population(it.id).laborForce }
        if (l <= 0L) return 0L
        return mulDiv(cities.sumOf { world.population(it.id).unemployed }, 1_000_000L, l)
    }

    override fun energyPrice(region: RegionId): Money = grid.price(region)
}
