package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.futures.Position
import app.many.economytycoon.futures.PositionStatus
import app.many.economytycoon.futures.Side
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class FutureGood(val id: String, val name: String, val spot: String, val contractValue: String)

data class PositionCard(
    val id: Long,
    val title: String,
    val detail: String,
    val result: String,
    val resultTone: Tone,
    val margin: String,
    val cover: Double,
    val coverTone: Tone,
    val expires: String,
    val call: String?,
    val shortfall: Money,
)

data class PastPosition(val title: String, val summary: String, val tone: Tone, val date: String)

data class FuturesModel(
    val tiles: List<Tile>,
    val goods: List<FutureGood>,
    val positions: List<PositionCard>,
    val history: List<PastPosition>,
    val tenors: List<Int>,
    val maxContracts: Long,
    val contractSize: String,
)

object FuturesPresenter {
    fun sideLabel(s: Side) = if (s == Side.LONG) "Buy" else "Sell"

    fun statusLabel(s: PositionStatus) = when (s) {
        PositionStatus.OPEN -> "Open"
        PositionStatus.CLOSED -> "Closed early"
        PositionStatus.EXPIRED -> "Settled at expiry"
        PositionStatus.LIQUIDATED -> "Closed out by the clearing house"
    }

    private fun resultTone(m: Money) = if (m.raw > 0L) Tone.GOOD else if (m.raw < 0L) Tone.BAD else Tone.NEUTRAL

    fun card(session: GameSession, p: Position): PositionCard {
        val e = session.economy
        val f = e.futures
        val good = e.content.good(p.good).name
        val pnl = f.unrealised(p)
        val need = f.maintenance(f.currentNotional(p))
        val cover = if (need.raw <= 0L) 1.0 else (f.equity(p).raw.toDouble() / (need.raw * 2.0)).coerceIn(0.0, 1.0)
        val left = f.daysLeft(p, session.tick)
        val tone = if (p.calledAt != null) Tone.BAD else if (cover < 0.6) Tone.WARN else Tone.GOOD
        return PositionCard(
            p.id.value, "${sideLabel(p.side)} ${Formatting.plural(p.contracts, "contract")} of $good",
            "Entered at ${session.settings.money(p.entry)} · now ${session.settings.money(p.mark)}",
            Formatting.signedMoney(pnl), resultTone(pnl), "Margin ${session.settings.money(p.margin)}", cover, tone,
            if (left == 0) "Settles today" else "Settles in ${Formatting.plural(left.toLong(), "day")}",
            if (p.calledAt != null) "Margin call: add ${session.settings.money(f.shortfall(p))} or it will be closed out" else null,
            f.shortfall(p),
        )
    }

    fun build(session: GameSession): FuturesModel? {
        val me: CompanyId = session.selectedCompany ?: return null
        val e = session.economy
        val f = e.futures
        val mine = f.openOf(me).sortedBy { it.id }
        val locked = mine.fold(Money.ZERO) { a, p -> a + p.margin }
        val open = mine.fold(Money.ZERO) { a, p -> a + f.unrealised(p) }
        val calls = mine.count { it.calledAt != null }
        val tiles = listOf(
            Tile("Open positions", mine.size.toString()),
            Tile("Margin locked", session.settings.money(locked)),
            Tile("Open result", Formatting.signedMoney(open), resultTone(open)),
            Tile("Margin calls", calls.toString(), if (calls > 0) Tone.BAD else Tone.NEUTRAL),
        )
        val goods = e.content.allGoods.map { g ->
            val spot = e.futuresRunner.spot(g.id)
            FutureGood(g.id.key, g.name, session.settings.money(spot), session.settings.money(f.notional(spot, 1)))
        }.sortedBy { it.name }
        val history = f.historyOf(me).sortedByDescending { it.closedAt?.value ?: 0L }.take(10).map { p ->
            PastPosition(
                "${sideLabel(p.side)} ${Formatting.plural(p.contracts, "contract")} of ${e.content.good(p.good).name}",
                "${statusLabel(p.status)}: ${Formatting.signedMoney(p.realised)}", resultTone(p.realised), p.closedAt?.let { Formatting.date(it) } ?: "",
            )
        }
        return FuturesModel(tiles, goods, mine.map { card(session, it) }, history, f.params.tenors, f.params.maxContracts, Formatting.quantity(f.params.contractSize))
    }
}
