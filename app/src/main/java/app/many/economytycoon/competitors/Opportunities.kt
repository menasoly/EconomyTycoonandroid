package app.many.economytycoon.competitors

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.mulDiv

/** A recipe that looks worth running in a region, with its expected economics for one line. */
class Opportunity(
    val recipe: RecipeDef,
    val facilityType: FacilityTypeId,
    val region: RegionId,
    /** Revenue minus input, labor and energy cost of one batch. */
    val marginPerBatch: Money,
    val profitPerTick: Money,
    val capital: Money,
    /** Ticks for one line's profit to repay the building; Long.MAX_VALUE when it never does. */
    val paybackTicks: Long,
    val scarcityPpm: Long,
) {
    val profitable: Boolean get() = marginPerBatch.raw > 0
}

/** Prices every recipe against current market conditions. */
class OpportunityScanner(private val content: ContentRepository, private val market: MarketOutlook) {

    fun batchMargin(recipe: RecipeDef, region: RegionId): Money {
        var revenue = 0L
        var scarcity = 0L
        for (o in recipe.outputs) {
            revenue += mulDiv(market.price(region, o.good).perUnit.raw, o.quantity.raw, 1_000L)
            scarcity = maxOf(scarcity, market.scarcityPpm(region, o.good))
        }
        var cost = 0L
        for (i in recipe.inputs) {
            if (recipe.catalytic && recipe.outputs.any { it.good == i.good }) continue
            cost += mulDiv(market.price(region, i.good).perUnit.raw, i.quantity.raw, 1_000L)
        }
        for (l in recipe.labor) cost += market.wage(l.role, region).raw * l.headcount * recipe.durationTicks
        cost += mulDiv(market.energyPrice(region).raw, recipe.energyPerBatch.raw, 1_000L)
        return Money(revenue - cost)
    }

    fun evaluate(recipe: RecipeDef, region: RegionId): Opportunity {
        val type = content.facilityTypeOrNull(recipe.facilityType)
        val margin = batchMargin(recipe, region)
        val perTick = Money(margin.raw / recipe.durationTicks.coerceAtLeast(1))
        val capital = type?.buildCost ?: Money.ZERO
        val upkeep = type?.upkeepPerTick?.raw ?: 0L
        val net = perTick.raw - upkeep
        val payback = if (net <= 0L) Long.MAX_VALUE else (capital.raw + net - 1) / net
        val scarcity = recipe.outputs.maxOf { market.scarcityPpm(region, it.good) }
        return Opportunity(recipe, recipe.facilityType, region, margin, Money(net), capital, payback, scarcity)
    }

    /** All recipes the company may run, best payback first; unprofitable ones are dropped. */
    fun scan(regions: List<RegionId>, allowed: (RecipeDef) -> Boolean, types: Set<FacilityTypeId>): List<Opportunity> {
        val out = ArrayList<Opportunity>()
        for (recipe in content.allRecipes) {
            if (!allowed(recipe)) continue
            if (recipe.facilityType !in types) continue
            for (r in regions) {
                val o = evaluate(recipe, r)
                if (o.profitable && o.paybackTicks != Long.MAX_VALUE) out += o
            }
        }
        return out.sortedWith(
            compareBy<Opportunity> { it.paybackTicks - it.scarcityPpm / 10_000L }
                .thenBy { it.recipe.id }.thenBy { it.region },
        )
    }

    /** The best recipe for a facility type in a region, or null if none makes money. */
    fun bestRecipe(type: FacilityTypeId, region: RegionId, allowed: (RecipeDef) -> Boolean): RecipeDef? =
        content.recipesForFacility(type).filter(allowed)
            .map { it to evaluate(it, region) }
            .filter { it.second.profitable }
            .maxWithOrNull(compareBy<Pair<RecipeDef, Opportunity>> { it.second.profitPerTick.raw }.thenByDescending { it.first.id })
            ?.first
}
