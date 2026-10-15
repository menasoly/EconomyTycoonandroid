package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.KeyValue
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.AnalysisPresenter

/** The company's books read as ratios, graded, with cost structure, cash runway, outlook and a peer comparison. */
@Composable
fun AnalysisScreen(ui: GameUi) {
    val model = remember(ui.revision / 5) { AnalysisPresenter.build(ui.session) }
    ScreenFrame("Analysis", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No company", "The analysis needs a company to look at.")
            return@ScreenFrame
        }
        ScreenList {
            item { Pill(model.headline, model.tone.color()) }
            if (model.worries.isNotEmpty()) {
                item { SectionHeader("Weak spots") }
                item { Card { for (w in model.worries) Text(w, style = Type.Body, color = Theme.tokens.textPrimary) } }
            }
            item { SectionHeader("Cash") }
            item { Card { FactList(listOf(model.runway)) } }
            item { SectionHeader("Ratios") }
            items(model.readings.size) { i ->
                val r = model.readings[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.label, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(r.grade, r.tone.color())
                    }
                    Text(r.value, style = Type.Title, color = Theme.tokens.textPrimary)
                    Text(r.hint, style = Type.Caption, color = Theme.tokens.textSecondary)
                }
            }
            item { SectionHeader("What drives the return") }
            item { Card { FactList(model.returns) } }
            item { SectionHeader("How hard the assets work") }
            item { Card { FactList(model.efficiency) } }
            item { SectionHeader("Costs") }
            item {
                Card {
                    if (model.costs.isEmpty()) Text("No costs in the last 30 days.", style = Type.Body, color = Theme.tokens.textSecondary)
                    for (c in model.costs) {
                        Column {
                            KeyValue(c.label + if (c.variable) "" else " (fixed)", c.amount + " · " + c.share)
                            MeterBar(c.ratio)
                        }
                    }
                }
            }
            item { Card { FactList(model.breakEven) } }
            item { SectionHeader("Outlook") }
            item { Card { FactList(model.outlook) } }
            item { app.many.economytycoon.ui.compose.components.SecondaryButton("Value the company", { ui.open(app.many.economytycoon.ui.nav.Route.Valuation) }, Modifier.fillMaxWidth()) }
            item { SectionHeader("Against other companies") }
            item { Text(model.peerNote, style = Type.Caption, color = Theme.tokens.textSecondary) }
            items(model.peers.size) { i ->
                val p = model.peers[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.label, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(p.place, p.tone.color())
                    }
                    KeyValue("You", p.you)
                    KeyValue("Typical", p.median)
                    KeyValue("Best", p.best)
                }
            }
        }
    }
}
