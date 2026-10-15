package app.many.economytycoon.ui.compose.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.many.economytycoon.markets.Side
import app.many.economytycoon.core.OrderId
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.present.OrdersPresenter
import app.many.economytycoon.ui.present.Tone

@Composable
fun OrdersScreen(ui: GameUi) {
    val orders = remember(ui.revision) { OrdersPresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    ScreenFrame("My orders", onBack = { ui.back() }) {
        if (orders.isEmpty() || company == null) {
            EmptyState("No open orders", "Orders you place from a good's page appear here until they fill or expire.")
            return@ScreenFrame
        }
        ScreenList {
            items(orders.size) { i ->
                val o = orders[i]
                Card {
                    ListRow(o.title, subtitle = o.detail, badge = if (o.side == Side.SELL) "Sell" else "Buy", badgeTone = if (o.side == Side.SELL) Tone.INFO else Tone.WARN)
                    MeterBar(o.fillFraction)
                    Text(o.expires, style = Type.Caption, color = Theme.tokens.textSecondary)
                    LinkButton("Cancel order", { ui.issue(Command.CancelOrder(company, OrderId(o.id)), "Order cancelled.") })
                }
            }
        }
    }
}
