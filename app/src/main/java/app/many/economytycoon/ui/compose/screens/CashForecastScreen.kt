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
import app.many.economytycoon.cashflow.Scenario
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.CashForecastPresenter

/** Where the company's cash is heading over the next twelve weeks. */
@Composable
fun CashForecastScreen(ui: GameUi) {
    var scenario by remember { mutableStateOf(Scenario.EXPECTED) }
    val model = remember(ui.revision / 10, scenario) { CashForecastPresenter.build(ui.session, scenario) }
    ScreenFrame("Cash forecast", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No company", CashForecastPresenter.NOTHING)
            return@ScreenFrame
        }
        ScreenList {
            item { Text("View", style = Type.Label, color = Theme.tokens.textSecondary) }
            item { ChoiceChips(Scenario.values().toList(), scenario, { it.label }, { scenario = it }) }
            item { TileGrid(model.tiles) }
            item { Text(model.verdict, style = Type.Body, color = model.tone.color()) }
            item { SectionHeader("The three views") }
            item { Card { FactList(model.scenarios) } }
            if (model.dueSoon.isNotEmpty()) {
                item { SectionHeader("Payments already due") }
                item { Card { FactList(model.dueSoon) } }
            }
            item { SectionHeader("Week by week") }
            items(model.weeks.size) { i ->
                val w = model.weeks[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(w.label, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Text(w.balance, style = Type.Heading, color = w.tone.color())
                    }
                    Text("Change in the week: ${w.change}", style = Type.Caption, color = Theme.tokens.textSecondary)
                    for (e in w.events) Text(e, style = Type.Caption, color = Theme.tokens.textSecondary)
                }
            }
        }
    }
}
