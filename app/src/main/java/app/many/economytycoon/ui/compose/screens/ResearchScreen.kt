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
import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.SwitchRow
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.present.ResearchPresenter
import app.many.economytycoon.ui.present.TechItem

@Composable
fun ResearchScreen(ui: GameUi) {
    var category by remember { mutableStateOf<TechCategory?>(null) }
    var hideLocked by remember { mutableStateOf(false) }
    val model = remember(ui.revision, category, hideLocked) { ResearchPresenter.build(ui.session, category, hideLocked) }
    val company = ui.session.selectedCompany

    ScreenFrame("Research", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Research belongs to a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            if (model.active.isNotEmpty()) {
                item { SectionHeader("In progress") }
                items(model.active.size) { i -> TechCard(ui, company, model.active[i]) }
            }
            if (model.queued.isNotEmpty()) {
                item { SectionHeader("Queued") }
                items(model.queued.size) { i -> TechCard(ui, company, model.queued[i]) }
            }
            item { SectionHeader("All technologies") }
            item { ChoiceChips(model.categories, category, { it?.let { c -> app.many.economytycoon.session.Formatting.title(c.name.lowercase()) } ?: "All" }, { category = it }) }
            item { SwitchRow("Hide locked", hideLocked, { hideLocked = it }) }
            items(model.tree.size) { i -> TechCard(ui, company, model.tree[i]) }
        }
    }
}

@Composable
private fun TechCard(ui: GameUi, company: app.many.economytycoon.core.CompanyId, t: TechItem) {
    Card {
        ListRow(t.name, subtitle = "Tier ${t.tier} · ${t.category} · ${t.cost}", badge = t.stateLabel, badgeTone = t.tone)
        if (t.progress > 0.0 && t.progress < 1.0) MeterBar(t.progress)
        for (m in t.missing) Text("• $m", style = Type.Caption, color = Theme.tokens.warning)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (t.canStart) SecondaryButton("Research now", { ui.issue(Command.StartResearch(company, t.id), "Research started.") }, Modifier.weight(1f))
            if (t.canQueue) SecondaryButton("Queue", { ui.issue(Command.QueueResearch(company, t.id), "Queued.") }, Modifier.weight(1f))
            if (t.canCancel) LinkButton("Cancel", { ui.issue(Command.CancelResearch(company, t.id), "Cancelled.") })
        }
    }
}
