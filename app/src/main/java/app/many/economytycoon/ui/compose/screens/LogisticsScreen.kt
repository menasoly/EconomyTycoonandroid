package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.VehicleTypeId
import app.many.economytycoon.logistics.StockRef
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
import app.many.economytycoon.ui.compose.components.SwitchRow
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.components.healthColor
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.model.ShipForm
import app.many.economytycoon.ui.present.LogisticsPresenter
import app.many.economytycoon.ui.present.Tone
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier

@Composable
fun LogisticsScreen(ui: GameUi) {
    var finished by remember { mutableStateOf(false) }
    val model = remember(ui.revision, finished) { LogisticsPresenter.build(ui.session, finished) }
    val company = ui.session.selectedCompany
    var shipping by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf(false) }
    var leasing by remember { mutableStateOf(false) }

    ScreenFrame("Logistics", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Logistics belongs to a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            item { PrimaryButton("Ship goods", { shipping = true }, Modifier.fillMaxWidth()) }
            item {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Buy a vehicle", { buying = true }, Modifier.weight(1f))
                    SecondaryButton("Lease a warehouse", { leasing = true }, Modifier.weight(1f))
                }
            }
            item { SectionHeader("Shipments") }
            item { SwitchRow("Include finished", finished, { finished = it }) }
            if (model.shipments.isEmpty()) item { Text("Nothing is on the move.", style = Type.Body, color = Theme.tokens.textSecondary) }
            items(model.shipments.size) { i ->
                val s = model.shipments[i]
                Card { ListRow(s.title, subtitle = s.route + if (s.eta.isNotEmpty()) " · ${s.eta}" else "", trailing = s.cost, badge = s.status, badgeTone = s.tone) }
            }
            item { SectionHeader("Fleet") }
            if (model.fleet.isEmpty()) item { Text("You own no vehicles. Shipments are carried by hired hauliers at a higher price.", style = Type.Body, color = Theme.tokens.textSecondary) }
            items(model.fleet.size) { i ->
                val v = model.fleet[i]
                Card {
                    ListRow(v.title, subtitle = v.detail, badge = v.status, badgeTone = v.tone)
                    MeterBar(v.condition, color = healthColor(v.condition))
                    Row2 {
                        LinkButton("Repair", { ui.issue(Command.RepairVehicle(company, app.many.economytycoon.core.VehicleId(v.id)), "Repaired.") })
                        LinkButton("Sell", { ui.issue(Command.SellVehicle(company, app.many.economytycoon.core.VehicleId(v.id)), "Sold.") })
                    }
                }
            }
            item { SectionHeader("Warehouses") }
            if (model.warehouses.isEmpty()) item { Text("You rent no warehouses.", style = Type.Body, color = Theme.tokens.textSecondary) }
            items(model.warehouses.size) { i ->
                val w = model.warehouses[i]
                Card {
                    ListRow(w.title, subtitle = w.fillText, trailing = w.rent, badge = if (w.behindOnRent) "Rent overdue" else null, badgeTone = Tone.BAD)
                    MeterBar(w.fill)
                    LinkButton("Close", { ui.issue(Command.CloseWarehouse(company, app.many.economytycoon.core.WarehouseId(w.id)), "Closed.") })
                }
            }
        }
        if (shipping) ShipDialog(ui, company) { shipping = false }
        if (buying) BuyVehicleDialog(ui, company, model.choices) { buying = false }
        if (leasing) LeaseDialog(ui, company) { leasing = false }
    }
}

@Composable
private fun Row2(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) { content() }
}

@Composable
private fun ShipDialog(ui: GameUi, company: app.many.economytycoon.core.CompanyId, onDone: () -> Unit) {
    val points = remember { ui.session.reports.logistics.stockPoints(company) }
    val goods = remember { ui.session.economy.content.allGoods.filter { it.tradable }.sortedBy { it.name } }
    var from by remember { mutableStateOf<StockRef?>(points.firstOrNull()?.ref) }
    var to by remember { mutableStateOf<StockRef?>(points.getOrNull(1)?.ref) }
    var good by remember { mutableStateOf<GoodId?>(goods.firstOrNull()?.id) }
    var quantity by remember { mutableStateOf("") }
    var useFleet by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FormResult?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone, containerColor = Theme.tokens.surface,
        title = { Text("Ship goods", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (points.size < 2) item { Text("You need two places to ship between, such as two facilities or a warehouse.", style = Type.Body, color = Theme.tokens.warning) }
                item { Text("From", style = Type.Label, color = Theme.tokens.textSecondary) }
                item { ChoiceChips(points.map { it.ref }, from, { r -> points.first { it.ref == r }.name }, { from = it }) }
                item { Text("To", style = Type.Label, color = Theme.tokens.textSecondary) }
                item { ChoiceChips(points.map { it.ref }, to, { r -> points.first { it.ref == r }.name }, { to = it }) }
                item { Text("Good", style = Type.Label, color = Theme.tokens.textSecondary) }
                item { ChoiceChips(goods.map { it.id }, good, { id -> goods.first { it.id == id }.name }, { good = it }) }
                item { LabeledField("Quantity", quantity, { quantity = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("quantity")) }
                item { SwitchRow("Use my own vehicles", useFleet, { useFleet = it }) }
                result?.errorFor("from")?.let { e -> item { Text(e, style = Type.Caption, color = Theme.tokens.negative) } }
                result?.errorFor("to")?.let { e -> item { Text(e, style = Type.Caption, color = Theme.tokens.negative) } }
            }
        },
        confirmButton = {
            PrimaryButton("Ship", {
                val form = ShipForm(company, from, to, good, quantity, null, useFleet)
                val r = form.validate(); result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Shipment sent.")) onDone()
            }, enabled = points.size >= 2)
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}

@Composable
private fun BuyVehicleDialog(ui: GameUi, company: app.many.economytycoon.core.CompanyId, choices: List<app.many.economytycoon.ui.present.VehicleChoice>, onDone: () -> Unit) {
    val cities = remember { ui.session.economy.world.cities().sortedBy { it.name } }
    var city by remember { mutableStateOf<CityId?>(cities.firstOrNull()?.id) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone, containerColor = Theme.tokens.surface,
        title = { Text("Buy a vehicle", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                item { Text("Based in", style = Type.Label, color = Theme.tokens.textSecondary) }
                item { ChoiceChips(cities.map { it.id }, city, { id -> cities.first { it.id == id }.name }, { city = it }) }
                items(choices.size) { i ->
                    val c = choices[i]
                    ListRow(c.name, subtitle = "${c.mode} · ${c.detail}" + (c.lockNote?.let { " · $it" } ?: ""), trailing = c.price, onClick = if (c.locked || city == null) null else ({
                        if (ui.issue(Command.BuyVehicle(company, VehicleTypeId(c.typeKey), city!!), "Vehicle bought.")) onDone()
                    }))
                }
            }
        },
        confirmButton = { LinkButton("Close", onDone) },
    )
}

@Composable
private fun LeaseDialog(ui: GameUi, company: app.many.economytycoon.core.CompanyId, onDone: () -> Unit) {
    val cities = remember { ui.session.economy.world.cities().sortedBy { it.name } }
    var city by remember { mutableStateOf<CityId?>(cities.firstOrNull()?.id) }
    var capacity by remember { mutableStateOf("1000") }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone, containerColor = Theme.tokens.surface,
        title = { Text("Lease a warehouse", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                ChoiceChips(cities.map { it.id }, city, { id -> cities.first { it.id == id }.name }, { city = it })
                LabeledField("Capacity", capacity, { capacity = it }, kind = FieldKind.DECIMAL, error = error)
            }
        },
        confirmButton = {
            PrimaryButton("Lease", {
                val q = app.many.economytycoon.ui.model.Parsing.quantity(capacity).valueOrNull
                if (q == null || q.raw <= 0) error = "Enter a capacity above zero."
                else if (city != null && ui.issue(Command.LeaseWarehouse(company, city!!, q), "Warehouse leased.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
