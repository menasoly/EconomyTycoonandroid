package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.many.economytycoon.ui.app.AppScreen
import app.many.economytycoon.ui.compose.app.AppHost
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ProblemsDialog
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.ScenariosPresenter
import app.many.economytycoon.ui.present.Tone

/** The scenario list on the main menu, with the chosen scenario's story and a button to start it. */
@Composable
fun ScenariosScreen(host: AppHost, screen: AppScreen.Scenarios) {
    var problems by remember { mutableStateOf<List<String>>(emptyList()) }
    val model = ScenariosPresenter.list(host.profile, screen.selected)
    ScreenFrame("Scenarios", onBack = { host.showMenu() }) {
        ScreenList {
            item { Text(model.note, style = Type.Body, color = Theme.tokens.textSecondary) }
            item { Text(model.stars, style = Type.Label, color = Theme.tokens.textSecondary) }
            items(model.items.size) { i ->
                val s = model.items[i]
                Card(onClick = if (s.locked) null else ({ host.selectScenario(s.id) })) {
                    ListRow(
                        (if (s.id == screen.selected) "› " else "") + s.title, subtitle = s.record ?: s.tagline,
                        trailing = if (s.locked) "Locked" else s.medalLabel, trailingTone = s.tone,
                    )
                }
            }
            model.detail?.let { d ->
                item { SectionHeader(d.title) }
                item { Text(d.story, style = Type.Body, color = Theme.tokens.textPrimary) }
                item { FactList(d.facts) }
                item { SectionHeader("To win") }
                items(d.goals.size) { i -> Text("• ${d.goals[i]}", style = Type.Body, color = Theme.tokens.textPrimary) }
                item { Text(d.medals, style = Type.Caption, color = Theme.tokens.textSecondary) }
                if (d.tips.isNotEmpty()) {
                    item { SectionHeader("Tips") }
                    items(d.tips.size) { i -> Text("• ${d.tips[i]}", style = Type.Caption, color = Theme.tokens.textSecondary) }
                }
                item { LabeledField("Company name", screen.companyName, { host.updateScenarioName(it) }) }
                item { PrimaryButton("Start scenario", { problems = host.startScenario().orEmpty() }, Modifier.fillMaxWidth()) }
            }
        }
    }
    if (problems.isNotEmpty()) ProblemsDialog("Cannot start yet", problems, { problems = emptyList() })
}

/** How the running scenario stands: each target, the clock and the medal on offer. */
@Composable
fun ScenarioProgressScreen(ui: GameUi) {
    val model = remember(ui.revision / 5) { ScenariosPresenter.status(ui.session) }
    ScreenFrame("Scenario", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("Free play", "This game was not started from a scenario.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text(model.title, style = Type.Title, color = Theme.tokens.textPrimary) }
            item { Text(model.statusLabel, style = Type.Label, color = model.tone.color()) }
            item {
                Card {
                    ListRow("Time", trailing = model.timeLeft)
                    MeterBar(model.timeRatio, color = (if (model.timeRatio > 0.85) Tone.WARN else Tone.INFO).color())
                }
            }
            model.medalNote?.let { n -> item { Text(n, style = Type.Caption, color = Theme.tokens.textSecondary) } }
            item { SectionHeader("Targets") }
            items(model.goals.size) { i ->
                val g = model.goals[i]
                Card {
                    ListRow((if (g.done) "✓ " else "") + g.text, trailing = g.progress)
                    MeterBar(g.ratio, color = (if (g.done) Tone.GOOD else Tone.INFO).color())
                }
            }
        }
    }
}
