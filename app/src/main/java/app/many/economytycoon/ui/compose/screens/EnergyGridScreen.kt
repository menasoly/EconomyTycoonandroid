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
import app.many.economytycoon.energygrid.EnergyGridState
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.EnergyGridPresenter

private enum class GridSection(val label: String) {
    OVERVIEW("Overview"), DISPATCH("Dispatch"), AGREEMENTS("Agreements"), OUTAGES("Outages"), PRICES("Prices"),
}

/** The electricity grids: one region at a time, with its supply, price, agreements and outages. */
@Composable
fun EnergyGridScreen(ui: GameUi, state: EnergyGridState, tick: Long) {
    var selected by remember { mutableStateOf(0L) }
    var section by remember { mutableStateOf(GridSection.OVERVIEW) }
    val money = ui.session.settings::money
    val list = remember(ui.revision / 5, tick) { EnergyGridPresenter.list(state, money) }
    val grid = state.grids.firstOrNull { it.id.value == selected } ?: state.grids.firstOrNull()
    ScreenFrame("Electricity", onBack = { ui.back() }) {
        ScreenList {
            if (grid == null) {
                item { EmptyState("No grids", list.empty) }
            } else {
                item { ChoiceChips(state.grids.map { it.id.value }, grid.id.value, { id -> list.rows.firstOrNull { it.id == id }?.name ?: "Grid $id" }, { selected = it }) }
                item { ChoiceChips(GridSection.values().toList(), section, { it.label }, { section = it }) }
                when (section) {
                    GridSection.OVERVIEW -> {
                        val m = EnergyGridPresenter.overview(state, grid, tick, money)
                        item { TileGrid(m.tiles) }
                        item { FactList(m.facts) }
                        if (m.insights.isNotEmpty()) item { SectionHeader("Worth a look") }
                        items(m.insights.size) { i ->
                            val s = m.insights[i]
                            Card { Text(s.title, style = Type.Heading, color = s.tone.color()); Text(s.detail, style = Type.Caption, color = Theme.tokens.textSecondary) }
                        }
                        item { SectionHeader("Capacity by fuel") }
                        items(m.mix.size) { i ->
                            val r = m.mix[i]
                            Card {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(r.tech, style = Type.Heading, color = Theme.tokens.textPrimary)
                                    Text(r.capacity, style = Type.Body, color = Theme.tokens.textPrimary)
                                }
                                Text("${r.share} of capacity in ${r.units} units", style = Type.Caption, color = Theme.tokens.textSecondary)
                            }
                        }
                    }
                    GridSection.DISPATCH -> {
                        val m = EnergyGridPresenter.dispatchStack(grid, money)
                        if (m.hours.isEmpty()) item { EmptyState("Nothing dispatched", m.empty) }
                        items(m.hours.size) { i ->
                            val h = m.hours[i]
                            Card {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Hour ${h.hour}", style = Type.Heading, color = h.tone.color())
                                    Text(h.price, style = Type.Body, color = Theme.tokens.textPrimary)
                                }
                                Text("Demand ${h.demand}, price set by ${h.setter}", style = Type.Caption, color = Theme.tokens.textSecondary)
                                h.unserved?.let { Text(it, style = Type.Caption, color = Theme.tokens.textSecondary) }
                                Text(h.layers.joinToString(", ") { "${it.tech} ${it.power}" }, style = Type.Caption, color = Theme.tokens.textSecondary)
                            }
                        }
                    }
                    GridSection.AGREEMENTS -> {
                        val m = EnergyGridPresenter.ppaList(state, grid, tick, money)
                        item { TileGrid(m.tiles) }
                        if (m.rows.isEmpty()) item { EmptyState("No agreements", m.empty) }
                        items(m.rows.size) { i ->
                            val r = m.rows[i]
                            Card {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(r.parties, style = Type.Heading, color = Theme.tokens.textPrimary)
                                    Pill(r.status, r.tone.color())
                                }
                                Text(r.terms, style = Type.Body, color = Theme.tokens.textPrimary)
                                Text(r.progress, style = Type.Caption, color = Theme.tokens.textSecondary)
                            }
                        }
                    }
                    GridSection.OUTAGES -> {
                        val m = EnergyGridPresenter.outageReport(grid, money)
                        item { TileGrid(m.tiles) }
                        if (m.outages.isEmpty() && m.blackouts.isEmpty()) item { EmptyState("All quiet", m.empty) }
                        if (m.blackouts.isNotEmpty()) item { SectionHeader("Blackouts") }
                        items(m.blackouts.size) { i ->
                            val b = m.blackouts[i]
                            Card {
                                Text("${b.when_}, ${b.length}", style = Type.Heading, color = Theme.tokens.textPrimary)
                                Text("${b.shed}, damage ${b.damage}, ${b.loads} customers", style = Type.Caption, color = Theme.tokens.textSecondary)
                            }
                        }
                        if (m.outages.isNotEmpty()) item { SectionHeader("Units out of service") }
                        items(m.outages.size) { i ->
                            val o = m.outages[i]
                            Card {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(o.what, style = Type.Heading, color = Theme.tokens.textPrimary)
                                    Pill(o.kind, o.tone.color())
                                }
                                Text(o.period, style = Type.Caption, color = Theme.tokens.textSecondary)
                            }
                        }
                    }
                    GridSection.PRICES -> {
                        val m = EnergyGridPresenter.priceHistory(grid, 30, money)
                        if (m.tiles.isEmpty()) item { EmptyState("No prices yet", m.empty) } else item { TileGrid(m.tiles) }
                        if (m.duration.isNotEmpty()) item { SectionHeader("Demand through the day") }
                        items(m.duration.size) { i ->
                            val d = m.duration[i]
                            Card { Text(d.first, style = Type.Caption, color = Theme.tokens.textSecondary); Text(d.second, style = Type.Heading, color = Theme.tokens.textPrimary) }
                        }
                    }
                }
            }
        }
    }
}
