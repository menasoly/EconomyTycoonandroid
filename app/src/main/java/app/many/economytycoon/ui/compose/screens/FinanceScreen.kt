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
import app.many.economytycoon.companies.Department
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
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.model.BudgetForm
import app.many.economytycoon.ui.model.DividendForm
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.FinancePresenter
import app.many.economytycoon.ui.present.PolicyPresenter
import app.many.economytycoon.visualization.compose.DonutChart
import app.many.economytycoon.visualization.compose.LineChart

@Composable
fun FinanceScreen(ui: GameUi) {
    val model = remember(ui.revision) { FinancePresenter.build(ui.session) }
    val policy = remember(ui.revision) { PolicyPresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    var editing by remember { mutableStateOf<Department?>(null) }
    var dividendOpen by remember { mutableStateOf(false) }

    ScreenFrame("Finance") {
        if (model == null || company == null) {
            EmptyState("No company", "Finance belongs to a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { Pill(model.standing, model.standingTone.color()) }
            item { TileGrid(model.tiles) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Loans", { ui.open(Route.Loans) }, Modifier.weight(1f))
                    SecondaryButton("Statements", { ui.open(Route.Statements) }, Modifier.weight(1f))
                    SecondaryButton("Yearly", { ui.open(Route.Reports) }, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Analysis", { ui.open(Route.Analysis) }, Modifier.weight(1f))
                    SecondaryButton("Outlook", { ui.open(Route.Outlook) }, Modifier.weight(1f))
                    SecondaryButton("Credit", { ui.open(Route.Credit) }, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Grants", { ui.open(Route.Grants) }, Modifier.weight(1f))
                    SecondaryButton("Group", { ui.open(Route.Group) }, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Ownership", { ui.open(Route.Ownership) }, Modifier.weight(1f))
                    SecondaryButton("Stock exchange", { ui.open(Route.Exchange) }, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Insurance", { ui.open(Route.Insurance) }, Modifier.weight(1f))
                    SecondaryButton("Bonds", { ui.open(Route.Bonds) }, Modifier.weight(1f))
                }
            }
            item { SectionHeader("Net worth and cash") }
            item {
                Card {
                    if (model.netWorthSeries.points.size < 2) Text("The chart fills in as days pass.", style = Type.Body, color = Theme.tokens.textSecondary)
                    else LineChart(listOf(model.netWorthSeries, model.cashSeries), xLabel = { "day ${it.toLong()}" })
                }
            }
            item { SectionHeader("Where the money goes") }
            item {
                Card {
                    if (model.costBreakdown.isEmpty()) Text("No costs in the last 30 days.", style = Type.Body, color = Theme.tokens.textSecondary)
                    else DonutChart(model.costBreakdown, valueText = { v -> ui.session.settings.money(app.many.economytycoon.core.Money((v * app.many.economytycoon.core.Money.SCALE).toLong())) })
                }
            }
            item { SectionHeader("Ratios") }
            item { Card { FactList(model.ratios) } }
            if (policy != null) {
                item { SectionHeader("Departments", action = null) }
                item { Text("Budgets are spent every day. Total: ${policy.totalBudget}", style = Type.Caption, color = Theme.tokens.textSecondary) }
                items(policy.departments.size) { i ->
                    val d = policy.departments[i]
                    Card(onClick = { editing = d.department }) { ListRow(d.label, subtitle = d.blurb, trailing = d.budget) }
                }
                item { SectionHeader("Dividends") }
                item { Card(onClick = { dividendOpen = true }) { Text(policy.dividend.summary, style = Type.Body, color = Theme.tokens.textPrimary); LinkButton("Change", { dividendOpen = true }) } }
            }
        }
        val dept = editing
        if (dept != null && policy != null) BudgetDialog(ui, company, policy.departments.first { it.department == dept }.budgetEditText, dept) { editing = null }
        if (dividendOpen && policy != null) DividendDialog(ui, company, policy.dividend.payoutText, policy.dividend.reserveDays, policy.dividend.intervalDays) { dividendOpen = false }
    }
}

@Composable
private fun BudgetDialog(ui: GameUi, company: app.many.economytycoon.core.CompanyId, current: String, department: Department, onDone: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    var result by remember { mutableStateOf<FormResult?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone, containerColor = Theme.tokens.surface,
        title = { Text(department.label, style = Type.Title, color = Theme.tokens.textPrimary) },
        text = { LabeledField("Spend per day", text, { text = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("amount")) },
        confirmButton = {
            PrimaryButton("Set", {
                val r = BudgetForm(company, department, text).validate(); result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Budget updated.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}

@Composable
private fun DividendDialog(ui: GameUi, company: app.many.economytycoon.core.CompanyId, payout: String, reserve: Int, interval: Int, onDone: () -> Unit) {
    var payoutText by remember { mutableStateOf(payout) }
    var reserveText by remember { mutableStateOf(reserve.toString()) }
    var intervalText by remember { mutableStateOf(interval.toString()) }
    var result by remember { mutableStateOf<FormResult?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone, containerColor = Theme.tokens.surface,
        title = { Text("Dividend policy", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                LabeledField("Share of earnings paid out", payoutText, { payoutText = it }, kind = FieldKind.DECIMAL, suffix = "%", error = result?.errorFor("payout"))
                LabeledField("Cash reserve (days of costs)", reserveText, { reserveText = it }, kind = FieldKind.NUMBER, error = result?.errorFor("reserve"))
                LabeledField("Pay every (days)", intervalText, { intervalText = it }, kind = FieldKind.NUMBER, error = result?.errorFor("interval"))
            }
        },
        confirmButton = {
            PrimaryButton("Save", {
                val r = DividendForm(company, payoutText, reserveText.toIntOrNull() ?: -1, intervalText.toIntOrNull() ?: -1).validate(); result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Dividend policy saved.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
