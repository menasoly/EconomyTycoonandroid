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
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
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
import app.many.economytycoon.ui.model.BidForm
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.present.TenderCard
import app.many.economytycoon.ui.present.TendersPresenter
import app.many.economytycoon.ui.present.PolicyPresenter

@Composable
fun TendersScreen(ui: GameUi) {
    val model = remember(ui.revision) { TendersPresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    var bidding by remember { mutableStateOf<TenderCard?>(null) }

    ScreenFrame("Public tenders", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Tenders are open to companies.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            item {
                Text(
                    "Governments buy goods in bulk. The lowest eligible bid wins; the winner must deliver the whole quantity from one facility before the deadline, or pay a penalty.",
                    style = Type.Body, color = Theme.tokens.textSecondary,
                )
            }
            model.standing?.let { s -> item { Text(s, style = Type.Body, color = Theme.tokens.warning) } }
            if (model.won.isNotEmpty()) {
                item { SectionHeader("To deliver") }
                items(model.won.size) { i -> TenderCardView(ui, company, model.won[i]) { } }
            }
            item { SectionHeader("Open for bids") }
            if (model.open.isEmpty()) item { EmptyState("No open tenders", "New ones appear every few days.") }
            items(model.open.size) { i -> TenderCardView(ui, company, model.open[i]) { bidding = it } }
            if (model.history.isNotEmpty()) {
                item { SectionHeader("Earlier") }
                items(model.history.size) { i -> TenderCardView(ui, company, model.history[i]) { } }
            }
        }
        val b = bidding
        if (b != null) BidDialog(ui, company, b) { bidding = null }
    }
}

@Composable
private fun TenderCardView(ui: GameUi, company: CompanyId, t: TenderCard, onBid: (TenderCard) -> Unit) {
    Card {
        ListRow(t.title, subtitle = "${t.quantity} · ${t.ceiling}", trailing = t.value, badge = t.status, badgeTone = t.statusTone)
        Text(t.timing, style = Type.Caption, color = Theme.tokens.textSecondary)
        if (t.reference.isNotBlank()) Text(t.reference, style = Type.Caption, color = Theme.tokens.textSecondary)
        t.progressText?.let { p ->
            MeterBar(t.progress)
            Text(p, style = Type.Caption, color = Theme.tokens.textSecondary)
        }
        if (t.bids > 0) {
            val lead = if (t.winning) "You lead at ${t.leadingBid}" else "Lowest bid ${t.leadingBid}"
            Text("${t.bids} bids · $lead", style = Type.Caption, color = if (t.winning) Theme.tokens.positive else Theme.tokens.textSecondary)
        }
        t.yourBid?.let { y -> Text("Your bid: $y", style = Type.Caption, color = Theme.tokens.textPrimary) }
        if (t.canBid || t.canWithdraw) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (t.canBid) PrimaryButton(if (t.yourBid == null) "Place a bid" else "Change bid", { onBid(t) }, Modifier.weight(1f))
                if (t.canWithdraw) SecondaryButton("Withdraw", { ui.issue(Command.WithdrawBid(company, t.id), "Bid withdrawn.") }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BidDialog(ui: GameUi, company: CompanyId, t: TenderCard, onDone: () -> Unit) {
    val facilities = remember { TendersPresenter.facilitiesFor(ui.session, t.id) }
    var facility by remember { mutableStateOf<FacilityId?>(facilities.firstOrNull()?.id) }
    val suggestion = remember { TendersPresenter.suggestedPrice(ui.session, t.id) }
    var price by remember { mutableStateOf(suggestion?.let { PolicyPresenter.editText(it) } ?: "") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val tender = ui.session.economy.tenders.get(t.id)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text("Bid: ${t.title}", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { Text("${t.quantity} wanted, ${t.ceiling}. ${t.timing}.", style = Type.Body, color = Theme.tokens.textPrimary) }
                if (facilities.isEmpty()) item { Text("Build a facility first.", style = Type.Body, color = Theme.tokens.warning) }
                else {
                    item { Text("Deliver from", style = Type.Label, color = Theme.tokens.textSecondary) }
                    item { ChoiceChips(facilities.map { it.id }, facility, { id -> facilities.first { it.id == id }.let { it.name + " · " + it.stock } }, { facility = it }) }
                    facilities.firstOrNull { it.id == facility }?.let { f ->
                        item { Text(f.cost + if (f.enough) "" else " · you will need to stock up before the deadline", style = Type.Caption, color = if (f.enough) Theme.tokens.textSecondary else Theme.tokens.warning) }
                    }
                }
                result?.errorFor("facility")?.let { e -> item { Text(e, style = Type.Caption, color = Theme.tokens.negative) } }
                item { LabeledField("Your price per unit", price, { price = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("price")) }
            }
        },
        confirmButton = {
            PrimaryButton("Place bid", {
                val floor = tender?.let { ui.session.economy.tenderRunner.floorFor(it) }
                val r = BidForm(company, t.id, facility, price, floor, tender?.ceiling).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Bid placed.")) onDone()
            }, enabled = facilities.isNotEmpty())
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
