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
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.KeyValue
import app.many.economytycoon.ui.compose.components.LabeledField
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.NetChatPresenter
import app.many.economytycoon.ui.present.NetScreenSource

private val TABS = listOf("Lobby", "Players", "Standings", "Trades", "Chat", "Sync")

/** Playing with other people: the lobby, who is connected and how well, the season table, offers, chat and sync problems. */
@Composable
fun MultiplayerScreen(ui: GameUi, source: NetScreenSource) {
    var tick by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(TABS[0]) }
    var message by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val revision = ui.revision / 5 + tick
    val banner = remember(revision) { source.link() }
    val lobby = remember(revision, tab) { if (tab == "Lobby") source.lobby() else null }
    val players = remember(revision, tab) { if (tab == "Players") source.players() else null }
    val standings = remember(revision, tab) { if (tab == "Standings") source.leaderboard() else null }
    val trades = remember(revision, tab) { if (tab == "Trades") source.trade() else null }
    val chat = remember(revision, tab) { if (tab == "Chat") source.chat() else null }
    val sync = remember(revision, tab) { if (tab == "Sync") source.desync() else null }
    ScreenFrame("Multiplayer", onBack = { ui.back() }) {
        ScreenList {
            item {
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(banner.state, style = Type.Heading, color = banner.tone.color())
                        Pill(banner.quality, banner.qualityTone.color())
                    }
                    if (banner.detail.isNotEmpty()) Text(banner.detail, style = Type.Caption, color = Theme.tokens.textSecondary)
                }
            }
            item { ChoiceChips(TABS, tab, { it }, { tab = it }) }
            message?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.textSecondary) } }
            when (tab) {
                "Lobby" -> if (lobby != null) {
                    val model = lobby
                    item { Text(model.phase, style = Type.Heading, color = Theme.tokens.textPrimary) }
                    item { TileGrid(model.tiles) }
                    items(model.members.size) { i ->
                        val m = model.members[i]
                        Card {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(m.name + if (m.me) " (you)" else "", style = Type.Heading, color = Theme.tokens.textPrimary)
                                Pill(m.status, m.tone.color())
                            }
                            Text(m.role + " · " + m.seat + if (m.host) " · Host" else "", style = Type.Caption, color = Theme.tokens.textSecondary)
                            if (m.canKick) SecondaryButton("Remove", { source.kick(m.id); tick++ }, Modifier.fillMaxWidth())
                        }
                    }
                    items(model.actions.size) { i ->
                        val a = model.actions[i]
                        Card {
                            PrimaryButton(a.label, { message = source.act(a.id); tick++ }, Modifier.fillMaxWidth(), enabled = a.enabled)
                            Text(a.hint, style = Type.Caption, color = Theme.tokens.textSecondary)
                        }
                    }
                    item { Text(model.note, style = Type.Caption, color = Theme.tokens.textSecondary) }
                }
                "Players" -> if (players != null) {
                    val model = players
                    item { TileGrid(model.tiles) }
                    if (model.rows.isEmpty()) item { EmptyState("Nobody here", model.empty) }
                    items(model.rows.size) { i ->
                        val r = model.rows[i]
                        Card {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(r.name, style = Type.Heading, color = Theme.tokens.textPrimary)
                                Pill(r.quality, r.qualityTone.color())
                            }
                            if (r.tags.isNotEmpty()) Text(r.tags.joinToString(" · "), style = Type.Caption, color = Theme.tokens.textSecondary)
                            KeyValue("Ping", r.ping)
                            KeyValue("Lost messages", r.loss)
                            KeyValue("Game", r.sync, valueColor = r.syncTone.color())
                            KeyValue("Standing", r.standing, valueColor = r.standingTone.color())
                        }
                    }
                }
                "Standings" -> if (standings != null) {
                    val model = standings
                    item { TileGrid(model.tiles) }
                    if (model.rows.isEmpty()) item { EmptyState("No standings yet", model.empty) }
                    items(model.rows.size) { i ->
                        val r = model.rows[i]
                        Card {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(r.rank + ". " + r.name, style = Type.Heading, color = r.tone.color())
                                Text(r.points, style = Type.Heading, color = r.tone.color())
                            }
                            KeyValue("Net worth", r.worth)
                            KeyValue("Income", r.income)
                            for (f in r.breakdown) KeyValue(f.label, f.value, valueColor = f.tone.color())
                        }
                    }
                }
                "Trades" -> if (trades != null) {
                    val model = trades
                    if (model.rows.isEmpty()) item { EmptyState("No offers", model.empty) }
                    items(model.rows.size) { i ->
                        val r = model.rows[i]
                        Card {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(r.headline, style = Type.Heading, color = Theme.tokens.textPrimary)
                                Pill(r.status, r.tone.color())
                            }
                            KeyValue("You give", r.gives)
                            KeyValue("You get", r.wants)
                            if (r.canAnswer) PrimaryButton("Accept", { source.answerTrade(r.id, true); tick++ }, Modifier.fillMaxWidth())
                            if (r.canAnswer) SecondaryButton("Decline", { source.answerTrade(r.id, false); tick++ }, Modifier.fillMaxWidth())
                            if (r.canWithdraw) SecondaryButton("Withdraw", { source.answerTrade(r.id, false); tick++ }, Modifier.fillMaxWidth())
                        }
                    }
                }
                "Chat" -> if (chat != null) {
                    val rows = chat
                    if (rows.isEmpty()) item { EmptyState("Quiet so far", "Say hello to the others.") }
                    items(rows.size) { i ->
                        val r = rows[i]
                        Card { Text(r.who, style = Type.Caption, color = Theme.tokens.textSecondary); Text(r.text, style = Type.Body, color = r.tone.color()) }
                    }
                    item { LabeledField("Message", draft, { draft = it }) }
                    item { PrimaryButton("Send", { message = source.send(draft); if (message == null) draft = ""; tick++ }, Modifier.fillMaxWidth()) }
                    item { SectionHeader("Quick replies") }
                    items(NetChatPresenter.emotes.size) { i ->
                        Card(onClick = { source.emote(i); tick++ }) { Text(NetChatPresenter.emotes[i], style = Type.Body, color = Theme.tokens.textPrimary) }
                    }
                }
                else -> if (sync != null) {
                    val model = sync
                    item { TileGrid(model.tiles) }
                    if (model.rows.isEmpty()) item { EmptyState("All in step", model.empty) }
                    items(model.rows.size) { i ->
                        val r = model.rows[i]
                        Card {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(r.headline, style = Type.Heading, color = r.tone.color())
                                Text(r.tick, style = Type.Caption, color = Theme.tokens.textSecondary)
                            }
                            Text(r.detail, style = Type.Body, color = Theme.tokens.textPrimary)
                            for (g in r.groups) Text(g, style = Type.Caption, color = Theme.tokens.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
