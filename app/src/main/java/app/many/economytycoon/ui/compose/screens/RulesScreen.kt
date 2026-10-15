package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.SwitchRow
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.model.BuyRuleForm
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.model.SellRuleForm
import app.many.economytycoon.ui.present.RuleItem
import app.many.economytycoon.ui.present.RulesPresenter
import app.many.economytycoon.ui.present.Tone

@Composable
fun RulesScreen(ui: GameUi) {
    val model = remember(ui.revision) { RulesPresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    var adding by remember { mutableStateOf<Boolean?>(null) }

    ScreenFrame("Automation rules", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Rules belong to a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text("Rules keep trading going while you do other things: sell surplus output, and keep inputs stocked.", style = Type.Body, color = Theme.tokens.textSecondary) }
            item { SectionHeader("Selling", action = "Add", onAction = { adding = true }) }
            if (model.sells.isEmpty()) item { Text("No selling rules.", style = Type.Body, color = Theme.tokens.textSecondary) }
            items(model.sells.size) { i -> RuleCard(ui, company, model.sells[i]) }
            item { SectionHeader("Buying", action = "Add", onAction = { adding = false }) }
            if (model.buys.isEmpty()) item { Text("No buying rules.", style = Type.Body, color = Theme.tokens.textSecondary) }
            items(model.buys.size) { i -> RuleCard(ui, company, model.buys[i]) }
        }
        val sell = adding
        if (sell != null) RuleDialog(ui, company, sell) { adding = null }
    }
}

@Composable
private fun RuleCard(ui: GameUi, company: app.many.economytycoon.core.CompanyId, r: RuleItem) {
    Card {
        ListRow(r.title, subtitle = r.summary, badge = if (r.enabled) null else "Off", badgeTone = Tone.MUTED)
        LinkButton("Remove rule", {
            ui.issue(if (r.sell) Command.RemoveSellRule(company, r.facility, r.good) else Command.RemoveBuyRule(company, r.facility, r.good), "Rule removed.")
        })
    }
}

@Composable
private fun RuleDialog(ui: GameUi, company: app.many.economytycoon.core.CompanyId, sell: Boolean, onDone: () -> Unit) {
    val facilities = remember { ui.session.reports.company.facilities(company) }
    val goods = remember { ui.session.economy.content.allGoods.filter { it.tradable }.sortedBy { it.name } }
    var facility by remember { mutableStateOf(facilities.firstOrNull()?.id) }
    var good by remember { mutableStateOf(goods.firstOrNull()?.id) }
    var strategy by remember { mutableStateOf(PriceStrategy.MARKET) }
    var markup by remember { mutableStateOf("0") }
    var reserve by remember { mutableStateOf("") }
    var maxOrder by remember { mutableStateOf("") }
    var minMargin by remember { mutableStateOf("0") }
    var target by remember { mutableStateOf("") }
    var multiple by remember { mutableStateOf("110") }
    var enabled by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<FormResult?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text(if (sell) "New selling rule" else "New buying rule", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (facilities.isEmpty()) item { Text("Build a facility first.", style = Type.Body, color = Theme.tokens.warning) }
                else {
                    item { Text("Facility", style = Type.Label, color = Theme.tokens.textSecondary) }
                    item { ChoiceChips(facilities.map { it.id }, facility, { id -> facilities.first { it.id == id }.name }, { facility = it }) }
                }
                item { Text("Good", style = Type.Label, color = Theme.tokens.textSecondary) }
                item { ChoiceChips(goods.map { it.id }, good, { id -> goods.first { it.id == id }.name }, { good = it }) }
                if (sell) {
                    item { ChoiceChips(PriceStrategy.values().toList(), strategy, { RulesPresenter.strategyLabel(it) }, { strategy = it }) }
                    item { LabeledField("Markup %", markup, { markup = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("markup")) }
                    item { LabeledField("Keep in stock", reserve, { reserve = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("reserve")) }
                    item { LabeledField("Largest order", maxOrder, { maxOrder = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("maxPerOrder")) }
                    item { LabeledField("Minimum margin %", minMargin, { minMargin = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("minMargin")) }
                } else {
                    item { LabeledField("Keep this much in stock", target, { target = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("target")) }
                    item { LabeledField("Pay up to % of market price", multiple, { multiple = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("maxMultiple")) }
                }
                item { SwitchRow("Active", enabled, { enabled = it }) }
            }
        },
        confirmButton = {
            PrimaryButton("Save rule", {
                val f = facility; val g = good
                if (f == null || g == null) ui.showMessage("Choose a facility and a good.")
                else {
                    val r = if (sell) SellRuleForm(company, f, g, strategy, markup, reserve, maxOrder, minMargin, enabled).validate()
                    else BuyRuleForm(company, f, g, target, multiple, enabled).validate()
                    result = r
                    val cmd = r.commandOrNull
                    if (cmd != null && ui.issue(cmd, "Rule saved.")) onDone()
                }
            }, enabled = facilities.isNotEmpty())
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
