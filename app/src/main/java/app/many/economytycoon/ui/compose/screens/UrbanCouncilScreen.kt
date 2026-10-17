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
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.urban.CityView
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.UrbanView

import app.many.economytycoon.ui.present.UrbanCouncilPresenter
import app.many.economytycoon.ui.present.PetitionRow

/** The council, the petitions before it and the lobbying around them. [issue] runs a command and returns its message. */
@Composable
fun UrbanCouncilScreen(ui: GameUi, view: UrbanView, issue: (UrbanCommand) -> String) {
    var selected by remember { mutableStateOf<CityView?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    ScreenFrame("City council", onBack = { ui.back() }) {
        if (view.cities.isEmpty()) {
            EmptyState("No cities", "No city has a plan yet.")
            return@ScreenFrame
        }
        val current = view.cities.firstOrNull { it.city == selected?.city } ?: view.cities.first()
        val model = remember(current, view.now) { UrbanCouncilPresenter.build(current, view.now) }
        ScreenList {
            item { ChoiceChips<CityView>(view.cities, current, { it.name }, { selected = it }) }
            item { TileGrid(model.tiles) }
            message?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.textSecondary) } }
            item { SectionHeader("Before the council") }
            if (model.open.isEmpty()) item { Text("No petitions are open.", style = Type.Caption, color = Theme.tokens.textSecondary) }
            items(model.open.size) { i ->
                val p = model.open[i]
                PetitionCard(p, if (p.canWithdraw) ({ message = issue(UrbanCouncilPresenter.withdraw(current.city, p)) }) else null)
            }
            if (model.closed.isNotEmpty()) {
                item { SectionHeader("Decided") }
                items(model.closed.size) { i -> PetitionCard(model.closed[i], null) }
            }
            item { SectionHeader("Members") }
            items(model.seats.size) { i ->
                val s = model.seats[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.name, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(s.stance, s.tone.color())
                    }
                    Text(s.ward + ", " + s.ties, style = Type.Caption, color = Theme.tokens.textSecondary)
                }
            }
            item { Text(model.note, style = Type.Caption, color = Theme.tokens.textSecondary) }
        }
    }
}

@Composable
private fun PetitionCard(p: PetitionRow, onWithdraw: (() -> Unit)?) {
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(p.title, style = Type.Heading, color = Theme.tokens.textPrimary)
            Pill(p.status, p.tone.color())
        }
        Text(p.district + ". " + p.timeline, style = Type.Caption, color = Theme.tokens.textSecondary)
        Text(p.vote, style = Type.Body, color = Theme.tokens.textSecondary)
        FactList(p.facts)
        if (onWithdraw != null) SecondaryButton("Withdraw", onWithdraw, Modifier.fillMaxWidth())
    }
}
