package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.many.economytycoon.banking.BankId
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.BankRow
import app.many.economytycoon.ui.present.BankingOverview

/** The banking sector: the banks the player runs, their rivals, and the state of the market. */
@Composable
fun BankingScreen(
    model: BankingOverview,
    onOpen: (BankId) -> Unit,
    onFound: () -> Unit,
    onStressTest: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenFrame("Banks", onBack = onBack) {
        ScreenList {
            item { Text(model.note, style = Type.Body, color = Theme.tokens.textSecondary) }
            item { TileGrid(model.tiles) }
            item { Card { FactList(model.market) } }
            item { SectionHeader("Your banks") }
            if (model.mine.isEmpty()) item { Text("You do not run a bank.", style = Type.Caption, color = Theme.tokens.textSecondary) }
            items(model.mine.size) { i -> BankCard(model.mine[i], onOpen) }
            item {
                Text(model.foundingNote, style = Type.Caption, color = Theme.tokens.textSecondary)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    if (model.canFound) PrimaryButton("Found a bank", onFound, Modifier.weight(1f))
                    SecondaryButton("Stress test", onStressTest, Modifier.weight(1f))
                }
            }
            item { SectionHeader("Other banks") }
            items(model.rivals.size) { i -> BankCard(model.rivals[i], onOpen) }
        }
    }
}

@Composable
private fun BankCard(row: BankRow, onOpen: (BankId) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.name, style = Type.Heading, color = Theme.tokens.textPrimary)
            Pill(row.status, row.tone.color())
        }
        Text(row.subtitle, style = Type.Caption, color = Theme.tokens.textSecondary)
        TileGrid(row.tiles)
        for (alert in row.alerts) Text(alert, style = Type.Body, color = Theme.tokens.textPrimary)
        SecondaryButton("Open", { onOpen(row.id) }, Modifier.fillMaxWidth())
    }
}
