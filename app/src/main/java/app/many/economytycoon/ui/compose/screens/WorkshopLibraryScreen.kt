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
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.SwitchRow
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.Tone
import app.many.economytycoon.ui.present.WorkshopNav
import app.many.economytycoon.ui.present.WorkshopPage
import app.many.economytycoon.ui.present.WorkshopLibraryPresenter
import app.many.economytycoon.workshop.ImportResult
import app.many.economytycoon.workshop.LibraryQuery
import app.many.economytycoon.workshop.LibrarySort
import app.many.economytycoon.workshop.WorkshopSession

/** Saved drafts: search, folders and tags, opening, copying, sharing and bringing in a shared code. */
@Composable
fun WorkshopLibraryScreen(nav: WorkshopNav, workshop: WorkshopSession) {
    var tick by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf(LibraryQuery()) }
    var pasted by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf<String?>(null) }
    val model = remember(tick, query) { WorkshopLibraryPresenter.model(workshop.library, query) }
    ScreenFrame("Saved scenarios", onBack = { nav.back() }) {
        ScreenList {
            item { TileGrid(model.tiles) }
            message?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.textSecondary) } }
            item { LabeledField("Search", query.text, { query = query.copy(text = it) }) }
            item { ChoiceChips(model.folders, (model.folders.firstOrNull { it.selected } ?: model.folders.first()), { it.label + " (" + it.count + ")" }, { query = WorkshopLibraryPresenter.withFolder(query, it.path) }) }
            item { ChoiceChips(LibrarySort.values().toList(), query.sort, { it.label }, { query = query.copy(sort = it) }) }
            item { SwitchRow("Starred only", query.starredOnly, { query = query.copy(starredOnly = it) }) }
            model.empty?.let { e -> item { EmptyState("Nothing to show", e) } }
            items(model.rows.size) { i ->
                val r = model.rows[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                        if (r.starred) Pill("Starred", Tone.INFO.color())
                    }
                    Text(r.subtitle, style = Type.Caption, color = Theme.tokens.textSecondary)
                    if (r.note.isNotEmpty()) Text(r.note, style = Type.Body, color = Theme.tokens.textPrimary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PrimaryButton("Open", { message = (workshop.open(r.id) as? Outcome.Rejected)?.rejection?.message; if (message == null) nav.open(WorkshopPage.EDITOR); tick++ }, Modifier.weight(1f))
                        SecondaryButton("Copy", { message = (workshop.changeLibrary { it.duplicate(r.id) } as? Outcome.Rejected)?.rejection?.message; tick++ }, Modifier.weight(1f))
                        SecondaryButton(if (r.starred) "Unstar" else "Star", { workshop.changeLibrary { it.star(r.id, !r.starred) }; tick++ }, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        SecondaryButton("Share", { shown = (workshop.library.exportShare(r.id) as? Outcome.Ok)?.value }, Modifier.weight(1f))
                        SecondaryButton("Delete", { workshop.delete(r.id); tick++ }, Modifier.weight(1f))
                    }
                }
            }
            shown?.let { code ->
                item { SectionHeader("Share code") }
                item { Card { Text(code, style = Type.Caption, color = Theme.tokens.textPrimary) } }
            }
            item { SectionHeader("Bring one in") }
            item { LabeledField("Paste a share code or a written scenario", pasted, { pasted = it }, singleLine = false) }
            item {
                PrimaryButton("Read it", {
                    message = when (val r = workshop.importText(pasted)) {
                        is ImportResult.Shared -> "Read '${r.draft.title}' from the code."
                        is ImportResult.Written -> "Read the scenario."
                        is ImportResult.Failed -> r.message + (r.at?.let { " (" + it + ")" } ?: "")
                    }
                    tick++
                }, Modifier.fillMaxWidth())
            }
        }
    }
}
