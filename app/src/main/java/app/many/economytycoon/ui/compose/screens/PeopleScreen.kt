package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FieldKind
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.LinkButton
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.components.healthColor
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.PeoplePresenter
import app.many.economytycoon.ui.present.Tone
import app.many.economytycoon.simulation.Command

@Composable
fun PeopleScreen(ui: GameUi) {
    val model = remember(ui.revision) { PeoplePresenter.build(ui.session) }
    val company = ui.session.selectedCompany
    var benefitsOpen by remember { mutableStateOf(false) }

    ScreenFrame("People", onBack = { ui.back() }) {
        if (model == null || company == null) {
            EmptyState("No company", "People work for a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            for (w in model.warnings) item { Text(w, style = Type.Body, color = Theme.tokens.warning) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    LinkButton("Change benefits", { benefitsOpen = true })
                    LinkButton("Labour relations", { ui.open(Route.Unions) })
                    if (ui.session.hasTalent) LinkButton("Skills", { ui.open(Route.Talent) })
                }
            }
            item { SectionHeader("Teams") }
            if (model.cohorts.isEmpty()) item { EmptyState("Nobody works for you yet", "Choose recipes for your facilities, then set staffing on each one.") }
            items(model.cohorts.size) { i ->
                val c = model.cohorts[i]
                Card(onClick = { ui.open(Route.FacilityDetail(c.facility.value)) }) {
                    ListRow(c.role, subtitle = c.where, trailing = c.staffing, trailingTone = c.staffingTone)
                    Text("Wage ${c.wage}" + (c.wageNote?.let { " · $it" } ?: ""), style = Type.Caption, color = Theme.tokens.textSecondary)
                    MeterBar(c.morale, color = healthColor(c.morale))
                    if (c.flags.isNotEmpty()) Text(c.flags.joinToString(" · "), style = Type.Caption, color = Tone.WARN.color())
                }
            }
        }
        if (benefitsOpen) BenefitsDialog(ui, company) { benefitsOpen = false }
    }
}

@Composable
private fun BenefitsDialog(ui: GameUi, company: app.many.economytycoon.core.CompanyId, onDone: () -> Unit) {
    val current = remember { ui.session.reports.people.workforce(company).benefitsPpm }
    var text by remember { mutableStateOf((current / 10_000L).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone, containerColor = Theme.tokens.surface,
        title = { Text("Benefits", style = Type.Title, color = Theme.tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text("Better benefits raise morale and attract workers but cost payroll. 100% is the market norm.", style = Type.Body, color = Theme.tokens.textSecondary)
                LabeledField("Benefits level", text, { text = it }, kind = FieldKind.NUMBER, suffix = "%", error = error)
            }
        },
        confirmButton = {
            PrimaryButton("Set", {
                val pct = text.toLongOrNull()
                if (pct == null || pct !in 0L..300L) error = "Enter a whole number from 0 to 300."
                else if (ui.issue(Command.SetBenefits(company, pct * 10_000L), "Benefits updated.")) onDone()
            })
        },
        dismissButton = { LinkButton("Cancel", onDone) },
    )
}
