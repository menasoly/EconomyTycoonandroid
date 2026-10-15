package app.many.economytycoon.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type

private val ButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = Theme.tokens
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = Spacing.Touch), enabled = enabled, shape = RoundedCornerShape(Spacing.RadiusSmall),
        colors = ButtonDefaults.buttonColors(containerColor = t.accent, contentColor = t.onAccent), contentPadding = ButtonPadding,
    ) { Text(text, style = Type.Heading) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = Theme.tokens
    OutlinedButton(
        onClick = onClick, modifier = modifier.heightIn(min = Spacing.Touch), enabled = enabled, shape = RoundedCornerShape(Spacing.RadiusSmall),
        border = BorderStroke(1.dp, t.outline), colors = ButtonDefaults.outlinedButtonColors(contentColor = t.textPrimary), contentPadding = ButtonPadding,
    ) { Text(text, style = Type.Heading) }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = Theme.tokens
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = Spacing.Touch), enabled = enabled, shape = RoundedCornerShape(Spacing.RadiusSmall),
        colors = ButtonDefaults.buttonColors(containerColor = t.negative, contentColor = t.background), contentPadding = ButtonPadding,
    ) { Text(text, style = Type.Heading) }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = Spacing.Touch), enabled = enabled) {
        Text(text, style = Type.Label, color = Theme.tokens.accent)
    }
}
