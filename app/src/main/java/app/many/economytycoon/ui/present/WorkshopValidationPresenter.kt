package app.many.economytycoon.ui.present

import app.many.economytycoon.workshop.DraftIssue
import app.many.economytycoon.workshop.IssueSeverity
import app.many.economytycoon.workshop.SourceMap
import app.many.economytycoon.workshop.ValidationReport

data class DraftIssueRow(val path: String, val code: String, val severity: String, val tone: Tone, val message: String, val fix: String?, val where: String?)

data class IssueGroup(val title: String, val tone: Tone, val rows: List<DraftIssueRow>)

data class ValidationModel(val headline: String, val tone: Tone, val tiles: List<Tile>, val groups: List<IssueGroup>, val playable: Boolean)

/** The list of problems with a draft, worst first, each with the field it is about and, for typed-in text, the place in the text. */
object WorkshopValidationPresenter {
    const val CLEAN = "No problems found. The scenario can be played."

    fun tone(s: IssueSeverity): Tone = when (s) { IssueSeverity.ERROR -> Tone.BAD; IssueSeverity.WARNING -> Tone.WARN; IssueSeverity.NOTE -> Tone.INFO }

    private fun title(s: IssueSeverity, n: Int) = when (s) {
        IssueSeverity.ERROR -> if (n == 1) "1 error to fix" else "$n errors to fix"
        IssueSeverity.WARNING -> if (n == 1) "1 warning" else "$n warnings"
        IssueSeverity.NOTE -> if (n == 1) "1 note" else "$n notes"
    }

    fun row(i: DraftIssue, map: SourceMap? = null): DraftIssueRow =
        DraftIssueRow(i.path, i.code, i.severity.name.lowercase(), tone(i.severity), i.message, i.fix, map?.locate(i.path)?.toString())

    fun model(report: ValidationReport, map: SourceMap? = null): ValidationModel {
        val groups = IssueSeverity.values().mapNotNull { s ->
            val rows = report.issues.filter { it.severity == s }.map { row(it, map) }
            if (rows.isEmpty()) null else IssueGroup(title(s, rows.size), tone(s), rows)
        }
        val tone = when {
            report.errors.isNotEmpty() -> Tone.BAD
            report.warnings.isNotEmpty() -> Tone.WARN
            else -> Tone.GOOD
        }
        val headline = when {
            report.errors.isNotEmpty() -> "Cannot be played yet: ${report.summary()}"
            report.issues.isEmpty() -> CLEAN
            else -> "Can be played: ${report.summary()}"
        }
        val tiles = listOf(
            Tile("Errors", report.errors.size.toString(), if (report.errors.isEmpty()) Tone.GOOD else Tone.BAD),
            Tile("Warnings", report.warnings.size.toString(), if (report.warnings.isEmpty()) Tone.GOOD else Tone.WARN),
            Tile("Notes", report.notes.size.toString(), Tone.MUTED),
        )
        return ValidationModel(headline, tone, tiles, groups, report.ok)
    }

    /** The findings about one field or section, for showing beside it. */
    fun forField(report: ValidationReport, path: String): List<DraftIssueRow> = report.at(path).map { row(it) }

    /** The worst severity among the findings about [path], or null when there are none. */
    fun worstAt(report: ValidationReport, path: String): Tone? = report.at(path).minByOrNull { it.severity.ordinal }?.let { tone(it.severity) }
}
