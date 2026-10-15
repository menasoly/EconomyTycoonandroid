package app.many.economytycoon.ui.compose.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.many.economytycoon.session.NewsKind
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.FeedPresenter

@Composable
fun AlertsScreen(ui: GameUi) {
    val alerts = remember(ui.revision) { FeedPresenter.alerts(ui.session) }
    ScreenFrame("Alerts", onBack = { ui.back() }) {
        if (alerts.isEmpty()) {
            EmptyState("All quiet", "Nothing needs your attention right now.")
            return@ScreenFrame
        }
        ScreenList {
            items(alerts.size) { i ->
                val a = alerts[i]
                Card(onClick = a.route?.let { r -> { ui.open(r) } }) {
                    ListRow(a.title, badge = a.severity.name.lowercase().replaceFirstChar { it.uppercase() }, badgeTone = a.tone)
                    Text(a.detail, style = Type.Body, color = Theme.tokens.textSecondary)
                }
            }
        }
    }
}

@Composable
fun NewsScreen(ui: GameUi) {
    var kind by remember { mutableStateOf<NewsKind?>(null) }
    val items = remember(ui.revision / 5, kind) { FeedPresenter.news(ui.session, kind) }
    ScreenFrame("News", onBack = { ui.back() }) {
        ScreenList {
            item { ChoiceChips(listOf<NewsKind?>(null) + NewsKind.values().toList(), kind, { it?.let { k -> FeedPresenter.kindLabel(k) } ?: "All" }, { kind = it }) }
            if (items.isEmpty()) item { EmptyState("No news yet", "Headlines appear as things happen in the world.") }
            items(items.size) { i ->
                val n = items[i]
                Card {
                    ListRow(n.headline, subtitle = n.whenText, badge = n.kindLabel, badgeTone = n.tone)
                }
            }
        }
    }
}

@Composable
fun GoalsScreen(ui: GameUi) {
    val groups = remember(ui.revision / 5) { FeedPresenter.goals(ui.session) }
    ScreenFrame("Goals", onBack = { ui.back() }) {
        ScreenList {
            for (g in groups) {
                item { SectionHeader("${g.name} · ${g.doneCount} of ${g.goals.size}") }
                items(g.goals.size) { i ->
                    val goal = g.goals[i]
                    Card {
                        ListRow(goal.title, subtitle = goal.description, trailing = goal.progressText, badge = if (goal.done) "Done" else null, badgeTone = app.many.economytycoon.ui.present.Tone.GOOD)
                        MeterBar(goal.progress, color = if (goal.done) Theme.tokens.positive else Theme.tokens.accent)
                    }
                }
            }
        }
    }
}
