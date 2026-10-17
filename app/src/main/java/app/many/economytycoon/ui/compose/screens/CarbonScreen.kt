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
import app.many.economytycoon.carbon.OffsetKind
import app.many.economytycoon.carbon.PermitSide
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.CarbonDesk
import app.many.economytycoon.ui.present.CarbonMarketPresenter
import app.many.economytycoon.ui.present.CarbonPresenter

/** The company's carbon position and the permit market of its home country. The caller supplies the desk. */
@Composable
fun CarbonScreen(ui: GameUi, desk: CarbonDesk) {
    var tick by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf(OffsetKind.METHANE_CAPTURE) }
    var side by remember { mutableStateOf(PermitSide.BUY) }
    var quantity by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    val model = remember(ui.revision / 5, tick) { CarbonPresenter.build(desk) }
    val country = model.accounts.firstOrNull { it.canBuy }?.country
    val market = remember(ui.revision / 5, tick, country) { country?.let { CarbonMarketPresenter.build(desk, it) } }
    ScreenFrame("Carbon", onBack = { ui.back() }) {
        ScreenList {
            item { TileGrid(model.tiles) }
            message?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.textSecondary) } }
            model.empty?.let { e -> item { EmptyState("No carbon policy", e) } }
            items(model.insights.size) { i ->
                val s = model.insights[i]
                Card { Text(s.title, style = Type.Heading, color = s.tone.color()); Text(s.detail, style = Type.Body, color = Theme.tokens.textSecondary) }
            }
            items(model.accounts.size) { i ->
                val a = model.accounts[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(a.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(a.regime, a.tone.color())
                    }
                    a.deadline?.let { d -> Text(d, style = Type.Caption, color = Theme.tokens.textSecondary) }
                    FactList(a.facts)
                }
            }
            if (market != null && market.canTrade) {
                item { SectionHeader("Permit market") }
                item { TileGrid(market.tiles) }
                item { Card { FactList(market.supply) } }
                items(market.auctions.size) { i ->
                    val a = market.auctions[i]
                    Card {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(a.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                            Pill(a.result, a.tone.color())
                        }
                        Text(a.detail, style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                }
                item { SectionHeader("Bid or trade") }
                item { ChoiceChips(listOf(PermitSide.BUY, PermitSide.SELL), side, { if (it == PermitSide.BUY) "Buy" else "Sell" }, { side = it }) }
                item { LabeledField("Tonnes", quantity, { quantity = it }, kind = FieldKind.NUMBER) }
                item { LabeledField("Price a tonne", limit, { limit = it }, kind = FieldKind.NUMBER) }
                item {
                    SecondaryButton("Bid at the next auction", {
                        message = CarbonMarketPresenter.bid(desk, market.country, quantity, limit)
                        tick++
                    }, Modifier.fillMaxWidth())
                }
                item {
                    SecondaryButton("Place an order", {
                        message = CarbonMarketPresenter.order(desk, market.country, side, quantity, limit)
                        tick++
                    }, Modifier.fillMaxWidth())
                }
            }
            item { SectionHeader("Credits") }
            item { ChoiceChips(OffsetKind.values().toList(), kind, { it.label }, { kind = it }) }
            items(model.offsets.size) { i ->
                val o = model.offsets[i]
                Card { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(o.title, style = Type.Body, color = Theme.tokens.textPrimary); Text(o.held, style = Type.Body, color = Theme.tokens.textSecondary) } }
            }
            item {
                SecondaryButton("Buy credits", {
                    message = CarbonPresenter.buyOffsets(desk, kind, quantity)
                    tick++
                }, Modifier.fillMaxWidth())
            }
            if (model.options.isNotEmpty()) {
                item { SectionHeader("Cut emissions") }
                items(model.options.size) { i ->
                    val o = model.options[i]
                    Card(onClick = { message = CarbonPresenter.startAbatement(desk, o); tick++ }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(o.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                            Pill(o.cost, o.tone.color())
                        }
                        Text(o.detail, style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                }
            }
            if (model.projects.isNotEmpty()) {
                item { SectionHeader("Under way") }
                items(model.projects.size) { i ->
                    val p = model.projects[i]
                    Card {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                            Pill(p.status, p.tone.color())
                        }
                        Text(p.detail, style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                }
            }
            if (model.rating.isNotEmpty()) {
                item { SectionHeader("Carbon rating") }
                item { Card { FactList(model.rating) } }
            }
            if (model.statements.isNotEmpty()) {
                item { SectionHeader("Yearly statements") }
                items(model.statements.size) { i ->
                    val s = model.statements[i]
                    Card {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                            Pill(s.result, s.tone.color())
                        }
                        Text(s.detail, style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                }
            }
        }
    }
}
