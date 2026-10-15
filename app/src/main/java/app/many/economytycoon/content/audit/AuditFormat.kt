package app.many.economytycoon.content.audit

import app.many.economytycoon.core.Money

/** Plain-text rendering of an [AuditReport], for logs and for pasting into a bug report. */
object AuditFormat {
    private fun units(raw: Long): String = Money(raw).wholeUnits().toString()

    fun summary(r: AuditReport): String =
        "${r.recipes.size} recipes, ${r.paybacks.size} facility types: ${r.errors.size} errors, ${r.warnings.size} warnings, " +
            "${r.bySeverity(AuditSeverity.INFO).size} notes"

    fun recipeTable(r: AuditReport): String = buildString {
        appendLine("recipe".padEnd(34) + "cost".padStart(10) + "value".padStart(10) + "margin".padStart(9))
        for (e in r.recipes.sortedBy { it.marginPpm }) {
            appendLine(e.recipe.id.key.padEnd(34) + units(e.totalCost).padStart(10) + units(e.outputValue).padStart(10) + "${e.marginPpm / 10_000}%".padStart(9))
        }
    }

    fun paybackTable(r: AuditReport): String = buildString {
        appendLine("facility".padEnd(30) + "build".padStart(12) + "net/day".padStart(10) + "payback".padStart(10))
        for (p in r.paybacks.sortedBy { it.paybackDays ?: Long.MAX_VALUE }) {
            appendLine(p.facility.id.key.padEnd(30) + units(p.buildCost).padStart(12) + units(p.netPerDay).padStart(10) + (p.paybackDays?.let { "${it}d" } ?: "never").padStart(10))
        }
    }

    fun findings(r: AuditReport): String = r.findings.joinToString("\n")
}
