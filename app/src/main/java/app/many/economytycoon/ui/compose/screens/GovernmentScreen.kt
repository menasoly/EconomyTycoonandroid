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
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
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
import app.many.economytycoon.ui.model.LobbyForm
import app.many.economytycoon.ui.present.GovernmentPresenter
import app.many.economytycoon.ui.present.LawCard

@Composable
fun GovernmentScreen(ui: GameUi) {
    val model = remember(ui.revision) { GovernmentPresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    var lobbying by remember { mutableStateOf<Pair<LawCard, Boolean>?>(null) }

    ScreenFrame("Government", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "Politics affects companies that operate in a country.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text("${model.country}: ${model.stance} government", style = Type.Title, color = Theme.tokens.textPrimary) }
            item { Text(model.stanceBlurb, style = Type.Body, color = Theme.tokens.textSecondary) }
            item { TileGrid(model.tiles) }
            model.standing?.let { s -> item { Text(s, style = Type.Body, color = Theme.tokens.warning) } }
            item { SectionHeader("Rules in force") }
            items(model.rules.size) { i ->
                val r = model.rules[i]
                Card { ListRow(r.label, subtitle = r.note, trailing = r.value) }
            }
            item { SectionHeader("Before parliament") }
            if (model.open.isEmpty()) item { EmptyState("Nothing under debate", "New proposals come every month or two.") }
            items(model.open.size) { i -> LawView(model.open[i]) { card, support -> lobbying = card to support } }
            if (model.coming.isNotEmpty()) {
                item { SectionHeader("Passed, not yet in force") }
                items(model.coming.size) { i -> LawView(model.coming[i]) { _, _ -> } }
            }
            if (model.fines.isNotEmpty()) {
                item { SectionHeader("Your pollution fines") }
                items(model.fines.size) { i ->
                    val f = model.fines[i]
                    Card {
                        ListRow(f.facility, subtitle = f.date, trailing = f.amount, badge = if (f.closed) "Closed" else null, badgeTone = app.many.economytycoon.ui.present.Tone.BAD)
                        Text(f.detail, style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                }
            }
            if (model.history.isNotEmpty()) {
                item { SectionHeader("Earlier laws") }
                items(model.history.size) { i -> LawView(model.history[i]) { _, _ -> } }
            }
        }
        val l = lobbying
        if (l != null) LobbyDialog(ui, company, l.first, l.second) { lobbying = null }
    }
}

@Composable
private fun LawView(law: LawCard, onLobby: (LawCard, Boolean) -> Unit) {
    Card {
        ListRow(law.title, subtitle = "${law.proposedBy} · ${law.timing}", badge = law.status, badgeTone = law.statusTone)
        Text(law.outlook, style = Type.Label, color = law.outlookTone.color())
        if (law.affectsYou) Text("Affects your facilities", style = Type.Caption, color = Theme.tokens.textSecondary)
        if (law.canLobby) {
            Text("Backers ${law.backing} · opponents ${law.opposing}", style = Type.Caption, color = Theme.tokens.textSecondary)
            law.yours?.let { Text(it, style = Type.Caption, color = Theme.tokens.textPrimary) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PrimaryButton("Back it", { onLobby(law, true) }, Modifier.weight(1f))
                SecondaryButton("Oppose it", { onLobby(law, false) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LobbyDialog(ui: GameUi, company: CompanyId, law: LawCard, support: Boolean, onDone: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FormResult?>(null) }
    val minimum = ui.session.economy.regulation.params.minLobby
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Theme.tokens.surface,
        title = { Text(if (support) "Back this law" else "Oppose this law", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { Text(law.title, style = Type.Body, color = Theme.tokens.textPrimary) }
                item { Text("Money spent here goes to the treasury as campaign support and cannot be taken back. Larger sums swing the vote further, with diminishing returns.", style = Type.Caption, color = Theme.tokens.textSecondary) }
                item { LabeledField("Amount", amount, { amount = it }, kind = FieldKind.DECIMAL, error = result?.errorFor("amount")) }
            }
        },
        confirmButton = {
            PrimaryButton("Spend", {
                val r = LobbyForm(company, law.id, support, amount, minimum, GovernmentPresenter.lobbyBudget(ui.session)).validate()
                result = r
                val cmd = r.commandOrNull
                if (cmd != null && ui.issue(cmd, "Lobbying under way.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
