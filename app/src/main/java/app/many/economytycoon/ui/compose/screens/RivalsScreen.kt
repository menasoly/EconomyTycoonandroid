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
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.model.OfferForm
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.OfferItem
import app.many.economytycoon.ui.present.RivalsPresenter

@Composable
fun RivalsScreen(ui: GameUi) {
    val model = remember(ui.revision / 5) { RivalsPresenter.build(ui.session) }
    ScreenFrame("Rivals", onBack = { ui.back() }) {
        ScreenList {
            if (model.incoming.isNotEmpty()) {
                item { SectionHeader("Offers for your company") }
                items(model.incoming.size) { i -> OfferCard(ui, model.incoming[i]) }
            }
            if (model.outgoing.isNotEmpty()) {
                item { SectionHeader("Your offers") }
                items(model.outgoing.size) { i -> OfferCard(ui, model.outgoing[i]) }
            }
            item { SectionHeader("Companies") }
            if (model.rivals.isEmpty()) item { EmptyState("No rivals", "Nobody else is competing in this world.") }
            items(model.rivals.size) { i ->
                val r = model.rivals[i]
                Card(onClick = { ui.open(Route.Rival(r.id.value)) }) {
                    ListRow(r.name, subtitle = r.style, trailing = r.netWorth)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Pill(r.standing, r.standingTone.color())
                        if (r.yourStake != null) Pill("You hold ${r.yourStake}", Theme.tokens.info)
                    }
                    Text("Profit ${r.profit} · revenue ${r.revenue} · ${r.staff} staff", style = Type.Caption, color = r.profitTone.color())
                }
            }
        }
    }
}

@Composable
fun OfferCard(ui: GameUi, o: OfferItem) {
    val me = ui.session.selectedCompany
    Card {
        ListRow(o.title, subtitle = o.price, badge = o.state)
        Text(o.fairness + if (o.expires.isNotEmpty()) " · ${o.expires}" else "", style = Type.Caption, color = o.fairnessTone.color())
        if (me != null && (o.canRespond || o.canWithdraw)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (o.canRespond) {
                    PrimaryButton("Accept", { ui.issue(Command.RespondOffer(me, o.id, true), "Offer accepted.") })
                    SecondaryButton("Decline", { ui.issue(Command.RespondOffer(me, o.id, false), "Offer declined.") })
                }
                if (o.canWithdraw) SecondaryButton("Withdraw", { ui.issue(Command.WithdrawOffer(me, o.id), "Offer withdrawn.") })
            }
        }
    }
}

@Composable
fun RivalScreen(ui: GameUi, companyId: Long) {
    val id = CompanyId(companyId)
    val model = remember(ui.revision / 5, companyId) { RivalsPresenter.detail(ui.session, id) }
    var bidding by remember { mutableStateOf(false) }
    ScreenFrame(model?.item?.name ?: "Company", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("Not found", "This company is gone or is your own.", action = "Back", onAction = { ui.back() })
            return@ScreenFrame
        }
        ScreenList {
            item { Pill(model.item.standing, model.item.standingTone.color()) }
            item { Card { FactList(model.facts) } }
            if (model.industries.isNotEmpty()) item { Text("Industries: " + model.industries.joinToString(", "), style = Type.Body, color = Theme.tokens.textSecondary) }
            item { PrimaryButton("Make a takeover offer", { bidding = true }, Modifier.fillMaxWidth()) }
            if (model.offers.isNotEmpty()) {
                item { SectionHeader("Your offers") }
                items(model.offers.size) { i -> OfferCard(ui, model.offers[i]) }
            }
        }
        if (bidding) OfferDialog(ui, id) { bidding = false }
    }
}

@Composable
private fun OfferDialog(ui: GameUi, target: CompanyId, onDone: () -> Unit) {
    val me = ui.session.selectedCompany ?: return
    var shares by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val total = remember { ui.session.economy.finance.equity.table(ui.session.economy.companies.require(target).owner)?.totalOutstanding() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone, containerColor = Theme.tokens.surface,
        title = { Text("Takeover offer", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (total != null) Text("The company has ${app.many.economytycoon.session.Formatting.grouped(total)} shares.", style = Type.Caption, color = Theme.tokens.textSecondary)
                LabeledField("Shares wanted", shares, { shares = it }, kind = FieldKind.NUMBER, error = result?.errorFor("shares"))
                LabeledField("Price per share", price, { price = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("price"))
                result?.errorFor("target")?.let { Text(it, style = Type.Caption, color = Theme.tokens.negative) }
            }
        },
        confirmButton = {
            PrimaryButton("Send offer", {
                val r = OfferForm(me, target, shares, price, total).validate(); result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Offer sent.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
