package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money

/** The permits a country plans to put into the market in one year and how much of that has happened. */
class YearSupply(val year: Int, val cap: Tonnes, val free: Tonnes, val auctionPlanned: Tonnes) {
    /** Permits the stability reserve took out of this year's auctions. */
    var withheld: Tonnes = Tonnes.ZERO
        internal set
    /** Permits the stability reserve added to this year's auctions. */
    var released: Tonnes = Tonnes.ZERO
        internal set
    /** Permits put up at auctions so far. */
    var offered: Tonnes = Tonnes.ZERO
        internal set
    var sold: Tonnes = Tonnes.ZERO
        internal set
    var ceilingSold: Tonnes = Tonnes.ZERO
        internal set
    var unsold: Tonnes = Tonnes.ZERO
        internal set
    /** Free permits handed out so far; the rest wait in the pool until companies have a baseline. */
    var freeIssued: Tonnes = Tonnes.ZERO
        internal set
    var auctionsHeld: Int = 0
        internal set

    /** What is still to be offered this year. */
    val remaining: Tonnes get() = (auctionPlanned - withheld + released - offered).coerceAtLeast(Tonnes.ZERO)

    internal fun restore(
        withheld: Tonnes, released: Tonnes, offered: Tonnes, sold: Tonnes, ceilingSold: Tonnes, unsold: Tonnes, freeIssued: Tonnes, auctionsHeld: Int,
    ) {
        this.withheld = withheld; this.released = released; this.offered = offered; this.sold = sold; this.ceilingSold = ceilingSold
        this.unsold = unsold; this.freeIssued = freeIssued; this.auctionsHeld = auctionsHeld
    }
}

/**
 * The state of one country's permit market: who holds what, the bids waiting for the next auction, the resting orders,
 * the stability reserve and the price history. Only the carbon service changes it.
 */
class PermitMarket(val country: CountryId) {
    val holdings = PermitHoldings()
    internal val bidMap = java.util.TreeMap<BidId, AuctionBid>()
    internal val orderMap = java.util.TreeMap<PermitOrderId, PermitOrder>()
    internal val supplyMap = java.util.TreeMap<Int, YearSupply>()
    internal val auctionLog = ArrayList<AuctionResult>()
    internal val tradeLog = ArrayList<PermitTrade>()
    internal val quarterlyPrices = ArrayList<Money>()

    /** Permits held back by the stability reserve. */
    var reserve: Tonnes = Tonnes.ZERO
        internal set
    var lastTradePrice: Money = Money.ZERO
        internal set
    var lastAuctionPrice: Money = Money.ZERO
        internal set
    /** Free permits for the current year that have not yet been handed out. */
    var pendingFree: Tonnes = Tonnes.ZERO
        internal set
    var cancelled: Tonnes = Tonnes.ZERO
        internal set
    var expired: Tonnes = Tonnes.ZERO
        internal set
    var ceilingReleased: Tonnes = Tonnes.ZERO
        internal set
    /** Permits in circulation at the last compliance deadline. */
    var lastCirculation: Tonnes = Tonnes.ZERO
        internal set
    var tradedVolume: Tonnes = Tonnes.ZERO
        internal set
    /** The latest compliance year whose deadline review (reserve, expiry) has been done; zero if none. */
    var reviewedYear: Int = 0
        internal set

    fun bids(): List<AuctionBid> = bidMap.values.toList()
    fun bidsOf(company: CompanyId): List<AuctionBid> = bidMap.values.filter { it.company == company }
    fun orders(): List<PermitOrder> = orderMap.values.toList()
    fun ordersOf(company: CompanyId): List<PermitOrder> = orderMap.values.filter { it.company == company }
    fun supply(year: Int): YearSupply? = supplyMap[year]
    fun supplies(): List<YearSupply> = supplyMap.values.toList()
    fun auctions(): List<AuctionResult> = auctionLog.toList()
    fun trades(): List<PermitTrade> = tradeLog.toList()
    fun prices(): List<Money> = quarterlyPrices.toList()

    fun bestBid(): Money? = OrderMatcher.depth(orderMap.values, PermitSide.BUY).firstOrNull()?.price
    fun bestAsk(): Money? = OrderMatcher.depth(orderMap.values, PermitSide.SELL).firstOrNull()?.price

    /** Permits a company has offered for sale and not yet sold. */
    fun reservedForSale(company: CompanyId): Tonnes =
        Tonnes.sum(orderMap.values.filter { it.company == company && it.side == PermitSide.SELL }.map { it.remaining })

    /**
     * The price people treat as current: the last trade if there was one more recently than the last auction,
     * otherwise the last auction, otherwise [fallback] (the floor).
     */
    fun referencePrice(fallback: Money): Money = when {
        lastTradePrice.isPositive -> lastTradePrice
        lastAuctionPrice.isPositive -> lastAuctionPrice
        else -> fallback
    }

    internal fun clear() {
        holdings.clear(); bidMap.clear(); orderMap.clear(); supplyMap.clear(); auctionLog.clear(); tradeLog.clear(); quarterlyPrices.clear()
        reserve = Tonnes.ZERO; lastTradePrice = Money.ZERO; lastAuctionPrice = Money.ZERO; pendingFree = Tonnes.ZERO
        cancelled = Tonnes.ZERO; expired = Tonnes.ZERO; ceilingReleased = Tonnes.ZERO; lastCirculation = Tonnes.ZERO; tradedVolume = Tonnes.ZERO
        reviewedYear = 0
    }

    internal fun restoreTotals(
        reserve: Tonnes, lastTrade: Money, lastAuction: Money, pendingFree: Tonnes, cancelled: Tonnes, expired: Tonnes,
        ceilingReleased: Tonnes, lastCirculation: Tonnes, tradedVolume: Tonnes,
    ) {
        this.reserve = reserve; lastTradePrice = lastTrade; lastAuctionPrice = lastAuction; this.pendingFree = pendingFree
        this.cancelled = cancelled; this.expired = expired; this.ceilingReleased = ceilingReleased; this.lastCirculation = lastCirculation; this.tradedVolume = tradedVolume
    }

    internal fun markReviewed(year: Int) { reviewedYear = year }
    internal fun mutateReserve(value: Tonnes) { reserve = value }
    internal fun mutatePendingFree(value: Tonnes) { pendingFree = value }
    internal fun addCancelled(value: Tonnes) { cancelled += value }
    internal fun addExpired(value: Tonnes) { expired += value }
    internal fun addCeilingReleased(value: Tonnes) { ceilingReleased += value }
    internal fun setCirculation(value: Tonnes) { lastCirculation = value }
    internal fun setTradePrice(price: Money, volume: Tonnes) { lastTradePrice = price; tradedVolume += volume }
    internal fun setAuctionPrice(price: Money) { lastAuctionPrice = price }
}
