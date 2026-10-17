package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.many.economytycoon.persistence.SaveManager
import app.many.economytycoon.session.CurrencyStyle
import app.many.economytycoon.session.GameSettings
import app.many.economytycoon.session.GameSpeed
import app.many.economytycoon.ui.compose.app.AppHost
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.ConfirmDialog
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.SwitchRow
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.HelpContent
import app.many.economytycoon.ui.present.SavesPresenter

@Composable
fun MoreMenuScreen(ui: GameUi, onQuit: () -> Unit) {
    val alertCount = remember(ui.revision / 5) { ui.session.alerts().size }
    var confirmQuit by remember { mutableStateOf(false) }
    ScreenFrame("More") {
        ScreenList {
            val base: List<Triple<String, String, Route>> = listOf(
                Triple("Alerts", if (alertCount == 0) "Nothing needs you" else "$alertCount to look at", Route.Alerts),
                Triple("Tutorial", "Step-by-step lessons", Route.Tutorial),
                Triple("Managers", "Hand routine jobs to managers", Route.Managers),
                Triple("Goals", "What to aim for", Route.Goals),
                Triple("Advisor", "Suggestions from your books", Route.Advisor),
                Triple("News", "What is happening in the world", Route.News),
                Triple("World", "Map, countries and regions", Route.World),
                Triple("Electricity", "Supply, prices, agreements and outages", Route.Electricity),
                Triple("Play with others", "Host or join a game, standings and chat", Route.Multiplayer),
                Triple("Government", "Laws, elections and pollution fines", Route.Government),
                Triple("Rivals", "Other companies and takeovers", Route.Rivals),
                Triple("People", "Staff, wages and morale", Route.People),
                Triple("Research", "Unlock technology", Route.Research),
                Triple("Logistics", "Vehicles, warehouses and shipments", Route.Logistics),
                Triple("Blueprints", "Save and reuse facility set-ups", Route.Blueprints),
                Triple("Achievements", "Milestones and lifetime totals", Route.Achievements),
                Triple("Coming up", "Payments, deliveries and deadlines", Route.Agenda),
                Triple("Activity", "What you have ordered", Route.Activity),
                Triple("Export", "Share your figures as spreadsheets", Route.Export),
                Triple("Saves", "Save, load and back up", Route.Saves),
                Triple("Settings", "Display, saving and speed", Route.Settings),
                Triple("Help", "How the game works", Route.Help),
            )
            val entries: List<Triple<String, String, Route>> =
                if (ui.session.economy.scenario.run != null) base.take(2) + Triple("Scenario", "Targets and time left", Route.Scenario) + base.drop(2) else base
            items(entries.size) { i ->
                val (title, subtitle, route) = entries[i]
                Card(onClick = { ui.open(route) }) { ListRow(title, subtitle = subtitle) }
            }
            item { SecondaryButton("Save and quit to menu", { confirmQuit = true }, Modifier.fillMaxWidth()) }
        }
        if (confirmQuit) ConfirmDialog("Leave this game?", "Your progress is saved to the 'Last session' slot first.", "Save and quit", { confirmQuit = false; onQuit() }, { confirmQuit = false })
    }
}

@Composable
fun SavesScreen(ui: GameUi, host: AppHost) {
    var refresh by remember { mutableStateOf(0) }
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    val items = remember(refresh, ui.revision / 30) { SavesPresenter.items(host.slots()) }

    ScreenFrame("Saves", onBack = { ui.back() }) {
        ScreenList {
            item { PrimaryButton("Save now", { naming = true; name = "" }, Modifier.fillMaxWidth()) }
            item { SecondaryButton("Quick save", {
                val slot = ui.session.save("quick", "Quick save")
                ui.showMessage(if (slot != null) "Saved." else "Saving is not available.")
                refresh++
            }, Modifier.fillMaxWidth()) }
            if (items.isEmpty()) item { EmptyState("No saves yet", "Saves you make and autosaves appear here.") }
            items(items.size) { i ->
                val s = items[i]
                Card {
                    ListRow(s.title, subtitle = s.subtitle, badge = s.badge, badgeTone = s.tone)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        if (s.canLoad) LinkButton("Load", { if (!host.load(s.id)) ui.showMessage(host.lastFailure?.message ?: "Could not load.") })
                        if (s.canRename) LinkButton("Rename", { renaming = s.id; name = s.title })
                        LinkButton("Delete", { deleting = s.id })
                    }
                }
            }
        }
        if (naming) NameDialog("Save as", name, { name = it }, "Save", { naming = false }) {
            val slotId = "manual-" + System.currentTimeMillis()
            ui.session.save(slotId, name.trim().ifEmpty { "My game" })
            naming = false; refresh++; ui.showMessage("Saved.")
        }
        val rename = renaming
        if (rename != null) NameDialog("Rename save", name, { name = it }, "Rename", { renaming = null }) {
            host.renameSlot(rename, name.trim()); renaming = null; refresh++
        }
        val del = deleting
        if (del != null) ConfirmDialog("Delete this save?", "It cannot be recovered.", "Delete", { host.deleteSlot(del); deleting = null; refresh++ }, { deleting = null }, destructive = true)
    }
}

@Composable
private fun NameDialog(title: String, value: String, onChange: (String) -> Unit, confirm: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel, containerColor = Theme.tokens.surface,
        title = { Text(title, style = Type.Title, color = Theme.tokens.textPrimary) },
        text = { LabeledField("Name", value, onChange) },
        confirmButton = { PrimaryButton(confirm, onConfirm) },
        dismissButton = { LinkButton("Cancel", onCancel) },
    )
}

@Composable
fun SettingsScreen(ui: GameUi, host: AppHost) {
    var s by remember { mutableStateOf(host.settings) }
    fun change(next: GameSettings) { s = next; host.updateSettings(next); ui.session.applySettings(next) }
    ScreenFrame("Settings", onBack = { ui.back() }) {
        ScreenList {
            item { SectionHeader("Display") }
            item { Text("Currency symbol", style = Type.Label, color = Theme.tokens.textSecondary) }
            item { ChoiceChips(CurrencyStyle.values().toList(), s.currency, { if (it == CurrencyStyle.PLAIN) "None" else when (it) { CurrencyStyle.DOLLAR -> "$"; CurrencyStyle.EURO -> "€"; else -> "£" } }, { change(s.copy(currency = it)) }) }
            item { Text("Language", style = Type.Label, color = Theme.tokens.textSecondary) }
            item { ChoiceChips(app.many.economytycoon.i18n.GameLocale.values().toList(), s.language, { it.nativeName }, { change(s.copy(language = it)) }) }
            item { SwitchRow("Short numbers", s.compactNumbers, { change(s.copy(compactNumbers = it)) }, hint = "Show 1.2M instead of 1,200,000.00") }
            item { SwitchRow("Reduce motion", s.reduceMotion, { change(s.copy(reduceMotion = it)) }) }
            item { Text("Text size ${s.uiScalePercent}%", style = Type.Label, color = Theme.tokens.textSecondary) }
            item { ChoiceChips(listOf(80, 90, 100, 115, 130, 160), s.uiScalePercent, { "$it%" }, { change(s.copy(uiScalePercent = it)) }) }
            item { SectionHeader("Play") }
            item { Text("Starting speed", style = Type.Label, color = Theme.tokens.textSecondary) }
            item { ChoiceChips(GameSpeed.values().filter { it != GameSpeed.PAUSED }, s.defaultSpeed, { it.label }, { change(s.copy(defaultSpeed = it)) }) }
            item { SwitchRow("Pause on critical alerts", s.pauseOnCritical, { change(s.copy(pauseOnCritical = it)) }) }
            item { SwitchRow("Confirm before destroying things", s.confirmDestructive, { change(s.copy(confirmDestructive = it)) }) }
            item { SectionHeader("Saving") }
            item { SwitchRow("Autosave", s.autosave, { change(s.copy(autosave = it)) }) }
            item { Text("Autosave every ${s.autosaveEveryTicks} days", style = Type.Label, color = Theme.tokens.textSecondary) }
            item { ChoiceChips(listOf(10, 30, 60, 120, 360), s.autosaveEveryTicks, { "$it" }, { change(s.copy(autosaveEveryTicks = it)) }) }
        }
    }
}

@Composable
fun HelpScreen(ui: GameUi) {
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    val topics = remember(query) { HelpContent.search(query) }
    ScreenFrame("Help", onBack = { ui.back() }) {
        ScreenList {
            item { LabeledField("Search help", query, { query = it }) }
            item { SecondaryButton("Glossary of terms", { ui.open(Route.Glossary) }, Modifier.fillMaxWidth()) }
            if (topics.isEmpty()) item { EmptyState("No match", "Try a different word.") }
            items(topics.size) { i ->
                val t = topics[i]
                Card(onClick = { open = if (open == t.id) null else t.id }) {
                    ListRow(t.title, subtitle = t.summary)
                    if (open == t.id) for (p in t.paragraphs) Text(p, style = Type.Body, color = Theme.tokens.textPrimary)
                }
            }
        }
    }
}
