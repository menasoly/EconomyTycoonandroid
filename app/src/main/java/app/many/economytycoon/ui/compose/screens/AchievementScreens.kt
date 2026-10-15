package app.many.economytycoon.ui.compose.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.many.economytycoon.ui.compose.app.AppHost
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.AchievementsModel
import app.many.economytycoon.ui.present.AchievementsPresenter
import app.many.economytycoon.ui.present.Tone

@Composable
private fun AchievementsBody(model: AchievementsModel) {
    ScreenList {
        item { Text(model.points, style = Type.Title, color = Theme.tokens.textPrimary) }
        item { Text(model.summary, style = Type.Label, color = Theme.tokens.textSecondary) }
        for (g in model.groups) {
            item { SectionHeader("${g.name} · ${g.unlockedCount} of ${g.items.size}") }
            items(g.items.size) { i ->
                val a = g.items[i]
                Card {
                    ListRow((if (a.unlocked) "✓ " else "") + a.title, subtitle = a.description, trailing = a.tier, trailingTone = a.tone)
                    if (!a.unlocked) {
                        Text(a.progress, style = Type.Caption, color = Theme.tokens.textSecondary)
                        MeterBar(a.ratio, color = Tone.INFO.color())
                    }
                }
            }
        }
        if (model.stats.isNotEmpty()) {
            item { SectionHeader("Lifetime") }
            item { FactList(model.stats) }
        }
    }
}

/** Achievements of the running game's profile. */
@Composable
fun AchievementsScreen(ui: GameUi) {
    val model = remember(ui.revision / 5) { AchievementsPresenter.build(ui.session) }
    ScreenFrame("Achievements", onBack = { ui.back() }) {
        if (model == null) EmptyState("Not tracked", "Achievements are not being recorded in this session.") else AchievementsBody(model)
    }
}

/** Achievements from the main menu, judged from the saved profile alone. */
@Composable
fun AchievementsMenuScreen(host: AppHost) {
    val model = AchievementsPresenter.build(host.profile) { 0L }
    ScreenFrame("Achievements", onBack = { host.showMenu() }) { AchievementsBody(model) }
}
