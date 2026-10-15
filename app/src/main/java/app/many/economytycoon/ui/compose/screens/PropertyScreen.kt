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
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.ConfirmDialog
import app.many.economytycoon.ui.compose.components.DangerButton
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
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.model.LeaseOfferForm
import app.many.economytycoon.ui.model.SellParcelForm
import app.many.economytycoon.ui.present.LeaseCard
import app.many.economytycoon.ui.present.ListingCard
import app.many.economytycoon.ui.present.ParcelCard
import app.many.economytycoon.ui.present.ParcelState
import app.many.economytycoon.ui.present.PropertyModel
import app.many.economytycoon.ui.present.PropertyPresenter

@Composable
fun PropertyScreen(ui: GameUi) {
    val model = remember(ui.revision) { PropertyPresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    var selling by remember { mutableStateOf<ParcelCard?>(null) }
    var letting by remember { mutableStateOf<ParcelCard?>(null) }
    var ending by remember { mutableStateOf<LeaseCard?>(null) }

    ScreenFrame("Land", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Land belongs to a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            model.note?.let { n -> item { Text(n, style = Type.Body, color = Theme.tokens.textSecondary) } }
            item { SectionHeader("Your land") }
            if (model.parcels.isEmpty()) item { EmptyState("No land", "Buy a plot from the build screen, or from the listings below.") }
            items(model.parcels.size) { i ->
                val p = model.parcels[i]
                Card {
                    ListRow("${p.title}", subtitle = "${p.place} · ${p.paid}", trailing = p.value)
                    Text(p.stateLabel, style = Type.Caption, color = Theme.tokens.textSecondary)
                    when (p.state) {
                        ParcelState.FREE -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            SecondaryButton("Sell", { selling = p }, Modifier.weight(1f))
                            SecondaryButton("Rent out", { letting = p }, Modifier.weight(1f))
                        }
                        ParcelState.LISTED -> LinkButton("Take off the market", { ui.issue(Command.DelistParcel(company, ParcelId(p.id)), "Listing withdrawn.") })
                        ParcelState.OFFERED -> LinkButton("Withdraw the offer", { ui.issue(Command.WithdrawLease(company, ParcelId(p.id)), "Offer withdrawn.") })
                        else -> Unit
                    }
                }
            }
            if (model.leases.isNotEmpty()) {
                item { SectionHeader("Leases") }
                items(model.leases.size) { i ->
                    val l = model.leases[i]
                    Card {
                        ListRow(l.title, subtitle = "${l.tenant} · ${l.earned}", trailing = l.rent)
                        Text(l.ends, style = Type.Caption, color = Theme.tokens.textSecondary)
                        l.trouble?.let { Text(it, style = Type.Caption, color = Theme.tokens.warning) }
                        DangerButton("End early", { ending = l })
                    }
                }
            }
            item { SectionHeader("For sale") }
            if (model.market.isEmpty()) item { EmptyState("Nothing listed", "Other companies put land up for sale from time to time.") }
            items(model.market.size) { i ->
                val l = model.market[i]
                Card {
                    ListRow(l.title, subtitle = "${l.place} · ${l.verdict}", trailing = l.ask, trailingTone = l.tone)
                    Text("Appraised ${l.appraisal} · ${l.expires}", style = Type.Caption, color = Theme.tokens.textSecondary)
                    if (!l.mine) PrimaryButton("Buy", { ui.issue(Command.BuyListed(company, ParcelId(l.parcel)), "Land bought.") }, Modifier.fillMaxWidth())
                }
            }
        }
        val s = selling
        if (s != null) SellDialog(ui, company, s) { selling = null }
        val t = letting
        if (t != null) LeaseDialog(ui, company, t, model) { letting = null }
        val l = ending
        if (l != null) {
            ConfirmDialog(
                "End the lease?", "${l.buyout}. The tenant leaves and the plot is vacant again.",
                "End lease", { ui.issue(Command.EndLease(company, ParcelId(l.parcel)), "Lease ended."); ending = null }, { ending = null },
            )
        }
    }
}

@Composable
private fun SellDialog(ui: GameUi, company: CompanyId, p: ParcelCard, onDone: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val params = ui.session.economy.property
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text("Sell this plot", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { Text("${p.title} in ${p.place} is appraised at ${p.value}. The buyer pays the transfer tax.", style = Type.Body, color = Theme.tokens.textPrimary) }
                item { LabeledField("Asking price", text, { text = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("ask")) }
            }
        },
        confirmButton = {
            PrimaryButton("List", {
                val r = SellParcelForm(company, ParcelId(p.id), text, params.minAsk(p.appraisal), params.maxAsk(p.appraisal)).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Listed for sale.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}

@Composable
private fun LeaseDialog(ui: GameUi, company: CompanyId, p: ParcelCard, model: PropertyModel, onDone: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var term by remember { mutableStateOf(model.terms.first()) }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val params = ui.session.economy.property
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text("Rent this plot out", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { Text("A fair rent for ${p.title} is about ${ui.session.settings.money(p.fairRent)} a month. Higher rents wait longer for a tenant.", style = Type.Body, color = Theme.tokens.textPrimary) }
                item { ChoiceChips(model.terms, term, { "$it days" }, { term = it }) }
                item { LabeledField("Rent a month", text, { text = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("rent")) }
            }
        },
        confirmButton = {
            PrimaryButton("Offer", {
                val value = p.appraisal
                val r = LeaseOfferForm(company, ParcelId(p.id), term, text, model.terms, params.minRent(value), params.maxRent(value)).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Offered to tenants.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
