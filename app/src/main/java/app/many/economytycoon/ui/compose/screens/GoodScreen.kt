package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.markets.Side
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.model.OrderForm
import app.many.economytycoon.ui.present.GoodDetailPresenter
import app.many.economytycoon.ui.present.PriceStatsPresenter
import app.many.economytycoon.ui.present.Tone
import app.many.economytycoon.visualization.compose.BarList
import app.many.economytycoon.visualization.compose.LineChart

@Composable
fun GoodScreen(ui: GameUi, key: String) {
    val good = GoodId(key)
    val model = remember(ui.revision, key) { GoodDetailPresenter.build(ui.session, good) }
    val priceStats = remember(ui.revision / 10, key) { PriceStatsPresenter.build(ui.session, good) }
    var region by remember { mutableStateOf<RegionId?>(null) }
    var trading by remember { mutableStateOf<Side?>(null) }

    ScreenFrame(model?.name ?: "Good", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("Not traded", "This good has no market.", action = "Back", onAction = { ui.back() })
            return@ScreenFrame
        }
        val selected = region ?: model.regions.firstOrNull()?.region
        val tokens = Theme.tokens
        val depth = remember(ui.revision, selected, key) { selected?.let { GoodDetailPresenter.depth(ui.session, it, good) }.orEmpty() }
        ScreenList {
            item { Text(model.category, style = Type.Body, color = Theme.tokens.textSecondary) }
            item { Card { FactList(model.facts) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PrimaryButton("Sell", { trading = Side.SELL }, Modifier.weight(1f))
                    PrimaryButton("Buy", { trading = Side.BUY }, Modifier.weight(1f))
                }
            }
            item { SectionHeader("Price history") }
            item { Card { if (model.history.points.size < 2) Text("Not enough trading yet.", style = Type.Body, color = Theme.tokens.textSecondary) else LineChart(listOf(model.history), xLabel = { "day ${it.toLong()}" }) } }
            if (priceStats != null) {
                item { SectionHeader("How the price behaves") }
                item {
                    Card {
                        Text(priceStats.headline, style = Type.Body, color = priceStats.tone.color())
                        FactList(priceStats.facts)
                        for (n in priceStats.notes) Text(n, style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                }
            }
            item { SectionHeader("By region") }
            items(model.regions.size) { i ->
                val r = model.regions[i]
                Card(onClick = { region = r.region }) {
                    ListRow(r.name, subtitle = "Ask / bid ${r.spread} · traded ${r.volume}", trailing = r.price, badge = if (r.region == selected) "Shown below" else null, badgeTone = Tone.INFO)
                    Text(r.change, style = Type.Caption, color = r.changeTone.color())
                }
            }
            item { SectionHeader("Order book") }
            item {
                Card {
                    if (depth.isEmpty()) Text("No open orders in this region.", style = Type.Body, color = Theme.tokens.textSecondary)
                    else BarList(depth.map { (if (it.side == Side.SELL) "Ask " else "Bid ") + it.price to it.fraction }, valueText = { "" }, colorOf = { idx -> if (depth[idx].side == Side.SELL) tokens.negative else tokens.positive })
                    for (d in depth) Text("${if (d.side == Side.SELL) "Sell" else "Buy"} ${d.quantity} at ${d.price} (${d.orders} orders)", style = Type.Caption, color = Theme.tokens.textSecondary)
                }
            }
            if (model.producers.isNotEmpty()) {
                item { SectionHeader("Who makes it") }
                item { Card { for ((name, n) in model.producers) ListRow(name, trailing = "$n sites") } }
            }
            item { SectionHeader("Recent trades") }
            if (model.trades.isEmpty()) item { Text("No trades yet.", style = Type.Body, color = Theme.tokens.textSecondary) }
            items(model.trades.size) { i ->
                val t = model.trades[i]
                ListRow("${t.quantity} at ${t.price}", subtitle = "${t.whenText} · ${t.region} · ${t.parties}")
            }
        }
        val side = trading
        if (side != null && selected != null) OrderDialog(ui, good, side, selected, model.name) { trading = null }
    }
}

@Composable
fun OrderDialog(ui: GameUi, good: GoodId, side: Side, region: RegionId, goodName: String, onDone: () -> Unit) {
    val company = ui.session.selectedCompany ?: return
    val facilities = remember(ui.revision) { ui.session.reports.company.facilities(company) }
    var facility by remember { mutableStateOf(facilities.firstOrNull()?.id) }
    var quantity by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(5) }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val cash = remember(ui.revision) { ui.session.reports.company.headline(company)?.cash }
    val stock = remember(ui.revision, facility) {
        facility?.let { f -> ui.session.reports.company.stock(f).firstOrNull { it.good == good }?.let { it.quantity - it.reserved } }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text((if (side == Side.SELL) "Sell " else "Buy ") + goodName, style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (facilities.isEmpty()) Text("You need a facility to trade from.", style = Type.Body, color = Theme.tokens.warning)
                else {
                    Text(if (side == Side.SELL) "Sell from" else "Deliver to", style = Type.Label, color = Theme.tokens.textSecondary)
                    ChoiceChips(facilities.map { it.id }, facility, { id -> facilities.first { it.id == id }.name }, { facility = it })
                }
                if (side == Side.SELL && stock != null) Text("In stock: ${app.many.economytycoon.session.Formatting.quantity(stock)}", style = Type.Caption, color = Theme.tokens.textSecondary)
                LabeledField("Quantity", quantity, { quantity = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("quantity"))
                LabeledField("Limit price (blank for market)", limit, { limit = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("limit"))
                Text("Open for ${if (days == 1) "1 day" else "$days days"}", style = Type.Caption, color = Theme.tokens.textSecondary)
                ChoiceChips(listOf(1, 3, 5, 10, 30), days, { "${it}d" }, { days = it })
            }
        },
        confirmButton = {
            PrimaryButton("Place order", {
                val f = facility
                if (f == null) ui.showMessage("Choose a facility first.")
                else {
                    val form = OrderForm(company, f, side, good, quantity, limit, days, stock, cash)
                    val r = form.validate(); result = r
                    val cmd = r.commandOrNull
                    if (cmd != null && ui.issue(cmd, "Order placed.")) onDone()
                }
            }, enabled = facilities.isNotEmpty())
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
