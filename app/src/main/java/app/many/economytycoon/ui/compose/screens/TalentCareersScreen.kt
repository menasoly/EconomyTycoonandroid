package app.many.economytycoon.ui.compose.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.present.CareersModel

/** Career ladders, key positions and their successors. */
@Composable
fun TalentCareersScreen(model: CareersModel, onAction: (TalentCommand) -> Unit, onBack: () -> Unit) {
    ScreenFrame("Careers", onBack = onBack) {
        ScreenList {
            item { TileGrid(model.tiles) }
            item { Text(model.note, style = Type.Body, color = Theme.tokens.textSecondary) }
            item { SectionHeader("Ladders") }
            items(model.ladders.size) { i ->
                val l = model.ladders[i]
                TalentCard(
                    l.title, l.state, l.tone, listOf(l.path) + l.steps.map { it.text },
                    listOf(CardAction("Pause or resume") { onAction(l.toggle) }, CardAction("Remove") { onAction(l.remove) }),
                )
            }
            item { SectionHeader("Key positions") }
            items(model.positions.size) { i ->
                val p = model.positions[i]
                val successors = p.successors.map { "${it.name}: ${it.readiness}, ${it.development}" }
                val actions = p.successors.mapNotNull { s -> s.upgrade?.let { cmd -> CardAction("Develop ${s.name}") { onAction(cmd) } } }
                TalentCard(p.title, null, p.tone, listOfNotNull(p.holder, p.effect, p.warning) + successors, actions)
            }
            item { SectionHeader("People") }
            items(model.people.size) { i ->
                val p = model.people[i]
                TalentCard(p.name, p.loyalty, p.tone, listOf(p.detail))
            }
        }
    }
}
