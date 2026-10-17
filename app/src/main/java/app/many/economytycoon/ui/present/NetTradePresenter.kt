package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.net.AssetKind
import app.many.economytycoon.net.PlayerId
import app.many.economytycoon.net.TradeAsset
import app.many.economytycoon.net.TradeState
import app.many.economytycoon.net.NetMessage
import app.many.economytycoon.session.Formatting

data class NetTradeRow(val id: Long, val headline: String, val gives: String, val wants: String, val status: String, val tone: Tone, val canAnswer: Boolean, val canWithdraw: Boolean)

data class NetTradeModel(val rows: List<NetTradeRow>, val empty: String)

/** Offers between players, held by the host until both sides have what they were promised. */
object NetTradePresenter {
    const val EMPTY = "No offers yet. Offers are held safely by the host until both sides agree."

    fun asset(a: TradeAsset): String = when (a.kind) {
        AssetKind.CASH -> Formatting.money(Money(a.amount))
        AssetKind.GOODS -> Formatting.quantity(Quantity(a.amount), a.key)
    }

    fun assets(list: List<TradeAsset>): String = if (list.isEmpty()) "nothing" else list.joinToString(", ") { asset(it) }

    fun status(s: TradeState): Pair<String, Tone> = when (s) {
        TradeState.OFFERED -> "Waiting for an answer" to Tone.INFO
        TradeState.ACCEPTED -> "Being settled" to Tone.WARN
        TradeState.SETTLED -> "Done" to Tone.GOOD
        TradeState.DECLINED -> "Declined" to Tone.MUTED
        TradeState.CANCELLED -> "Withdrawn" to Tone.MUTED
        TradeState.EXPIRED -> "Ran out of time" to Tone.MUTED
        TradeState.FAILED -> "Could not be completed, funds returned" to Tone.BAD
    }

    fun row(n: NetMessage.TradeNotice, me: PlayerId, names: (PlayerId) -> String): NetTradeRow {
        val mine = n.from == me
        val (text, tone) = status(n.state)
        val headline = if (mine) "Offer to ${names(n.to)}" else "Offer from ${names(n.from)}"
        // the notice is written from the offerer's side: they give [give] and want [want]
        val gives = if (mine) assets(n.give) else assets(n.want)
        val wants = if (mine) assets(n.want) else assets(n.give)
        return NetTradeRow(n.trade.value, headline, gives, wants, text, tone, canAnswer = !mine && n.state == TradeState.OFFERED, canWithdraw = mine && n.state == TradeState.OFFERED)
    }

    fun build(notices: List<NetMessage.TradeNotice>, me: PlayerId, names: (PlayerId) -> String): NetTradeModel =
        NetTradeModel(notices.sortedWith(compareByDescending<NetMessage.TradeNotice> { it.state.open }.thenByDescending { it.trade }).map { row(it, me, names) }, EMPTY)
}
