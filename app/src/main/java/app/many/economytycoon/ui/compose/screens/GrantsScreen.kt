package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.GrantRow
import app.many.economytycoon.ui.present.GrantsPresenter

/** Government grants: programmes to apply for, the ones the company is counting growth for, and past results. */
@Composable
fun GrantsScreen(ui: GameUi) {
    val model = remember(ui.revision / 3) { GrantsPresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    ScreenFrame("Grants", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Grants belong to a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text(model.note, style = Type.Body, color = Theme.tokens.textSecondary) }
            item { Card { FactListOf("Received so far", model.received) } }
            item { SectionHeader("Open for applications") }
            if (model.open.isEmpty()) item { Text("Nothing is open right now.", style = Type.Caption, color = Theme.tokens.textSecondary) }
            items(model.open.size) { i -> GrantCard(ui, model.open[i]) }
            item { SectionHeader("Yours, in progress") }
            if (model.running.isEmpty()) item { Text("You have not applied to anything.", style = Type.Caption, color = Theme.tokens.textSecondary) }
            items(model.running.size) { i -> GrantCard(ui, model.running[i]) }
            if (model.finished.isNotEmpty()) {
                item { SectionHeader("Finished") }
                items(model.finished.size) { i -> GrantCard(ui, model.finished[i]) }
            }
        }
    }
}

@Composable
private fun FactListOf(label: String, value: String) {
    FactList(listOf(app.many.economytycoon.ui.present.Fact(label, value)))
}

@Composable
private fun GrantCard(ui: GameUi, g: GrantRow) {
    val company = ui.session.selectedCompany ?: return
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(g.title, style = Type.Heading, color = Theme.tokens.textPrimary)
            Pill(g.status, g.tone.color())
        }
        Text("${g.country} · ${g.timing}", style = Type.Caption, color = Theme.tokens.textSecondary)
        Text(g.blurb, style = Type.Body, color = Theme.tokens.textSecondary)
        FactList(g.terms)
        g.standing?.let { Text(it, style = Type.Body, color = Theme.tokens.textPrimary) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (g.canApply) PrimaryButton("Apply", { ui.issue(Command.ApplyGrant(company, g.id), "Application sent.") }, Modifier.weight(1f))
            if (g.canWithdraw) SecondaryButton("Withdraw", { ui.issue(Command.WithdrawGrant(company, g.id), "Application withdrawn.") }, Modifier.weight(1f))
        }
    }
}
