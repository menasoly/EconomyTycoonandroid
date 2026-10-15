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
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.SectorFilter
import app.many.economytycoon.ui.present.SectorRow
import app.many.economytycoon.ui.present.SectorsPresenter

/** Who makes each good, how crowded it is and where a new producer would be welcome. */
@Composable
fun SectorsScreen(ui: GameUi) {
    var filter by remember { mutableStateOf(SectorFilter.OPENINGS) }
    var open by remember { mutableStateOf<GoodId?>(null) }
    val model = remember(ui.revision / 5, filter) { SectorsPresenter.build(ui.session, filter) }
    ScreenFrame("Who makes what", onBack = { ui.back() }) {
        ScreenList {
            item { ChoiceChips(SectorFilter.values().toList(), filter, { it.label }, { filter = it; open = null }) }
            item { Text(model.summary, style = Type.Caption, color = Theme.tokens.textSecondary) }
            if (model.rows.isEmpty()) item { EmptyState("Nothing here", model.empty) }
            items(model.rows.size) { i ->
                val r = model.rows[i]
                SectorCard(r, open == r.good) { open = if (open == r.good) null else r.good }
            }
        }
    }
}

@Composable
private fun SectorCard(r: SectorRow, expanded: Boolean, toggle: () -> Unit) {
    Card(onClick = toggle) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(r.name, style = Type.Heading, color = Theme.tokens.textPrimary)
            Pill(r.balance, r.balanceTone.color())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(r.concentration, style = Type.Caption, color = r.concentrationTone.color())
            Text(r.price, style = Type.Caption, color = r.priceTone.color())
        }
        Text("Biggest: ${r.leader}", style = Type.Body, color = Theme.tokens.textSecondary)
        r.yours?.let { Text(it, style = Type.Body, color = Theme.tokens.textPrimary) }
        if (r.opening) Text("Room for another producer.", style = Type.Body, color = Theme.tokens.positive)
        if (expanded) {
            for (p in r.producers) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (p.mine) "${p.name} (you)" else p.name, style = Type.Body, color = Theme.tokens.textPrimary)
                    Text("${p.share} · ${p.facilities} site${if (p.facilities == 1) "" else "s"}", style = Type.Figure, color = Theme.tokens.textSecondary)
                }
            }
        }
    }
}
