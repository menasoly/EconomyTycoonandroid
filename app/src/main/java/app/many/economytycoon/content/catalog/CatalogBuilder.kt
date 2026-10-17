package app.many.economytycoon.content.catalog

import app.many.economytycoon.content.ContentPack
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.audit.FacilityPayback
import app.many.economytycoon.content.audit.Payback
import app.many.economytycoon.content.audit.RecipeEconomics
import app.many.economytycoon.content.audit.RecipeEconomy

/**
 * Builds the catalog from content alone. It reads definitions and reference prices and nothing else, so the same
 * packs always give the same report.
 */
object CatalogBuilder {
    /** Catalog of [packs], limited to the packs named in [only] when that is not empty. */
    fun build(packs: List<ContentPack>, only: Set<String> = emptySet()): CatalogReport {
        val content = ContentRepository.of(packs)
        val economics = RecipeEconomics.all(content).associateBy { it.recipe.id.key }
        val paybacks = FacilityPayback.all(content, economics.values.toList()).associateBy { it.facility.id.key }
        val chosen = packs.filter { only.isEmpty() || it.version.packId in only }
        val packEntries = chosen.map { packEntry(it, economics) }
        val industryEntries = chosen.flatMap { pack -> pack.industries.sortedBy { it.id }.map { industryEntry(content, pack, it.id.key, economics, paybacks) } }
        return CatalogReport(packEntries, industryEntries)
    }

    private fun packEntry(pack: ContentPack, economics: Map<String, RecipeEconomy>): PackEntry = PackEntry(
        packId = pack.version.packId,
        version = pack.version.toString(),
        industries = pack.industries.size,
        goods = pack.goods.size,
        recipes = pack.recipes.size,
        facilityTypes = pack.facilityTypes.size,
        machines = pack.machineTypes.size,
        techs = pack.technologies.size,
        demandProfiles = pack.demandProfiles.size,
        margins = MarginStats.of(pack.recipes.mapNotNull { economics[it.id.key]?.marginPpm }),
    )

    private fun industryEntry(
        content: ContentRepository,
        pack: ContentPack,
        key: String,
        economics: Map<String, RecipeEconomy>,
        paybacks: Map<String, Payback>,
    ): IndustryEntry {
        val def = pack.industries.first { it.id.key == key }
        val recipes = content.allRecipes.filter { it.industry == def.id }
        val goodKeys = recipes.flatMap { r -> r.outputs.map { it.good.key } }.distinct().sorted()
        val facilityKeys = recipes.map { it.facilityType.key }.distinct().sorted()
        val machineKeys = recipes.flatMap { r -> r.machines.map { it.key } }.distinct().sorted()
        val techKeys = (recipes.mapNotNull { it.requiredTech?.key } +
            content.allTechnologies.filter { t -> t.unlocksRecipes.any { id -> recipes.any { it.id == id } } }.map { it.id.key }).distinct().sorted()
        val days = facilityKeys.mapNotNull { paybacks[it]?.paybackDays }
        return IndustryEntry(
            key = key,
            name = def.name,
            family = def.family,
            packId = pack.version.packId,
            goods = goodKeys.size,
            recipes = recipes.size,
            facilityTypes = facilityKeys.size,
            machines = machineKeys.size,
            techs = techKeys.size,
            margins = MarginStats.of(recipes.mapNotNull { economics[it.id.key]?.marginPpm }),
            bestPaybackDays = days.minOrNull(),
            worstPaybackDays = days.maxOrNull(),
            goodKeys = goodKeys,
            recipeKeys = recipes.map { it.id.key }.sorted(),
            facilityKeys = facilityKeys,
            machineKeys = machineKeys,
            techKeys = techKeys,
        )
    }
}
