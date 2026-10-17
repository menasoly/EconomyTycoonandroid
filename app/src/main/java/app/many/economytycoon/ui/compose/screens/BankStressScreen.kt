package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.many.economytycoon.ui.compose.components.Card
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
import app.many.economytycoon.ui.present.StressScenarioRow
import app.many.economytycoon.ui.present.StressView

/** Run a scenario against every bank in the country and read where capital would give way. */
@Composable
fun BankStressScreen(
    scenarios: List<StressScenarioRow>,
    result: StressView?,
    onRun: (String) -> Unit,
    onBack: () -> Unit,
) {
    ScreenFrame("Stress test", onBack = onBack) {
        ScreenList {
            item { SectionHeader("Scenarios") }
            items(scenarios.size) { i ->
                val s = scenarios[i]
                Card {
                    Text(s.name, style = Type.Heading, color = Theme.tokens.textPrimary)
                    Text(s.description, style = Type.Body, color = Theme.tokens.textSecondary)
                    Text(s.shocks, style = Type.Caption, color = Theme.tokens.textSecondary)
                    SecondaryButton("Run", { onRun(s.id) }, Modifier.fillMaxWidth())
                }
            }
            if (result != null) {
                item { SectionHeader(result.title) }
                item { Text(result.summary, style = Type.Body, color = Theme.tokens.textPrimary) }
                item { TileGrid(result.tiles) }
                item { Card { FactList(result.facts) } }
                items(result.rows.size) { i ->
                    val r = result.rows[i]
                    Card {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(r.name, style = Type.Heading, color = Theme.tokens.textPrimary)
                            Pill(r.verdict, r.tone.color())
                        }
                        Text("Capital ${r.start} to ${r.trough} · shortfall ${r.shortfall}", style = Type.Caption, color = Theme.tokens.textSecondary)
                        Text(r.note, style = Type.Body, color = Theme.tokens.textSecondary)
                    }
                }
            }
        }
    }
}
