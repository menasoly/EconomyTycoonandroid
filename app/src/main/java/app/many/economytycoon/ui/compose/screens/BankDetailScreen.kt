package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.many.economytycoon.banking.BankAction
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
import app.many.economytycoon.ui.present.AdviceCard
import app.many.economytycoon.ui.present.BankDetail
import app.many.economytycoon.ui.present.Fact

/** One bank: statements, ratios, policy and the advisor. Every change goes out as a [BankAction]. */
@Composable
fun BankDetailScreen(
    detail: BankDetail,
    advice: List<AdviceCard>,
    onAction: (BankAction) -> Unit,
    onDismissAdvice: (String) -> Unit,
    onBack: () -> Unit,
) {
    ScreenFrame(detail.name, onBack = onBack) {
        ScreenList {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(detail.headline, style = Type.Body, color = Theme.tokens.textSecondary)
                    Pill(detail.status, detail.tone.color())
                }
            }
            detail.notice?.let { notice -> item { Text(notice, style = Type.Body, color = Theme.tokens.textPrimary) } }
            item { TileGrid(detail.tiles) }
            if (advice.isNotEmpty()) {
                item { SectionHeader("Advice") }
                items(advice.size) { i -> AdviceItem(advice[i], detail.canManage, onAction, onDismissAdvice) }
            }
            item { SectionHeader("Assets") }
            item { Card { FactList(detail.balanceAssets) } }
            item { SectionHeader("Funding") }
            item { Card { FactList(detail.balanceFunding) } }
            item { SectionHeader("Capital") }
            item { Card { FactList(detail.capital) } }
            item { SectionHeader("Liquidity") }
            item { Card { FactList(detail.liquidity) } }
            item { SectionHeader("Earnings") }
            item { Card { FactList(detail.earnings) } }
            item { SectionHeader("Depositors") }
            item { Card { FactList(detail.confidence) } }
            if (detail.drivers.isNotEmpty()) {
                item {
                    Card {
                        for (d in detail.drivers) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(d.label, style = Type.Body, color = Theme.tokens.textPrimary)
                                Pill(d.effect, d.tone.color())
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Lending by sector") }
            items(detail.lending.size) { i ->
                val l = detail.lending[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(l.label, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(l.balance, l.tone.color())
                    }
                    Text("Share ${l.share} · yield ${l.yield} · expected loss ${l.expectedLoss}", style = Type.Caption, color = Theme.tokens.textSecondary)
                }
            }
            item { SectionHeader("Policy") }
            item { Card { FactList(detail.policy.map { Fact(it.label, it.value, hint = it.hint) }) } }
            if (detail.history.isNotEmpty()) {
                item { SectionHeader("Recent months") }
                items(detail.history.size) { i ->
                    val h = detail.history[i]
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${h.month} · income ${h.income}", style = Type.Body, color = Theme.tokens.textSecondary)
                        Pill(h.result, h.tone.color())
                    }
                }
            }
        }
    }
}

@Composable
private fun AdviceItem(card: AdviceCard, canManage: Boolean, onAction: (BankAction) -> Unit, onDismiss: (String) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(card.title, style = Type.Heading, color = Theme.tokens.textPrimary)
            Pill(card.topic, card.tone.color())
        }
        Text(card.detail, style = Type.Body, color = Theme.tokens.textSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            val action = card.action
            if (canManage && action != null) PrimaryButton(card.actionLabel ?: "Do it", { onAction(action) }, Modifier.weight(1f))
            SecondaryButton("Hide", { onDismiss(card.code) }, Modifier.weight(1f))
        }
    }
}
