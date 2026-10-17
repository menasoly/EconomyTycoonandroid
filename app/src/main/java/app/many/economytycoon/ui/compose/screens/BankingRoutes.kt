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
import app.many.economytycoon.banking.BankId
import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.model.FoundBankForm
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.BankActionText
import app.many.economytycoon.ui.present.BankStressPresenter

/** Connects the banking screens, which take models and callbacks, to the running game. */
@Composable
fun BankingRoute(ui: GameUi) {
    val model = remember(ui.revision / 3) { ui.session.bankingOverview() }
    if (model == null) {
        ScreenFrame("Banks", onBack = { ui.back() }) { EmptyState("No banks", "Banks belong to a company in a game with a banking sector.") }
        return
    }
    BankingScreen(
        model = model,
        onOpen = { ui.open(Route.BankDetail(it.value)) },
        onFound = { ui.open(Route.FoundBank) },
        onStressTest = { ui.open(Route.BankStress) },
        onBack = { ui.back() },
    )
}

@Composable
fun BankDetailRoute(ui: GameUi, bank: Long) {
    val id = BankId(bank)
    val detail = remember(ui.revision, bank) { ui.session.bankDetail(id) }
    val advice = remember(ui.revision, bank) { ui.session.bankAdvice(id) }
    val company = ui.session.selectedCompany
    if (detail == null || company == null) {
        ScreenFrame("Bank", onBack = { ui.back() }) { EmptyState("No such bank", "This bank no longer exists.") }
        return
    }
    BankDetailScreen(
        detail = detail,
        advice = advice,
        onAction = { a -> ui.issue(Command.BankDecision(company, a), BankActionText.short(a) { ui.session.settings.money(it) }) },
        onDismissAdvice = { code -> ui.session.hideBankAdvice(code) },
        onBack = { ui.back() },
    )
}

@Composable
fun BankStressRoute(ui: GameUi) {
    var result by remember { mutableStateOf<app.many.economytycoon.ui.present.StressView?>(null) }
    BankStressScreen(BankStressPresenter.scenarios(), result, onRun = { id -> result = ui.session.bankStress(id) }, onBack = { ui.back() })
}

@Composable
fun FoundBankRoute(ui: GameUi) {
    val company = ui.session.selectedCompany?.let { ui.session.economy.companies.company(it) }
    ScreenFrame("Found a bank", onBack = { ui.back() }) {
        if (company == null) {
            EmptyState("No company", "Banks belong to a company.")
            return@ScreenFrame
        }
        var name by remember { mutableStateOf("") }
        var capital by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<FormResult?>(null) }
        val minimum = Money.units(ui.session.economy.banking.params.minFoundingCapitalUnits)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("The bank opens in your home country. At least ${ui.session.settings.money(minimum)} of capital is needed.", style = Type.Body, color = Theme.tokens.textSecondary)
            LabeledField("Name", name, { name = it }, error = result?.errorFor("name"))
            LabeledField("Capital", capital, { capital = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("capital"))
            PrimaryButton("Found the bank", {
                val cash = ui.session.economy.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(company.id))
                val r = FoundBankForm(company.id, company.country, name, capital, minimum, cash).validate(); result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Bank founded.")) ui.back()
            }, Modifier.fillMaxWidth())
        }
    }
}
