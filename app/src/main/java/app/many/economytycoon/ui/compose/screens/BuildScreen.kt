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
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
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
import app.many.economytycoon.ui.present.BlueprintLibraryPresenter
import app.many.economytycoon.ui.present.BuildItem
import app.many.economytycoon.ui.present.BuildPresenter
import app.many.economytycoon.ui.present.Tone

@Composable
fun BuildScreen(ui: GameUi, initialRegion: Long) {
    var region by remember { mutableStateOf(RegionId(initialRegion)) }
    var family by remember { mutableStateOf<IndustryFamily?>(null) }
    var onlyBuildable by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<BuildItem?>(null) }
    var siteName by remember { mutableStateOf("") }
    var pickBlueprint by remember { mutableStateOf(false) }
    val regions = remember { ui.session.economy.world.regions().sortedBy { it.id } }
    val model = remember(ui.revision, region, family, onlyBuildable) { BuildPresenter.build(ui.session, region, family, onlyBuildable) }

    ScreenFrame("Build", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("Nowhere to build", "Pick a region to see what can be built there.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text("Cash available: ${model.cash}", style = Type.Body, color = Theme.tokens.textSecondary) }
            item { SectionHeader("Region") }
            item { ChoiceChips(regions.map { it.id }, region, { id -> regions.first { it.id == id }.name }, { region = it }) }
            item { SectionHeader("Industry") }
            item { ChoiceChips(model.families, family, { BuildPresenter.familyLabel(it) }, { family = it }) }
            item { SecondaryButton("Build from a blueprint", { pickBlueprint = true }, Modifier.fillMaxWidth()) }
            item { SwitchRow("Only what I can build now", onlyBuildable, { onlyBuildable = it }) }
            if (model.items.isEmpty()) item { EmptyState("Nothing to show", "Try another industry or switch the filter off.") }
            items(model.items.size) { i ->
                val b = model.items[i]
                Card(onClick = { chosen = b; siteName = "" }) {
                    ListRow(b.name, subtitle = b.family, trailing = b.cost, badge = if (b.canBuild) "Ready" else "Blocked", badgeTone = if (b.canBuild) Tone.GOOD else Tone.WARN)
                    Text(b.summary, style = Type.Caption, color = Theme.tokens.textSecondary)
                    Text(b.outputs, style = Type.Caption, color = Theme.tokens.textSecondary)
                    for (reason in b.blockers) Text("• $reason", style = Type.Caption, color = Theme.tokens.warning)
                }
            }
        }
        if (pickBlueprint) {
            val library = BlueprintLibraryPresenter.build(ui.session.blueprints)
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pickBlueprint = false },
                containerColor = Theme.tokens.surface,
                title = { Text("Build from a blueprint", style = Type.Title, color = Theme.tokens.textPrimary) },
                text = {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        val rows = library?.rows.orEmpty()
                        if (rows.isEmpty()) Text(library?.empty ?: "Start or load a game first.", style = Type.Body, color = Theme.tokens.textSecondary)
                        for (r in rows) ListRow(r.name, subtitle = r.subtitle, badge = r.status, badgeTone = r.statusTone, onClick = {
                            pickBlueprint = false
                            val controller = ui.session.blueprints
                            val company = ui.session.selectedCompany
                            when (val began = controller.beginNew(r.id, region)) {
                                is Outcome.Rejected -> ui.showMessage(began.rejection.message)
                                is Outcome.Ok -> {
                                    val step = began.value.plan.steps.firstOrNull()
                                    val cmd = if (company == null || step == null) null else step.action.toCommand(company, null, emptyList())
                                    if (cmd == null || !ui.issue(cmd, "Construction started. The set-up is applied when the building is finished.")) {
                                        controller.pending.remove(began.value.pending.id)
                                        controller.save()
                                    } else ui.back()
                                }
                            }
                        })
                    }
                },
                confirmButton = {},
                dismissButton = { LinkButton("Cancel", { pickBlueprint = false }) },
            )
        }
        val pick = chosen
        if (pick != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { chosen = null },
                containerColor = Theme.tokens.surface,
                title = { Text(pick.name, style = Type.Title, color = Theme.tokens.textPrimary) },
                text = {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(pick.summary, style = Type.Body, color = Theme.tokens.textSecondary)
                        for (d in pick.deposits) {
                            ListRow(d.title, subtitle = d.detail, trailing = if (d.canSurvey) "Survey" else if (d.canClaim) "Claim" else null, onClick = {
                                val cmd = if (d.canSurvey) BuildPresenter.surveyCommand(ui.session, d.id) else if (d.canClaim) BuildPresenter.claimCommand(ui.session, d.id) else null
                                if (cmd != null) ui.issue(cmd, if (d.canSurvey) "Survey done." else "Deposit claimed.")
                            })
                        }
                        LabeledField("Name (optional)", siteName, { siteName = it })
                        for (reason in pick.blockers) Text("• $reason", style = Type.Caption, color = Theme.tokens.warning)
                    }
                },
                confirmButton = {
                    PrimaryButton("Build for ${pick.cost}", {
                        val cmd = BuildPresenter.command(ui.session, pick.type, region, siteName)
                        if (cmd == null) ui.showMessage("This cannot be built right now.")
                        else if (ui.issue(cmd, "Construction started.")) { chosen = null; ui.back() }
                    }, enabled = pick.canBuild)
                },
                dismissButton = { LinkButton("Cancel", { chosen = null }) },
            )
        }
    }
}
