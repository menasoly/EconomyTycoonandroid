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
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.ConfirmDialog
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ProblemsDialog
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.FieldInput
import app.many.economytycoon.ui.present.FieldRow
import app.many.economytycoon.ui.present.Tone
import app.many.economytycoon.ui.present.WorkshopNav
import app.many.economytycoon.ui.present.WorkshopPage
import app.many.economytycoon.ui.present.WorkshopPlayPresenter
import app.many.economytycoon.ui.present.WorkshopEditorPresenter
import app.many.economytycoon.ui.present.WorkshopValidationPresenter
import app.many.economytycoon.workshop.EventKind
import app.many.economytycoon.workshop.FailKind
import app.many.economytycoon.workshop.WorkshopSession

/** One field of the draft. Typing applies the change at once; a value the draft cannot hold is explained under the field. */
@Composable
private fun DraftField(session: WorkshopSession, row: FieldRow, onChanged: () -> Unit) {
    var text by remember(row.path, row.value) { mutableStateOf(row.value) }
    var error by remember(row.path, row.value) { mutableStateOf<String?>(null) }
    LabeledField(
        row.label, text,
        { typed ->
            text = typed
            error = WorkshopEditorPresenter.setField(session.editor, row.path, typed)
            if (error == null) onChanged()
        },
        kind = if (row.input == FieldInput.NUMBER) FieldKind.NUMBER else FieldKind.TEXT,
        error = error,
        singleLine = row.input != FieldInput.LONG_TEXT,
    )
    row.hint?.let { Text(it, style = Type.Caption, color = Theme.tokens.textSecondary) }
}

/** The scenario editor: a section at a time, with undo and redo, the problems found so far, and the way on to testing. */
@Composable
fun WorkshopScreen(nav: WorkshopNav, workshop: WorkshopSession) {
    var tick by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var sectionKey by remember { mutableStateOf("identity") }
    var newTitle by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(WorkshopEditorPresenter.templates().first()) }
    var confirmPlay by remember { mutableStateOf(false) }
    var playProblems by remember { mutableStateOf<List<String>>(emptyList()) }
    val report = remember(tick) { workshop.validation() }
    val model = remember(tick) { WorkshopEditorPresenter.model(workshop.editor, report) }
    val problems = remember(tick) { WorkshopValidationPresenter.model(report) }
    val section = model.sections.firstOrNull { it.key == sectionKey } ?: model.sections.first()
    ScreenFrame("Scenario workshop", onBack = { nav.back() }) {
        ScreenList {
            item { TileGrid(model.tiles) }
            message?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.textSecondary) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(model.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                    if (model.dirty) Pill("Unsaved", Tone.WARN.color())
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Undo", { message = WorkshopEditorPresenter.undo(workshop.editor); tick++ }, Modifier.weight(1f), enabled = model.canUndo)
                    SecondaryButton("Redo", { message = WorkshopEditorPresenter.redo(workshop.editor); tick++ }, Modifier.weight(1f), enabled = model.canRedo)
                    PrimaryButton("Save", { message = (workshop.save() as? app.many.economytycoon.core.Outcome.Rejected)?.rejection?.message ?: "Saved."; tick++ }, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Test", { nav.open(WorkshopPage.REPORT) }, Modifier.weight(1f))
                    SecondaryButton("Saved", { nav.open(WorkshopPage.LIBRARY) }, Modifier.weight(1f))
                    PrimaryButton("Play", { confirmPlay = true }, Modifier.weight(1f), enabled = report.ok)
                }
            }
            item { Text(problems.headline, style = Type.Body, color = problems.tone.color()) }
            item { ChoiceChips(model.sections, section, { it.title }, { sectionKey = it.key }) }
            item { Text(section.summary, style = Type.Caption, color = Theme.tokens.textSecondary) }
            items(section.fields.size) { i -> Card { DraftField(workshop, section.fields[i]) { tick++ } } }
            items(section.items.size) { i ->
                val row = section.items[i]
                Card {
                    Text(row.title, style = Type.Heading, color = row.problem?.color() ?: Theme.tokens.textPrimary)
                    for (f in row.fields) DraftField(workshop, f) { tick++ }
                    when (section.key) {
                        "goals" -> SecondaryButton("Remove", { message = WorkshopEditorPresenter.removeGoal(workshop.editor, i); tick++ }, Modifier.fillMaxWidth())
                        "events" -> SecondaryButton("Remove", { message = WorkshopEditorPresenter.removeEvent(workshop.editor, i); tick++ }, Modifier.fillMaxWidth())
                        "fails" -> SecondaryButton("Remove", { message = WorkshopEditorPresenter.removeFail(workshop.editor, i); tick++ }, Modifier.fillMaxWidth())
                        "hints" -> SecondaryButton("Remove", { message = WorkshopEditorPresenter.removeHint(workshop.editor, i); tick++ }, Modifier.fillMaxWidth())
                    }
                }
            }
            if (section.canAdd) {
                item { SectionHeader("Add") }
                when (section.key) {
                    "goals" -> item { ChoiceChips(WorkshopEditorPresenter.goalKinds, null as GoalKind?, { it?.noun ?: "" }, { k -> if (k != null) { message = WorkshopEditorPresenter.addGoal(workshop.editor, k); tick++ } }) }
                    "events" -> item { ChoiceChips(EventKind.values().toList(), null as EventKind?, { it?.label ?: "" }, { k -> if (k != null) { message = WorkshopEditorPresenter.addEvent(workshop.editor, k); tick++ } }) }
                    "fails" -> item { ChoiceChips(FailKind.values().toList(), null as FailKind?, { it?.label ?: "" }, { k -> if (k != null) { message = WorkshopEditorPresenter.addFail(workshop.editor, k); tick++ } }) }
                    "hints" -> item { SecondaryButton("Add a hint", { message = WorkshopEditorPresenter.addHint(workshop.editor); tick++ }, Modifier.fillMaxWidth()) }
                }
            }
            if (section.key == "world") {
                item { ChoiceChips(WorkshopEditorPresenter.worldSizes, workshop.draft.world.size, { it.name.lowercase() }, { message = WorkshopEditorPresenter.setWorldSize(workshop.editor, it); tick++ }) }
            }
            if (section.key == "identity") {
                item { SectionHeader("Difficulty tags") }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) { for (c in model.tags.take(5)) SecondaryButton((if (c.selected) "* " else "") + c.label, { message = WorkshopEditorPresenter.toggleTag(workshop.editor, c.key); tick++ }, Modifier.weight(1f)) } }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) { for (c in model.tags.drop(5)) SecondaryButton((if (c.selected) "* " else "") + c.label, { message = WorkshopEditorPresenter.toggleTag(workshop.editor, c.key); tick++ }, Modifier.weight(1f)) } }
            }
            if (problems.groups.isNotEmpty()) {
                item { SectionHeader("Problems") }
                for (g in problems.groups) {
                    items(g.rows.size) { i ->
                        val r = g.rows[i]
                        Card { Text(r.message, style = Type.Body, color = r.tone.color()); Text(r.path, style = Type.Caption, color = Theme.tokens.textSecondary); r.fix?.let { f -> Text(f, style = Type.Caption, color = Theme.tokens.textSecondary) } }
                    }
                }
            }
            item { SectionHeader("Start another draft") }
            item { ChoiceChips(WorkshopEditorPresenter.templates(), template, { it.name }, { template = it }) }
            item { Text(template.summary, style = Type.Caption, color = Theme.tokens.textSecondary) }
            item { LabeledField("Title", newTitle, { newTitle = it }) }
            item {
                SecondaryButton("Start from this template", {
                    message = (workshop.startFrom(template.key, newTitle) as? app.many.economytycoon.core.Outcome.Rejected)?.rejection?.message
                    sectionKey = "identity"; tick++
                }, Modifier.fillMaxWidth())
            }
            if (!report.ok) item { EmptyState("Not playable yet", "Fix the errors above and the scenario can be tested and shared.") }
        }
        if (confirmPlay) {
            val prompt = remember(tick) { WorkshopPlayPresenter.prompt(workshop) }
            if (prompt.canPlay) ConfirmDialog(prompt.title, prompt.lines.joinToString("\n"), "Play", { confirmPlay = false; playProblems = nav.play().orEmpty() }, { confirmPlay = false })
            else ProblemsDialog(prompt.title, prompt.lines, { confirmPlay = false })
        }
        if (playProblems.isNotEmpty()) ProblemsDialog("Cannot start yet", playProblems, { playProblems = emptyList() })
    }
}
