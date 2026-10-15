package app.many.economytycoon.ui.compose.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.many.economytycoon.ui.present.Tone

/** Maps a presenter's tone to a colour in the current theme. */
@Composable
fun Tone.color(): Color {
    val t = Theme.tokens
    return when (this) {
        Tone.NEUTRAL -> t.textPrimary
        Tone.GOOD -> t.positive
        Tone.BAD -> t.negative
        Tone.WARN -> t.warning
        Tone.INFO -> t.info
        Tone.MUTED -> t.textSecondary
    }
}
