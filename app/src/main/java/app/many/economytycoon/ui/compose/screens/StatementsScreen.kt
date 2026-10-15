package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.Divider
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.StatementSection
import app.many.economytycoon.ui.present.StatementsPresenter

private enum class StatementKind(val label: String) { INCOME("Income"), BALANCE("Balance sheet"), CASH("Cash flow") }
private enum class Period(val label: String) { MONTH("30 days"), YEAR("360 days") }

@Composable
fun StatementsScreen(ui: GameUi) {
    var kind by remember { mutableStateOf(StatementKind.INCOME) }
    var period by remember { mutableStateOf(Period.MONTH) }
    val company = ui.session.selectedCompany
    val pack = remember(ui.revision) { company?.let { ui.session.reports.company.finance(it) } }

    ScreenFrame("Statements", onBack = { ui.back() }) {
        if (pack == null) {
            EmptyState("No company", "Statements belong to a company.")
            return@ScreenFrame
        }
        val settings = ui.session.settings
        val sections: List<StatementSection> = when (kind) {
            StatementKind.INCOME -> StatementsPresenter.income(if (period == Period.MONTH) pack.month else pack.year, settings)
            StatementKind.BALANCE -> StatementsPresenter.balance(pack.balance, settings)
            StatementKind.CASH -> StatementsPresenter.cashFlow(pack.cashFlow, settings)
        }
        ScreenList {
            item { ChoiceChips(StatementKind.values().toList(), kind, { it.label }, { kind = it }) }
            if (kind == StatementKind.INCOME) item { ChoiceChips(Period.values().toList(), period, { it.label }, { period = it }) }
            if (kind == StatementKind.BALANCE && !pack.balance.balances) item { Text("This balance sheet does not balance. Please report this.", style = Type.Body, color = Theme.tokens.negative) }
            for (section in sections) {
                item { SectionHeader(section.title) }
                item {
                    Card {
                        for (line in section.lines) {
                            if (line.level == 0) Divider()
                            Row(Modifier.fillMaxWidth().padding(start = if (line.level == 0) androidx.compose.ui.unit.Dp(0f) else Spacing.M)) {
                                Text(line.label, style = if (line.level == 0) Type.Heading else Type.Body, color = Theme.tokens.textPrimary, modifier = Modifier.weight(1f))
                                Text(line.value, style = Type.Figure, color = line.tone.color())
                            }
                        }
                    }
                }
            }
        }
    }
}

