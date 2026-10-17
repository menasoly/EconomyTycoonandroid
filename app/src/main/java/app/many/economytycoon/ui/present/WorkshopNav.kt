package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.workshop.WorkshopSession

/** The three workshop screens. */
enum class WorkshopPage { EDITOR, REPORT, LIBRARY }

/**
 * What the workshop screens need from wherever they are shown, the main menu or a running game: a way back, a way to the
 * other pages, and a way to play the current draft. [play] returns null on success, otherwise the reasons it cannot start.
 */
class WorkshopNav(val back: () -> Unit, val open: (WorkshopPage) -> Unit, val play: () -> List<String>?)

/** What to tell the player before the current draft is played. */
data class PlayPrompt(val canPlay: Boolean, val title: String, val lines: List<String>)

object WorkshopPlayPresenter {
    fun prompt(workshop: WorkshopSession): PlayPrompt = when (val c = workshop.convert()) {
        is Outcome.Rejected -> PlayPrompt(false, "Not playable yet", listOf(c.rejection.message))
        is Outcome.Ok -> {
            val lines = ArrayList<String>()
            lines += "Starts a new game of '${c.value.def.title}'. A game in progress is saved first."
            if (c.value.notes.isNotEmpty()) {
                lines += "Left out of the game:"
                lines += c.value.notes.map { "- $it" }
            }
            PlayPrompt(true, "Play this scenario?", lines)
        }
    }
}
