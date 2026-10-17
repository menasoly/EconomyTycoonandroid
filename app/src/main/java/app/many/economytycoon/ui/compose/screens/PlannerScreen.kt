package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.many.economytycoon.content.GoodDef
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.PlannerPresenter

/** Works out the buildings, staff and purchases needed to make a product, and whether it would pay. */
@Composable
fun PlannerScreen(ui: GameUi) {
    var query by remember { mutableStateOf("") }
    var good by remember { mutableStateOf<GoodId?>(null) }
    var rate by remember { mutableStateOf(10L) }
    var buy by remember { mutableStateOf(setOf<GoodId>()) }
    val goods = remember { ui.session.plannableGoods() }
    val matches = remember(query, goods) {
        val q = query.trim().lowercase()
        (if (q.isEmpty()) goods else goods.filter { it.name.lowercase().contains(q) }).take(12)
    }
    val plan = remember(good, rate, buy) { PlannerPresenter.plan(ui.session, good, rate, buy) }
    val model = remember(plan, ui.revision / 30) { PlannerPresenter.build(ui.session, plan) }
    ScreenFrame("Supply chain planner", onBack = { ui.back() }) {
        ScreenList {
            item { LabeledField("Find a product", query, { query = it }) }
            item { ChoiceChips<GoodDef?>(matches, goods.firstOrNull { it.id == good }, { it?.name ?: "" }, { x -> good = x?.id; buy = emptySet() }) }
            item { Text("Units a day", style = Type.Label, color = Theme.tokens.textSecondary) }
            item { ChoiceChips(PlannerPresenter.rates, rate, { "$it" }, { rate = it }) }
            if (plan == null) {
                item { EmptyState("No product chosen", PlannerPresenter.NOTHING) }
            } else {
                item { Text(model.verdict, style = Type.Body, color = model.verdictTone.color()) }
                item { TileGrid(model.tiles) }
                item { SectionHeader("Production tree") }
                item { Text("Tap an input to buy it instead of making it.", style = Type.Caption, color = Theme.tokens.textSecondary) }
                items(model.tree.size) { i ->
                    val l = model.tree[i]
                    Card(onClick = { if (l.depth > 0) buy = if (l.good in buy) buy - l.good else buy + l.good }) {
                        Row(Modifier.padding(start = (l.depth * 12).dp)) {
                            Text(l.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                        }
                        Text(l.detail, style = Type.Caption, color = Theme.tokens.textSecondary, modifier = Modifier.padding(start = (l.depth * 12).dp))
                    }
                }
                if (model.buildings.isNotEmpty()) { item { SectionHeader("Buildings") }; item { FactList(model.buildings) } }
                if (model.purchases.isNotEmpty()) { item { SectionHeader("Bought in") }; item { FactList(model.purchases) } }
                item { SectionHeader("Daily figures") }
                item { FactList(model.costs) }
            }
        }
    }
}
