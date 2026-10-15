package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.model.BuybackForm
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.model.ListingForm
import app.many.economytycoon.ui.model.TradeForm
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.ExchangePresenter
import app.many.economytycoon.ui.present.HoldingRow
import app.many.economytycoon.ui.present.StockRow
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.visualization.compose.LineChart
import app.many.economytycoon.visualization.compose.SparklineView

@Composable
fun ExchangeScreen(ui: GameUi) {
    var floatText by remember { mutableStateOf("25") }
    val floatPpm = floatText.toDoubleOrNull()?.let { (it * 10_000).toLong() }
    val model = remember(ui.revision, floatPpm) { ExchangePresenter.build(ui.session, floatPpm) }
    val company = ui.session.selectedCompany
    var floating by remember { mutableStateOf(false) }

    ScreenFrame("Stock exchange", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Choose a company to trade for.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            item {
                Card {
                    if (model.index.points.size < 2) Text("The index chart fills in as days pass.", style = Type.Body, color = Theme.tokens.textSecondary)
                    else LineChart(listOf(model.index), xLabel = { "day ${it.toLong()}" })
                }
            }
            if (!model.flotation.listed) {
                item {
                    Card {
                        Text("Go public", style = Type.Heading, color = Theme.tokens.textPrimary)
                        val problem = model.flotation.problem
                        if (problem != null) Text("Not yet: $problem.", style = Type.Body, color = Theme.tokens.warning)
                        else {
                            Text("Sell part of your company to the public to raise cash. You keep control while you hold the majority.", style = Type.Body, color = Theme.tokens.textSecondary)
                            PrimaryButton("Float your company", { floating = true }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (model.holdings.isNotEmpty()) {
                item { SectionHeader("Your portfolio") }
                items(model.holdings.size) { i -> HoldingView(ui, model.holdings[i]) }
            }
            item { SectionHeader("Listed companies") }
            if (model.board.isEmpty()) item { EmptyState("Nobody is listed yet", "Companies can list once they have traded for a while and grown.") }
            items(model.board.size) { i -> BoardRow(ui, model.board[i]) }
            item { Text(model.portfolioNote, style = Type.Caption, color = Theme.tokens.textSecondary) }
        }
        if (floating) FloatDialog(ui, company, floatText, { floatText = it }) { floating = false }
    }
}

@Composable
private fun BoardRow(ui: GameUi, r: StockRow) {
    Row(Modifier.fillMaxWidth().clickable { ui.open(Route.Stock(r.id.value)) }.padding(vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(r.name + if (r.mine) " (you)" else "", style = Type.Heading, color = Theme.tokens.textPrimary)
            Text("Worth ${r.marketCap}" + if (r.held > 0) " · you hold ${r.held}" else "", style = Type.Caption, color = Theme.tokens.textSecondary)
        }
        SparklineView(r.spark, color = r.tone.color())
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
            Text(r.price, style = Type.Heading, color = Theme.tokens.textPrimary)
            Text(r.change, style = Type.Caption, color = r.tone.color())
        }
    }
}

@Composable
private fun HoldingView(ui: GameUi, h: HoldingRow) {
    Card {
        ListRow(h.name, subtitle = "${h.shares} shares at ${h.price}", trailing = h.value, onClick = { ui.open(Route.Stock(h.id.value)) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Paid ${h.cost}", style = Type.Caption, color = Theme.tokens.textSecondary)
            Text("${h.gain} (${h.gainPercent})", style = Type.Caption, color = h.gainTone.color())
        }
    }
}

@Composable
private fun FloatDialog(ui: GameUi, company: CompanyId, floatText: String, onText: (String) -> Unit, onDone: () -> Unit) {
    var result by remember { mutableStateOf<FormResult?>(null) }
    val preview = remember(floatText) { ExchangePresenter.flotation(ui.session, floatText.toDoubleOrNull()?.let { (it * 10_000).toLong() }) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text("Float your company", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { LabeledField("Share of the company to sell (%)", floatText, onText, kind = FieldKind.DECIMAL, error = result?.errorFor("float")) }
                item { Text("Between ${preview.minPercent}% and ${preview.maxPercent}%.", style = Type.Caption, color = Theme.tokens.textSecondary) }
                preview.preview?.let { p -> item { Text(p, style = Type.Body, color = Theme.tokens.textPrimary) } }
                item { Text("New shares are sold at a discount to what the business is worth, and bankers take a fee from the proceeds.", style = Type.Caption, color = Theme.tokens.textSecondary) }
            }
        },
        confirmButton = {
            PrimaryButton("Float", {
                val r = ListingForm(company, floatText, ui.session.economy.exchange.params.minFloatPpm, ui.session.economy.exchange.params.maxFloatPpm).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Your company is now listed.")) onDone()
            })
        },
        dismissButton = { LinkButton("Not now", onDone) },
    )
}

@Composable
fun StockScreen(ui: GameUi, issuer: Long) {
    val id = CompanyId(issuer)
    val model = remember(ui.revision) { ExchangePresenter.detail(ui.session, id) }
    val company = ui.session.selectedCompany
    var trading by remember { mutableStateOf<Boolean?>(null) }
    var buyingBack by remember { mutableStateOf(false) }
    var delisting by remember { mutableStateOf(false) }

    ScreenFrame(model?.name ?: "Stock", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("Not listed", "This company is not on the exchange.")
            return@ScreenFrame
        }
        ScreenList {
            item {
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(model.price, style = Type.Title, color = Theme.tokens.textPrimary)
                        Text(model.change, style = Type.Heading, color = model.tone.color())
                    }
                    if (model.history.points.size < 2) Text("The price chart fills in as days pass.", style = Type.Body, color = Theme.tokens.textSecondary)
                    else LineChart(listOf(model.history), xLabel = { "day ${it.toLong()}" })
                    Text(model.verdict, style = Type.Caption, color = model.verdictTone.color())
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PrimaryButton("Buy", { trading = true }, Modifier.weight(1f), enabled = model.canBuy)
                    SecondaryButton("Sell", { trading = false }, Modifier.weight(1f), enabled = model.canSell)
                }
            }
            if (model.mine) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        SecondaryButton("Buy back shares", { buyingBack = true }, Modifier.weight(1f), enabled = model.canBuyBack)
                        SecondaryButton("Go private", { delisting = true }, Modifier.weight(1f), enabled = model.canDelist)
                    }
                }
            }
            if (model.position.isNotEmpty()) {
                item { SectionHeader("Your position") }
                item { Card { model.position.forEach { f -> ListRow(f.label, subtitle = f.hint, trailing = f.value, trailingTone = f.tone) } } }
            }
            item { SectionHeader("About the stock") }
            item { Card { model.facts.forEach { f -> ListRow(f.label, subtitle = f.hint, trailing = f.value, trailingTone = f.tone) } } }
        }
        val side = trading
        if (side != null) TradeDialog(ui, company, id, model.name, side, if (side) model.publicShares else model.held) { trading = null }
        if (buyingBack) BuybackDialog(ui, company, model.publicShares) { buyingBack = false }
        if (delisting) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { delisting = false },
                containerColor = Theme.tokens.surface,
                title = { Text("Go private?", style = Type.Title, color = Theme.tokens.textPrimary) },
                text = { Text("You buy every public share back at a small premium to the market price and leave the exchange. This needs the cash up front.", style = Type.Body, color = Theme.tokens.textPrimary) },
                confirmButton = { PrimaryButton("Buy out the public", { if (ui.issue(Command.DelistCompany(company), "Your company is private again.")) delisting = false }) },
                dismissButton = { LinkButton("Cancel", { delisting = false }) },
            )
        }
    }
}

@Composable
private fun TradeDialog(ui: GameUi, company: CompanyId, issuer: CompanyId, name: String, buying: Boolean, available: Long, onDone: () -> Unit) {
    var shares by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val quote = remember(shares, ui.revision) { ExchangePresenter.quote(ui.session, issuer, buying, shares.toLongOrNull() ?: 0L) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text((if (buying) "Buy " else "Sell ") + name, style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { Text((if (buying) "Available from the public: " else "You hold: ") + available, style = Type.Caption, color = Theme.tokens.textSecondary) }
                item { LabeledField("Shares", shares, { shares = it }, kind = FieldKind.NUMBER, error = result?.errorFor("shares")) }
                if (quote.summary.isNotBlank()) {
                    item { Text(quote.summary, style = Type.Body, color = Theme.tokens.textPrimary) }
                    item { Text(quote.total, style = Type.Heading, color = Theme.tokens.textPrimary) }
                    item { Text(quote.impact, style = Type.Caption, color = quote.tone.color()) }
                }
                quote.problem?.let { p -> item { Text(p, style = Type.Caption, color = Theme.tokens.negative) } }
            }
        },
        confirmButton = {
            PrimaryButton(if (buying) "Buy" else "Sell", {
                val r = TradeForm(company, issuer, buying, shares, available).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, if (buying) "Shares bought." else "Shares sold.")) onDone()
            }, enabled = quote.problem == null)
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}

@Composable
private fun BuybackDialog(ui: GameUi, company: CompanyId, publicShares: Long, onDone: () -> Unit) {
    var shares by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text("Buy back shares", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { Text("The public holds $publicShares shares. Buying them back raises your share of the profits.", style = Type.Body, color = Theme.tokens.textSecondary) }
                item { LabeledField("Shares", shares, { shares = it }, kind = FieldKind.NUMBER, error = result?.errorFor("shares")) }
            }
        },
        confirmButton = {
            PrimaryButton("Buy back", {
                val r = BuybackForm(company, shares, publicShares).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Shares bought back.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
