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
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.GroupMemberRow
import app.many.economytycoon.ui.present.GroupPresenter
import app.many.economytycoon.ui.present.StatementSection

private enum class GroupView(val label: String) { MEMBERS("Businesses"), INCOME("Income"), BALANCE("Balance sheet") }

/** The company and everything it owns, added up by ownership share. */
@Composable
fun GroupScreen(ui: GameUi) {
    var view by remember { mutableStateOf(GroupView.MEMBERS) }
    val model = remember(ui.revision / 3) { GroupPresenter.build(ui.session) }
    ScreenFrame("Group", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No group", "This company does not own other businesses. Buy a controlling share of one to form a group.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text(model.title, style = Type.Title, color = Theme.tokens.textPrimary) }
            item { Card { FactList(model.tiles) } }
            item { ChoiceChips(GroupView.values().toList(), view, { it.label }, { view = it }) }
            when (view) {
                GroupView.MEMBERS -> {
                    items(model.members.size) { i -> MemberCard(model.members[i]) }
                    for (note in model.notes) item { Text(note, style = Type.Caption, color = Theme.tokens.textSecondary) }
                }
                GroupView.INCOME -> statementItems(model.income)
                GroupView.BALANCE -> statementItems(model.balance)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.statementItems(sections: List<StatementSection>) {
    for (section in sections) {
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
private fun MemberCard(m: GroupMemberRow) {
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(m.name, style = Type.Heading, color = Theme.tokens.textPrimary)
            Text(if (m.isRoot) "Parent" else "Owns ${m.interest}", style = Type.Caption, color = Theme.tokens.textSecondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Revenue share", style = Type.Body, color = Theme.tokens.textSecondary)
            Text(m.revenue, style = Type.Figure, color = Theme.tokens.textPrimary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Net income share", style = Type.Body, color = Theme.tokens.textSecondary)
            Text(m.netIncome, style = Type.Figure, color = m.netTone.color())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Assets share", style = Type.Body, color = Theme.tokens.textSecondary)
            Text(m.assets, style = Type.Figure, color = Theme.tokens.textPrimary)
        }
        Text("${m.shareOfGroup} of group revenue", style = Type.Caption, color = Theme.tokens.textSecondary)
    }
}
