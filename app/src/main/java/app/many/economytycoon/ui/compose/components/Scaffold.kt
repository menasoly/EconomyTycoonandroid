package app.many.economytycoon.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.GameSpeed
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.nav.Tab

/** A screen's frame: a title row with an optional back arrow, then content. */
@Composable
fun ScreenFrame(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    val t = Theme.tokens
    Column(modifier.fillMaxSize().background(t.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Text("‹", style = Type.Display, color = t.accent, modifier = Modifier.clickable(onClick = onBack).padding(end = Spacing.M, start = Spacing.XS))
            }
            Text(title, style = Type.Title, color = t.textPrimary, modifier = Modifier.weight(1f))
            actions()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

/** The usual scrolling list with a 16dp gutter. */
@Composable
fun ScreenList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.L, vertical = Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.S), content = content,
    )
}

@Composable
fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit, badge: (Tab) -> Int = { 0 }) {
    val t = Theme.tokens
    Row(Modifier.fillMaxWidth().background(t.surface).padding(vertical = Spacing.XS)) {
        for (tab in Tab.values()) {
            val active = tab == selected
            Column(
                Modifier.weight(1f).clickable { onSelect(tab) }.padding(vertical = Spacing.S),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(tab.glyph, style = Type.Title, color = if (active) t.accent else t.textSecondary)
                    val n = badge(tab)
                    if (n > 0) Text("$n", style = Type.Caption, color = t.onAccent, modifier = Modifier.background(t.negative).padding(horizontal = 4.dp))
                }
                Text(tab.label, style = Type.Caption, color = if (active) t.accent else t.textSecondary)
            }
        }
    }
}

/** Date, speed buttons and the pause toggle shown above every in-game screen. */
@Composable
fun ClockBar(session: GameSession, dateText: String, onSpeed: (GameSpeed) -> Unit) {
    val t = Theme.tokens
    Row(Modifier.fillMaxWidth().background(t.surface).padding(horizontal = Spacing.L, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        Text(dateText, style = Type.Figure, color = t.textPrimary, modifier = Modifier.weight(1f))
        for (speed in GameSpeed.values()) {
            val active = session.speed == speed
            Text(
                glyph(speed), style = Type.Heading, color = if (active) t.onAccent else t.textSecondary,
                modifier = Modifier.padding(start = Spacing.XS).size(36.dp).background(if (active) t.accent else t.surfaceRaised).clickable { onSpeed(speed) }.padding(top = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private fun glyph(s: GameSpeed): String = when (s) {
    GameSpeed.PAUSED -> "❚❚"
    GameSpeed.NORMAL -> "▶"
    GameSpeed.FAST -> "▶▶"
    GameSpeed.FASTEST -> "▶▶▶"
}
