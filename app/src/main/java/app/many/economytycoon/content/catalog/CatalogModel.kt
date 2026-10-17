package app.many.economytycoon.content.catalog

import app.many.economytycoon.content.IndustryFamily

/** Lowest, middle and highest value of a set of margins, in parts per million of cost. */
data class MarginStats(val count: Int, val minPpm: Long, val medianPpm: Long, val maxPpm: Long) {
    companion object {
        val NONE = MarginStats(0, 0L, 0L, 0L)

        /** Median of an even count is the lower of the two middle values so the result is always one of the inputs. */
        fun of(marginsPpm: List<Long>): MarginStats {
            if (marginsPpm.isEmpty()) return NONE
            val sorted = marginsPpm.sorted()
            return MarginStats(sorted.size, sorted.first(), sorted[(sorted.size - 1) / 2], sorted.last())
        }
    }
}

/** One industry of the catalog with everything that belongs to it counted. */
data class IndustryEntry(
    val key: String,
    val name: String,
    val family: IndustryFamily,
    val packId: String,
    val goods: Int,
    val recipes: Int,
    val facilityTypes: Int,
    val machines: Int,
    val techs: Int,
    val margins: MarginStats,
    /** Shortest payback in days among the facility types this industry's recipes run in; null when none earns back. */
    val bestPaybackDays: Long?,
    /** Longest payback in days among the same facility types; null when none earns back. */
    val worstPaybackDays: Long?,
    val goodKeys: List<String>,
    val recipeKeys: List<String>,
    val facilityKeys: List<String>,
    val machineKeys: List<String>,
    val techKeys: List<String>,
)

/** Totals for one content pack. */
data class PackEntry(
    val packId: String,
    val version: String,
    val industries: Int,
    val goods: Int,
    val recipes: Int,
    val facilityTypes: Int,
    val machines: Int,
    val techs: Int,
    val demandProfiles: Int,
    val margins: MarginStats,
)

/** The whole catalog: industries sorted by pack order then key, packs in the order given. */
data class CatalogReport(val packs: List<PackEntry>, val industries: List<IndustryEntry>) {
    val totalGoods: Int get() = packs.sumOf { it.goods }
    val totalRecipes: Int get() = packs.sumOf { it.recipes }
    val totalFacilityTypes: Int get() = packs.sumOf { it.facilityTypes }
    val totalTechs: Int get() = packs.sumOf { it.techs }

    fun industry(key: String): IndustryEntry? = industries.firstOrNull { it.key == key }
    fun pack(packId: String): PackEntry? = packs.firstOrNull { it.packId == packId }
    fun industriesOf(packId: String): List<IndustryEntry> = industries.filter { it.packId == packId }
    fun industriesIn(family: IndustryFamily): List<IndustryEntry> = industries.filter { it.family == family }
}
