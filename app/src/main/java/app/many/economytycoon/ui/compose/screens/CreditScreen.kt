package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.CreditPresenter
import app.many.economytycoon.ui.present.FactorRow

/** What the credit rating is made of, factor by factor, and what would move it. */
@Composable
fun CreditScreen(ui: GameUi) {
    val model = remember(ui.revision / 5) { CreditPresenter.build(ui.session) }
    ScreenFrame("Credit rating", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("Not rated yet", "A rating is worked out at the first monthly close.")
            return@ScreenFrame
        }
        ScreenList {
            item {
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(model.score, style = Type.Title, color = Theme.tokens.textPrimary)
                        Pill(model.rating, model.ratingTone.color())
                    }
                    Text(model.headline, style = Type.Body, color = Theme.tokens.textPrimary)
                    model.outlook?.let { Text(it, style = Type.Caption, color = Theme.tokens.textSecondary) }
                    model.nextStep?.let { Text(it, style = Type.Body, color = Theme.tokens.textSecondary) }
                    model.cushion?.let { Text(it, style = Type.Caption, color = Theme.tokens.textSecondary) }
                }
            }
            if (model.levers.isNotEmpty()) {
                item { SectionHeader("Biggest chances to improve") }
                item { Card { for (l in model.levers) Text(l, style = Type.Body, color = Theme.tokens.textPrimary) } }
            }
            item { SectionHeader("What it is made of") }
            items(model.factors.size) { i -> FactorCard(model.factors[i]) }
        }
    }
}

@Composable
private fun FactorCard(f: FactorRow) {
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(f.label, style = Type.Heading, color = Theme.tokens.textPrimary)
            Text(f.points, style = Type.Figure, color = f.tone.color())
        }
        MeterBar(f.fillPpm / 1_000_000.0)
        Text(f.state, style = Type.Caption, color = f.tone.color())
        f.advice?.let { Text(it, style = Type.Body, color = Theme.tokens.textSecondary) }
    }
}
