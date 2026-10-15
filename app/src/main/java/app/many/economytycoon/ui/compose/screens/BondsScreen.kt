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
import app.many.economytycoon.core.BondId
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
import app.many.economytycoon.ui.model.BondAction
import app.many.economytycoon.ui.model.BondForm
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.present.BondsPresenter

private class Pending(val id: BondId, val title: String, val action: BondAction)

@Composable
fun BondsScreen(ui: GameUi) {
    val model = remember(ui.revision) { BondsPresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    var pending by remember { mutableStateOf<Pending?>(null) }

    ScreenFrame("Government bonds", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Bonds are held by companies.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            item {
                Text(
                    "Governments borrow by selling bonds. You pay face value, collect a coupon every month and are repaid at maturity. " +
                        "The central bank rate is ${model.rate}; coupons follow it. Before maturity a dealer will buy your bonds back at a price that moves with interest rates.",
                    style = Type.Body, color = Theme.tokens.textSecondary,
                )
            }
            item { SectionHeader("Open for subscription") }
            if (model.offers.isEmpty()) item { EmptyState("No open issues", "Governments sell new bonds every couple of months.") }
            items(model.offers.size) { i ->
                val o = model.offers[i]
                Card {
                    ListRow(o.title, subtitle = "${o.coupon} · ${o.unitPrice}", trailing = o.left)
                    Text(o.closes, style = Type.Caption, color = Theme.tokens.textSecondary)
                    o.yours?.let { Text(it, style = Type.Caption, color = Theme.tokens.textPrimary) }
                    if (o.canSubscribe) PrimaryButton("Subscribe", { pending = Pending(o.id, o.title, BondAction.SUBSCRIBE) }, Modifier.fillMaxWidth())
                }
            }
            if (model.holdings.isNotEmpty()) {
                item { SectionHeader("Your bonds") }
                items(model.holdings.size) { i ->
                    val h = model.holdings[i]
                    Card {
                        ListRow(h.title, subtitle = "${h.units} units · paid ${h.cost}", trailing = h.value)
                        Text("Against cost ${h.gain}", style = Type.Caption, color = h.gainTone.color())
                        Text("${h.income} · ${h.matures}", style = Type.Caption, color = Theme.tokens.textSecondary)
                        if (h.canSell) SecondaryButton("Sell to dealer", { pending = Pending(h.id, h.title, BondAction.SELL) }, Modifier.fillMaxWidth())
                    }
                }
            }
            if (model.forSale.isNotEmpty()) {
                item { SectionHeader("Dealer's stock") }
                items(model.forSale.size) { i ->
                    val s = model.forSale[i]
                    Card {
                        ListRow(s.title, subtitle = "${s.units} units · ${s.yieldText}", trailing = s.price)
                        Text(s.matures, style = Type.Caption, color = Theme.tokens.textSecondary)
                        if (s.canBuy) SecondaryButton("Buy from dealer", { pending = Pending(s.id, s.title, BondAction.BUY) }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        val p = pending
        if (p != null) BondDialog(ui, company, p) { pending = null }
    }
}

@Composable
private fun BondDialog(ui: GameUi, company: CompanyId, p: Pending, onDone: () -> Unit) {
    var units by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val e = ui.session.economy
    val bond = e.bonds.get(p.id)
    val cash = e.finance.ledger.cash(e.companies.require(company).owner)
    val tick = ui.session.tick
    val quoteOne = remember(p.id, p.action) {
        when (p.action) {
            BondAction.SUBSCRIBE -> bond?.faceEach
            BondAction.BUY -> (e.bondRunner.quote(p.id, true, 1, tick) as? app.many.economytycoon.core.Outcome.Ok)?.value?.pricePerUnit
            BondAction.SELL -> null
        }
    }
    val available = when (p.action) {
        BondAction.SUBSCRIBE -> bond?.unitsLeft
        BondAction.BUY -> bond?.heldBy(app.many.economytycoon.bonds.DEALER)
        BondAction.SELL -> bond?.heldBy(company)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text(when (p.action) { BondAction.SUBSCRIBE -> "Subscribe"; BondAction.BUY -> "Buy"; BondAction.SELL -> "Sell" } + ": " + p.title, style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                quoteOne?.let { q -> item { Text("${ui.session.settings.money(q)} a unit", style = Type.Body, color = Theme.tokens.textPrimary) } }
                available?.let { a -> item { Text("Up to $a units", style = Type.Caption, color = Theme.tokens.textSecondary) } }
                item { LabeledField("Units", units, { units = it }, kind = FieldKind.NUMBER, error = result?.errorFor("units")) }
            }
        },
        confirmButton = {
            PrimaryButton("Confirm", {
                val r = BondForm(company, p.id, p.action, units, available, quoteOne, cash).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Done.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
