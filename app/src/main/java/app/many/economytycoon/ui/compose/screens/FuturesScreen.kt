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
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.futures.Side
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
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.model.FutureForm
import app.many.economytycoon.ui.model.MarginForm
import app.many.economytycoon.ui.present.FutureGood
import app.many.economytycoon.ui.present.FuturesModel
import app.many.economytycoon.ui.present.FuturesPresenter
import app.many.economytycoon.ui.present.PositionCard

@Composable
fun FuturesScreen(ui: GameUi) {
    val model = remember(ui.revision) { FuturesPresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    var opening by remember { mutableStateOf(false) }
    var topping by remember { mutableStateOf<PositionCard?>(null) }
    var closing by remember { mutableStateOf<PositionCard?>(null) }

    ScreenFrame("Futures", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Futures are traded by a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            item {
                Text(
                    "A futures contract fixes a price for ${model.contractSize} of a good. Sell one to lock in what you will get for stock you hold; buy one to fix what you will pay. It settles in cash.",
                    style = Type.Body, color = Theme.tokens.textSecondary,
                )
            }
            item { PrimaryButton("New position", { opening = true }, Modifier.fillMaxWidth()) }
            item { SectionHeader("Open positions") }
            if (model.positions.isEmpty()) item { EmptyState("Nothing open", "Hedge a price you depend on, or take a view on where it is heading.") }
            items(model.positions.size) { i ->
                val p = model.positions[i]
                Card {
                    ListRow(p.title, subtitle = p.detail, trailing = p.result, trailingTone = p.resultTone)
                    MeterBar(p.cover, color = p.coverTone.color())
                    Text("${p.margin} · ${p.expires}", style = Type.Caption, color = Theme.tokens.textSecondary)
                    p.call?.let { Text(it, style = Type.Caption, color = Theme.tokens.negative) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        SecondaryButton("Add margin", { topping = p }, Modifier.weight(1f))
                        DangerButton("Close", { closing = p }, Modifier.weight(1f))
                    }
                }
            }
            if (model.history.isNotEmpty()) {
                item { SectionHeader("Earlier positions") }
                items(model.history.size) { i ->
                    val h = model.history[i]
                    Card { ListRow(h.title, subtitle = h.summary, trailing = h.date) }
                }
            }
        }
        if (opening) OpenDialog(ui, company, model) { opening = false }
        val t = topping
        if (t != null) MarginDialog(ui, company, t) { topping = null }
        val c = closing
        if (c != null) {
            ConfirmDialog(
                "Close this position?", "It settles now at today's price. ${c.result} so far.",
                "Close", { ui.issue(Command.CloseFuture(company, c.id), "Position closed."); closing = null }, { closing = null },
            )
        }
    }
}

@Composable
private fun OpenDialog(ui: GameUi, company: CompanyId, model: FuturesModel, onDone: () -> Unit) {
    var good by remember { mutableStateOf<FutureGood?>(null) }
    var side by remember { mutableStateOf(Side.SHORT) }
    var tenor by remember { mutableStateOf(model.tenors.first()) }
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val cash = remember { ui.session.economy.finance.ledger.cash(ui.session.economy.companies.company(company)!!.owner) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text("New position", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { ChoiceChips(listOf(Side.SHORT, Side.LONG), side, { FuturesPresenter.sideLabel(it) }, { side = it }) }
                item { ChoiceChips(model.tenors, tenor, { "$it days" }, { tenor = it }) }
                item { LabeledField("Contracts", text, { text = it }, kind = FieldKind.NUMBER, error = result?.errorFor("contracts")) }
                item { Text(good?.let { "${it.name}: ${it.spot} each, ${it.contractValue} a contract" } ?: "Choose a good below", style = Type.Caption, color = Theme.tokens.textSecondary) }
                result?.errorFor("good")?.let { e -> item { Text(e, style = Type.Caption, color = Theme.tokens.negative) } }
                items(model.goods.size) { i ->
                    val g = model.goods[i]
                    LinkButton(g.name + if (good?.id == g.id) "  ✓" else "", { good = g })
                }
            }
        },
        confirmButton = {
            PrimaryButton("Open", {
                val contracts = text.trim().toLongOrNull()
                val q = if (good != null && contracts != null && contracts in 1..model.maxContracts) ui.session.economy.futuresRunner.quote(GoodId(good!!.id), side, contracts, tenor) else null
                val r = FutureForm(company, good?.let { GoodId(it.id) }, side, text, tenor, model.maxContracts, model.tenors, q?.cashNeeded, cash).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Position opened.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}

@Composable
private fun MarginDialog(ui: GameUi, company: CompanyId, p: PositionCard, onDone: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val cash = remember { ui.session.economy.finance.ledger.cash(ui.session.economy.companies.company(company)!!.owner) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text("Add margin", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = { LabeledField("Amount", text, { text = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("amount")) },
        confirmButton = {
            PrimaryButton("Add", {
                val r = MarginForm(company, p.id, text, cash).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Margin added.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
