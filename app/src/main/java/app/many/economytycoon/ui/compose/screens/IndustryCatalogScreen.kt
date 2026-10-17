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
import app.many.economytycoon.content.ExpandedContent
import app.many.economytycoon.content.catalog.CatalogBuilder
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.CatalogFilter
import app.many.economytycoon.ui.present.CatalogLookup
import app.many.economytycoon.ui.present.IndustryCatalogPresenter

/** A browsable list of every industry in the content, with its goods, recipes and technologies one tap away. */
@Composable
fun IndustryCatalogScreen(ui: GameUi) {
    var packId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    val report = remember { CatalogBuilder.build(ExpandedContent.packs) }
    val model = remember(packId, query) { IndustryCatalogPresenter.build(report, CatalogFilter(packId = packId, query = query)) }
    val lookup = open?.let { IndustryCatalogPresenter.detail(report, it) }
    ScreenFrame("Industries", onBack = { if (open != null) open = null else ui.back() }) {
        ScreenList {
            if (lookup is CatalogLookup.Found) {
                val d = lookup.detail
                item { SectionHeader(d.title) }
                item { TileGrid(d.tiles) }
                for (section in d.sections) {
                    item { SectionHeader(section.heading) }
                    items(section.items.size) { i -> Text(section.items[i], style = Type.Body, color = Theme.tokens.textPrimary) }
                }
                item { SecondaryButton("Back to the list", { open = null }, Modifier.fillMaxWidth()) }
            } else {
                item { TileGrid(model.tiles) }
                item { LabeledField("Find an industry", query, { query = it }) }
                item { ChoiceChips<String?>(listOf<String?>(null) + model.packs, packId, { it ?: "All packs" }, { packId = it }) }
                if (model.rows.isEmpty()) item { EmptyState("Nothing found", model.empty) }
                items(model.rows.size) { i ->
                    val r = model.rows[i]
                    Card(onClick = { open = r.key }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(r.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                            Pill(r.margin, r.tone.color())
                        }
                        Text(r.subtitle, style = Type.Caption, color = Theme.tokens.textSecondary)
                        Text("Payback ${r.payback}", style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                }
            }
        }
    }
}
