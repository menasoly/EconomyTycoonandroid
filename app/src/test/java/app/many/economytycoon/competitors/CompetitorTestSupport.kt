package app.many.economytycoon.competitors

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.Department
import app.many.economytycoon.companies.DividendPolicy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.FacilityClass
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.content.contentPack
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick

object CompetitorTestSupport {
    val region = RegionId(1)
    val region2 = RegionId(2)
    val company = CompanyId(7)
    val bakery = FacilityTypeId("bakery")
    val flour = GoodId("flour"); val wheat = GoodId("wheat"); val bread = GoodId("bread")
    val baker = RoleId("baker")

    val content: ContentRepository by lazy {
        ContentRepository.of(listOf(contentPack("ai-test", 1, 0) {
            industry("baking", "Baking", IndustryFamily.FOOD)
            good("wheat", "Wheat", GoodCategory.RAW, 10.0)
            good("flour", "Flour", GoodCategory.INTERMEDIATE, 25.0)
            good("bread", "Bread", GoodCategory.CONSUMER, 40.0)
            role("baker", "Baker", SkillBand.SKILLED, 5.0)
            facility("bakery", "Bakery", FacilityClass.FACTORY, IndustryFamily.FOOD) { cost = 50_000.0; buildTicks = 4; lines = 3; storage = 1_000.0; upkeep = 2.0 }
            recipe("make_flour", "Mill flour", "baking", "bakery", 2) { input("wheat", 4.0); output("flour", 3.0); labor("baker", 2) }
            recipe("make_bread", "Bake bread", "baking", "bakery", 1) { input("flour", 2.0); output("bread", 4.0); labor("baker", 1) }
        }))
    }

    class Outlook : MarketOutlook {
        val multiplier = HashMap<GoodId, Long>()
        val scarcity = HashMap<GoodId, Long>()
        var unemployment = 80_000L
        override fun price(region: RegionId, good: GoodId): Price {
            val base = content.good(good).referencePrice.perUnit.raw
            return Price(Money(base * (multiplier[good] ?: 1_000_000L) / 1_000_000L))
        }
        override fun scarcityPpm(region: RegionId, good: GoodId) = scarcity[good] ?: 1_000_000L
        override fun wage(role: RoleId, region: RegionId) = content.role(role).baseWage
        override fun unemploymentPpm(region: RegionId) = unemployment
        override fun energyPrice(region: RegionId) = Money.units(2)
    }

    fun line(id: Long, recipe: String?, util: Long = 500_000L, enabled: Boolean = true, blocked: String = "none") =
        LineView(LineId(id), recipe?.let { RecipeId(it) }, enabled, blocked, util)

    fun facility(
        id: Long, lines: List<LineView>, profit: Long = 100, age: Long = 200, stock: Map<GoodId, Quantity> = emptyMap(),
        operating: Boolean = true, region: RegionId = CompetitorTestSupport.region, maxLines: Int = 3,
    ) = FacilityView(FacilityId(id), bakery, region, operating, lines, maxLines, stock, Quantity.units(1_000), Money.units(profit), age)

    fun situation(
        cash: Long = 1_000_000, debt: Long = 0, credit: Long = 0, opex: Long = 1_000, revenue: Long = 5_000,
        facilities: List<FacilityView> = emptyList(), tick: Long = 100, pending: Int = 0, targets: List<TargetView> = emptyList(),
        sells: Map<Pair<FacilityId, GoodId>, SellRule> = emptyMap(), buys: Map<Pair<FacilityId, GoodId>, BuyRule> = emptyMap(),
        budgets: Map<Department, Money> = emptyMap(), dividend: DividendPolicy = DividendPolicy(),
        staff: Map<Pair<FacilityId, RoleId>, Long> = emptyMap(), slots: Int = 0, ranked: List<TechId> = emptyList(),
        recipes: Set<RecipeId> = setOf(RecipeId("make_flour"), RecipeId("make_bread")),
        types: Set<FacilityTypeId> = setOf(bakery), homes: List<RegionId> = listOf(region),
    ) = Situation(
        company, Tick(tick), Money.units(cash), Money.units(debt), Money.units(credit), Money.units(opex), Money.units(revenue),
        facilities, sells, buys, budgets, dividend, staff, slots, ranked, recipes, types, targets, homes, pending,
    )
}
