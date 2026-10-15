package app.many.economytycoon.ui.compose.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.OwnershipPresenter
import app.many.economytycoon.ui.present.Tone

@Composable
fun OwnershipScreen(ui: GameUi) {
    val model = remember(ui.revision) { OwnershipPresenter.build(ui.session) }
    ScreenFrame("Ownership", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No company", "Ownership belongs to a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text(model.controller?.let { "Controlled by $it." } ?: "Nobody else controls your company.", style = Type.Body, color = Theme.tokens.textSecondary) }
            item { SectionHeader("What you own") }
            if (model.holdings.isEmpty()) item { Text("You hold no shares in other companies. Make an offer from a rival's page.", style = Type.Body, color = Theme.tokens.textSecondary) }
            items(model.holdings.size) { i ->
                val s = model.holdings[i]
                Card(onClick = { ui.open(Route.Rival(s.company.value)) }) { ListRow(s.name, trailing = s.share, badge = if (s.controlled) "Controlled" else null, badgeTone = Tone.GOOD) }
            }
            item { SectionHeader("Who owns you") }
            if (model.holders.isEmpty()) item { Text("No other company holds your shares.", style = Type.Body, color = Theme.tokens.textSecondary) }
            items(model.holders.size) { i ->
                val s = model.holders[i]
                Card(onClick = { ui.open(Route.Rival(s.company.value)) }) { ListRow(s.name, trailing = s.share, badge = if (s.controlled) "Controls you" else null, badgeTone = Tone.WARN) }
            }
        }
    }
}
