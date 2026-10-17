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
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.Outlook
import app.many.economytycoon.ui.present.ValuationPresenter

/** What the company is worth by several methods, how sure that is, and what the market makes of it. */
@Composable
fun ValuationScreen(ui: GameUi) {
    var outlook by remember { mutableStateOf(Outlook.STANDARD) }
    val model = remember(ui.revision / 10, outlook) { ValuationPresenter.build(ui.session, outlook) }
    ScreenFrame("Valuation", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No company", ValuationPresenter.NOTHING)
            return@ScreenFrame
        }
        ScreenList {
            item { Text("Assumptions", style = Type.Label, color = Theme.tokens.textSecondary) }
            item { ChoiceChips(Outlook.values().toList(), outlook, { it.label }, { outlook = it }) }
            item { TileGrid(model.tiles) }
            item { Text(model.verdict, style = Type.Body, color = model.tone.color()) }
            item { SectionHeader("By method") }
            items(model.methods.size) { i ->
                val m = model.methods[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(m.label, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Text(m.value, style = Type.Heading, color = if (m.applicable) Theme.tokens.textPrimary else Theme.tokens.textSecondary)
                    }
                    Text("Per share ${m.perShare}, weight ${m.weight}", style = Type.Caption, color = Theme.tokens.textSecondary)
                    Text(m.note, style = Type.Caption, color = Theme.tokens.textSecondary)
                }
            }
            val grid = model.grid
            if (grid != null) {
                item { SectionHeader("How the cash-flow value moves") }
                items(grid.rows.size) { r ->
                    val row = grid.rows[r]
                    Card {
                        Text(row.first, style = Type.Heading, color = Theme.tokens.textPrimary)
                        for (c in row.second.indices) Text("${grid.columns[c]}: ${row.second[c]}", style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                }
            }
            if (model.reverse.isNotEmpty()) {
                item { SectionHeader("What the market price implies") }
                item { FactList(model.reverse) }
            }
        }
    }
}
