package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SwitchRow
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.components.healthColor
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.FacilitiesPresenter
import app.many.economytycoon.ui.present.FacilitySort

@Composable
fun FacilitiesScreen(ui: GameUi) {
    var sort by rememberSaveable { mutableStateOf(FacilitySort.NAME) }
    var query by rememberSaveable { mutableStateOf("") }
    var problemsOnly by rememberSaveable { mutableStateOf(false) }
    val model = remember(ui.revision, sort, query, problemsOnly) { FacilitiesPresenter.list(ui.session, sort, query, problemsOnly) }
    val region = remember { ui.session.economy.world.regions().firstOrNull()?.id?.value ?: 0L }

    ScreenFrame("Facilities") {
        ScreenList {
            item { TileGrid(model.tiles) }
            item { PrimaryButton("Build something new", { ui.open(Route.Build(region)) }, Modifier.fillMaxWidth()) }
            item { SecondaryButton("Land", { ui.open(Route.Property) }, Modifier.fillMaxWidth()) }
            item { LabeledField("Search", query, { query = it }) }
            item { ChoiceChips(FacilitySort.values().toList(), sort, { it.label }, { sort = it }) }
            item { SwitchRow("Only show problems", problemsOnly, { problemsOnly = it }, hint = "Blocked lines or worn-out buildings") }
            if (model.items.isEmpty()) {
                item {
                    EmptyState(
                        if (model.hiddenByFilter > 0) "Nothing matches" else "No facilities yet",
                        if (model.hiddenByFilter > 0) "${model.hiddenByFilter} hidden by your search or filter." else "Build a farm, mine or workshop to start producing.",
                    )
                }
            }
            items(model.items.size) { i ->
                val f = model.items[i]
                Card(onClick = { ui.open(Route.FacilityDetail(f.id.value)) }) {
                    ListRow(f.title, subtitle = f.subtitle, badge = f.status, badgeTone = f.statusTone)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        Text("Condition", style = Type.Caption, color = Theme.tokens.textSecondary)
                        MeterBar(f.condition, Modifier.weight(1f), healthColor(f.condition))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        Text("In use", style = Type.Caption, color = Theme.tokens.textSecondary)
                        MeterBar(f.utilization, Modifier.weight(1f))
                    }
                    if (f.blocked != null) Text(f.blocked, style = Type.Caption, color = f.statusTone.color())
                }
            }
        }
    }
}
