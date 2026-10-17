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
import app.many.economytycoon.ui.present.ProgrammeRow
import app.many.economytycoon.ui.present.TrainingModel

private fun lines(r: ProgrammeRow): List<String> = listOfNotNull(r.detail, r.timing, r.bond)

/** Employer training programmes: what is running and what could be started. */
@Composable
fun TalentTrainingScreen(model: TrainingModel, onAction: (TalentCommand) -> Unit, onBack: () -> Unit) {
    ScreenFrame("Training", onBack = onBack) {
        ScreenList {
            item { TileGrid(model.tiles) }
            item { Text(model.note, style = Type.Body, color = Theme.tokens.textSecondary) }
            item { SectionHeader("Running") }
            items(model.running.size) { i ->
                val r = model.running[i]
                val cancel = r.cancel
                val actions = if (cancel != null) listOf(CardAction("Cancel") { onAction(cancel) }) else emptyList()
                TalentCard(r.title, r.status, r.tone, lines(r), actions)
            }
            item { SectionHeader("Could start") }
            items(model.options.size) { i ->
                val o = model.options[i]
                TalentCard(o.title, o.cost, app.many.economytycoon.ui.present.Tone.INFO, listOf(o.detail), listOf(CardAction("Start") { onAction(o.start) }))
            }
            item { SectionHeader("Finished") }
            items(model.past.size) { i ->
                val r = model.past[i]
                TalentCard(r.title, r.status, r.tone, lines(r))
            }
        }
    }
}
