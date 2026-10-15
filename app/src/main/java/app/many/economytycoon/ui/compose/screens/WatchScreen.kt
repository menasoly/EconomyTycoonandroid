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
import app.many.economytycoon.content.GoodDef
import app.many.economytycoon.core.Money
import app.many.economytycoon.world.Region
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.SwitchRow
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.WatchPresenter

/** Rules that tell the player when something they care about happens. */
@Composable
fun WatchScreen(ui: GameUi) {
    var tick by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var good by remember { mutableStateOf<GoodDef?>(null) }
    var region by remember { mutableStateOf<Region?>(null) }
    var above by remember { mutableStateOf(true) }
    var level by remember { mutableStateOf("") }
    val model = remember(ui.revision / 5, tick) { WatchPresenter.build(ui.session) }
    val economy = ui.session.simulation.economy
    val goods = remember(query) { economy.content.allGoods.filter { it.tradable && it.name.lowercase().contains(query.trim().lowercase()) }.take(10) }
    val regions = remember { economy.world.regions().sortedBy { it.id } }
    ScreenFrame("Watchlist", onBack = { ui.back() }) {
        ScreenList {
            item { TileGrid(model.tiles) }
            message?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.textSecondary) } }
            if (model.rules.isEmpty()) item { EmptyState("Nothing watched", model.empty) }
            items(model.rules.size) { i ->
                val r = model.rules[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.text, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(r.severity, r.tone.color())
                    }
                    Text(r.status, style = Type.Caption, color = Theme.tokens.textSecondary)
                    SwitchRow("Switched on", r.enabled, { WatchPresenter.toggle(ui.session, r.id); tick++ })
                    SecondaryButton("Remove", { WatchPresenter.remove(ui.session, r.id); tick++ }, Modifier.fillMaxWidth())
                }
            }
            item { SectionHeader("Add a rule") }
            items(model.presets.size) { i ->
                val p = model.presets[i]
                Card(onClick = { message = WatchPresenter.addPreset(ui.session, p.id); tick++ }) {
                    Text(p.label, style = Type.Heading, color = Theme.tokens.textPrimary)
                    Text(p.hint, style = Type.Caption, color = Theme.tokens.textSecondary)
                }
            }
            item { SectionHeader("Watch a price") }
            item { LabeledField("Find a good", query, { query = it }) }
            item { ChoiceChips<GoodDef?>(goods, good, { it?.name ?: "" }, { good = it }) }
            item { ChoiceChips<Region?>(regions, region, { it?.name ?: "" }, { region = it }) }
            item { ChoiceChips(listOf(true, false), above, { if (it) "Goes above" else "Falls below" }, { above = it }) }
            item { LabeledField("Price", level, { level = it }, kind = FieldKind.NUMBER) }
            val g = good; val r = region
            if (g != null && r != null) item { Text(WatchPresenter.priceNote(ui.session, r.id, g.id), style = Type.Caption, color = Theme.tokens.textSecondary) }
            item {
                SecondaryButton("Watch this price", {
                    val lvl = level.toLongOrNull()
                    message = when {
                        g == null || r == null -> "Choose a good and a region first."
                        lvl == null -> "Enter the price as a whole number."
                        else -> WatchPresenter.addPrice(ui.session, r.id, g.id, above, Money.units(lvl))
                    }
                    tick++
                }, Modifier.fillMaxWidth())
            }
            if (model.hits.isNotEmpty()) {
                item { SectionHeader("What fired") }
                items(model.hits.size) { i ->
                    val h = model.hits[i]
                    Card { Text(h.text, style = Type.Body, color = h.tone.color()); Text(h.day, style = Type.Caption, color = Theme.tokens.textSecondary) }
                }
            }
        }
    }
}
