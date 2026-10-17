package app.many.economytycoon.ui.compose.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.present.EducationModel
import app.many.economytycoon.ui.present.InstitutionRow
import app.many.economytycoon.ui.present.Tone

private fun lines(r: InstitutionRow): List<String> =
    listOfNotNull(r.where, r.seats, "Quality ${r.quality}", r.fees, r.results, "Teaches ${r.fields}", r.building)

/** Schools, colleges and universities, and how qualified each region is. */
@Composable
fun TalentEducationScreen(model: EducationModel, onAction: (TalentCommand) -> Unit, onBack: () -> Unit) {
    ScreenFrame("Education", onBack = onBack) {
        ScreenList {
            item { TileGrid(model.tiles) }
            item { Text(model.note, style = Type.Body, color = Theme.tokens.textSecondary) }
            item { SectionHeader("Yours") }
            if (model.mine.isEmpty()) item { EmptyState("No institutions", "Open a college where skills are scarce.") }
            items(model.mine.size) { i ->
                val r = model.mine[i]
                val close = r.close
                val actions = if (close != null) listOf(CardAction("Close") { onAction(close) }) else emptyList()
                TalentCard(r.title, r.owner, Tone.INFO, lines(r), actions)
            }
            item { SectionHeader("Others") }
            items(model.others.size) { i ->
                val r = model.others[i]
                TalentCard(r.title, r.owner, r.qualityTone, lines(r))
            }
            item { SectionHeader("Qualified people") }
            items(model.qualifications.size) { i ->
                val q = model.qualifications[i]
                TalentCard(q.where, q.index, q.tone, listOf("Schooled ${q.school}", "Vocational ${q.vocational}", "University ${q.university}"))
            }
        }
    }
}
