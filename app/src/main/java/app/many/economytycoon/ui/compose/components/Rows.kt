package app.many.economytycoon.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.Fact
import app.many.economytycoon.ui.present.Tile
import app.many.economytycoon.ui.present.Tone

/** A two-line list entry with an optional trailing value, tappable when [onClick] is given. */
@Composable
fun ListRow(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: String? = null, trailingTone: Tone = Tone.NEUTRAL,
    badge: String? = null, badgeTone: Tone = Tone.NEUTRAL, onClick: (() -> Unit)? = null,
) {
    val t = Theme.tokens
    val base = modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Hair)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(title, style = Type.Heading, color = t.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (badge != null) Pill(badge, badgeTone.color())
            }
            if (subtitle != null) Text(subtitle, style = Type.Caption, color = t.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, style = Type.Figure, color = trailingTone.color(), modifier = Modifier.padding(start = Spacing.S))
    }
}

@Composable
fun FactList(facts: List<Fact>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        for (f in facts) KeyValue(f.label, f.value, valueColor = f.tone.color(), hint = f.hint)
    }
}

/** Tiles in a wrapping grid, two per row on a phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TileGrid(tiles: List<Tile>, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S), maxItemsInEachRow = 2) {
        for (tile in tiles) {
            StatTile(
                tile.label, tile.value, Modifier.weight(1f), delta = tile.note,
                deltaPositive = when (tile.tone) { Tone.GOOD -> true; Tone.BAD -> false; else -> null },
            )
        }
    }
}
