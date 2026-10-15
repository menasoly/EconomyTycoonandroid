package app.many.economytycoon.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type

enum class FieldKind { TEXT, NUMBER, DECIMAL }

/** A text field with a label and an optional error line underneath. */
@Composable
fun LabeledField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    kind: FieldKind = FieldKind.TEXT, error: String? = null, suffix: String? = null, singleLine: Boolean = true,
) {
    val keyboard = when (kind) {
        FieldKind.TEXT -> KeyboardType.Text
        FieldKind.NUMBER -> KeyboardType.Number
        FieldKind.DECIMAL -> KeyboardType.Decimal
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Hair)) {
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
            singleLine = singleLine, isError = error != null, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            suffix = suffix?.let { { Text(it, style = Type.Label) } },
        )
        if (error != null) Text(error, style = Type.Caption, color = Theme.tokens.negative, modifier = Modifier.padding(start = Spacing.M))
    }
}

/** Pick one of a few options. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        for (o in options) {
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o), style = Type.Label) })
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    val t = Theme.tokens
    Row(modifier.fillMaxWidth().padding(vertical = Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.Body, color = t.textPrimary)
            if (hint != null) Text(hint, style = Type.Caption, color = t.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = t.accent))
    }
}
