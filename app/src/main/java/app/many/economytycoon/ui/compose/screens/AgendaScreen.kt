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
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.AgendaPresenter
import app.many.economytycoon.ui.present.Tone

/** Payments, deliveries and deadlines in the order they fall due. */
@Composable
fun AgendaScreen(ui: GameUi) {
    val model = remember(ui.revision / 5) { AgendaPresenter.build(ui.session) }
    ScreenFrame("Coming up", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No company", "Dates belong to a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { TileGrid(model.tiles) }
            if (model.warning != null) item { Card { Text(model.warning, style = Type.Body, color = Tone.WARN.color()) } }
            if (model.days.isEmpty()) item { EmptyState("Nothing due", model.empty) }
            for (day in model.days) {
                item { SectionHeader("${day.inDays} · ${day.label}") }
                item {
                    Card {
                        for (r in day.rows) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(r.kind + ": " + r.title, style = Type.Body, color = Theme.tokens.textPrimary, modifier = Modifier.weight(1f))
                                if (r.cash != null) Text(r.cash, style = Type.Body, color = r.cashTone.color())
                            }
                            Text(r.detail, style = Type.Caption, color = Theme.tokens.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
