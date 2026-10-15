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
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.trade.TradeSort
import app.many.economytycoon.ui.present.TradePresenter

/** Goods that sell for more somewhere else than they cost here, after freight. */
@Composable
fun TradeScreen(ui: GameUi) {
    var size by remember { mutableStateOf(100L) }
    var sort by remember { mutableStateOf(TradeSort.PROFIT) }
    val model = remember(ui.revision / 30, size, sort) { TradePresenter.build(ui.session, size, sort) }
    ScreenFrame("Trade finder", onBack = { ui.back() }) {
        ScreenList {
            item { Text("Shipment size", style = Type.Label, color = Theme.tokens.textSecondary) }
            item { ChoiceChips(model.sizes, size, { "$it units" }, { size = it }) }
            item { Text("Order by", style = Type.Label, color = Theme.tokens.textSecondary) }
            item { ChoiceChips(TradeSort.values().toList(), sort, { it.label }, { sort = it }) }
            item { TileGrid(model.tiles) }
            if (model.rows.isEmpty()) item { EmptyState("Nothing worth moving", model.empty) }
            items(model.rows.size) { i ->
                val r = model.rows[i]
                Card(onClick = { ui.open(Route.Good(r.good.key)) }) {
                    ListRow(r.title, subtitle = r.route, trailing = r.profit, badge = r.margin, badgeTone = r.tone)
                    Text(r.prices, style = Type.Caption, color = Theme.tokens.textSecondary)
                    Text(r.timing, style = Type.Caption, color = Theme.tokens.textSecondary)
                    if (r.note != null) Text(r.note, style = Type.Caption, color = r.tone.color())
                }
            }
        }
    }
}
