package app.many.economytycoon.ui.present

import app.many.economytycoon.blueprint.BlueprintLibrary
import app.many.economytycoon.blueprint.ParseResult
import app.many.economytycoon.blueprint.BlueprintText
import app.many.economytycoon.content.ContentRepository

enum class ImportItemKind { NEW, DUPLICATE, NO_ROOM, INVALID }

/** One code found in the pasted text. Errors say where in the text they are and show the surrounding characters. */
data class ImportItem(
    val index: Int,
    val kind: ImportItemKind,
    val title: String,
    val subtitle: String,
    val message: String,
    val tone: Tone,
    /** `line 3, column 14` for a damaged code. */
    val where: String?,
    /** A short stretch of the text around the problem with the exact spot marked. */
    val excerpt: String?,
    val notes: List<String>,
)

data class ImportDialogModel(
    val items: List<ImportItem>,
    val summary: String,
    val canImport: Boolean,
    val hint: String?,
) {
    val newCount: Int get() = items.count { it.kind == ImportItemKind.NEW }
    val errorCount: Int get() = items.count { it.kind == ImportItemKind.INVALID }
}

/** The import dialog: reads pasted text without saving anything and says what importing it would do. */
object BlueprintImportPresenter {
    const val HINT = "Paste one or more blueprint codes. Each starts with ETBP1-."
    private const val CONTEXT = 12

    /** [spot] marks the offending place inside [text]; a few characters on each side are kept. */
    fun excerpt(text: String, offset: Int): String {
        val at = offset.coerceIn(0, text.length)
        val from = (at - CONTEXT).coerceAtLeast(0)
        val to = (at + CONTEXT).coerceAtMost(text.length)
        fun clean(s: String) = s.replace('\n', ' ').replace('\r', ' ')
        return (if (from > 0) "…" else "") + clean(text.substring(from, at)) + "⟦" + (if (at < text.length) clean(text.substring(at, at + 1)) else "end") + "⟧" +
            clean(if (at < text.length) text.substring(at + 1, to) else "") + (if (to < text.length) "…" else "")
    }

    fun preview(text: String, library: BlueprintLibrary, content: ContentRepository?): ImportDialogModel {
        if (text.isBlank()) return ImportDialogModel(emptyList(), "Nothing pasted yet.", false, HINT)
        val results = BlueprintText.parseAll(text)
        if (results.isEmpty()) return ImportDialogModel(emptyList(), "No blueprint code found.", false, HINT)
        var room = library.limits.maxEntries - library.size
        val seen = HashSet<String>()
        val items = results.mapIndexed { i, r ->
            when (r) {
                is ParseResult.Failure -> ImportItem(
                    i, ImportItemKind.INVALID, "Code ${i + 1}", "Could not be read", r.error.message, Tone.BAD, r.error.where, excerpt(text, r.error.offset), emptyList(),
                )
                is ParseResult.Success -> {
                    val bp = r.blueprint
                    val typeName = content?.facilityTypeOrNull(bp.facilityType)?.name ?: bp.facilityType.key
                    val sub = "$typeName · ${bp.spec.lines.size} line${if (bp.spec.lines.size == 1) "" else "s"}"
                    val existing = library.findByHash(bp.hash)
                    when {
                        existing != null || !seen.add(bp.hash) -> ImportItem(
                            i, ImportItemKind.DUPLICATE, bp.name, sub, existing?.let { "You already have this set-up as '${it.name}'." } ?: "Pasted more than once.", Tone.MUTED, null, null, r.notes,
                        )
                        room <= 0 -> ImportItem(i, ImportItemKind.NO_ROOM, bp.name, sub, "The library is full.", Tone.WARN, null, null, r.notes)
                        else -> { room--; ImportItem(i, ImportItemKind.NEW, bp.name, sub, "Will be added.", Tone.GOOD, null, null, r.notes) }
                    }
                }
            }
        }
        val n = items.count { it.kind == ImportItemKind.NEW }
        val summary = buildString {
            append("$n new")
            items.count { it.kind == ImportItemKind.DUPLICATE }.takeIf { it > 0 }?.let { append(", $it already saved") }
            items.count { it.kind == ImportItemKind.NO_ROOM }.takeIf { it > 0 }?.let { append(", $it with no room") }
            items.count { it.kind == ImportItemKind.INVALID }.takeIf { it > 0 }?.let { append(", $it damaged") }
        }
        return ImportDialogModel(items, summary, n > 0, null)
    }
}
