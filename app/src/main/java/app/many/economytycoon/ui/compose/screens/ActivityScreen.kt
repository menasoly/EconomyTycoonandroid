package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.many.economytycoon.activity.ActivityArea
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.ActivityPresenter

/** A day-by-day list of what has been ordered for the company, with a filter by area of the game. */
@Composable
fun ActivityScreen(ui: GameUi) {
    var area by remember { mutableStateOf<ActivityArea?>(null) }
    val model = remember(ui.revision / 5, area) { ActivityPresenter.build(ui.session, area) }
    ScreenFrame("Activity", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No company", "Activity belongs to a company.")
            return@ScreenFrame
        }
        ScreenList {
            if (model.areas.size > 1) {
                item { ChoiceChips(listOf<ActivityArea?>(null) + model.areas, area, { it?.label ?: "All" }, { area = it }) }
            }
            if (model.days.isEmpty()) item { EmptyState("Nothing to show", model.empty) }
            for (day in model.days) {
                item { SectionHeader(day.label) }
                item {
                    Card {
                        for (r in day.rows) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(r.text, style = Type.Body, color = Theme.tokens.textPrimary, modifier = Modifier.weight(1f))
                                Text(r.area, style = Type.Caption, color = r.tone.color())
                            }
                        }
                    }
                }
            }
        }
    }
}
