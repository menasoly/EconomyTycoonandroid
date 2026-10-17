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
import app.many.economytycoon.tourism.TourismState
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
import app.many.economytycoon.ui.present.TourismPresenter

private enum class TourismSection(val label: String) {
    OVERVIEW("Overview"), HOTELS("Hotels"), ATTRACTIONS("Attractions"),
}

/** Tourism: one destination at a time, with its visitors, hotels and attractions, and one hotel opened for detail. */
@Composable
fun TourismScreen(ui: GameUi, state: TourismState, tick: Long) {
    var selected by remember { mutableStateOf(0L) }
    var hotel by remember { mutableStateOf(0L) }
    var section by remember { mutableStateOf(TourismSection.OVERVIEW) }
    val money = ui.session.settings::money
    val list = remember(ui.revision / 5, tick) { TourismPresenter.destinations(state, money) }
    val dest = state.destinations.firstOrNull { it.id.value == selected } ?: state.destinations.firstOrNull()
    ScreenFrame("Tourism", onBack = { ui.back() }) {
        ScreenList {
            if (dest == null) {
                item { EmptyState("No destinations", list.empty) }
            } else {
                item { ChoiceChips(state.destinations.map { it.id.value }, dest.id.value, { id -> list.rows.firstOrNull { it.id == id }?.name ?: "Destination $id" }, { selected = it; hotel = 0L }) }
                item { ChoiceChips(TourismSection.values().toList(), section, { it.label }, { section = it }) }
                when (section) {
                    TourismSection.OVERVIEW -> {
                        val m = TourismPresenter.overview(state, dest, tick, money)
                        item { TileGrid(m.tiles) }
                        item { FactList(m.facts) }
                        m.notices.forEach { n -> item { Card { Text(n, style = Type.Heading, color = Theme.tokens.textPrimary) } } }
                        item { SectionHeader("What each kind of visitor sees") }
                        items(m.appeal.size) { i ->
                            val a = m.appeal[i]
                            Card {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(a.segment, style = Type.Heading, color = Theme.tokens.textPrimary)
                                    Text(a.value, style = Type.Body, color = Theme.tokens.textPrimary)
                                }
                                Text(a.parts.joinToString(", ") { "${it.first} ${it.second}" }, style = Type.Caption, color = Theme.tokens.textSecondary)
                            }
                        }
                        item { SectionHeader("Demand through the year") }
                        items(m.season.size) { i ->
                            val b = m.season[i]
                            Card {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(b.label, style = Type.Heading, color = Theme.tokens.textPrimary)
                                    Text("${b.indexPpm / 10_000L}%", style = Type.Body, color = Theme.tokens.textPrimary)
                                }
                            }
                        }
                    }
                    TourismSection.HOTELS -> {
                        val open = state.propertiesIn(dest.id).firstOrNull { it.id.value == hotel }
                        if (open == null) {
                            val m = TourismPresenter.properties(state, dest, money)
                            if (m.tiles.isNotEmpty()) item { TileGrid(m.tiles) }
                            if (m.rows.isEmpty()) item { EmptyState("No hotels", m.empty) }
                            items(m.rows.size) { i ->
                                val r = m.rows[i]
                                Card {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(r.name, style = Type.Heading, color = Theme.tokens.textPrimary)
                                        Pill(r.status, r.tone.color())
                                    }
                                    Text("${r.stars}, ${r.rooms} rooms, rated ${r.rating}", style = Type.Body, color = Theme.tokens.textPrimary)
                                    Text("Occupancy ${r.occupancy}, rate ${r.adr}, per room ${r.revPar}", style = Type.Caption, color = Theme.tokens.textSecondary)
                                    ChoiceChips(listOf(r.id), -1L, { "Details" }, { hotel = it })
                                }
                            }
                        } else {
                            val m = TourismPresenter.propertyDetail(state, open, tick, money)
                            item { ChoiceChips(listOf(0L), -1L, { "Back to hotels" }, { hotel = 0L }) }
                            item { TileGrid(m.tiles) }
                            item { FactList(m.facts) }
                            if (m.insights.isNotEmpty()) item { SectionHeader("Worth a look") }
                            items(m.insights.size) { i ->
                                val s = m.insights[i]
                                Card { Text(s.title, style = Type.Heading, color = s.tone.color()); Text(s.detail, style = Type.Caption, color = Theme.tokens.textSecondary) }
                            }
                            item { SectionHeader("Last two weeks") }
                            items(m.nights.size) { i ->
                                val n = m.nights[i]
                                Card {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(n.day, style = Type.Heading, color = Theme.tokens.textPrimary)
                                        Text("${n.sold} at ${n.rate}", style = Type.Body, color = Theme.tokens.textPrimary)
                                    }
                                    n.note?.let { Text(it, style = Type.Caption, color = Theme.tokens.textSecondary) }
                                }
                            }
                        }
                    }
                    TourismSection.ATTRACTIONS -> {
                        val m = TourismPresenter.attractions(state, dest, money)
                        if (m.rows.isEmpty()) item { EmptyState("No attractions", m.empty) }
                        items(m.rows.size) { i ->
                            val r = m.rows[i]
                            Card {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(r.name, style = Type.Heading, color = Theme.tokens.textPrimary)
                                    Pill(r.kind, r.tone.color())
                                }
                                Text("${r.visits}, ${r.wait}", style = Type.Body, color = Theme.tokens.textPrimary)
                                Text("Ticket ${r.ticket}", style = Type.Caption, color = Theme.tokens.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
