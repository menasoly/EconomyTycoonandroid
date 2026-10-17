package app.many.economytycoon.content.catalog

import app.many.economytycoon.session.Formatting

/** Plain-text rendering of a [CatalogReport], one stable line per row so reports can be compared. */
object CatalogFormat {
    fun margin(stats: MarginStats): String =
        if (stats.count == 0) "n/a" else "${Formatting.percentPpm(stats.minPpm)} / ${Formatting.percentPpm(stats.medianPpm)} / ${Formatting.percentPpm(stats.maxPpm)}"

    fun payback(best: Long?, worst: Long?): String = when {
        best == null || worst == null -> "n/a"
        best == worst -> "${best}d"
        else -> "${best}-${worst}d"
    }

    fun packLine(p: PackEntry): String =
        "${p.packId.padEnd(22)} ind ${p.industries.toString().padStart(2)}  goods ${p.goods.toString().padStart(3)}  recipes ${p.recipes.toString().padStart(3)}" +
            "  facilities ${p.facilityTypes.toString().padStart(3)}  machines ${p.machines.toString().padStart(2)}  techs ${p.techs.toString().padStart(3)}  margin ${margin(p.margins)}"

    fun industryLine(e: IndustryEntry): String =
        "${e.key.padEnd(30)} ${e.family.displayName.padEnd(16)} goods ${e.goods.toString().padStart(3)}  recipes ${e.recipes.toString().padStart(3)}" +
            "  facilities ${e.facilityTypes.toString().padStart(2)}  techs ${e.techs.toString().padStart(2)}  margin ${margin(e.margins)}  payback ${payback(e.bestPaybackDays, e.worstPaybackDays)}"

    fun text(report: CatalogReport): String = buildString {
        appendLine("Content packs")
        report.packs.forEach { appendLine(packLine(it)) }
        appendLine()
        appendLine("Industries")
        report.industries.forEach { appendLine(industryLine(it)) }
        appendLine()
        append("Total: ${report.totalGoods} goods, ${report.totalRecipes} recipes, ${report.totalFacilityTypes} facility types, ${report.totalTechs} techs")
    }

    fun csv(report: CatalogReport): String = buildString {
        appendLine("industry,pack,family,goods,recipes,facilities,machines,techs,margin_min_ppm,margin_median_ppm,margin_max_ppm,payback_best,payback_worst")
        for (e in report.industries) {
            appendLine(listOf(e.key, e.packId, e.family.name, e.goods, e.recipes, e.facilityTypes, e.machines, e.techs, e.margins.minPpm, e.margins.medianPpm, e.margins.maxPpm, e.bestPaybackDays ?: "", e.worstPaybackDays ?: "").joinToString(","))
        }
    }.trimEnd()
}
