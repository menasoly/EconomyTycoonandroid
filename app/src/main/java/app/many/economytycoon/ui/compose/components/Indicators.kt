package app.many.economytycoon.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type

/** A small coloured label such as a status. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = Type.Caption, color = color,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = Spacing.S, vertical = Spacing.Hair + 1.dp),
    )
}

/** A horizontal bar showing [fraction] (0..1) of its width. */
@Composable
fun MeterBar(fraction: Double, modifier: Modifier = Modifier, color: Color = Theme.tokens.accent, height: Int = 6) {
    val t = Theme.tokens
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50)).background(t.surfaceRaised)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat()).background(color))
    }
}

/** Colour a 0..1 health-like value: red when low, amber in the middle, green when high. */
@Composable
fun healthColor(fraction: Double): Color {
    val t = Theme.tokens
    return when {
        fraction < 0.35 -> t.negative
        fraction < 0.65 -> t.warning
        else -> t.positive
    }
}

@Composable
fun signColor(value: Long): Color {
    val t = Theme.tokens
    return when {
        value > 0 -> t.positive
        value < 0 -> t.negative
        else -> t.textPrimary
    }
}
