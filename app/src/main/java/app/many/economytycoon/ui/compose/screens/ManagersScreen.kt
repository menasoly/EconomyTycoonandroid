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
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.SwitchRow
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.present.ManagersPresenter

/** Hand routine jobs to managers and see what they did. */
@Composable
fun ManagersScreen(ui: GameUi) {
    var tick by remember { mutableStateOf(0) }
    val model = remember(ui.revision / 5, tick) { ManagersPresenter.build(ui.session) }
    var reserve by remember(model.reserveText) { mutableStateOf(model.reserveText) }
    var message by remember { mutableStateOf<String?>(null) }
    ScreenFrame("Managers", onBack = { ui.back() }) {
        ScreenList {
            item { TileGrid(model.tiles) }
            message?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.textSecondary) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("All on", { ManagersPresenter.setAll(ui.session, true); tick++ }, Modifier.weight(1f))
                    SecondaryButton("All off", { ManagersPresenter.setAll(ui.session, false); tick++ }, Modifier.weight(1f))
                }
            }
            items(model.rows.size) { i ->
                val r = model.rows[i]
                Card { SwitchRow(r.label, r.on, { ManagersPresenter.toggle(ui.session, r.kind); tick++ }, hint = r.summary + " " + r.cadence + ".") }
            }
            item { SectionHeader("Limits") }
            item {
                Card {
                    LabeledField("Cash managers never spend below", reserve, { reserve = it }, kind = FieldKind.NUMBER)
                    SecondaryButton("Save reserve", { message = ManagersPresenter.setReserve(ui.session, reserve) ?: "Reserve saved."; tick++ }, Modifier.fillMaxWidth())
                }
            }
            item {
                Card {
                    Text("Repair below ${model.repairPercent}% condition", style = Type.Body, color = Theme.tokens.textPrimary)
                    ChoiceChips(listOf(50, 65, 75, 90), model.repairPercent, { "$it%" }, { message = ManagersPresenter.setRepairPercent(ui.session, it); tick++ })
                }
            }
            item {
                Card {
                    Text("Clear waste at ${model.wasteUnits} units", style = Type.Body, color = Theme.tokens.textPrimary)
                    ChoiceChips(listOf(10L, 20L, 50L, 100L), model.wasteUnits, { "$it" }, { message = ManagersPresenter.setWasteUnits(ui.session, it); tick++ })
                }
            }
            item {
                Card {
                    Text("Lowest margin to sell at: ${model.marginPercent}%", style = Type.Body, color = Theme.tokens.textPrimary)
                    ChoiceChips(listOf(-10, 0, 10, 25), model.marginPercent, { "$it%" }, { message = ManagersPresenter.setMarginPercent(ui.session, it); tick++ })
                }
            }
            item {
                Card {
                    Text("Research favours", style = Type.Body, color = Theme.tokens.textPrimary)
                    ChoiceChips<TechCategory?>(listOf<TechCategory?>(null) + model.focuses, model.focus, { it?.name?.lowercase()?.replaceFirstChar { c -> c.uppercase() } ?: "Anything" }, { ManagersPresenter.setFocus(ui.session, it); tick++ })
                }
            }
            item { SectionHeader("What they did") }
            if (model.log.isEmpty()) item { EmptyState("Nothing yet", model.empty) }
            items(model.log.size) { i ->
                val l = model.log[i]
                Card { ListRow(l.note, subtitle = l.label, trailing = l.day) }
            }
        }
    }
}
