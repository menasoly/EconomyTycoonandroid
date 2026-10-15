package app.many.economytycoon.ui.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type

@Composable
fun ConfirmDialog(
    title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val t = Theme.tokens
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        title = { Text(title, style = Type.Title, color = t.textPrimary) },
        text = { Text(message, style = Type.Body, color = t.textSecondary) },
        confirmButton = { if (destructive) DangerButton(confirmLabel, onConfirm) else PrimaryButton(confirmLabel, onConfirm) },
        dismissButton = { LinkButton("Cancel", onDismiss) },
    )
}

/** Lists the reasons a command could not be issued. */
@Composable
fun ProblemsDialog(title: String, problems: List<String>, onDismiss: () -> Unit) {
    val t = Theme.tokens
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        title = { Text(title, style = Type.Title, color = t.textPrimary) },
        text = {
            Column(Modifier.padding(top = Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                for (p in problems) Text("• $p", style = Type.Body, color = t.textPrimary)
            }
        },
        confirmButton = { LinkButton("OK", onDismiss) },
    )
}
