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
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.urban.CityView
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.UrbanView

import app.many.economytycoon.core.CityId
import app.many.economytycoon.urban.OverlayKind
import app.many.economytycoon.urban.UrbanOverlay
import app.many.economytycoon.ui.present.UrbanMapPresenter
import app.many.economytycoon.ui.present.UrbanPresenter

/** City planning: how the cities rank and what is going on in each district. [overlay] supplies the map data of a city. */
@Composable
fun UrbanScreen(ui: GameUi, view: UrbanView, overlay: (CityId, OverlayKind) -> UrbanOverlay?) {
    var selected by remember { mutableStateOf<CityId?>(null) }
    var kind by remember { mutableStateOf(OverlayKind.ZONING) }
    val overview = remember(view) { UrbanPresenter.overview(view) }
    ScreenFrame("City planning", onBack = { ui.back() }) {
        if (view.cities.isEmpty()) {
            EmptyState("No cities", overview.note)
            return@ScreenFrame
        }
        val current = view.cities.firstOrNull { it.city == selected } ?: view.cities.first()
        val city = remember(current) { UrbanPresenter.city(current) }
        val map = remember(current, kind) { overlay(current.city, kind)?.let { UrbanMapPresenter.build(it, current.name) } }
        ScreenList {
            item { TileGrid(overview.tiles) }
            item { Text(overview.note, style = Type.Caption, color = Theme.tokens.textSecondary) }
            item { ChoiceChips<CityView>(view.cities, current, { it.name }, { selected = it.city }) }
            item { SectionHeader(city.title) }
            item { TileGrid(city.tiles) }
            item { Text(city.trend, style = Type.Caption, color = Theme.tokens.textSecondary) }
            item { Card { FactList(city.factors) } }
            if (map != null) {
                item { SectionHeader(map.title) }
                item { ChoiceChips<OverlayKind>(OverlayKind.values().toList(), kind, { it.label }, { kind = it }) }
                item { Text(map.summary, style = Type.Caption, color = Theme.tokens.textSecondary) }
                items(map.legend.size) { i -> Text(map.legend[i].label, style = Type.Caption, color = Theme.tokens.textPrimary) }
                items(map.lineSummary.size) { i -> Text(map.lineSummary[i].label + ": " + map.lineSummary[i].value, style = Type.Caption, color = Theme.tokens.textSecondary) }
            }
            if (city.insights.isNotEmpty()) item { SectionHeader("Planning advice") }
            items(city.insights.size) { i ->
                val r = city.insights[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(r.severity, r.tone.color())
                    }
                    Text(r.detail, style = Type.Body, color = Theme.tokens.textSecondary)
                    r.suggestion?.let { Text(it, style = Type.Caption, color = Theme.tokens.textSecondary) }
                }
            }
            item { SectionHeader("Districts") }
            items(city.districts.size) { i ->
                val d = city.districts[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(d.name, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(d.zone, d.tone.color())
                    }
                    if (d.badges.isNotEmpty()) Text(d.badges.joinToString(", "), style = Type.Caption, color = Theme.tokens.textSecondary)
                    FactList(d.facts)
                }
            }
        }
    }
}
