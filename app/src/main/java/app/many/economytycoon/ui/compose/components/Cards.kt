package app.many.economytycoon.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type

/** A bordered panel that groups related content. Pass [onClick] to make the whole card tappable. */
@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val t = Theme.tokens
    val shape = RoundedCornerShape(Spacing.Radius)
    val base = modifier.fillMaxWidth().clip(shape)
    Surface(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        shape = shape, color = t.surface, border = BorderStroke(1.dp, t.outline),
    ) {
        Column(Modifier.padding(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.S), content = content)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = Spacing.L, bottom = Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = Type.Label, color = Theme.tokens.textSecondary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = Type.Label, color = Theme.tokens.accent, modifier = Modifier.clickable(onClick = onAction).padding(Spacing.XS))
        }
    }
}

/** A label on the left and a value on the right. */
@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Theme.tokens.textPrimary, hint: String? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = Spacing.Hair), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.Body, color = Theme.tokens.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) Text(hint, style = Type.Caption, color = Theme.tokens.textSecondary)
        }
        Spacer(Modifier.size(Spacing.S))
        Text(value, style = Type.Figure, color = valueColor, maxLines = 1)
    }
}

/** A large number with a caption, for the top of a screen. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, delta: String? = null, deltaPositive: Boolean? = null) {
    val t = Theme.tokens
    Column(modifier.clip(RoundedCornerShape(Spacing.Radius)).background(t.surfaceRaised).padding(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(label, style = Type.Label, color = t.textSecondary, maxLines = 1)
        Text(value, style = Type.BigFigure, color = t.textPrimary, maxLines = 1)
        if (delta != null) {
            val color = when (deltaPositive) { true -> t.positive; false -> t.negative; null -> t.textSecondary }
            Text(delta, style = Type.Caption, color = color)
        }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(1.dp).background(Theme.tokens.outline))
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(Spacing.XL), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(title, style = Type.Heading, color = Theme.tokens.textPrimary)
        Text(body, style = Type.Body, color = Theme.tokens.textSecondary)
        if (action != null && onAction != null) PrimaryButton(action, onAction)
    }
}
