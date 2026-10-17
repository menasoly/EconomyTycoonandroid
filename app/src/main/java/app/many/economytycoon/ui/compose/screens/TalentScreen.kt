package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.TalentModel
import app.many.economytycoon.ui.present.Tone

/** Skills, shortages and the people side of the company at a glance, with a way into each of the detail screens. */
@Composable
fun TalentScreen(model: TalentModel, onOpen: (Route) -> Unit, onBack: () -> Unit) {
    ScreenFrame("Talent", onBack = onBack) {
        ScreenList {
            item { TileGrid(model.tiles) }
            item { Text(model.note, style = Type.Body, color = Theme.tokens.textSecondary) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        LinkButton("Schools", { onOpen(Route.TalentEducation) })
                        LinkButton("Training", { onOpen(Route.TalentTraining) })
                        LinkButton("Recruiting", { onOpen(Route.TalentRecruiting) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        LinkButton("Careers", { onOpen(Route.TalentCareers) })
                        LinkButton("Providers", { onOpen(Route.TalentProviders) })
                    }
                }
            }
            if (model.advice.isNotEmpty()) {
                item { SectionHeader("Advice") }
                items(model.advice.size) { i ->
                    val a = model.advice[i]
                    TalentCard(a.title, null, a.tone, listOf(a.detail))
                }
            }
            item { SectionHeader("Teams") }
            if (model.cohorts.isEmpty()) item { EmptyState("No staff", "Hire people and their skills will show up here.") }
            items(model.cohorts.size) { i ->
                val c = model.cohorts[i]
                val lines = listOfNotNull(c.people, c.output, c.pay, "Morale ${c.morale}", c.weakness, c.openings, c.training)
                TalentCard(c.role, c.openings, if (c.openings != null) Tone.WARN else c.outputTone, lines)
            }
            if (model.shortages.isNotEmpty()) {
                item { SectionHeader("Shortages") }
                items(model.shortages.size) { i ->
                    val s = model.shortages[i]
                    TalentCard(s.title, s.kind, s.tone, listOf(s.where, s.detail))
                }
            }
            if (model.migration.isNotEmpty()) {
                item { SectionHeader("Migration") }
                items(model.migration.size) { i ->
                    val m = model.migration[i]
                    TalentCard(m.country, null, m.tone, listOf(m.doors, m.flows, m.brainDrain))
                }
            }
        }
    }
}
