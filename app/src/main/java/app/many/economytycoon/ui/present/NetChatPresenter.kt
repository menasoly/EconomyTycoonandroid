package app.many.economytycoon.ui.present

import app.many.economytycoon.net.ChatKind
import app.many.economytycoon.net.ChatLine
import app.many.economytycoon.net.NetLimits
import app.many.economytycoon.net.PlayerId

data class NetChatRow(val who: String, val text: String, val tone: Tone, val tick: Long)

/** The session chat and the quick emotes. */
object NetChatPresenter {
    val emotes: List<String> = listOf(
        "Hello", "Good luck", "Well played", "Thanks", "Sorry", "Nice deal", "Wait for me", "Ready",
        "Laughing", "Thinking", "Oops", "Cheers", "Impressive", "Unlucky", "Goodbye", "Rematch?",
    )

    init { check(emotes.size == NetLimits.MAX_EMOTE + 1) { "one label per emote number" } }

    fun emote(id: Int): String = emotes.getOrNull(id) ?: "..."

    fun row(l: ChatLine, me: PlayerId, names: (PlayerId) -> String): NetChatRow = when (l.kind) {
        ChatKind.SYSTEM -> NetChatRow("Host", l.text, Tone.INFO, l.tick)
        ChatKind.EMOTE -> NetChatRow(names(l.from), "[${emote(l.emote)}]", if (l.from == me) Tone.INFO else Tone.MUTED, l.tick)
        ChatKind.TEXT -> NetChatRow(names(l.from), l.text, if (l.from == me) Tone.INFO else Tone.NEUTRAL, l.tick)
    }

    fun build(lines: List<ChatLine>, me: PlayerId, names: (PlayerId) -> String, shown: Int = 50): List<NetChatRow> =
        lines.takeLast(shown).map { row(it, me, names) }

    /** Why a draft cannot be sent, or null when it can. */
    fun problem(draft: String): String? = when {
        draft.isBlank() -> "Write something first."
        draft.codePointCount(0, draft.length) > NetLimits.MAX_CHAT_CHARS -> "Keep it under ${NetLimits.MAX_CHAT_CHARS} characters."
        else -> null
    }
}
