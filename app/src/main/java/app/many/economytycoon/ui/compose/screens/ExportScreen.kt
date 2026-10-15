package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.app.ShareText
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.present.ExportPresenter

/** Share the company's figures as spreadsheet text. */
@Composable
fun ExportScreen(ui: GameUi) {
    val context = LocalContext.current
    val model = remember(ui.revision / 10) { ExportPresenter.build(ui.session) }
    ScreenFrame("Export", onBack = { ui.back() }) {
        if (model == null) {
            EmptyState("No company", "Exports follow a company.")
            return@ScreenFrame
        }
        ScreenList {
            item { Text(model.note, style = Type.Body, color = Theme.tokens.textSecondary) }
            items(model.items.size) { i ->
                val item = model.items[i]
                Card {
                    Text(item.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                    Text(item.blurb, style = Type.Body, color = Theme.tokens.textSecondary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SecondaryButton("Share", {
                            val file = ExportPresenter.file(ui.session, item.kind)
                            if (file == null || file.isEmpty) ui.showMessage(ExportPresenter.EMPTY_FILE)
                            else ShareText.send(context, ExportPresenter.subject(model.company, item.kind), file.text)
                        }, Modifier)
                    }
                }
            }
        }
    }
}
