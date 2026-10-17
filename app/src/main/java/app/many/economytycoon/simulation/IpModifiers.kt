package app.many.economytycoon.simulation

import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Rate
import app.many.economytycoon.ip.ProductEffects
import app.many.economytycoon.production.ProductionModifiers

/**
 * Production modifiers with what patents, licences and trade secrets mean for a recipe added on top of [base]: a recipe
 * that needs a technology costs more or less to run by the company's rights in it, and is stopped altogether while a court
 * order bars the company from using it. A recipe with no required technology, or any recipe in a game with no patent
 * office, gets exactly what [base] gives.
 */
class IpModifiers(private val base: ProductionModifiers, private val e: Economy) : ProductionModifiers by base {
    private var cachedTick = -1L
    private val cache = HashMap<Pair<CompanyId, app.many.economytycoon.core.RecipeId>, ProductEffects?>()

    private fun effects(owner: CompanyId, recipe: RecipeDef): ProductEffects? {
        if (!e.ipActive) return null
        val tech = recipe.requiredTech ?: return null
        if (e.tick.value != cachedTick) { cache.clear(); cachedTick = e.tick.value }
        return cache.getOrPut(owner to recipe.id) {
            val country = e.companies.company(owner)?.country
            if (country == null) null else e.ip.effects.effects(owner, tech, country, null, e.tick)
        }
    }

    override fun inputSavings(owner: CompanyId, recipe: RecipeDef): Rate {
        val saved = base.inputSavings(owner, recipe)
        val fx = effects(owner, recipe) ?: return saved
        return Rate(saved.ppm - fx.costDeltaPpm)
    }

    override fun qualityBonus(owner: CompanyId, recipe: RecipeDef): Rate {
        val bonus = base.qualityBonus(owner, recipe)
        val fx = effects(owner, recipe) ?: return bonus
        return Rate(bonus.ppm + fx.qualityDeltaPpm)
    }

    override fun throughputBonus(owner: CompanyId, recipe: RecipeDef): Rate {
        val fx = effects(owner, recipe)
        return if (fx != null && fx.blocked) Rate(-Rate.ONE_PPM) else base.throughputBonus(owner, recipe)
    }
}
