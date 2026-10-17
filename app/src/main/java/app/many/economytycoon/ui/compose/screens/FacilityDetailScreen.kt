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
import app.many.economytycoon.blueprint.ApplyPlan
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.production.SchedulePolicy
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ConfirmDialog
import app.many.economytycoon.ui.compose.components.DangerButton
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.model.StaffingForm
import app.many.economytycoon.ui.present.FacilitiesPresenter
import app.many.economytycoon.ui.present.BlueprintApplyPresenter
import app.many.economytycoon.ui.present.BlueprintLibraryPresenter
import app.many.economytycoon.ui.present.FacilityDetailPresenter
import app.many.economytycoon.ui.present.applyBlueprint
import app.many.economytycoon.ui.present.RecipeChoice
import app.many.economytycoon.ui.present.RoleChoice

@Composable
fun FacilityDetailScreen(ui: GameUi, facilityId: Long) {
    val id = FacilityId(facilityId)
    val model = remember(ui.revision, facilityId) { FacilityDetailPresenter.build(ui.session, id) }
    val company = ui.session.selectedCompany
    var confirmDemolish by remember { mutableStateOf(false) }
    var recipeLine by remember { mutableStateOf<LineId?>(null) }
    var staffRole by remember { mutableStateOf<RoleChoice?>(null) }
    var maintainText by remember { mutableStateOf("") }
    var pickBlueprint by remember { mutableStateOf(false) }
    var blueprintPlan by remember { mutableStateOf<ApplyPlan?>(null) }

    ScreenFrame(model?.title ?: "Facility", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("Facility not found", "It may have been demolished.", action = "Back", onAction = { ui.back() })
            return@ScreenFrame
        }
        ScreenList {
            item { Text(model.subtitle, style = Type.Body, color = Theme.tokens.textSecondary) }
            item { Card { FactList(model.facts) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    if (model.operating) SecondaryButton("Shut down", { ui.issue(Command.Shutdown(company, id), "Shut down.") }, Modifier.weight(1f))
                    else PrimaryButton("Reopen", { ui.issue(Command.Reopen(company, id), "Reopened.") }, Modifier.weight(1f))
                    SecondaryButton(if (model.policy.startsWith("By")) "Use best margin" else "Use priority", {
                        val next = if (model.policy.startsWith("By")) SchedulePolicy.AUTO_MARGIN else SchedulePolicy.PRIORITY
                        ui.issue(Command.SetSchedulePolicy(company, id, next))
                    }, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Save as blueprint", {
                        when (val saved = ui.session.blueprints.capture(id)) {
                            is Outcome.Ok -> ui.showMessage("Saved as '${saved.value.name}'.")
                            is Outcome.Rejected -> ui.showMessage(saved.rejection.message)
                        }
                    }, Modifier.weight(1f))
                    SecondaryButton("Apply a blueprint", { pickBlueprint = true }, Modifier.weight(1f))
                }
            }
            val waiting = ui.session.blueprintsReady().firstOrNull { it.facility == id }
            if (waiting != null) item {
                PrimaryButton("Configure from '${waiting.pending.blueprint.name}'", {
                    when (val planned = ui.session.blueprints.finishPending(waiting)) {
                        is Outcome.Ok -> blueprintPlan = planned.value
                        is Outcome.Rejected -> ui.showMessage(planned.rejection.message)
                    }
                }, Modifier.fillMaxWidth())
            }
            item { SectionHeader("Production lines", action = if (model.canAddLine) "Add line" else null, onAction = { ui.issue(Command.AddLine(company, id), "Line added.") }) }
            items(model.lines.size) { i ->
                val l = model.lines[i]
                Card {
                    ListRow(l.title, subtitle = "${l.machines} · ${l.batches}", badge = l.state, badgeTone = l.tone)
                    MeterBar(l.progress)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        SecondaryButton("Recipe", { recipeLine = LineId(l.id) }, Modifier.weight(1f))
                        SecondaryButton(if (l.tone == app.many.economytycoon.ui.present.Tone.MUTED && l.state == "Switched off") "Switch on" else "Switch off", {
                            ui.issue(Command.SetLineEnabled(company, id, LineId(l.id), l.state == "Switched off"))
                        }, Modifier.weight(1f))
                    }
                }
            }
            item { SectionHeader("Staff") }
            if (model.roles.isEmpty()) item { Text("No roles needed yet. Choose a recipe first.", style = Type.Body, color = Theme.tokens.textSecondary) }
            items(model.roles.size) { i ->
                val r = model.roles[i]
                Card(onClick = { staffRole = r }) {
                    ListRow(r.name, subtitle = if (r.needed > 0) "Recipes need ${r.needed}" else "Not needed by current recipes", trailing = "${r.headcount}/${r.target}")
                }
            }
            if (model.staff.isNotEmpty()) item { Card { FactList(model.staff) } }
            item { SectionHeader("Stock") }
            item { Card { if (model.stock.isEmpty()) Text("Nothing in storage.", style = Type.Body, color = Theme.tokens.textSecondary) else FactList(model.stock) } }
            item { SectionHeader("Upkeep") }
            item {
                Card {
                    Text(model.maintenanceHint, style = Type.Body, color = Theme.tokens.textSecondary)
                    LabeledField("Spend on maintenance", maintainText, { maintainText = it }, kind = FieldKind.DECIMAL)
                    PrimaryButton("Pay for maintenance", {
                        val amount = app.many.economytycoon.ui.model.Parsing.money(maintainText).valueOrNull
                        if (amount == null || amount.raw <= 0) ui.showMessage("Enter an amount above zero.")
                        else if (ui.issue(Command.Maintain(company, id, amount), "Maintenance paid.")) maintainText = ""
                    }, Modifier.fillMaxWidth())
                    SecondaryButton("Enlarge storage", { ui.issue(Command.ExpandStorage(company, id), "Storage enlarged.") }, Modifier.fillMaxWidth())
                    SecondaryButton("Dispose of waste", { ui.issue(Command.DisposeWaste(company, id), "Waste removed.") }, Modifier.fillMaxWidth())
                }
            }
            item { DangerButton("Demolish", { if (ui.session.settings.confirmDestructive) confirmDemolish = true else { ui.issue(Command.Demolish(company, id)); ui.back() } }, Modifier.fillMaxWidth()) }
        }

        if (confirmDemolish) {
            ConfirmDialog("Demolish ${model.title}?", "The building and everything in it is lost. This cannot be undone.", "Demolish", {
                confirmDemolish = false
                if (ui.issue(Command.Demolish(company, id), "Demolished.")) ui.back()
            }, { confirmDemolish = false }, destructive = true)
        }
        if (pickBlueprint) {
            val library = BlueprintLibraryPresenter.build(ui.session.blueprints)
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pickBlueprint = false },
                containerColor = Theme.tokens.surface,
                title = { Text("Apply a blueprint", style = Type.Title, color = Theme.tokens.textPrimary) },
                text = {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        val rows = library?.rows.orEmpty()
                        if (rows.isEmpty()) Text(library?.empty ?: "Start or load a game first.", style = Type.Body, color = Theme.tokens.textSecondary)
                        for (r in rows) ListRow(r.name, subtitle = r.subtitle, badge = r.status, badgeTone = r.statusTone, onClick = {
                            pickBlueprint = false
                            when (val planned = ui.session.blueprints.planExisting(r.id, id)) {
                                is Outcome.Ok -> blueprintPlan = planned.value
                                is Outcome.Rejected -> ui.showMessage(planned.rejection.message)
                            }
                        })
                    }
                },
                confirmButton = {},
                dismissButton = { LinkButton("Cancel", { pickBlueprint = false }) },
            )
        }
        val plan = blueprintPlan
        if (plan != null) {
            val preview = BlueprintApplyPresenter.model(plan)
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { blueprintPlan = null },
                containerColor = Theme.tokens.surface,
                title = { Text(preview.title, style = Type.Title, color = Theme.tokens.textPrimary) },
                text = {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(preview.summary, style = Type.Body, color = Theme.tokens.textSecondary)
                        for (g in preview.groups) {
                            Text(g.label, style = Type.Heading, color = Theme.tokens.textPrimary)
                            for (row in g.rows) Text("${row.number}. ${row.text}" + (row.cost?.let { " ($it)" } ?: ""), style = Type.Caption, color = Theme.tokens.textSecondary)
                        }
                        for (q in preview.problems) Text("- ${q.text}: ${q.reason}", style = Type.Caption, color = Theme.tokens.warning)
                    }
                },
                confirmButton = {
                    PrimaryButton(preview.button, {
                        val result = ui.session.applyBlueprint(plan, id)
                        blueprintPlan = null
                        ui.showMessage(BlueprintApplyPresenter.report(result.report).headline)
                    }, enabled = preview.canApply)
                },
                dismissButton = { LinkButton("Cancel", { blueprintPlan = null }) },
            )
        }
        val line = recipeLine
        if (line != null) RecipePicker(model.recipes, onPick = { r -> recipeLine = null; ui.issue(Command.SetRecipe(company, id, line, r?.id)) }, onDismiss = { recipeLine = null })
        val role = staffRole
        if (role != null) StaffingDialog(ui, company, id, role) { staffRole = null }
    }
}

@Composable
private fun RecipePicker(recipes: List<RecipeChoice>, onPick: (RecipeChoice?) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Theme.tokens.surface,
        title = { Text("Choose a recipe", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                items(recipes.size) { i ->
                    val r = recipes[i]
                    ListRow(r.name, subtitle = "${r.inputs} → ${r.outputs} · ${r.duration}" + (r.lockNote?.let { " · $it" } ?: ""), onClick = if (r.locked) null else ({ onPick(r) }))
                }
            }
        },
        confirmButton = { LinkButton("Leave empty", { onPick(null) }) },
        dismissButton = { LinkButton("Cancel", onDismiss) },
    )
}

@Composable
private fun StaffingDialog(ui: GameUi, company: app.many.economytycoon.core.CompanyId, facility: FacilityId, role: RoleChoice, onDone: () -> Unit) {
    var target by remember { mutableStateOf(if (role.target > 0) role.target.toString() else role.needed.toString()) }
    var wage by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val minimum = app.many.economytycoon.ui.model.Parsing.money(role.minimumWageText.replace(",", "")).valueOrNull ?: Money.ZERO
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text("Staff: ${role.name}", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                LabeledField("How many people", target, { target = it }, kind = FieldKind.NUMBER, error = result?.errorFor("target"))
                LabeledField("Daily wage (blank for the market rate)", wage, { wage = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("wage"))
                Text("Legal minimum ${role.minimumWageText} a day", style = Type.Caption, color = Theme.tokens.textSecondary)
            }
        },
        confirmButton = {
            PrimaryButton("Set", {
                val r = StaffingForm(company, facility, role.role, target, wage, minimum).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Staffing updated.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
