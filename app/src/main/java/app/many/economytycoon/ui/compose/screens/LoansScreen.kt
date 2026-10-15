package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.LoanId
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.model.LoanForm
import app.many.economytycoon.ui.model.RepayForm
import app.many.economytycoon.ui.present.FinancePresenter

@Composable
fun LoansScreen(ui: GameUi) {
    val model = remember(ui.revision) { FinancePresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    val pack = remember(ui.revision) { company?.let { ui.session.reports.company.finance(it) } }
    val plans = remember(ui.revision / 10) { app.many.economytycoon.ui.present.LoanPlanPresenter.build(ui.session).associateBy { it.id } }
    var borrowing by remember { mutableStateOf(false) }
    var repaying by remember { mutableStateOf<LoanId?>(null) }

    ScreenFrame("Loans", onBack = { ui.back() }) {
        if (model == null || company == null || pack == null) {
            EmptyState("No company", "Loans belong to a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text(model.creditLine ?: "The bank will not lend to you right now.", style = Type.Body, color = Theme.tokens.textSecondary) }
            item { PrimaryButton("Borrow", { borrowing = true }, Modifier.fillMaxWidth(), enabled = (pack.creditLimit?.raw ?: 0L) > 0L) }
            item { SectionHeader("Your loans") }
            if (model.loans.isEmpty()) item { Text("You owe nothing.", style = Type.Body, color = Theme.tokens.textSecondary) }
            items(model.loans.size) { i ->
                val l = model.loans[i]
                Card {
                    ListRow(l.title, subtitle = l.detail, trailing = l.outstanding, badge = l.status, badgeTone = l.tone)
                    MeterBar(l.repaid)
                    val plan = plans[l.id]
                    if (plan != null) {
                        Text(plan.headline, style = Type.Caption, color = Theme.tokens.textSecondary)
                        for (line in plan.lines) Text("${line.label}: ${line.payment} (${line.split})", style = Type.Caption, color = Theme.tokens.textSecondary)
                        if (plan.moreCount > 0) Text("and ${plan.moreCount} more", style = Type.Caption, color = Theme.tokens.textSecondary)
                        FactList(plan.facts)
                    }
                    if (l.active) LinkButton("Repay", { repaying = l.id })
                }
            }
        }
        if (borrowing) BorrowDialog(ui, company, pack.creditLimit) { borrowing = false }
        val loan = repaying
        if (loan != null) {
            val row = pack.loans.first { it.id == loan }
            RepayDialog(ui, company, row.id, row.outstanding) { repaying = null }
        }
    }
}

@Composable
private fun BorrowDialog(ui: GameUi, company: CompanyId, limit: app.many.economytycoon.core.Money?, onDone: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var months by remember { mutableStateOf(12) }
    var result by remember { mutableStateOf<FormResult?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone, containerColor = Theme.tokens.surface,
        title = { Text("Borrow", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (limit != null) Text("Up to ${ui.session.settings.money(limit)}", style = Type.Caption, color = Theme.tokens.textSecondary)
                LabeledField("Amount", amount, { amount = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("amount"))
                Text("Term", style = Type.Label, color = Theme.tokens.textSecondary)
                ChoiceChips(listOf(3, 6, 12, 24, 36, 60), months, { "$it months" }, { months = it })
                result?.errorFor("term")?.let { Text(it, style = Type.Caption, color = Theme.tokens.negative) }
            }
        },
        confirmButton = {
            PrimaryButton("Take loan", {
                val r = LoanForm(company, amount, months, limit).validate(); result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Loan drawn.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}

@Composable
private fun RepayDialog(ui: GameUi, company: CompanyId, loan: LoanId, outstanding: app.many.economytycoon.core.Money, onDone: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone, containerColor = Theme.tokens.surface,
        title = { Text("Repay loan ${loan.value}", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text("Outstanding: ${ui.session.settings.money(outstanding)}", style = Type.Caption, color = Theme.tokens.textSecondary)
                LabeledField("Amount", amount, { amount = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("amount"))
                LinkButton("Pay it all", { amount = app.many.economytycoon.ui.present.PolicyPresenter.editText(outstanding) })
            }
        },
        confirmButton = {
            PrimaryButton("Repay", {
                val r = RepayForm(company, loan, amount, outstanding).validate(); result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Payment made.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
