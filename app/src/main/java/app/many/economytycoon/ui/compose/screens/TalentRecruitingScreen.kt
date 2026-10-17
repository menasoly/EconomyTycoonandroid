package app.many.economytycoon.ui.compose.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.present.OfferRow
import app.many.economytycoon.ui.present.RecruitingModel

private fun lines(o: OfferRow): List<String> = listOfNotNull(o.terms, o.timing, o.odds)

/** Poaching and defence against it, non-competes, retention bonuses, employer branding and recruiting campaigns. */
@Composable
fun TalentRecruitingScreen(model: RecruitingModel, onAction: (TalentCommand) -> Unit, onBack: () -> Unit) {
    ScreenFrame("Recruiting", onBack = onBack) {
        ScreenList {
            item { TileGrid(model.tiles) }
            item { Text(model.note, style = Type.Body, color = Theme.tokens.textSecondary) }
            item {
                Card {
                    FactList(listOf(model.counterPolicy) + model.retention)
                    SecondaryButton("Switch counter-offers", { onAction(model.toggleCounter) })
                }
            }
            item { SectionHeader("Approaches against you") }
            items(model.againstUs.size) { i ->
                val o = model.againstUs[i]
                TalentCard(o.title, o.status, o.tone, lines(o))
            }
            item { SectionHeader("Your approaches") }
            items(model.hunting.size) { i ->
                val o = model.hunting[i]
                TalentCard(o.title, o.status, o.tone, lines(o))
            }
            item { SectionHeader("Non-competes") }
            items(model.covenants.size) { i ->
                val c = model.covenants[i]
                val release = c.release
                val actions = if (release != null) listOf(CardAction("Release") { onAction(release) }) else emptyList()
                TalentCard(c.title, null, c.tone, listOf(c.detail), actions)
            }
            item { SectionHeader("Campaigns") }
            items(model.campaigns.size) { i ->
                val c = model.campaigns[i]
                val cancel = c.cancel
                val actions = if (cancel != null) listOf(CardAction("Cancel") { onAction(cancel) }) else emptyList()
                TalentCard(c.title, c.status, c.tone, listOf(c.detail), actions)
            }
            items(model.suggestions.size) { i ->
                val s = model.suggestions[i]
                TalentCard(s.title, null, app.many.economytycoon.ui.present.Tone.INFO, listOf(s.detail), listOf(CardAction("Launch") { onAction(s.launch) }))
            }
            item { SectionHeader("Your name as an employer") }
            items(model.brands.size) { i ->
                val b = model.brands[i]
                TalentCard(b.where, b.appeal, b.tone, listOf(b.known))
            }
        }
    }
}
