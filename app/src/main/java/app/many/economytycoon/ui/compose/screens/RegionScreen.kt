package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.Tone
import app.many.economytycoon.ui.present.WorldPresenter
import app.many.economytycoon.visualization.compose.DonutChart

@Composable
fun RegionScreen(ui: GameUi, regionId: Long) {
    val model = remember(ui.revision / 5, regionId) { WorldPresenter.region(ui.session, RegionId(regionId)) }
    ScreenFrame(model?.name ?: "Region", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("Unknown region", "This region does not exist.", action = "Back", onAction = { ui.back() })
            return@ScreenFrame
        }
        ScreenList {
            item { Card { FactList(model.facts) } }
            item { PrimaryButton("Build here", { ui.open(Route.Build(regionId)) }, Modifier.fillMaxWidth()) }
            item { SectionHeader("Population by age") }
            item { Card { DonutChart(model.ageBands, valueText = { v -> app.many.economytycoon.session.Formatting.grouped(v.toLong()) }) } }
            item { SectionHeader("Cities") }
            items(model.cities.size) { i ->
                val c = model.cities[i]
                Card {
                    ListRow(c.name, subtitle = "Unemployment ${c.unemployment} · wage ${c.wage}", trailing = c.population)
                    MeterBar(c.happiness)
                    if (c.tags.isNotEmpty()) Text(c.tags.joinToString(" · "), style = Type.Caption, color = Theme.tokens.info)
                }
            }
        }
    }
}
