package app.many.economytycoon.exchange

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.Holder

/** Where the service gets facts it does not own: what a business is worth and how many shares there are. */
interface MarketFacts {
    fun fairPrice(issuer: CompanyId, tick: Tick): Money
    fun publicShares(issuer: CompanyId): Long
    fun outstanding(issuer: CompanyId): Long
    /** Mood of the economy the company works in, ppm around 1,000,000 (neutral). */
    fun sentimentPpm(issuer: CompanyId): Long
}

/**
 * Keeps the list of listed companies, their prices and what investors paid for their holdings. It decides
 * prices and trade terms; moving shares and cash is done by the caller so the books stay in one place.
 */
class ExchangeService(val params: ExchangeParams = ExchangeParams()) {
    private val listings = java.util.TreeMap<CompanyId, Listing>()
    private val basis = java.util.TreeMap<PositionKey, Money>()
    private val indexCloses = ArrayDeque<Long>()
    var index: Long = params.startingIndex
        private set
    var indexPrevious: Long = params.startingIndex
        private set

    fun listing(issuer: CompanyId): Listing? = listings[issuer]
    fun listings(): List<Listing> = listings.values.toList()
    fun isListed(issuer: CompanyId) = listings.containsKey(issuer)
    fun indexHistory(): List<Long> = indexCloses.toList()
    val indexChangePpm: Long get() = if (indexPrevious <= 0L) 0L else (index - indexPrevious) * 1_000_000L / indexPrevious

    // ---- listing -------------------------------------------------------------------------------

    fun list(issuer: CompanyId, tick: Tick, price: Money, fair: Money = price): Outcome<Listing> {
        if (listings.containsKey(issuer)) return Outcome.reject(Rules.DUPLICATE, "already listed")
        if (price < params.minPrice) return Outcome.reject(Rules.INVALID_PRICE, "the price is too low to list")
        val l = Listing(issuer, tick, price)
        if (fair.raw > 0L) l.fairValue = fair
        l.closes.addLast(price.raw)
        listings[issuer] = l
        return Outcome.ok(l)
    }

    fun delist(issuer: CompanyId): Boolean {
        listings.remove(issuer) ?: return false
        for (k in basis.keys.filter { it.issuer == issuer }) basis.remove(k)
        return true
    }

    // ---- positions -----------------------------------------------------------------------------

    fun basisOf(holder: Holder, issuer: CompanyId): Money = basis[PositionKey(holder, issuer)] ?: Money.ZERO
    fun positions(): List<Pair<PositionKey, Money>> = basis.entries.map { it.key to it.value }
    fun positionsOf(holder: Holder): List<Pair<CompanyId, Money>> = basis.filterKeys { it.holder == holder }.map { it.key.issuer to it.value }

    fun addBasis(holder: Holder, issuer: CompanyId, cost: Money) {
        val k = PositionKey(holder, issuer)
        basis[k] = (basis[k] ?: Money.ZERO) + cost
    }

    /** Takes [sold] of [held] shares out of the cost basis and returns the cost that goes with them. */
    fun releaseBasis(holder: Holder, issuer: CompanyId, sold: Long, held: Long): Money {
        val k = PositionKey(holder, issuer)
        val total = basis[k] ?: return Money.ZERO
        if (held <= 0L || sold >= held) { basis.remove(k); return total }
        val part = Money(mulDiv(total.raw, sold, held))
        val rest = total - part
        if (rest.raw <= 0L) basis.remove(k) else basis[k] = rest
        return part
    }

    // ---- quotes --------------------------------------------------------------------------------

    /** Impact of a trade of [value] on a company whose public float is worth [floatValue], ppm. */
    fun impactPpm(value: Money, floatValue: Money): Long {
        if (floatValue.raw <= 0L) return params.maxImpactPpm
        val denom = floatValue.raw * params.liquidityMultiple
        return mulDiv(value.raw, 1_000_000L, denom.coerceAtLeast(1L)).coerceIn(0L, params.maxImpactPpm)
    }

    fun quote(issuer: CompanyId, side: TradeSide, shares: Long, publicShares: Long): Outcome<TradeQuote> {
        val l = listings[issuer] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "not listed")
        if (shares <= 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "trade at least one share")
        if (side == TradeSide.BUY && shares > publicShares) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "only $publicShares shares are for sale")
        val mid = l.price.raw
        val floatValue = Money(mulDiv(mid, publicShares.coerceAtLeast(1L), 1L))
        val roughValue = Money(mulDiv(mid, shares, 1L))
        val impact = impactPpm(roughValue, floatValue)
        val half = params.spreadPpm / 2
        val factor = if (side == TradeSide.BUY) 1_000_000L + half + impact / 2 else (1_000_000L - half - impact / 2).coerceAtLeast(10_000L)
        val avg = Money(mulDiv(mid, factor, 1_000_000L).coerceAtLeast(params.minPrice.raw))
        val gross = avg * shares
        val fee = gross.scale(params.feePpm, 1_000_000L)
        val duty = if (side == TradeSide.BUY) gross.scale(params.stampDutyPpm, 1_000_000L) else Money.ZERO
        return Outcome.ok(TradeQuote(issuer, side, shares, avg, gross, fee, duty, impact))
    }

    /** Records a completed trade: volume counts up and the price feels the pressure. */
    fun recordTrade(q: TradeQuote, publicShares: Long) {
        val l = listings[q.issuer] ?: return
        l.volumeToday += q.shares
        l.volumeTotal += q.shares
        l.turnoverToday += q.gross
        val sign = if (q.side == TradeSide.BUY) 1L else -1L
        l.pressurePpm = (l.pressurePpm + sign * q.impactPpm).coerceIn(-params.maxImpactPpm, params.maxImpactPpm)
        // Half the impact is permanent and shows at once, so a big order is not followed by a free bounce back.
        val nudge = Money(mulDiv(l.price.raw, sign * (q.impactPpm / 2), 1_000_000L))
        l.price = Money((l.price.raw + nudge.raw).coerceAtLeast(params.minPrice.raw))
    }

    // ---- the day's move ------------------------------------------------------------------------

    /**
     * Closes the day: each price drifts toward what the business is worth, feels the day's buying and selling
     * and takes a random step. Returns the notable moves.
     */
    fun closeDay(tick: Tick, facts: MarketFacts, random: DeterministicRandom): List<ExchangeEvent> {
        val events = ArrayList<ExchangeEvent>()
        var weightedReturn = 0L
        var totalWeight = 0L
        val revalue = params.valuationEvery <= 1 || tick.value % params.valuationEvery == 0L
        for (l in listings.values) {
            val old = l.price.raw
            if (revalue) {
                val fair = facts.fairPrice(l.issuer, tick)
                if (fair.raw > 0L) l.fairValue = fair
            }
            val sentiment = facts.sentimentPpm(l.issuer)
            val target = mulDiv(l.fairValue.raw, sentiment, 1_000_000L)
            var p = old + mulDiv(target - old, params.reversionPpm, 1_000_000L)
            p += mulDiv(old, mulDiv(l.pressurePpm, params.pressureWeightPpm, 1_000_000L), 1_000_000L)
            val shock = random.nextInRange(-params.volatilityPpm, params.volatilityPpm)
            p += mulDiv(old, shock, 1_000_000L)
            l.price = Money(p.coerceAtLeast(params.minPrice.raw))
            l.pressurePpm = mulDiv(l.pressurePpm, params.pressureKeepPpm, 1_000_000L)
            val change = if (l.previousClose.raw <= 0L) 0L else (l.price.raw - l.previousClose.raw) * 1_000_000L / l.previousClose.raw
            if (change >= SURGE_PPM) events += ExchangeEvent.Surge(l.issuer, tick, change)
            else if (change <= -SURGE_PPM) events += ExchangeEvent.Slump(l.issuer, tick, change)
            val cap = mulDiv(old, facts.outstanding(l.issuer), 1L).coerceAtLeast(0L)
            if (old > 0L && cap > 0L) {
                weightedReturn += mulDiv(cap, (l.price.raw * 1_000_000L) / old, 1_000_000L)
                totalWeight += cap
            }
            l.closes.addLast(l.price.raw)
            while (l.closes.size > params.historyLength) l.closes.removeFirst()
            l.previousClose = l.price
            l.volumeToday = 0L
            l.turnoverToday = Money.ZERO
        }
        indexPrevious = index
        if (totalWeight > 0L) index = (index * weightedReturn / totalWeight).coerceAtLeast(1L)
        indexCloses.addLast(index)
        while (indexCloses.size > params.historyLength) indexCloses.removeFirst()
        return events
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(index).putLong(indexPrevious).putInt(indexCloses.size)
        for (c in indexCloses) h.putLong(c)
        h.putInt(listings.size)
        for (l in listings.values) l.hashInto(h)
        h.putInt(basis.size)
        for ((k, v) in basis) h.putString(k.holder.toString()).putLong(k.issuer.value).putLong(v.raw)
    }

    internal fun restoreListing(l: Listing) { listings[l.issuer] = l }
    internal fun restoreBasis(k: PositionKey, v: Money) { basis[k] = v }
    internal fun restoreIndex(index: Long, previous: Long, closes: List<Long>) {
        this.index = index; this.indexPrevious = previous
        indexCloses.clear(); indexCloses.addAll(closes)
    }
    internal fun clearAll() { listings.clear(); basis.clear(); indexCloses.clear(); index = params.startingIndex; indexPrevious = params.startingIndex }

    companion object {
        /** A one-day move this large is worth a headline. */
        const val SURGE_PPM = 80_000L
    }
}
