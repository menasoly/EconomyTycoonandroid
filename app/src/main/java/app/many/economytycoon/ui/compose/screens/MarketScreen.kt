package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.MarketPresenter
import app.many.economytycoon.ui.present.MarketSort

@Composable
fun MarketScreen(ui: GameUi) {
    var category by remember { mutableStateOf<GoodCategory?>(null) }
    var sort by rememberSaveable { mutableStateOf(MarketSort.NAME) }
    var query by rememberSaveable { mutableStateOf("") }
    val model = remember(ui.revision, category, sort, query) { MarketPresenter.board(ui.session, category, sort, query) }

    ScreenFrame("Market") {
        ScreenList {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("My orders", { ui.open(Route.Orders) }, Modifier.weight(1f))
                    SecondaryButton("Rules", { ui.open(Route.Rules) }, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Contracts", { ui.open(Route.Contracts) }, Modifier.weight(1f))
                    SecondaryButton("Tenders", { ui.open(Route.Tenders) }, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Futures", { ui.open(Route.Futures) }, Modifier.weight(1f))
                    SecondaryButton("Who makes what", { ui.open(Route.Sectors) }, Modifier.weight(1f))
                }
            }
            if (model.inflation != null) {
                item { Text("Inflation ${model.inflation}" + (model.cpi?.let { " · consumer prices index $it" } ?: ""), style = Type.Caption, color = Theme.tokens.textSecondary) }
            }
            item { LabeledField("Search goods", query, { query = it }) }
            item { ChoiceChips(model.categories, category, { MarketPresenter.categoryLabel(it) }, { category = it }) }
            item { ChoiceChips(MarketSort.values().toList(), sort, { it.label }, { sort = it }) }
            if (model.rows.isEmpty()) item { EmptyState("No goods match", "Try a different search or category.") }
            items(model.rows.size) { i ->
                val r = model.rows[i]
                Card(onClick = { ui.open(Route.Good(r.good.key)) }) {
                    ListRow(r.name, subtitle = "${r.category} · traded ${r.volume}", trailing = r.price, badge = r.note, badgeTone = r.noteTone)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.change, style = Type.Caption, color = r.changeTone.color())
                        Text(r.versusReference, style = Type.Caption, color = r.referenceTone.color())
                    }
                }
            }
        }
    }
}
