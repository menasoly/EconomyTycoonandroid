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

import app.many.economytycoon.urban.LineView
import app.many.economytycoon.ui.present.UrbanTransitPresenter

/** Roads and transit lines of a city, with controls for service frequency. [issue] runs a command and returns its message. */
@Composable
fun UrbanTransitScreen(ui: GameUi, view: UrbanView, issue: (UrbanCommand) -> String) {
    var selected by remember { mutableStateOf<CityView?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    ScreenFrame("Transport", onBack = { ui.back() }) {
        if (view.cities.isEmpty()) {
            EmptyState("No cities", "No city has a plan yet.")
            return@ScreenFrame
        }
        val current = view.cities.firstOrNull { it.city == selected?.city } ?: view.cities.first()
        val model = remember(current) { UrbanTransitPresenter.build(current) }
        ScreenList {
            item { ChoiceChips<CityView>(view.cities, current, { it.name }, { selected = it }) }
            item { TileGrid(model.tiles) }
            item { Text(model.note, style = Type.Caption, color = Theme.tokens.textSecondary) }
            message?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.textSecondary) } }
            item { SectionHeader("Lines") }
            items(model.lines.size) { i ->
                val l = model.lines[i]
                val source: LineView? = current.lines.firstOrNull { it.id == l.id }
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(l.name + " (" + l.mode + ")", style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(l.status, l.tone.color())
                    }
                    Text(l.route, style = Type.Caption, color = Theme.tokens.textSecondary)
                    FactList(l.facts)
                    if (source != null) {
                        if (l.canSpeedUp) SecondaryButton("More frequent", { message = issue(UrbanTransitPresenter.speedUp(current.city, source)) }, Modifier.fillMaxWidth())
                        if (l.canSlowDown) SecondaryButton("Less frequent", { message = issue(UrbanTransitPresenter.slowDown(current.city, source)) }, Modifier.fillMaxWidth())
                        if (l.canToggle) SecondaryButton(l.toggleLabel, { message = issue(UrbanTransitPresenter.toggle(current.city, source)) }, Modifier.fillMaxWidth())
                    }
                }
            }
            item { SectionHeader("Busiest roads") }
            items(model.roads.size) { i ->
                val r = model.roads[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.name, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(r.service, r.tone.color())
                    }
                    Text(r.roadClass + ", " + r.load, style = Type.Caption, color = Theme.tokens.textSecondary)
                    r.toll?.let { Text("Toll " + it, style = Type.Caption, color = Theme.tokens.textSecondary) }
                }
            }
        }
    }
}
