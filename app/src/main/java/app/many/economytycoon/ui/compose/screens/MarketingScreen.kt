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
import app.many.economytycoon.marketing.CampaignId
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
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
import app.many.economytycoon.ui.present.CampaignAction
import app.many.economytycoon.ui.present.CampaignDetailModel
import app.many.economytycoon.ui.present.CampaignRow
import app.many.economytycoon.ui.present.MarketingScreenHost
import app.many.economytycoon.ui.present.MarketingScreenModel
import app.many.economytycoon.ui.present.MarketingTab

/** Advertising and promotion: campaigns and what they have done for the brand, channel comparison, the budget plan, results and advice. */
@Composable
fun MarketingScreen(ui: GameUi, host: MarketingScreenHost) {
    var tick by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(MarketingTab.CAMPAIGNS) }
    var open by remember { mutableStateOf<CampaignId?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val model = remember(ui.revision / 5, tick) { host.build() }
    ScreenFrame("Marketing", onBack = { if (open != null) { open = null } else { ui.back() } }) {
        if (model == null) {
            EmptyState("No company", "Marketing belongs to a company.")
            return@ScreenFrame
        }
        val detail = open?.let { model.details[it] }
        if (detail != null) {
            CampaignDetail(detail) { action -> message = host.perform(action); tick++ }
            return@ScreenFrame
        }
        ScreenList {
            item { ChoiceChips(MarketingTab.values().toList(), tab, { it.label }, { tab = it }) }
            message?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.textSecondary) } }
            when (tab) {
                MarketingTab.CAMPAIGNS -> campaignItems(model) { open = it }
                MarketingTab.CHANNELS -> channelItems(model)
                MarketingTab.PLAN -> planItems(model) { message = host.applyPlan(); tick++ }
                MarketingTab.RESULTS -> resultItems(model)
                MarketingTab.ADVICE -> adviceItems(model)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.campaignItems(model: MarketingScreenModel, onOpen: (CampaignId) -> Unit) {
    val list = model.list
    item { Text(list.note, style = Type.Body, color = Theme.tokens.textSecondary) }
    item { TileGrid(list.tiles) }
    item { SectionHeader("Running and booked") }
    if (list.live.isEmpty()) item { Text("Nothing is booked.", style = Type.Caption, color = Theme.tokens.textSecondary) }
    items(list.live.size) { i -> CampaignCard(list.live[i], onOpen) }
    if (list.brands.isNotEmpty()) {
        item { SectionHeader("How your brands stand") }
        items(list.brands.size) { i ->
            val b = list.brands[i]
            Card {
                Text(b.title, style = Type.Heading, color = b.tone.color())
                FactList(b.facts)
                b.note?.let { Text(it, style = Type.Caption, color = Theme.tokens.textSecondary) }
            }
        }
    }
    if (list.promotions.isNotEmpty()) {
        item { SectionHeader("Price promotions") }
        items(list.promotions.size) { i ->
            val p = list.promotions[i]
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(p.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                    Pill(p.status, p.tone.color())
                }
                Text(p.summary, style = Type.Body, color = Theme.tokens.textSecondary)
            }
        }
    }
    if (list.damages.isNotEmpty()) {
        item { SectionHeader("Bad news") }
        item { Card { FactList(list.damages) } }
    }
    if (list.ended.isNotEmpty()) {
        item { SectionHeader("Finished") }
        items(list.ended.size) { i -> CampaignCard(list.ended[i], onOpen) }
    }
}

@Composable
private fun CampaignCard(c: CampaignRow, onOpen: (CampaignId) -> Unit) {
    Card(onClick = { onOpen(c.id) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(c.title, style = Type.Heading, color = Theme.tokens.textPrimary)
            Pill(c.status, c.tone.color())
        }
        Text(c.place, style = Type.Caption, color = Theme.tokens.textSecondary)
        Text(c.summary, style = Type.Body, color = Theme.tokens.textSecondary)
    }
}

@Composable
private fun CampaignDetail(d: CampaignDetailModel, act: (CampaignAction) -> Unit) {
    ScreenList {
        item {
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(d.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                    Pill(d.status, d.tone.color())
                }
                Text(d.place, style = Type.Caption, color = Theme.tokens.textSecondary)
                Text(d.headline, style = Type.Body, color = Theme.tokens.textSecondary)
            }
        }
        items(d.warnings.size) { i -> Text(d.warnings[i], style = Type.Body, color = Theme.tokens.textPrimary) }
        item { SectionHeader("Booking") }
        item { Card { FactList(d.booking) } }
        item { SectionHeader("What it has done") }
        item { Card { FactList(d.effect) } }
        item { SectionHeader("Creative") }
        item { Card { FactList(d.creative) } }
        d.test?.let { t -> item { Text(t, style = Type.Body, color = Theme.tokens.textSecondary) } }
        if (d.roi.isNotEmpty()) {
            item { SectionHeader("Results") }
            item { Card { FactList(d.roi) } }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (d.actions.canPause) SecondaryButton("Pause", { act(CampaignAction.Pause(d.id)) }, Modifier.weight(1f))
                if (d.actions.canResume) SecondaryButton("Resume", { act(CampaignAction.Resume(d.id)) }, Modifier.weight(1f))
                if (d.actions.canExtend) SecondaryButton("Extend 30 days", { act(CampaignAction.Extend(d.id, 30)) }, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (d.actions.canRefresh) SecondaryButton("New creative", { act(CampaignAction.RefreshCreative(d.id)) }, Modifier.weight(1f))
                if (d.actions.canCancel) SecondaryButton("Cancel", { act(CampaignAction.Cancel(d.id)) }, Modifier.weight(1f))
            }
        }
        d.actions.cancelNote?.let { n -> item { Text(n, style = Type.Caption, color = Theme.tokens.textSecondary) } }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.channelItems(model: MarketingScreenModel) {
    val ch = model.channels
    if (ch == null) { item { EmptyState("Nothing to compare", "Choose a product you sell to compare channels.") }; return }
    item { Text(ch.title, style = Type.Heading, color = Theme.tokens.textPrimary) }
    item { Text(ch.note, style = Type.Caption, color = Theme.tokens.textSecondary) }
    items(ch.rows.size) { i ->
        val r = ch.rows[i]
        Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.label, style = Type.Heading, color = Theme.tokens.textPrimary)
                Pill(if (r.best) "Best return" else r.verdict, r.tone.color())
            }
            Text(r.blurb, style = Type.Body, color = Theme.tokens.textSecondary)
            FactList(r.facts)
            Text(r.verdict, style = Type.Caption, color = r.tone.color())
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.planItems(model: MarketingScreenModel, onBook: () -> Unit) {
    val plan = model.plan
    if (plan == null) { item { EmptyState("No plan yet", "The planner needs a budget and the products you sell.") }; return }
    item { TileGrid(plan.tiles) }
    item { Text(plan.stop, style = Type.Body, color = plan.tone.color()) }
    if (plan.canApply) item { PrimaryButton("Book this plan", onBook, Modifier.fillMaxWidth()) }
    items(plan.markets.size) { i ->
        val m = plan.markets[i]
        Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(m.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                Text(m.spend, style = Type.Body, color = Theme.tokens.textSecondary)
            }
            Text("Extra profit a day: ${m.extraProfit}", style = Type.Caption, color = m.tone.color())
            for (a in m.allocations) Text("${a.channel}: ${a.amount}, reach ${a.reach}, last step ${a.lastReturn}", style = Type.Body, color = Theme.tokens.textSecondary)
        }
    }
    if (plan.byChannel.isNotEmpty()) {
        item { SectionHeader("By channel") }
        item { Card { FactList(plan.byChannel) } }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resultItems(model: MarketingScreenModel) {
    val r = model.results
    if (r == null) { item { EmptyState("No results yet", "Results appear once a campaign has been running for a while.") }; return }
    item { Text(r.headline, style = Type.Heading, color = r.tone.color()) }
    item { TileGrid(r.tiles) }
    item { Card { FactList(r.facts) } }
    if (r.channels.isNotEmpty()) {
        item { SectionHeader("Spend by channel") }
        items(r.channels.size) { i ->
            val c = r.channels[i]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(c.channel, style = Type.Body, color = Theme.tokens.textPrimary)
                Text("${c.spend} (${c.share})", style = Type.Body, color = Theme.tokens.textSecondary)
            }
        }
    }
    item { Text(r.advice, style = Type.Body, color = Theme.tokens.textSecondary) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.adviceItems(model: MarketingScreenModel) {
    item { Text(model.advice.headline, style = Type.Body, color = Theme.tokens.textSecondary) }
    items(model.advice.items.size) { i ->
        val a = model.advice.items[i]
        Card {
            Text(a.title, style = Type.Heading, color = a.tone.color())
            Text(a.detail, style = Type.Body, color = Theme.tokens.textSecondary)
        }
    }
}
