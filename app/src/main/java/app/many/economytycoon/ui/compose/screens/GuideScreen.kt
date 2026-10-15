package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.many.economytycoon.guide.StepState
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.GuidePresenter
import app.many.economytycoon.ui.present.GuideRow

/** The list of first steps, with the one to do now opened out. */
@Composable
fun GuideScreen(ui: GameUi) {
    val model = remember(ui.revision / 3) { GuidePresenter.build(ui.session) }
    ScreenFrame("First steps", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No company", "The guide follows a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text(model.headline, style = Type.Title, color = Theme.tokens.textPrimary) }
            item {
                Card {
                    Text(model.summary, style = Type.Body, color = Theme.tokens.textSecondary)
                    MeterBar(model.ppm / 1_000_000.0)
                }
            }
            items(model.rows.size) { i -> StepCard(ui, model.rows[i]) }
        }
    }
}

@Composable
private fun StepCard(ui: GameUi, r: GuideRow) {
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(r.title, style = Type.Heading, color = if (r.state == StepState.LATER) Theme.tokens.textSecondary else Theme.tokens.textPrimary)
            Pill(r.stateLabel, r.tone.color())
        }
        if (r.state != StepState.DONE) Text(r.why, style = Type.Body, color = Theme.tokens.textSecondary)
        if (r.state == StepState.CURRENT) {
            Text(r.how, style = Type.Body, color = Theme.tokens.textPrimary)
            r.route?.let { route ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Open ${r.area}", { ui.open(route) }, Modifier.weight(1f))
                }
            }
        }
    }
}
