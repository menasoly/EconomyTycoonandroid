package app.many.economytycoon.research

import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.content.TechDef
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Tick

/** How much a company values each research category, ppm weights. Missing categories count as neutral. */
class ResearchAgenda(val weights: Map<TechCategory, Long> = emptyMap(), val maxTier: Int = 99) {
    fun weight(c: TechCategory): Long = weights[c] ?: 1_000_000L
}

/**
 * Picks technologies for AI companies: the most useful per point spent among those whose prerequisites
 * are met, with ties settled by id so every run chooses the same thing.
 */
object ResearchPlanner {
    /** Usefulness of a technology: its summed effects plus a share for what it unlocks and what it opens up. */
    fun value(def: TechDef, agenda: ResearchAgenda, opensUp: Int): Long {
        val effect = def.effects.sumOf { it.magnitude.ppm }
        val unlocks = (def.unlocksRecipes.size + def.unlocksFacilities.size + def.unlocksMachines.size) * 150_000L
        val gateway = opensUp * 100_000L
        return (effect + unlocks + gateway + 50_000L) * agenda.weight(def.category) / 1_000_000L
    }

    fun rank(service: ResearchService, company: CompanyId, agenda: ResearchAgenda): List<TechDef> {
        val all = service.content.allTechnologies
        val dependents = HashMap<app.many.economytycoon.core.TechId, Int>()
        for (t in all) for (p in t.prerequisites) dependents[p] = (dependents[p] ?: 0) + 1
        return service.available(company)
            .filter { it.tier <= agenda.maxTier }
            .sortedWith(
                compareByDescending<TechDef> { value(it, agenda, dependents[it.id] ?: 0) * 1_000L / service.costPoints(it) }
                    .thenBy { it.id },
            )
    }

    /** Fills free research slots with the best available choices. Returns what was started. */
    fun fillSlots(service: ResearchService, company: CompanyId, agenda: ResearchAgenda, tick: Tick): List<TechDef> {
        val started = ArrayList<TechDef>()
        for (def in rank(service, company, agenda)) {
            val s = service.of(company)
            if (s.active.size >= service.params.maxConcurrent) break
            if (service.start(company, def.id, tick).isOk) started += def
        }
        return started
    }
}
