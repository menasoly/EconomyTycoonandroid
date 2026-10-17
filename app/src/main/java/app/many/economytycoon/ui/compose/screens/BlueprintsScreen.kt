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
import app.many.economytycoon.blueprint.BlueprintId
import app.many.economytycoon.blueprint.BlueprintQuery
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.BlueprintDetailPresenter
import app.many.economytycoon.ui.present.BlueprintImportPresenter
import app.many.economytycoon.ui.present.BlueprintLibraryPresenter

/** Saved facility set-ups: browse, inspect, share and import. Applying is started from a facility's own screen. */
@Composable
fun BlueprintsScreen(ui: GameUi) {
    val controller = ui.session.blueprints
    var tick by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<BlueprintId?>(null) }
    var pasted by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val query = BlueprintQuery(text = search, folder = folder)
    val model = remember(ui.revision / 5, tick, search, folder) { BlueprintLibraryPresenter.build(controller, query) }
    val content = ui.session.simulation.economy.content
    ScreenFrame("Blueprints", onBack = { ui.back() }) {
        ScreenList {
            if (model == null) {
                item { EmptyState("No game running", "Start or load a game to use blueprints.") }
                return@ScreenList
            }
            item { TileGrid(model.tiles) }
            model.warnings.forEach { w -> item { Text(w, style = Type.Caption, color = Theme.tokens.textSecondary) } }
            message?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.textSecondary) } }
            item { LabeledField("Search", search, { search = it }) }
            item { ChoiceChips(model.folders.map { it.path }, folder ?: "", { p -> model.folders.firstOrNull { f -> f.path == p }?.let { f -> if (p.isEmpty()) "All" else f.label + " (" + f.count + ")" } ?: p }, { folder = if (it.isEmpty()) null else it }) }
            model.empty?.let { e -> item { EmptyState("Nothing here", e) } }
            items(model.rows.size) { i ->
                val r = model.rows[i]
                Card(onClick = { opened = if (opened == r.id) null else r.id }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text((if (r.favourite) "* " else "") + r.name, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(r.status, r.statusTone.color())
                    }
                    Text(r.subtitle, style = Type.Caption, color = Theme.tokens.textSecondary)
                    if (r.recipes.isNotEmpty()) Text(r.recipes, style = Type.Caption, color = Theme.tokens.textSecondary)
                    if (opened == r.id) {
                        val analysis = controller.analysis(r.id)
                        if (analysis != null) {
                            val d = BlueprintDetailPresenter.model(analysis, content)
                            TileGrid(d.tiles)
                            Text(d.verdict, style = Type.Body, color = d.verdictTone.color())
                            d.lines.forEach { l -> Text(l.title + " - " + l.machines + " - " + l.status, style = Type.Caption, color = l.tone.color()) }
                            d.issues.forEach { q -> Text(q.text, style = Type.Caption, color = q.tone.color()) }
                            d.notes.forEach { n -> Text(n, style = Type.Caption, color = Theme.tokens.textSecondary) }
                        }
                        SecondaryButton(if (r.favourite) "Remove favourite" else "Favourite", { controller.edit { it.toggleFavourite(r.id) }; tick++ }, Modifier.fillMaxWidth())
                        SecondaryButton("Duplicate", { controller.edit { it.duplicate(r.id) }; tick++ }, Modifier.fillMaxWidth())
                        SecondaryButton("Copy code", { message = controller.exportText(listOf(r.id)); tick++ }, Modifier.fillMaxWidth())
                        SecondaryButton("Delete", { controller.edit { it.remove(r.id) }; opened = null; tick++ }, Modifier.fillMaxWidth())
                    }
                }
            }
            item { SectionHeader("Import") }
            item { LabeledField("Paste codes", pasted, { pasted = it }) }
            val preview = BlueprintImportPresenter.preview(pasted, controller.library, content)
            item { Text(preview.hint ?: preview.summary, style = Type.Body, color = Theme.tokens.textSecondary) }
            items(preview.items.size) { i ->
                val x = preview.items[i]
                Card {
                    Text(x.title, style = Type.Heading, color = x.tone.color())
                    Text(x.message, style = Type.Caption, color = Theme.tokens.textSecondary)
                    x.where?.let { w -> Text(w, style = Type.Caption, color = Theme.tokens.textSecondary) }
                    x.excerpt?.let { e -> Text(e, style = Type.Caption, color = Theme.tokens.textSecondary) }
                }
            }
            if (preview.canImport) item {
                SecondaryButton("Import", { message = controller.importText(pasted).summary; pasted = ""; tick++ }, Modifier.fillMaxWidth())
            }
        }
    }
}
