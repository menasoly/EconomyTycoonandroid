package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

enum class PermitSide { BUY, SELL }

/** A limit order in a country's secondary permit book. */
class PermitOrder(
    val id: PermitOrderId,
    val company: CompanyId,
    val side: PermitSide,
    val limit: Money,
    val quantity: Tonnes,
    val placedAt: Tick,
) {
    var remaining: Tonnes = quantity
        internal set

    val isOpen: Boolean get() = remaining.raw > 0L
}

/** One match between a buyer and a seller, at the price of the order that was resting. */
data class PermitTrade(val tick: Tick, val buyer: CompanyId, val seller: CompanyId, val tonnes: Tonnes, val price: Money) {
    val value: Money get() = tonnes.cost(price)
}

/** Which side of the book to look at when asking for its depth. */
data class BookLevel(val price: Money, val tonnes: Tonnes, val orders: Int)

/**
 * Price-time matching of limit orders. A new order crosses the opposite side of the book while prices allow; what is
 * left rests. A trade happens at the resting order's price. The caller says how much each side can still do right now
 * (cash and the holding limit for a buyer, unreserved valid permits for a seller), and an order whose owner can no longer
 * do anything is dropped from the book instead of matched.
 */
object OrderMatcher {

    /**
     * What an order's owner can still take or give, asked at the moment of each trade, and the place where a trade is
     * booked. [settle] runs right after a match so the next match sees the new holdings and cash.
     */
    interface Capacity {
        fun canBuy(company: CompanyId, price: Money): Tonnes
        fun canSell(company: CompanyId): Tonnes
        fun settle(trade: PermitTrade)
    }

    class Result(val trades: List<PermitTrade>, val dropped: List<PermitOrder>)

    fun cross(book: MutableCollection<PermitOrder>, incoming: PermitOrder, tick: Tick, capacity: Capacity): Result {
        val trades = ArrayList<PermitTrade>()
        val dropped = ArrayList<PermitOrder>()
        val opposite = if (incoming.side == PermitSide.BUY) PermitSide.SELL else PermitSide.BUY
        val bestFirst = if (incoming.side == PermitSide.BUY) compareBy<PermitOrder>({ it.limit }, { it.id }) else compareBy<PermitOrder>({ -it.limit.raw }, { it.id })
        while (incoming.isOpen) {
            val best = book.filter { it.side == opposite && it.isOpen && it.company != incoming.company && crosses(incoming, it) }.minWithOrNull(bestFirst) ?: break
            val buyer = if (incoming.side == PermitSide.BUY) incoming else best
            val seller = if (incoming.side == PermitSide.BUY) best else incoming
            val price = best.limit
            val buyCap = capacity.canBuy(buyer.company, price).raw
            val sellCap = capacity.canSell(seller.company).raw
            // A resting order whose owner can no longer deal is dropped; an incoming one that cannot deal simply stops.
            if (buyCap <= 0L || sellCap <= 0L) {
                if ((buyer === best && buyCap <= 0L) || (seller === best && sellCap <= 0L)) { book.remove(best); dropped += best; continue }
                break
            }
            val qty = minOf(incoming.remaining.raw, best.remaining.raw, buyCap, sellCap)
            incoming.remaining = Tonnes(incoming.remaining.raw - qty)
            best.remaining = Tonnes(best.remaining.raw - qty)
            val trade = PermitTrade(tick, buyer.company, seller.company, Tonnes(qty), price)
            trades += trade
            capacity.settle(trade)
            if (!best.isOpen) book.remove(best)
        }
        return Result(trades, dropped)
    }

    private fun crosses(incoming: PermitOrder, resting: PermitOrder): Boolean =
        if (incoming.side == PermitSide.BUY) resting.limit <= incoming.limit else resting.limit >= incoming.limit

    /** The book aggregated by price: bids from the highest down, asks from the lowest up. */
    fun depth(orders: Collection<PermitOrder>, side: PermitSide): List<BookLevel> {
        val grouped = orders.filter { it.side == side && it.isOpen }.groupBy { it.limit }
        val levels = grouped.map { (p, list) -> BookLevel(p, Tonnes.sum(list.map { it.remaining }), list.size) }
        return if (side == PermitSide.BUY) levels.sortedByDescending { it.price } else levels.sortedBy { it.price }
    }
}
