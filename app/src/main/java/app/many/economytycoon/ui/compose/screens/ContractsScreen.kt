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
import app.many.economytycoon.contracts.ContractSide
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.ContractId
import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.DangerButton
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
import app.many.economytycoon.ui.model.AcceptForm
import app.many.economytycoon.ui.model.ContractForm
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.present.ContractCard
import app.many.economytycoon.ui.present.ContractSetup
import app.many.economytycoon.ui.present.ContractsPresenter

@Composable
fun ContractsScreen(ui: GameUi) {
    val model = remember(ui.revision) { ContractsPresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    var creating by remember { mutableStateOf(false) }
    var answering by remember { mutableStateOf<ContractCard?>(null) }

    ScreenFrame("Supply contracts", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Contracts are made between companies.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            item {
                Text(
                    "A contract fixes the price of a good for a run of regular deliveries. Both sides pick a facility; goods move and money is paid on each delivery day.",
                    style = Type.Body, color = Theme.tokens.textSecondary,
                )
            }
            item { PrimaryButton("Offer a contract", { creating = true }, Modifier.fillMaxWidth()) }
            if (model.incoming.isNotEmpty()) {
                item { SectionHeader("Offers for you") }
                items(model.incoming.size) { i -> ContractCardView(ui, company, model.incoming[i], onAnswer = { answering = it }) }
            }
            if (model.running.isNotEmpty()) {
                item { SectionHeader("Running") }
                items(model.running.size) { i -> ContractCardView(ui, company, model.running[i], onAnswer = {}) }
            }
            if (model.outgoing.isNotEmpty()) {
                item { SectionHeader("Waiting for an answer") }
                items(model.outgoing.size) { i -> ContractCardView(ui, company, model.outgoing[i], onAnswer = {}) }
            }
            if (model.history.isNotEmpty()) {
                item { SectionHeader("Earlier") }
                items(model.history.size) { i -> ContractCardView(ui, company, model.history[i], onAnswer = {}) }
            }
            if (model.incoming.isEmpty() && model.running.isEmpty() && model.outgoing.isEmpty() && model.history.isEmpty()) {
                item { EmptyState("No contracts yet", "Offer a regular supply deal to a supplier or a customer.") }
            }
        }
        if (creating) NewContractDialog(ui, company) { creating = false }
        val a = answering
        if (a != null) AcceptDialog(ui, company, a) { answering = null }
    }
}

@Composable
private fun ContractCardView(ui: GameUi, company: CompanyId, c: ContractCard, onAnswer: (ContractCard) -> Unit) {
    Card {
        ListRow(c.title, subtitle = c.terms, trailing = c.price, badge = c.status, badgeTone = c.statusTone)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(c.role, style = Type.Caption, color = Theme.tokens.textSecondary)
            Text(c.versusMarket, style = Type.Caption, color = c.versusTone.color())
        }
        if (c.status == "Running" || c.progress > 0.0) {
            MeterBar(c.progress)
            Text(c.progressText + " · paid " + c.paid, style = Type.Caption, color = Theme.tokens.textSecondary)
        }
        if (c.next.isNotBlank()) Text(c.next, style = Type.Caption, color = Theme.tokens.textSecondary)
        if (c.penalties != null) Text("Penalties so far: ${c.penalties}", style = Type.Caption, color = Theme.tokens.warning)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (c.canDecline) {
                PrimaryButton("Accept", { onAnswer(c) }, Modifier.weight(1f), enabled = c.canAccept)
                SecondaryButton("Decline", { ui.issue(Command.DeclineContract(company, ContractId(c.id)), "Offer declined.") }, Modifier.weight(1f))
            }
            if (c.canWithdraw) LinkButton("Withdraw offer", { ui.issue(Command.CancelContract(company, ContractId(c.id)), "Offer withdrawn.") })
            if (c.canCancel) DangerButton("Leave contract (fee ${c.cancelFee})", { ui.issue(Command.CancelContract(company, ContractId(c.id)), "Contract ended.") })
        }
        if (c.canDecline && !c.canAccept) {
            Text("None of your facilities can take part yet.", style = Type.Caption, color = Theme.tokens.warning)
        }
    }
}

@Composable
private fun AcceptDialog(ui: GameUi, company: CompanyId, c: ContractCard, onDone: () -> Unit) {
    var facility by remember { mutableStateOf(c.facilities.firstOrNull { it.suitable }?.id) }
    var result by remember { mutableStateOf<FormResult?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text("Accept contract", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { Text("${c.title}: ${c.terms}", style = Type.Body, color = Theme.tokens.textPrimary) }
                item { Text("Which facility takes part?", style = Type.Label, color = Theme.tokens.textSecondary) }
                val usable = c.facilities.filter { it.suitable }
                item { ChoiceChips(usable.map { it.id }, facility, { id -> usable.first { it.id == id }.let { it.name + " · " + it.detail } }, { facility = it }) }
                result?.errorFor("facility")?.let { e -> item { Text(e, style = Type.Caption, color = Theme.tokens.negative) } }
            }
        },
        confirmButton = {
            PrimaryButton("Accept", {
                val r = AcceptForm(company, ContractId(c.id), facility).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Contract started.")) onDone()
            })
        },
        dismissButton = { LinkButton("Not now", onDone) },
    )
}

@Composable
private fun NewContractDialog(ui: GameUi, company: CompanyId, onDone: () -> Unit) {
    val setup: ContractSetup? = remember { ContractsPresenter.setup(ui.session) }
    if (setup == null) { onDone(); return }
    var side by remember { mutableStateOf(ContractSide.SELLER) }
    var partner by remember { mutableStateOf(setup.partners.firstOrNull()?.id) }
    var good by remember { mutableStateOf(setup.goods.firstOrNull()?.id) }
    var facility by remember { mutableStateOf(setup.facilities.firstOrNull()?.id) }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("5") }
    var deliveries by remember { mutableStateOf("12") }
    var result by remember { mutableStateOf<FormResult?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text("Offer a contract", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { ChoiceChips(ContractSide.values().toList(), side, { if (it == ContractSide.SELLER) "I sell" else "I buy" }, { side = it }) }
                if (setup.facilities.isEmpty()) item { Text("Build a facility first.", style = Type.Body, color = Theme.tokens.warning) }
                else {
                    item { Text("Your facility", style = Type.Label, color = Theme.tokens.textSecondary) }
                    item { ChoiceChips(setup.facilities.map { it.id }, facility, { id -> setup.facilities.first { it.id == id }.name }, { facility = it }) }
                }
                item { Text("With", style = Type.Label, color = Theme.tokens.textSecondary) }
                item { ChoiceChips(setup.partners.map { it.id }, partner, { id -> setup.partners.first { it.id == id }.name }, { partner = it }) }
                result?.errorFor("partner")?.let { e -> item { Text(e, style = Type.Caption, color = Theme.tokens.negative) } }
                item { Text("Good", style = Type.Label, color = Theme.tokens.textSecondary) }
                item { ChoiceChips(setup.goods.map { it.id }, good, { id -> setup.goods.first { it.id == id }.let { it.name + " · " + it.reference } }, { good = it }) }
                item { LabeledField("Quantity each delivery", quantity, { quantity = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("quantity")) }
                item { LabeledField("Price per unit", price, { price = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("price")) }
                item { LabeledField("Days between deliveries", interval, { interval = it }, kind = FieldKind.NUMBER, error = result?.errorFor("interval")) }
                item { LabeledField("Number of deliveries", deliveries, { deliveries = it }, kind = FieldKind.NUMBER, error = result?.errorFor("deliveries")) }
                item { Text(setup.limits, style = Type.Caption, color = Theme.tokens.textSecondary) }
            }
        },
        confirmButton = {
            PrimaryButton("Send offer", {
                val g = good
                val ref: Money? = g?.let { ui.session.economy.content.goodOrNull(it)?.referencePrice?.perUnit }
                val params = ui.session.economy.contracts.params
                val low = ref?.let { Money(app.many.economytycoon.core.mulDiv(it.raw, params.minPriceMultiplePpm, 1_000_000L)) }
                val high = ref?.let { Money(app.many.economytycoon.core.mulDiv(it.raw, params.maxPriceMultiplePpm, 1_000_000L)) }
                val r = ContractForm(company, partner, side, facility, good, quantity, price, interval, deliveries, low, high, null).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Offer sent.")) onDone()
            }, enabled = setup.facilities.isNotEmpty() && setup.partners.isNotEmpty())
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
