package app.many.economytycoon.content.audit

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.FacilityTypeDef

/** How long a facility takes to earn back what it cost, at reference prices and with every line busy. */
data class Payback(
    val facility: FacilityTypeDef,
    /** The recipe that earns most per line-day at this facility, if any recipe runs there. */
    val bestRecipe: RecipeEconomy?,
    /** Money raw per day from all lines of the best recipe, before upkeep and overhead staff. */
    val grossPerDay: Long,
    val upkeepPerDay: Long,
    val overheadPerDay: Long,
    val buildCost: Long,
) {
    val netPerDay: Long get() = grossPerDay - upkeepPerDay - overheadPerDay

    /** Days to earn back the build cost; null when the facility never does. */
    val paybackDays: Long? get() = if (netPerDay <= 0L) null else (buildCost + netPerDay - 1L) / netPerDay
}

object FacilityPayback {
    fun of(content: ContentRepository, f: FacilityTypeDef, economics: Map<String, RecipeEconomy>): Payback {
        val candidates = content.recipesForFacility(f.id).mapNotNull { economics[it.id.key] }
        val best = candidates.maxByOrNull { it.profitPerLineDay }
        val gross = if (best == null) 0L else best.profitPerLineDay * f.maxLines
        val overhead = f.overheadStaff.sumOf { content.role(it.role).baseWage.raw * it.headcount }
        return Payback(f, best, gross, f.upkeepPerTick.raw, overhead, f.buildCost.raw)
    }

    fun all(content: ContentRepository, economics: List<RecipeEconomy>): List<Payback> {
        val byId = economics.associateBy { it.recipe.id.key }
        return content.allFacilityTypes.map { of(content, it, byId) }
    }
}
