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
import app.many.economytycoon.risk.CoverLevel
import app.many.economytycoon.risk.Deductible
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
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.model.PolicyForm
import app.many.economytycoon.ui.present.ClaimRow
import app.many.economytycoon.ui.present.InsurancePresenter
import app.many.economytycoon.ui.present.PolicyCard
import app.many.economytycoon.ui.present.UninsuredRow

@Composable
fun InsuranceScreen(ui: GameUi) {
    val model = remember(ui.revision) { InsurancePresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    var buying by remember { mutableStateOf<FacilityId?>(null) }

    ScreenFrame("Insurance", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Choose a company to insure.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            item { Text(model.note, style = Type.Body, color = Theme.tokens.textSecondary) }
            if (model.uninsured.isNotEmpty()) {
                item { SectionHeader("Not insured") }
                items(model.uninsured.size) { i -> UninsuredView(model.uninsured[i]) { buying = it } }
            }
            if (model.policies.isNotEmpty()) {
                item { SectionHeader("Policies") }
                items(model.policies.size) { i -> PolicyView(ui, company, model.policies[i]) }
            }
            if (model.claims.isNotEmpty()) {
                item { SectionHeader("Disasters and claims") }
                items(model.claims.size) { i -> ClaimView(model.claims[i]) }
            }
            if (model.uninsured.isEmpty() && model.policies.isEmpty()) {
                item { EmptyState("Nothing to insure", "Build a facility and it can be covered here.") }
            }
        }
        val choice = buying
        if (choice != null) CoverDialog(ui, company, choice) { buying = null }
    }
}

@Composable
private fun UninsuredView(r: UninsuredRow, onInsure: (FacilityId) -> Unit) {
    Card {
        ListRow(r.name, subtitle = "Worth ${r.value} · ${r.from}", trailing = r.risk, trailingTone = r.tone)
        SecondaryButton("Insure", { onInsure(r.id) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PolicyView(ui: GameUi, company: CompanyId, p: PolicyCard) {
    Card {
        ListRow(p.title, subtitle = p.cover, trailing = p.premium, badge = p.status, badgeTone = p.statusTone)
        Text(p.insured, style = Type.Caption, color = Theme.tokens.textSecondary)
        Text(p.claims + if (p.renews.isNotBlank()) " · " + p.renews else "", style = Type.Caption, color = Theme.tokens.textSecondary)
        if (p.active) LinkButton("Cancel cover", { ui.issue(Command.CancelPolicy(company, p.id), "Cover cancelled.") })
    }
}

@Composable
private fun ClaimView(c: ClaimRow) {
    Card {
        ListRow(c.title, subtitle = c.when_, trailing = "Lost ${c.loss}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(c.note, style = Type.Caption, color = c.tone.color())
            Text("Paid: ${c.paid}", style = Type.Caption, color = c.tone.color())
        }
    }
}

@Composable
private fun CoverDialog(ui: GameUi, company: CompanyId, facility: FacilityId, onDone: () -> Unit) {
    var level by remember { mutableStateOf(CoverLevel.STANDARD) }
    var excess by remember { mutableStateOf(Deductible.MEDIUM) }
    var coverage by remember { mutableStateOf("80") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val params = ui.session.economy.risk.params
    val ppm = coverage.toDoubleOrNull()?.let { (it * 10_000).toLong() } ?: 800_000L
    val quote = remember(level, excess, ppm, ui.revision) { InsurancePresenter.quote(ui.session, facility, level, excess, ppm) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text("Insure this facility", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { Text("Cover", style = Type.Label, color = Theme.tokens.textSecondary) }
                item { ChoiceChips(CoverLevel.values().toList(), level, { it.label }, { level = it }) }
                item { Text("Excess you pay on each claim", style = Type.Label, color = Theme.tokens.textSecondary) }
                item { ChoiceChips(Deductible.values().toList(), excess, { it.label }, { excess = it }) }
                item { LabeledField("Share of each loss repaid (%)", coverage, { coverage = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("coverage")) }
                if (quote != null) {
                    item { Text("Covers ${quote.perils}.", style = Type.Body, color = Theme.tokens.textPrimary) }
                    item { Text("Premium ${quote.premium} a month (${quote.perMonthNote}).", style = Type.Heading, color = Theme.tokens.textPrimary) }
                    item { Text("Insured for ${quote.insured}; your excess is ${quote.excess}.", style = Type.Caption, color = Theme.tokens.textSecondary) }
                    items(quote.chances.size) { i -> Text("${quote.chances[i].peril}: ${quote.chances[i].chance}", style = Type.Caption, color = Theme.tokens.textSecondary) }
                    quote.problem?.let { p -> item { Text(p, style = Type.Caption, color = Theme.tokens.negative) } }
                }
                result?.errorFor("premium")?.let { m -> item { Text(m, style = Type.Caption, color = Theme.tokens.negative) } }
            }
        },
        confirmButton = {
            PrimaryButton("Buy cover", {
                val cash = ui.session.economy.finance.ledger.cash(ui.session.economy.companies.require(company).owner)
                val r = PolicyForm(company, facility, level, excess, coverage, params.minCoveragePpm, params.maxCoveragePpm, cash, quote?.premiumMoney).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Cover bought.")) onDone()
            }, enabled = quote?.problem == null)
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
