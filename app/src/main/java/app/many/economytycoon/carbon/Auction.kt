package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.splitLargestRemainder

/** A sealed bid for permits at the next auction: up to [quantity] at no more than [limit] per tonne. */
class AuctionBid(val id: BidId, val company: CompanyId, val quantity: Tonnes, val limit: Money, val placedAt: Tick)

/** What one bidder won. */
data class AuctionFill(val bid: BidId, val company: CompanyId, val tonnes: Tonnes, val cost: Money)

/** The record of one auction, kept for the price history and the reports. */
data class AuctionResult(
    val tick: Tick,
    val year: Int,
    val quarter: Int,
    val supply: Tonnes,
    val sold: Tonnes,
    /** Permits sold at the ceiling price from the government's cost-containment reserve, on top of [supply]. */
    val ceilingSold: Tonnes,
    /** Permits nobody wanted at the floor; they go to the stability reserve. */
    val unsold: Tonnes,
    val price: Money,
    val bidders: Int,
    /** Total demanded divided by supply, in ppm. Above 1,000,000 the auction was oversubscribed. */
    val coverPpm: Long,
) {
    val revenue: Money get() = sold.cost(price) + ceilingSold.cost(price)
}

/** The outcome of clearing: the single price every winner pays and who got what. */
class AuctionOutcome(val price: Money, val fills: List<AuctionFill>, val sold: Tonnes, val ceilingSold: Tonnes, val unsold: Tonnes, val demand: Tonnes) {
    companion object {
        fun empty(supply: Tonnes, floor: Money) = AuctionOutcome(floor, emptyList(), Tonnes.ZERO, Tonnes.ZERO, supply, Tonnes.ZERO)
    }
}

/**
 * Uniform-price sealed-bid clearing.
 *
 * Bids below the floor are void and bids above the ceiling count at the ceiling. A company can only bid for what it can
 * pay: its bids are trimmed, highest limit first, until their total at their own limits fits its cash, so every winner can
 * pay the clearing price, which is never above any winning limit.
 *
 * With less demand than supply the price is the floor and everyone is filled. With more, the price is the limit of the
 * marginal bid; bids above it fill completely, and bids exactly at it share what is left in proportion to their size.
 * If the bids at the ceiling alone exceed the supply, the price is the ceiling, every such bid fills completely, and the
 * extra permits come from the ceiling reserve.
 */
object AuctionClearing {
    private class Live(val bid: AuctionBid, val limit: Money, var quantity: Tonnes)

    fun clear(supply: Tonnes, floor: Money, ceiling: Money, bids: List<AuctionBid>, cash: (CompanyId) -> Money): AuctionOutcome {
        if (supply.raw <= 0L && !ceiling.isPositive) return AuctionOutcome.empty(supply, floor)
        val hasCeiling = ceiling.isPositive
        val live = ArrayList<Live>()
        val budget = java.util.TreeMap<CompanyId, Money>()
        for (bid in bids.sortedWith(compareByDescending<AuctionBid> { effectiveLimit(it.limit, ceiling) }.thenBy { it.id })) {
            val limit = effectiveLimit(bid.limit, ceiling)
            if (bid.quantity.raw <= 0L || limit < floor || limit.raw <= 0L) continue
            val left = budget.getOrPut(bid.company) { cash(bid.company).coerceAtLeast(Money.ZERO) }
            val unitCost = limit
            if (unitCost.raw <= 0L) continue
            var affordable = Tonnes(java.lang.Math.floorDiv(Math.multiplyExact(left.raw, Tonnes.SCALE), unitCost.raw))
            affordable = Tonnes.min(affordable, bid.quantity)
            if (affordable.raw <= 0L) continue
            budget[bid.company] = left - affordable.cost(limit, app.many.economytycoon.core.RoundingMode.UP).coerceAtMost(left)
            live += Live(bid, limit, affordable)
        }
        val demand = Tonnes.sum(live.map { it.quantity })
        if (live.isEmpty()) return AuctionOutcome.empty(supply, floor)

        if (hasCeiling) {
            val atCeiling = live.filter { it.limit >= ceiling }
            val ceilingDemand = Tonnes.sum(atCeiling.map { it.quantity })
            if (ceilingDemand > supply) {
                val fills = atCeiling.map { AuctionFill(it.bid.id, it.bid.company, it.quantity, it.quantity.cost(ceiling)) }
                return AuctionOutcome(ceiling, fills, supply, ceilingDemand - supply, Tonnes.ZERO, demand)
            }
        }
        if (demand <= supply) {
            val fills = live.map { AuctionFill(it.bid.id, it.bid.company, it.quantity, it.quantity.cost(floor)) }
            return AuctionOutcome(floor, fills, demand, Tonnes.ZERO, supply - demand, demand)
        }
        // Oversubscribed: walk down the limits until the supply runs out.
        var left = supply
        var price = floor
        val fills = ArrayList<AuctionFill>()
        var i = 0
        val ordered = live // already in descending limit order, ties by bid id
        while (i < ordered.size && left.raw > 0L) {
            val level = ordered[i].limit
            var j = i
            while (j < ordered.size && ordered[j].limit == level) j++
            val group = ordered.subList(i, j)
            val groupDemand = Tonnes.sum(group.map { it.quantity })
            if (groupDemand <= left) {
                for (l in group) fills += AuctionFill(l.bid.id, l.bid.company, l.quantity, Money.ZERO)
                left -= groupDemand
                price = level
            } else {
                val parts = splitLargestRemainder(left.raw, LongArray(group.size) { group[it].quantity.raw })
                for ((k, l) in group.withIndex()) {
                    val part = Tonnes(minOf(parts[k], l.quantity.raw))
                    if (part.raw > 0L) fills += AuctionFill(l.bid.id, l.bid.company, part, Money.ZERO)
                }
                price = level
                left = Tonnes.ZERO
            }
            i = j
        }
        val priced = fills.map { it.copy(cost = it.tonnes.cost(price)) }
        return AuctionOutcome(price, priced, supply - left, Tonnes.ZERO, left, demand)
    }

    private fun effectiveLimit(limit: Money, ceiling: Money): Money =
        if (ceiling.isPositive && limit > ceiling) ceiling else limit
}
