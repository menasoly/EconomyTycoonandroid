package app.many.economytycoon.ui.compose.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.present.Glossary
import app.many.economytycoon.ui.present.GlossaryTerm

/** The words the game uses, explained, with links between related ones. */
@Composable
fun GlossaryScreen(ui: GameUi) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    val found = remember(query, group) {
        val list = Glossary.search(query)
        if (group == null) list else list.filter { it.group == group }
    }
    ScreenFrame("Glossary", onBack = { ui.back() }) {
        ScreenList {
            item { LabeledField("Search terms", query, { query = it; open = null }) }
            item { ChoiceChips(listOf<String?>(null) + Glossary.groups(), group, { it ?: "All" }, { group = it; open = null }) }
            if (found.isEmpty()) item { EmptyState("No match", "Try a different word.") }
            items(found.size) { i -> TermCard(found[i], open == found[i].id, { open = if (open == found[i].id) null else found[i].id }) { id ->
                query = ""; group = null; open = id
            } }
        }
    }
}

@Composable
private fun TermCard(t: GlossaryTerm, expanded: Boolean, toggle: () -> Unit, jump: (String) -> Unit) {
    Card(onClick = toggle) {
        Text(t.term, style = Type.Heading, color = Theme.tokens.textPrimary)
        Text(t.definition, style = Type.Body, color = Theme.tokens.textSecondary)
        if (expanded) {
            val related = Glossary.related(t) + Glossary.mentionedBy(t).filter { m -> t.seeAlso.none { it == m.id } }
            if (related.isNotEmpty()) {
                SectionHeader("See also")
                ChoiceChips<GlossaryTerm?>(related, null, { it?.term ?: "" }, { it?.let { x -> jump(x.id) } })
            }
        }
    }
}
