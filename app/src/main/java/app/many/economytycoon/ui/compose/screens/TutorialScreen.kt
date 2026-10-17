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
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.TutorialPresenter

/** Short lessons, one at a time, each checked off by the game as the player does it. */
@Composable
fun TutorialScreen(ui: GameUi) {
    val model = remember(ui.revision / 5) { TutorialPresenter.build(ui.session) }
    ScreenFrame("Tutorial", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No company", TutorialPresenter.NOTHING)
            return@ScreenFrame
        }
        ScreenList {
            item { Text(model.headline, style = Type.Title, color = Theme.tokens.textPrimary) }
            item { MeterBar(model.progress) }
            val cur = model.current
            if (cur != null) {
                item {
                    Card {
                        Text(cur.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Text(cur.body, style = Type.Body, color = Theme.tokens.textSecondary)
                        Text(cur.goal, style = Type.Label, color = Theme.tokens.textSecondary)
                        MeterBar(cur.progress)
                        if (cur.needsAcknowledgement) PrimaryButton("Got it", { ui.session.tutorial.acknowledge(cur.id); ui.session.tutorialView() }, Modifier.fillMaxWidth())
                        val target = cur.screen?.let { Route.parse(it) }
                        if (target != null) SecondaryButton("Take me there", { ui.open(target) }, Modifier.fillMaxWidth())
                        SecondaryButton("Skip this lesson", { ui.session.tutorial.skipCurrent(); ui.session.tutorialView() }, Modifier.fillMaxWidth())
                    }
                }
            }
            for (g in model.groups) {
                item { SectionHeader(g.title) }
                item { Text(g.summary, style = Type.Caption, color = Theme.tokens.textSecondary) }
                items(g.rows.size) { i ->
                    val r = g.rows[i]
                    Card {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(r.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                            Pill(r.status, r.tone.color())
                        }
                        Text(r.goal, style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                }
            }
            item { SecondaryButton("Start the tutorial again", { ui.session.tutorial.restart(); ui.session.tutorialView() }, Modifier.fillMaxWidth()) }
        }
    }
}
