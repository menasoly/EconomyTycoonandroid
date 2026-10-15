package app.many.economytycoon.ui.compose.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.OutlookModel
import app.many.economytycoon.ui.present.OutlookPresenter
import app.many.economytycoon.visualization.compose.LineChart
import kotlin.concurrent.thread

/** A trial run of the next weeks or months with nothing changed. The run happens off the screen's thread. */
@Composable
fun OutlookScreen(ui: GameUi) {
    var days by remember { mutableStateOf(90) }
    var result by remember { mutableStateOf<OutlookModel?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    ScreenFrame("Outlook", onBack = { ui.back() }) {
        if (ui.session.selectedCompany == null) {
            EmptyState("No company", "The outlook follows a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text(OutlookPresenter.CAVEAT, style = Type.Body, color = Theme.tokens.textSecondary) }
            item { ChoiceChips(OutlookPresenter.horizons, days, { OutlookPresenter.horizonLabel(it) }, { days = it; result = null }) }
            item {
                PrimaryButton(if (busy) "Working…" else "Run the trial", {
                    if (!busy) {
                        val input = OutlookPresenter.input(ui.session)
                        val settings = ui.session.settings
                        val length = days
                        if (input != null) {
                            busy = true; failed = false
                            thread(name = "outlook") {
                                try { result = OutlookPresenter.run(input, length, settings) } catch (e: Exception) { failed = true }
                                busy = false
                            }
                        }
                    }
                }, androidx.compose.ui.Modifier)
            }
            if (failed) item { Text("The trial could not be run. Save the game and try again.", style = Type.Body, color = Theme.tokens.negative) }
            result?.let { m ->
                item { Text(m.headline, style = Type.Title, color = m.tone.color()) }
                for (w in m.warnings) item { Text(w, style = Type.Body, color = Theme.tokens.warning) }
                item { SectionHeader("Where it goes") }
                item { Card { LineChart(m.series, xLabel = { "day ${it.toInt()}" }) } }
                item { Card { FactList(m.tiles) } }
            }
        }
    }
}
