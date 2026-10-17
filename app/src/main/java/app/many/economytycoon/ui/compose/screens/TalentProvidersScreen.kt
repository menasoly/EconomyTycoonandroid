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
import app.many.economytycoon.ui.present.ProviderRowModel
import app.many.economytycoon.ui.present.ProvidersModel

private fun lines(p: ProviderRowModel): List<String> = listOf(p.quality, p.results, p.business) + p.courses.map { "${it.title}: ${it.detail}" }

/** The training-provider market and the player's own education businesses. */
@Composable
fun TalentProvidersScreen(model: ProvidersModel, onAction: (TalentCommand) -> Unit, onBack: () -> Unit) {
    ScreenFrame("Training providers", onBack = onBack) {
        ScreenList {
            item { TileGrid(model.tiles) }
            item { Text(model.note, style = Type.Body, color = Theme.tokens.textSecondary) }
            item { SectionHeader("Yours") }
            if (model.mine.isEmpty()) item { EmptyState("No provider", "Open one where skills are scarce.") }
            items(model.mine.size) { i ->
                val p = model.mine[i]
                val actions = listOfNotNull(
                    p.accredit?.let { cmd -> CardAction("Apply for next level") { onAction(cmd) } },
                    p.moreTeaching?.let { cmd -> CardAction("Teach more") { onAction(cmd) } },
                    p.lessTeaching?.let { cmd -> CardAction("Teach less") { onAction(cmd) } },
                ) + p.courses.mapNotNull { c -> c.close?.let { cmd -> CardAction("Close ${c.title}") { onAction(cmd) } } }
                TalentCard("${p.rank} ${p.title}", p.accreditation, p.tone, lines(p), actions)
            }
            item { SectionHeader("Ranking") }
            items(model.market.size) { i ->
                val p = model.market[i]
                TalentCard("${p.rank} ${p.title}", p.accreditation, p.tone, listOf(p.owner) + lines(p))
            }
        }
    }
}
