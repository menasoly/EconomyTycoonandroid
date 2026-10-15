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
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.AdvisorPresenter

/** Suggestions drawn from the company's books, most pressing first. Each can be followed or set aside. */
@Composable
fun AdvisorScreen(ui: GameUi) {
    val model = remember(ui.revision / 5) { AdvisorPresenter.build(ui.session) }
    ScreenFrame("Advisor", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No company", "The advisor needs a company to look at.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text(model.headline, style = Type.Body, color = Theme.tokens.textSecondary) }
            items(model.items.size) { i ->
                val a = model.items[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Pill(a.topic, a.tone.color())
                        Text(a.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                    }
                    Text(a.detail, style = Type.Body, color = Theme.tokens.textSecondary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        val route = a.route
                        if (route != null && a.actionLabel != null) SecondaryButton(a.actionLabel, { ui.open(route) })
                        SecondaryButton("Hide for a month", { ui.session.hideAdvice(a.code) })
                    }
                }
            }
            if (model.hidden > 0) {
                item {
                    SecondaryButton("Show ${model.hidden} hidden", { ui.session.showHiddenAdvice() }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
