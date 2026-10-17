package app.many.economytycoon.ui.present

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.trade.TradeLeg
import app.many.economytycoon.trade.TradeMath
import app.many.economytycoon.trade.TradeSort

data class TradeRow(
    val good: GoodId,
    val title: String,
    val route: String,
    val prices: String,
    val profit: String,
    val margin: String,
    val tone: Tone,
    val timing: String,
    val note: String?,
)

data class TradeModel(val tiles: List<Tile>, val rows: List<TradeRow>, val sizes: List<Long>, val empty: String)

/** Where a good could be bought cheaply and sold dearly, with what moving it costs. */
object TradePresenter {
    const val EMPTY = "No gap in prices is wide enough to cover freight at this size. Try a larger shipment or check back later."
    val sizes: List<Long> = listOf(10L, 100L, 1_000L)

    fun marginTone(ppm: Long): Tone = when {
        ppm >= 250_000L -> Tone.GOOD
        ppm >= 80_000L -> Tone.INFO
        else -> Tone.NEUTRAL
    }

    fun row(session: GameSession, leg: TradeLeg): TradeRow {
        val money = session.settings::money
        val per = Formatting.plural(leg.days.toLong().coerceAtLeast(1L), "day")
        val note = when {
            leg.crossesBorder -> "Crosses a border, so a tariff of ${money(leg.tariff)} applies."
            leg.carriagePerUnit.raw * 2 > leg.spread.raw -> "Carriage takes more than half of the price gap."
            else -> null
        }
        return TradeRow(
            good = leg.good,
            title = "${leg.goodName} (${Formatting.quantity(leg.quantity)})",
            route = "${leg.fromName} to ${leg.toName}",
            prices = "Buy ${money(leg.buyPrice)}, sell ${money(leg.sellPrice)}",
            profit = money(leg.netProfit),
            margin = Formatting.percentPpm(leg.marginPpm),
            tone = marginTone(leg.marginPpm),
            timing = "$per, ${money(leg.profitPerDay)} a day",
            note = note,
        )
    }

    fun build(session: GameSession, quantity: Long = 100L, sort: TradeSort = TradeSort.PROFIT, limit: Int = 30): TradeModel {
        val legs = TradeMath.bestPerGood(session.tradeLegs(Quantity.units(quantity)), sort).take(limit)
        val money = session.settings::money
        val best = legs.firstOrNull()
        val tiles = listOf(
            Tile("Routes found", legs.size.toString()),
            Tile("Best profit", best?.let { money(it.netProfit) } ?: "-", if (best != null) Tone.GOOD else Tone.NEUTRAL, best?.let { it.goodName }),
            Tile("Best margin", legs.maxOfOrNull { it.marginPpm }?.let { Formatting.percentPpm(it) } ?: "-"),
        )
        return TradeModel(tiles, legs.map { row(session, it) }, sizes, EMPTY)
    }
}
