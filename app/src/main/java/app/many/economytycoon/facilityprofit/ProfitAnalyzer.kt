package app.many.economytycoon.facilityprofit

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.content.audit.RecipeEconomics
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.production.Facility
import app.many.economytycoon.simulation.Economy

/** Where prices come from, so the analysis can run on live markets or on fixed test prices. */
fun interface PriceSource {
    fun price(region: RegionId, good: GoodId): Money

    companion object {
        fun live(e: Economy) = PriceSource { region, good -> e.markets.price(MarketKey(region, good)).perUnit }
        fun reference(content: ContentRepository) = PriceSource { _, good -> RecipeEconomics.priceOf(content, good) }
    }
}

/** Reads what each facility's lines earn at today's prices and where a line could earn more. */
class ProfitAnalyzer(private val e: Economy, private val prices: PriceSource = PriceSource.live(e)) {
    private val content get() = e.content

    fun recipeEconomics(r: RecipeDef, region: RegionId): LinePerformance =
        line(null, r, region, enabled = true, utilisationPpm = 1_000_000L)

    private fun line(id: app.many.economytycoon.core.LineId?, r: RecipeDef, region: RegionId, enabled: Boolean, utilisationPpm: Long): LinePerformance {
        fun worth(a: app.many.economytycoon.content.GoodAmount) = prices.price(region, a.good).valueOf(a.quantity)
        val inputs = if (r.catalytic) r.inputs.filter { inp -> r.outputs.none { it.good == inp.good } } else r.inputs
        val labour = Money(r.labor.sumOf { l -> content.role(l.role).baseWage.raw * l.headcount * r.durationTicks })
        val energy = prices.price(region, RecipeEconomics.ELECTRICITY).valueOf(r.energyPerBatch)
        return LinePerformance(
            line = id ?: app.many.economytycoon.core.LineId(0L), recipe = r.id, recipeName = r.name, enabled = enabled, utilisationPpm = utilisationPpm,
            outputValue = Money(r.outputs.sumOf { worth(it).raw }), inputCost = Money(inputs.sumOf { worth(it).raw }), labour = labour, energy = energy,
            defectPpm = r.baseDefectPpm, durationDays = r.durationTicks,
        )
    }

    fun facility(f: Facility): FacilityPerformance {
        val type = content.facilityTypeOrNull(f.type)
        val lines = f.lines.sortedBy { it.priority }.map { l ->
            val r = l.recipe?.let { content.recipeOrNull(it) }
            if (r == null) LinePerformance(l.id, null, null, l.enabled, l.utilizationPpm, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, 0L, 1)
            else line(l.id, r, f.region, l.enabled, l.utilizationPpm)
        }
        val overhead = type?.overheadStaff?.sumOf { content.role(it.role).baseWage.raw * it.headcount } ?: 0L
        return FacilityPerformance(
            facility = f.id,
            name = f.name.ifBlank { type?.name ?: f.type.key },
            typeName = type?.name ?: f.type.key,
            regionName = e.world.region(f.region).name,
            lines = lines,
            upkeepPerDay = type?.upkeepPerTick ?: Money.ZERO,
            overheadPerDay = Money(overhead),
            capital = type?.buildCost ?: Money.ZERO,
        )
    }

    /** Lines whose recipe could be swapped for a more profitable one the facility can run. */
    fun switches(f: Facility, perf: FacilityPerformance, minGain: Money = Money.units(1)): List<Switch> {
        val type = content.facilityTypeOrNull(f.type) ?: return emptyList()
        val options = content.recipesForFacility(type.id)
            .filter { it.requiredTech == null || e.research.isUnlocked(f.owner, it.requiredTech!!) }
            .map { r -> recipeEconomics(r, f.region) }
        val out = ArrayList<Switch>()
        for (lp in perf.lines) {
            if (lp.line.value == 0L) continue
            val best = options.maxByOrNull { it.potentialPerDay.raw } ?: continue
            val gain = best.potentialPerDay - lp.potentialPerDay
            if (best.recipe != lp.recipe && gain >= minGain) out += Switch(f.id, lp.line, lp.recipeName, best.recipe!!, best.recipeName ?: best.recipe.key, gain)
        }
        return out
    }

    fun portfolio(company: CompanyId): PortfolioPerformance {
        val owned = e.production.facilitiesOf(company)
        val perfs = owned.map { facility(it) }
        val switches = owned.flatMapIndexed { i, f -> switches(f, perfs[i]) }.sortedByDescending { it.gainPerDay.raw }
        return PortfolioPerformance(perfs, switches)
    }
}
