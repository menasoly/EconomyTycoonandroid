package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.Tone

/** A button under a card: its label and what happens when it is pressed. */
class CardAction(val label: String, val run: () -> Unit)

/** The card the people screens share: a title with a status pill, a few lines of detail and optional buttons. */
@Composable
internal fun TalentCard(title: String, badge: String?, tone: Tone, lines: List<String>, actions: List<CardAction> = emptyList()) {
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = Type.Heading, color = Theme.tokens.textPrimary, modifier = Modifier.weight(1f))
            if (badge != null) Pill(badge, tone.color())
        }
        for (line in lines) Text(line, style = Type.Body, color = Theme.tokens.textSecondary)
        if (actions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                for (a in actions) SecondaryButton(a.label, a.run, Modifier.weight(1f))
            }
        }
    }
}
