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
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ConfirmDialog
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
import app.many.economytycoon.ui.model.DemandResponseForm
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.present.DemandCard
import app.many.economytycoon.ui.present.UnionsPresenter
import app.many.economytycoon.unions.ResponseKind

@Composable
fun UnionsScreen(ui: GameUi) {
    val model = remember(ui.revision) { UnionsPresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    var countering by remember { mutableStateOf<DemandCard?>(null) }
    var refusing by remember { mutableStateOf<DemandCard?>(null) }

    ScreenFrame("Labour relations", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Unions organise a company's workers.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            model.note?.let { n -> item { Text(n, style = Type.Body, color = Theme.tokens.textSecondary) } }
            if (model.demands.isNotEmpty()) {
                item { SectionHeader("Wage demands") }
                items(model.demands.size) { i ->
                    val d = model.demands[i]
                    Card {
                        ListRow("${d.region}: ${d.raise} raise", subtitle = d.monthlyCost, trailing = d.deadline)
                        Text(d.strength, style = Type.Caption, color = Theme.tokens.textSecondary)
                        Text(d.acceptableCounter, style = Type.Caption, color = Theme.tokens.textSecondary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            PrimaryButton("Accept", { ui.issue(Command.RespondDemand(company, d.id, ResponseKind.ACCEPT), "Raise agreed.") }, Modifier.weight(1f))
                            SecondaryButton("Counter", { countering = d }, Modifier.weight(1f))
                            DangerButton("Refuse", { refusing = d }, Modifier.weight(1f))
                        }
                    }
                }
            }
            item { SectionHeader("Your unions") }
            if (model.unions.isEmpty()) item { EmptyState("No unions", "Keep pay at market rates and morale up to keep it that way.") }
            items(model.unions.size) { i ->
                val u = model.unions[i]
                Card {
                    ListRow(u.region, subtitle = u.members, trailing = u.status, trailingTone = u.statusTone)
                    MeterBar(u.strength, color = u.strengthTone.color())
                    Text(u.record, style = Type.Caption, color = Theme.tokens.textSecondary)
                }
            }
            if (model.history.isNotEmpty()) {
                item { SectionHeader("Earlier rounds") }
                items(model.history.size) { i ->
                    val h = model.history[i]
                    Card { ListRow(h.region, subtitle = h.summary, trailing = h.date) }
                }
            }
        }
        val c = countering
        if (c != null) CounterDialog(ui, company, c) { countering = null }
        val r = refusing
        if (r != null) ConfirmRefuse(ui, company, r) { refusing = null }
    }
}

@Composable
private fun CounterDialog(ui: GameUi, company: CompanyId, d: DemandCard, onDone: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val demand = remember(d.id) { ui.session.economy.unions.demand(d.id)?.raisePpm }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text("Counter-offer", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { Text("They ask for ${d.raise}. ${d.acceptableCounter}.", style = Type.Body, color = Theme.tokens.textPrimary) }
                item { LabeledField("Raise you offer (%)", text, { text = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("counter")) }
            }
        },
        confirmButton = {
            PrimaryButton("Offer", {
                val r = DemandResponseForm(company, d.id, ResponseKind.COUNTER, text, demand).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Offer made.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}

@Composable
private fun ConfirmRefuse(ui: GameUi, company: CompanyId, d: DemandCard, onDone: () -> Unit) {
    ConfirmDialog(
        "Refuse the demand?", "The union may walk out. Strong unions are more likely to, and a strike stops work in ${d.region} until it ends.",
        "Refuse", { ui.issue(Command.RespondDemand(company, d.id, ResponseKind.REFUSE), "Demand refused."); onDone() }, onDone,
    )
}
