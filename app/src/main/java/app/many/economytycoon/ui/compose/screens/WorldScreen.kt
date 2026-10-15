package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.SwitchRow
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.WorldPresenter
import app.many.economytycoon.visualization.MapLayers
import app.many.economytycoon.visualization.MapMarker
import app.many.economytycoon.visualization.MarkerKind
import app.many.economytycoon.visualization.WorldMapModel
import app.many.economytycoon.visualization.compose.DonutChart
import app.many.economytycoon.visualization.compose.LineChart
import app.many.economytycoon.visualization.compose.MapCanvas

private enum class WorldView(val label: String) { MAP("Map"), COUNTRIES("Countries"), REGIONS("Regions") }

@Composable
fun WorldScreen(ui: GameUi) {
    var view by remember { mutableStateOf(WorldView.MAP) }
    ScreenFrame("World", onBack = { ui.back() }) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(horizontal = Spacing.L)) { ChoiceChips(WorldView.values().toList(), view, { it.label }, { view = it }) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (view) {
                    WorldView.MAP -> MapPane(ui)
                    WorldView.COUNTRIES -> CountriesPane(ui)
                    WorldView.REGIONS -> RegionsPane(ui)
                }
            }
        }
    }
}

@Composable
private fun MapPane(ui: GameUi) {
    val model = remember(ui.revision / 5, ui.session.selectedCompany) { WorldMapModel.build(ui.session.economy, ui.session.selectedCompany) }
    var layers by remember { mutableStateOf(MapLayers()) }
    var selected by remember { mutableStateOf<MapMarker?>(null) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { MapCanvas(model, layers, selected, { selected = it }) }
        Column(Modifier.padding(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            val s = selected
            if (s != null) {
                Text(s.name, style = Type.Heading, color = Theme.tokens.textPrimary)
                Text(s.detail, style = Type.Caption, color = Theme.tokens.textSecondary)
                when (s.kind) {
                    MarkerKind.REGION -> LinkButton("Open region", { ui.open(Route.Region(s.id)) })
                    MarkerKind.FACILITY -> LinkButton("Open facility", { ui.open(Route.FacilityDetail(s.id)) })
                    MarkerKind.DEPOSIT -> LinkButton("Plan a mine here", { ui.open(Route.Build(ui.session.economy.world.deposits.all().first { d -> d.id.value == s.id }.region.value)) })
                    else -> {}
                }
            } else Text("Pinch to zoom, drag to pan, tap a place for details.", style = Type.Caption, color = Theme.tokens.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                LinkButton((if (layers.deposits) "Hide" else "Show") + " deposits", { layers = layers.copy(deposits = !layers.deposits) })
                LinkButton((if (layers.routes) "Hide" else "Show") + " routes", { layers = layers.copy(routes = !layers.routes) })
            }
        }
    }
}

@Composable
private fun CountriesPane(ui: GameUi) {
    val model = remember(ui.revision / 5) { WorldPresenter.build(ui.session) }
    var chosen by remember { mutableStateOf<CountryId?>(null) }
    ScreenList {
        item { Text("World population ${model.population}", style = Type.Body, color = Theme.tokens.textSecondary) }
        items(model.countries.size) { i ->
            val c = model.countries[i]
            Card(onClick = { chosen = if (chosen == c.id) null else c.id }) {
                ListRow(c.name, badge = c.phase, badgeTone = c.phaseTone)
                FactList(c.facts)
                if (chosen == c.id) {
                    val macro = WorldPresenter.macro(ui.session, c.id)
                    if (macro.gdp.points.size >= 2) {
                        Text("GDP", style = Type.Label, color = Theme.tokens.textSecondary)
                        LineChart(listOf(macro.gdp), height = 120, xLabel = { "day ${it.toLong()}" })
                        Text("Inflation and unemployment (%)", style = Type.Label, color = Theme.tokens.textSecondary)
                        LineChart(listOf(macro.inflation, macro.unemployment, macro.policyRate), height = 140, xLabel = { "day ${it.toLong()}" })
                    } else Text("History builds up as days pass.", style = Type.Caption, color = Theme.tokens.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun RegionsPane(ui: GameUi) {
    val model = remember(ui.revision / 5) { WorldPresenter.build(ui.session) }
    ScreenList {
        items(model.regions.size) { i ->
            val r = model.regions[i]
            Card(onClick = { ui.open(Route.Region(r.id.value)) }) {
                ListRow(r.name, subtitle = "${r.country} · ${r.population} people", trailing = r.employment, trailingTone = r.employmentTone)
                Text("Average wage ${r.wage} · ${r.facilities}", style = Type.Caption, color = Theme.tokens.textSecondary)
                Text("Happiness", style = Type.Caption, color = Theme.tokens.textSecondary)
                MeterBar(r.happiness)
                Text("Power grid load", style = Type.Caption, color = Theme.tokens.textSecondary)
                MeterBar(r.grid, color = if (r.grid > 0.9) Theme.tokens.warning else Theme.tokens.accent)
            }
        }
    }
}
