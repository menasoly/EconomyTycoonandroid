package app.many.economytycoon.ui.present

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.catalog.CatalogFormat
import app.many.economytycoon.content.catalog.CatalogReport
import app.many.economytycoon.content.catalog.IndustryEntry
import app.many.economytycoon.session.Formatting

data class CatalogRow(val key: String, val title: String, val subtitle: String, val margin: String, val payback: String, val tone: Tone)

data class CatalogFilter(val packId: String? = null, val family: IndustryFamily? = null, val query: String = "")

data class CatalogModel(val tiles: List<Tile>, val packs: List<String>, val families: List<String>, val rows: List<CatalogRow>, val empty: String)

data class CatalogDetail(val title: String, val tiles: List<Tile>, val sections: List<CatalogSection>)

data class CatalogSection(val heading: String, val items: List<String>)

/** Why a detail page could not be shown. */
sealed class CatalogLookup {
    data class Found(val detail: CatalogDetail) : CatalogLookup()
    data class Missing(val key: String) : CatalogLookup()
}

/** Browsing the industries of the content: filter by pack, family or name, then open one for its goods, recipes and technologies. */
object IndustryCatalogPresenter {
    const val EMPTY = "No industry matches. Clear the filter to see them all."

    /** A median margin under 5% of cost reads as weak, under 10% as thin, anything above as healthy. */
    fun marginTone(medianPpm: Long, count: Int): Tone = when {
        count == 0 -> Tone.MUTED
        medianPpm < 50_000L -> Tone.BAD
        medianPpm < 100_000L -> Tone.WARN
        else -> Tone.GOOD
    }

    fun matches(e: IndustryEntry, f: CatalogFilter): Boolean {
        if (f.packId != null && e.packId != f.packId) return false
        if (f.family != null && e.family != f.family) return false
        val q = f.query.trim().lowercase()
        return q.isEmpty() || e.name.lowercase().contains(q) || e.key.contains(q.replace(' ', '_'))
    }

    fun row(e: IndustryEntry): CatalogRow = CatalogRow(
        key = e.key,
        title = e.name,
        subtitle = "${e.family.displayName}, ${Formatting.plural(e.goods.toLong(), "good")}, ${Formatting.plural(e.recipes.toLong(), "recipe")}, ${Formatting.plural(e.techs.toLong(), "technology")}",
        margin = CatalogFormat.margin(e.margins),
        payback = CatalogFormat.payback(e.bestPaybackDays, e.worstPaybackDays),
        tone = marginTone(e.margins.medianPpm, e.margins.count),
    )

    fun build(report: CatalogReport, filter: CatalogFilter = CatalogFilter()): CatalogModel {
        val rows = report.industries.filter { matches(it, filter) }.map { row(it) }
        return CatalogModel(
            tiles = listOf(
                Tile("Industries", report.industries.size.toString()),
                Tile("Goods", report.totalGoods.toString()),
                Tile("Recipes", report.totalRecipes.toString()),
                Tile("Facilities", report.totalFacilityTypes.toString()),
                Tile("Technologies", report.totalTechs.toString()),
            ),
            packs = report.packs.map { it.packId },
            families = report.industries.map { it.family }.distinct().sortedBy { it.ordinal }.map { it.displayName },
            rows = rows,
            empty = if (rows.isEmpty()) EMPTY else "",
        )
    }

    fun detail(report: CatalogReport, key: String): CatalogLookup {
        val e = report.industry(key) ?: return CatalogLookup.Missing(key)
        return CatalogLookup.Found(
            CatalogDetail(
                title = e.name,
                tiles = listOf(
                    Tile("Pack", e.packId),
                    Tile("Family", e.family.displayName),
                    Tile("Margin", CatalogFormat.margin(e.margins), marginTone(e.margins.medianPpm, e.margins.count), "lowest / middle / highest"),
                    Tile("Payback", CatalogFormat.payback(e.bestPaybackDays, e.worstPaybackDays), Tone.INFO),
                ),
                sections = listOf(
                    CatalogSection("Goods", e.goodKeys.map { Formatting.title(it) }),
                    CatalogSection("Recipes", e.recipeKeys.map { Formatting.title(it) }),
                    CatalogSection("Facilities", e.facilityKeys.map { Formatting.title(it) }),
                    CatalogSection("Machines", e.machineKeys.map { Formatting.title(it) }),
                    CatalogSection("Technologies", e.techKeys.map { Formatting.title(it) }),
                ).filter { it.items.isNotEmpty() },
            ),
        )
    }
}
