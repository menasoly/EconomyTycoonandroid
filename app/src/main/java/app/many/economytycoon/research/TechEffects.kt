package app.many.economytycoon.research

import app.many.economytycoon.content.EffectKind
import app.many.economytycoon.content.FacilityTypeDef
import app.many.economytycoon.content.MachineTypeDef
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.content.TechDef
import app.many.economytycoon.content.VehicleTypeDef
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.production.ProductionModifiers

/** Reads what a company's finished technologies are worth right now. */
class TechEffects(private val research: ResearchService) {
    var now: Tick = Tick(0)

    /** Strength of a technology after ageing, ppm: full until it becomes obsolete, then fading to the floor. */
    fun strengthPpm(def: TechDef, unlockedAt: Tick, at: Tick = now): Long {
        val span = def.obsolescenceTicks.toLong()
        if (span <= 0L) return 1_000_000L
        val age = at - unlockedAt
        if (age <= span) return 1_000_000L
        val floor = research.params.obsoleteFloorPpm
        val faded = (age - span).coerceAtMost(span)
        return 1_000_000L - (1_000_000L - floor) * faded / span
    }

    private fun matches(effectIndustry: IndustryId?, effectRecipe: app.many.economytycoon.core.RecipeId?, recipe: RecipeDef): Boolean =
        if (effectRecipe != null) effectRecipe == recipe.id else effectIndustry == null || effectIndustry == recipe.industry

    /** Sum of the matching effects of one kind, scaled by ageing and capped. */
    fun total(company: CompanyId, kind: EffectKind, recipe: RecipeDef?): Long {
        val state = research.peek(company) ?: return 0L
        var sum = 0L
        for ((tech, at) in state.unlocked) {
            val def = research.content.technologyOrNull(tech) ?: continue
            val strength = strengthPpm(def, at)
            for (e in def.effects) {
                if (e.kind != kind) continue
                if (recipe != null && !matches(e.industry, e.recipe, recipe)) continue
                if (recipe == null && (e.industry != null || e.recipe != null)) continue
                sum += e.magnitude.ppm * strength / 1_000_000L
            }
        }
        return sum.coerceAtMost(capFor(kind))
    }

    private fun capFor(kind: EffectKind): Long = when (kind) {
        EffectKind.YIELD_BONUS, EffectKind.THROUGHPUT_BONUS, EffectKind.STORAGE_BONUS -> research.params.bonusCapPpm
        else -> research.params.savingsCapPpm
    }

    fun recipeAvailable(company: CompanyId, recipe: RecipeDef): Boolean =
        recipe.requiredTech == null || research.isUnlocked(company, recipe.requiredTech!!) || unlockedByAny(company) { it.unlocksRecipes.contains(recipe.id) }

    fun facilityAvailable(company: CompanyId, type: FacilityTypeDef): Boolean =
        type.requiredTech == null || research.isUnlocked(company, type.requiredTech!!) || unlockedByAny(company) { it.unlocksFacilities.contains(type.id) }

    fun machineAvailable(company: CompanyId, type: MachineTypeDef): Boolean =
        type.requiredTech == null || research.isUnlocked(company, type.requiredTech!!) || unlockedByAny(company) { it.unlocksMachines.contains(type.id) }

    fun vehicleAvailable(company: CompanyId, type: VehicleTypeDef): Boolean =
        type.requiredTech == null || research.isUnlocked(company, type.requiredTech!!)

    private inline fun unlockedByAny(company: CompanyId, test: (TechDef) -> Boolean): Boolean {
        val state = research.peek(company) ?: return false
        return state.unlocked.keys.any { t -> research.content.technologyOrNull(t)?.let(test) == true }
    }

    /** Reduction of freight spending from technology, ppm. */
    fun transportSavings(company: CompanyId): Rate = Rate(total(company, EffectKind.TRANSPORT_SAVINGS, null))

    /** Extra storage from technology, ppm. */
    fun storageBonus(company: CompanyId): Rate = Rate(total(company, EffectKind.STORAGE_BONUS, null))
}

/** Plugs finished technologies into the production engine. */
class ResearchModifiers(private val effects: TechEffects) : ProductionModifiers {
    override fun throughputBonus(owner: CompanyId, recipe: RecipeDef) = Rate(effects.total(owner, EffectKind.THROUGHPUT_BONUS, recipe))
    override fun yieldBonus(owner: CompanyId, recipe: RecipeDef) = Rate(effects.total(owner, EffectKind.YIELD_BONUS, recipe))
    override fun inputSavings(owner: CompanyId, recipe: RecipeDef) = Rate(effects.total(owner, EffectKind.INPUT_SAVINGS, recipe))
    override fun energySavings(owner: CompanyId, recipe: RecipeDef) = Rate(effects.total(owner, EffectKind.ENERGY_SAVINGS, recipe))
    override fun qualityBonus(owner: CompanyId, recipe: RecipeDef) = Rate(effects.total(owner, EffectKind.QUALITY_BONUS, recipe))
    override fun laborSavings(owner: CompanyId, recipe: RecipeDef) = Rate(effects.total(owner, EffectKind.LABOR_SAVINGS, recipe))
    override fun storageBonus(owner: CompanyId) = effects.storageBonus(owner)
}
