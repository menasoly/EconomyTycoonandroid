package app.many.economytycoon.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type

/** A transient line of feedback. Clears itself after a few seconds, or on tap. */
@Composable
fun MessageBar(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    val t = Theme.tokens
    LaunchedEffect(message) {
        delay(4_000)
        onDismiss()
    }
    Row(
        modifier.fillMaxWidth().padding(Spacing.M).clip(RoundedCornerShape(Spacing.RadiusSmall)).background(t.textPrimary).clickable(onClick = onDismiss).padding(Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, style = Type.Body, color = t.background)
    }
}
