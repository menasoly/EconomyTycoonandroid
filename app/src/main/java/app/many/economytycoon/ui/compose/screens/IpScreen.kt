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
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.IpCard
import app.many.economytycoon.ui.present.IpPresenter
import app.many.economytycoon.ui.present.IpSessionView
import app.many.economytycoon.ui.present.IpTab

/** Patents, trade secrets, licences, disputes and the patent market of the selected company. */
@Composable
fun IpScreen(ui: GameUi, view: IpSessionView) {
    var tab by remember { mutableStateOf(IpTab.OVERVIEW) }
    var message by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    val model = remember(ui.revision / 3, tab, tick) { IpPresenter.build(view, tab) }
    ScreenFrame("Patents and secrets", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No company", "Intellectual property belongs to a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { ChoiceChips(model.tabs, model.tabs.first { it.tab == tab }, { it.label + if (it.count > 0) " " + it.count else "" }, { tab = it.tab }) }
            item { Text(model.note, style = Type.Body, color = Theme.tokens.textSecondary) }
            if (tab == IpTab.OVERVIEW) item { TileGrid(model.tiles) }
            message?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.textPrimary) } }
            if (model.insights.isNotEmpty()) {
                item { SectionHeader("Worth your attention") }
                items(model.insights.size) { i ->
                    val n = model.insights[i]
                    Card {
                        Text(n.title, style = Type.Heading, color = n.tone.color())
                        Text(n.detail, style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                }
            }
            for (section in model.sections) {
                if (section.cards.isEmpty() && section.empty == null) continue
                item { SectionHeader(section.heading) }
                if (section.cards.isEmpty()) item { Text(section.empty ?: "", style = Type.Caption, color = Theme.tokens.textSecondary) }
                items(section.cards.size) { i ->
                    IpCardView(section.cards[i], view, { message = it; tick++ })
                }
            }
        }
    }
}

@Composable
private fun IpCardView(card: IpCard, view: IpSessionView, onResult: (String) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(card.title, style = Type.Heading, color = Theme.tokens.textPrimary)
            card.badge?.let { Pill(it, card.tone.color()) }
        }
        Text(card.subtitle, style = Type.Caption, color = Theme.tokens.textSecondary)
        FactList(card.facts)
        card.text?.let { Text(it, style = Type.Body, color = Theme.tokens.textSecondary) }
        for (action in card.actions) {
            if (action.primary) PrimaryButton(action.label, { onResult(view.issue(action.command)) }, Modifier.fillMaxWidth())
            else SecondaryButton(action.label, { onResult(view.issue(action.command)) }, Modifier.fillMaxWidth())
        }
    }
}
