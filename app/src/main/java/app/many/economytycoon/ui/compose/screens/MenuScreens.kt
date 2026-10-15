package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import app.many.economytycoon.session.Difficulty
import app.many.economytycoon.ui.app.AppScreen
import app.many.economytycoon.ui.app.NewGameDraft
import app.many.economytycoon.ui.compose.app.AppHost
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.ConfirmDialog
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ProblemsDialog
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.present.SavesPresenter
import app.many.economytycoon.world.WorldSize

@Composable
fun MenuScreen(host: AppHost) {
    val t = Theme.tokens
    var failure by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(t.background).padding(Spacing.XL),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Economy Tycoon", style = Type.Display, color = t.textPrimary)
        Text("Build a company. Read the market. Outlast your rivals.", style = Type.Body, color = t.textSecondary)
        Spacer(Modifier.height(Spacing.XXL))
        if (host.canContinue()) {
            PrimaryButton("Continue", { if (!host.continueGame()) failure = true }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.S))
            SecondaryButton("New game", { host.openSetup() }, Modifier.fillMaxWidth())
        } else {
            PrimaryButton("New game", { host.openSetup() }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Spacing.S))
        SecondaryButton("Scenarios", { host.openScenarios() }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.S))
        SecondaryButton("Achievements", { host.openAchievements() }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.S))
        SecondaryButton("Load game", { host.openLoadList() }, Modifier.fillMaxWidth())
    }
    if (failure) ProblemsDialog("Could not continue", listOf(host.lastFailure?.message ?: "The save could not be read."), { failure = false })
}

@Composable
fun SetupScreen(host: AppHost, draft: NewGameDraft) {
    var problems by remember { mutableStateOf<List<String>>(emptyList()) }
    ScreenFrame("New game", onBack = { host.showMenu() }) {
        ScreenList {
            item { LabeledField("Company name", draft.companyName, { host.updateDraft(draft.copy(companyName = it)) }) }
            item { SectionHeader("Difficulty") }
            item { ChoiceChips(Difficulty.values().toList(), draft.difficulty, { it.label }, { host.updateDraft(draft.copy(difficulty = it)) }) }
            item { Text(draft.difficulty.summary, style = Type.Body, color = Theme.tokens.textSecondary) }
            item { SectionHeader("World") }
            item { ChoiceChips(WorldSize.values().toList(), draft.worldSize, { sizeLabel(it) }, { host.updateDraft(draft.copy(worldSize = it, countryIndex = 0)) }) }
            if (draft.worldSize.countries > 1) {
                item { Text("Home country", style = Type.Label, color = Theme.tokens.textSecondary) }
                item { ChoiceChips((0 until draft.worldSize.countries).toList(), draft.countryIndex, { "Country ${it + 1}" }, { host.updateDraft(draft.copy(countryIndex = it)) }) }
            }
            item { LabeledField("World seed (blank for a random world)", draft.seedText, { host.updateDraft(draft.copy(seedText = it)) }, kind = FieldKind.NUMBER) }
            item {
                val errors = draft.problems()
                if (draft.companyName.isNotEmpty() && errors.isNotEmpty()) Text(errors.first(), style = Type.Caption, color = Theme.tokens.negative)
            }
            item {
                PrimaryButton("Start", {
                    val result = host.begin()
                    if (result != null) problems = result
                }, Modifier.fillMaxWidth())
            }
        }
        if (problems.isNotEmpty()) ProblemsDialog("Cannot start yet", problems, { problems = emptyList() })
    }
}

private fun sizeLabel(s: WorldSize): String = when (s) {
    WorldSize.SMALL -> "Small · 1 country"
    WorldSize.MEDIUM -> "Medium · 3 countries"
    WorldSize.LARGE -> "Large · 6 countries"
    WorldSize.HUGE -> "Huge · 8 countries"
}

@Composable
fun LoadListScreen(host: AppHost) {
    val items = remember(host.revision) { SavesPresenter.items(host.slots()) }
    var deleting by remember { mutableStateOf<String?>(null) }
    ScreenFrame("Load game", onBack = { host.showMenu() }) {
        if (items.isEmpty()) {
            EmptyState("No saved games", "Start a new game and it will be saved here.", action = "New game", onAction = { host.openSetup() })
            return@ScreenFrame
        }
        ScreenList {
            host.lastFailure?.let { f -> item { Text(f.message, style = Type.Body, color = Theme.tokens.negative) } }
            items(items.size) { i ->
                val s = items[i]
                Card(onClick = if (s.canLoad) ({ host.load(s.id) }) else null) {
                    ListRow(s.title, subtitle = s.subtitle, badge = s.badge, badgeTone = s.tone)
                    LinkButton("Delete", { deleting = s.id })
                }
            }
        }
        val del = deleting
        if (del != null) ConfirmDialog("Delete this save?", "It cannot be recovered.", "Delete", { host.deleteSlot(del); deleting = null }, { deleting = null }, destructive = true)
    }
}
