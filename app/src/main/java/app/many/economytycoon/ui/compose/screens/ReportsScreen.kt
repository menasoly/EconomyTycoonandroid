package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.ReportsPresenter
import app.many.economytycoon.ui.present.StatementSection
import app.many.economytycoon.ui.present.YearCard

private enum class YearPart(val label: String) { SUMMARY("Summary"), INCOME("Income"), BALANCE("Balance sheet") }

/** One card per year of the company's life, newest first; a year opens out into its statements. */
@Composable
fun ReportsScreen(ui: GameUi) {
    val model = remember(ui.revision / 5) { ReportsPresenter.build(ui.session) }
    var open by remember { mutableStateOf<Int?>(null) }
    var part by remember { mutableStateOf(YearPart.SUMMARY) }
    ScreenFrame("Yearly results", onBack = { ui.back() }) {
        if (model == null || model.years.isEmpty()) {
            EmptyState("No results yet", "Results appear once the company has traded.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text(model.company, style = Type.Title, color = Theme.tokens.textPrimary) }
            item { Text(model.note, style = Type.Caption, color = Theme.tokens.textSecondary) }
            for (year in model.years) {
                item {
                    YearHeader(year, open == year.index) { open = if (open == year.index) null else year.index; part = YearPart.SUMMARY }
                }
                if (open == year.index) {
                    item { ChoiceChips(YearPart.values().toList(), part, { it.label }, { part = it }) }
                    when (part) {
                        YearPart.SUMMARY -> item {
                            Card {
                                FactList(year.tiles)
                                for (c in year.changes) Text(c, style = Type.Body, color = Theme.tokens.textSecondary)
                            }
                        }
                        YearPart.INCOME -> sections(year.income)
                        YearPart.BALANCE -> sections(year.balance)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sections(list: List<StatementSection>) {
    for (section in list) {
        item { SectionHeader(section.title) }
        item {
            Card {
                for (line in section.lines) {
                    Row(Modifier.fillMaxWidth().padding(start = if (line.level == 0) Spacing.S else Spacing.M)) {
                        Text(line.label, style = if (line.level == 0) Type.Heading else Type.Body, color = Theme.tokens.textPrimary, modifier = Modifier.weight(1f))
                        Text(line.value, style = Type.Figure, color = line.tone.color())
                    }
                }
            }
        }
    }
}

@Composable
private fun YearHeader(y: YearCard, open: Boolean, toggle: () -> Unit) {
    Card(onClick = toggle) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(y.title, style = Type.Title, color = Theme.tokens.textPrimary)
            Pill(y.verdict, y.tone.color())
        }
        Text(y.period, style = Type.Caption, color = Theme.tokens.textSecondary)
        if (!open) Text("Revenue ${y.tiles[0].value} · Net income ${y.tiles[1].value}", style = Type.Body, color = Theme.tokens.textSecondary)
    }
}
