package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
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
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.FacilityProfitPresenter

/** What each facility earns at today's prices, line by line, and where a different recipe would earn more. */
@Composable
fun FacilityProfitScreen(ui: GameUi) {
    val model = remember(ui.revision / 10) { FacilityProfitPresenter.build(ui.session) }
    ScreenFrame("Profit by facility", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No company", "Choose a company to see its facilities.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            if (model.rows.isEmpty()) item { EmptyState("Nothing built yet", model.empty) }
            if (model.switches.isNotEmpty()) {
                item { SectionHeader("Lines that could earn more") }
                items(model.switches.size) { i ->
                    val s = model.switches[i]
                    Card { ListRow(s.title, trailing = s.gain, trailingTone = app.many.economytycoon.ui.present.Tone.GOOD, subtitle = s.detail) }
                }
            }
            if (model.rows.isNotEmpty()) item { SectionHeader("Facilities, best first") }
            items(model.rows.size) { i ->
                val r = model.rows[i]
                Card(onClick = { ui.open(Route.FacilityDetail(r.id.value)) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Text(r.net, style = Type.Heading, color = r.tone.color())
                    }
                    Text(r.subtitle, style = Type.Caption, color = Theme.tokens.textSecondary)
                    FactList(r.facts)
                    for (l in r.lines) {
                        Text("${l.title}: ${l.profit}", style = Type.Body, color = l.tone.color())
                        Text(l.detail, style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                    if (r.note != null) Text(r.note, style = Type.Caption, color = Theme.tokens.textSecondary)
                }
            }
        }
    }
}
