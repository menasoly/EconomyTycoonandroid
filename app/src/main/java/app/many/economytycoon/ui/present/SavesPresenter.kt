package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Tick
import app.many.economytycoon.persistence.SaveSlot
import app.many.economytycoon.persistence.SlotKind
import app.many.economytycoon.session.Formatting

data class SlotItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String?,
    val tone: Tone,
    val canLoad: Boolean,
    val canRename: Boolean,
)

object SavesPresenter {
    fun size(bytes: Int): String = when {
        bytes < 1_024 -> "$bytes B"
        bytes < 1_048_576 -> "${bytes / 1_024} KB"
        else -> "${bytes / 1_048_576}.${(bytes % 1_048_576) * 10 / 1_048_576} MB"
    }

    /** Newest first; autosaves are marked so players can tell them from saves they made. */
    fun items(slots: List<SaveSlot>): List<SlotItem> = slots.sortedByDescending { it.savedAtMillis }.map { s ->
        val badge = when {
            s.damaged && s.hasBackup -> "Damaged, backup available"
            s.damaged -> "Damaged"
            s.kind == SlotKind.AUTO -> "Autosave"
            else -> null
        }
        SlotItem(
            id = s.id, title = s.label.ifBlank { s.id }, subtitle = "${Formatting.date(Tick(s.tick))} · ${size(s.sizeBytes)} · world ${s.seed}",
            badge = badge, tone = if (s.damaged) (if (s.hasBackup) Tone.WARN else Tone.BAD) else if (s.kind == SlotKind.AUTO) Tone.MUTED else Tone.NEUTRAL,
            canLoad = !s.damaged || s.hasBackup, canRename = s.kind == SlotKind.MANUAL && !s.damaged,
        )
    }
}
