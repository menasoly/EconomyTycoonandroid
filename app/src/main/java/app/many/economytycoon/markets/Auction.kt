package app.many.economytycoon.markets

/** One side of the book as the auction sees it: a real order or a synthetic consumer demand step. */
class BookEntry(
    val order: Order?,
    val party: Party,
    /** Price limit in raw money units; Long.MAX_VALUE for market bids, 0 for market asks. */
    val limit: Long,
    val quantity: Long,
    val seq: Long,
    val source: Long,
) {
    var remaining: Long = quantity
        internal set
}

class Match(val buy: BookEntry, val sell: BookEntry, val quantity: Long)

class AuctionResult(
    val price: Long,
    val volume: Long,
    val matches: List<Match>,
    /** Quantity on each side that did not trade. */
    val unfilledBids: Long,
    val unfilledAsks: Long,
) {
    companion object { val EMPTY = AuctionResult(0, 0, emptyList(), 0, 0) }
}

/**
 * Uniform-price call auction. Finds the volume-maximising crossing, picks the price inside the feasible
 * range that sits closest to the reference price, limits it to the per-tick band, and then fills every
 * order that accepts that price in priority order (best limit first, then oldest).
 */
object Auction {
    fun clear(bids: List<BookEntry>, asks: List<BookEntry>, reference: Long, bandLow: Long, bandHigh: Long): AuctionResult {
        if (bids.isEmpty() || asks.isEmpty()) {
            return AuctionResult(reference, 0, emptyList(), bids.sumOf { it.quantity }, asks.sumOf { it.quantity })
        }
        val sb = bids.sortedWith(compareByDescending<BookEntry> { it.limit }.thenBy { it.seq })
        val sa = asks.sortedWith(compareBy<BookEntry> { it.limit }.thenBy { it.seq })
        if (sb[0].limit < sa[0].limit) {
            return AuctionResult(reference, 0, emptyList(), sb.sumOf { it.quantity }, sa.sumOf { it.quantity })
        }

        // Pass 1: walk the crossing without mutating anything to find the marginal orders.
        var i = 0; var j = 0
        var remB = sb[0].quantity; var remA = sa[0].quantity
        var lastBidIdx = 0; var lastAskIdx = 0
        var volume = 0L
        while (i < sb.size && j < sa.size && sb[i].limit >= sa[j].limit) {
            lastBidIdx = i; lastAskIdx = j
            val m = minOf(remB, remA)
            volume += m
            remB -= m; remA -= m
            if (remB == 0L) { i++; if (i < sb.size) remB = sb[i].quantity }
            if (remA == 0L) { j++; if (j < sa.size) remA = sa[j].quantity }
        }
        var lo = sa[lastAskIdx].limit
        var hi = sb[lastBidIdx].limit
        // The first bid and ask left out bound the price from the other direction.
        val excludedBid = sb.getOrNull(lastBidIdx + 1)
        val excludedAsk = sa.getOrNull(lastAskIdx + 1)
        if (excludedBid != null && excludedBid.limit in lo..hi) lo = excludedBid.limit
        if (excludedAsk != null && excludedAsk.limit in lo..hi) hi = excludedAsk.limit
        if (lo > hi) { val mid = lo / 2 + hi / 2; lo = mid; hi = mid }

        var price = reference.coerceIn(lo, hi)
        price = price.coerceIn(bandLow, bandHigh)

        // Pass 2: fill at the final price.
        val ob = sb.filter { it.limit >= price }
        val oa = sa.filter { it.limit <= price }
        val matches = ArrayList<Match>()
        var bi = 0; var ai = 0
        var filled = 0L
        while (bi < ob.size && ai < oa.size) {
            val b = ob[bi]; val a = oa[ai]
            val m = minOf(b.remaining, a.remaining)
            if (m > 0) {
                matches += Match(b, a, m)
                b.remaining -= m; a.remaining -= m
                filled += m
            }
            if (b.remaining == 0L) bi++
            if (a.remaining == 0L) ai++
        }
        return AuctionResult(price, filled, matches, sb.sumOf { it.remaining }, sa.sumOf { it.remaining })
    }
}
