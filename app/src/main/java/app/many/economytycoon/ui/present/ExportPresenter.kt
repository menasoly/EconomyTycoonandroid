package app.many.economytycoon.ui.present

import app.many.economytycoon.export.ExportFile
import app.many.economytycoon.export.ExportKind
import app.many.economytycoon.session.GameSession

data class ExportItem(val kind: ExportKind, val title: String, val blurb: String)

data class ExportModel(val company: String, val items: List<ExportItem>, val note: String)

/** What can be taken out of the game as a spreadsheet, and the text to hand over for each. */
object ExportPresenter {
    const val NOTE = "Files are plain comma-separated text with the figures as numbers, so a spreadsheet can sum and chart them."
    const val EMPTY_FILE = "There is nothing to export here yet."

    fun items(kinds: List<ExportKind>): List<ExportItem> = kinds.map { ExportItem(it, it.label, it.blurb) }

    fun build(session: GameSession): ExportModel? {
        val id = session.selectedCompany ?: return null
        val name = session.economy.companies.company(id)?.name ?: return null
        return ExportModel(name, items(session.exports().available()), NOTE)
    }

    /** The file for one kind, or null when it cannot be made. */
    fun file(session: GameSession, kind: ExportKind): ExportFile? = session.exports().build(kind)

    /** Subject line for sharing: names the company and what the file holds. */
    fun subject(company: String, kind: ExportKind): String = "$company: ${kind.label}"
}
