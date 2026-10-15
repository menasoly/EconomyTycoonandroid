package app.many.economytycoon.markets

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.OrderId
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

data class MarketParams(
    val maxOpenOrdersPerParty: Int = 200,
    val maxOrderLifetime: Int = 90,
    val tradeLogSize: Int = 400,
    val minOrderQuantity: Quantity = Quantity.ofRaw(10),
)

/** Lets other systems veto an order before it is booked (credit checks, sanctions, stock checks). */
fun interface OrderGate {
    fun allow(party: Party, side: Side, key: MarketKey, quantity: Quantity, limit: Price?): Rejection?
}

class DepthLevel(val side: Side, val price: Price, val quantity: Quantity, val orders: Int)

class Quote(val bid: Price?, val ask: Price?, val last: Price)

/** What the market did this tick; the simulation settles money and moves goods from [trades]. */
class MarketReport(val tick: Tick) {
    val trades = ArrayList<Trade>()
    val expired = ArrayList<Order>()
    val filled = ArrayList<Order>()
    val householdSpend = java.util.TreeMap<RegionId, Money>()
    var marketsCleared: Int = 0
    var tradedValue: Money = Money.ZERO

    fun volumeOf(good: GoodId): Quantity = Quantity(trades.filter { it.key.good == good }.sumOf { it.quantity.raw })
}

/**
 * Order books and price formation for every good in every region. Households appear as a synthetic
 * bidder built from the demand curve, limited by the region's spendable budget.
 */
class MarketService(
    private val ids: IdAllocators,
    val content: ContentRepository,
    val population: PopulationView,
    val params: MarketParams = MarketParams(),
) {
    private val orders = java.util.TreeMap<OrderId, Order>()
    private val states = java.util.TreeMap<MarketKey, MarketState>()
    private val placedThisTick = java.util.TreeMap<Party, Int>()
    private val tradeLog = ArrayDeque<Trade>()
    var gate: OrderGate? = null
    val index = PriceIndex(content)
    val indexLog = IndexLog()
    private var seq = 0L
    private val orderSeq = java.util.TreeMap<OrderId, Long>()

    // ---- queries -------------------------------------------------------------------------------

    fun order(id: OrderId): Order? = orders[id]
    fun openOrders(party: Party): List<Order> = orders.values.filter { it.party == party }
    fun orders(key: MarketKey): List<Order> = orders.values.filter { it.key == key }
    fun allOrders(): List<Order> = orders.values.toList()
    fun state(key: MarketKey): MarketState? = states[key]
    fun states(): List<MarketState> = states.values.toList()
    fun recentTrades(limit: Int = 50): List<Trade> = tradeLog.toList().takeLast(limit)

    fun price(key: MarketKey): Price = states[key]?.last ?: content.good(key.good).referencePrice

    /** Mean of the regional last prices for a good, or its reference price if nothing has traded. */
    fun averagePrice(good: GoodId): Price {
        val ref = content.good(good).referencePrice
        var sum = 0L; var n = 0L
        for ((k, s) in states) if (k.good == good) { sum += s.last.perUnit.raw; n++ }
        return if (n == 0L) ref else Price(Money(sum / n))
    }

    fun quote(key: MarketKey): Quote {
        val bids = orders(key).filter { it.side == Side.BUY && it.isOpen && it.limit != null }
        val asks = orders(key).filter { it.side == Side.SELL && it.isOpen && it.limit != null }
        return Quote(bids.maxByOrNull { it.limitRaw }?.limit, asks.minByOrNull { it.limitRaw }?.limit, price(key))
    }

    fun depth(key: MarketKey, levels: Int = 5): List<DepthLevel> {
        val out = ArrayList<DepthLevel>()
        for (side in Side.values()) {
            val grouped = orders(key).filter { it.side == side && it.isOpen && it.limit != null }.groupBy { it.limitRaw }
            val keys = if (side == Side.BUY) grouped.keys.sortedDescending() else grouped.keys.sorted()
            for (k in keys.take(levels)) {
                val list = grouped.getValue(k)
                out += DepthLevel(side, Price(Money(k)), Quantity(list.sumOf { it.remaining.raw }), list.size)
            }
        }
        return out
    }

    /** Price a region pays for goods, as a lookup for production and AI decisions. */
    fun priceLookup(region: RegionId): (GoodId) -> Price = { g -> price(MarketKey(region, g)) }

    // ---- commands ------------------------------------------------------------------------------

    fun placeOrder(
        party: Party, key: MarketKey, side: Side, limit: Price?, quantity: Quantity, ttlTicks: Int, source: Long, tick: Tick,
    ): Outcome<Order> {
        val good = content.goodOrNull(key.good) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown good ${key.good.key}")
        if (key.region !in population.regions()) return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown region ${key.region.value}")
        if (quantity < params.minOrderQuantity) return Outcome.reject(Rules.INVALID_QUANTITY, "order is too small")
        if (ttlTicks !in 1..params.maxOrderLifetime) return Outcome.reject(Rules.INVALID_ARGUMENT, "lifetime must be 1-${params.maxOrderLifetime} ticks")
        if (limit != null) {
            val fc = PriceRules.floorAndCeiling(good, content.params)
            if (limit.perUnit.raw <= 0L || limit.perUnit.raw !in fc) return Outcome.reject(Rules.INVALID_PRICE, "price outside the allowed range")
        }
        if ((placedThisTick[party] ?: 0) >= content.params.orderRateLimitPerTick) return Outcome.reject(Rules.RATE_LIMITED, "too many orders this tick")
        if (openOrders(party).size >= params.maxOpenOrdersPerParty) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many open orders")
        gate?.allow(party, side, key, quantity, limit)?.let { return Outcome.Rejected(it) }
        val order = Order(ids.order(), key, side, party, limit, quantity, tick, tick + ttlTicks.toLong(), source)
        orders[order.id] = order
        orderSeq[order.id] = seq++
        placedThisTick[party] = (placedThisTick[party] ?: 0) + 1
        return Outcome.ok(order)
    }

    fun cancelOrder(party: Party, id: OrderId): Outcome<Order> {
        val o = orders[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "order ${id.value} is not open")
        if (o.party != party) return Outcome.reject(Rules.NOT_OWNER, "order belongs to someone else")
        orders.remove(id); orderSeq.remove(id)
        return Outcome.ok(o)
    }

    /** Removes everything a party has booked (bankruptcy, shutdown) and returns the orders so reservations can be released. */
    fun cancelAll(party: Party): List<Order> {
        val mine = openOrders(party)
        for (o in mine) { orders.remove(o.id); orderSeq.remove(o.id) }
        return mine
    }

    /** One-off price pressure, e.g. from a supply disruption or a news event; decays by 20% per tick. */
    fun applyShock(key: MarketKey, ppm: Long) {
        val s = states.getOrPut(key) { MarketState(key, content.good(key.good).referencePrice) }
        s.shockPpm = (s.shockPpm + ppm).coerceIn(-500_000L, 500_000L)
    }

    fun applyShockEverywhere(good: GoodId, ppm: Long) { for (r in population.regions()) applyShock(MarketKey(r, good), ppm) }

    fun setDemandShift(key: MarketKey, ppm: Long) {
        val s = states.getOrPut(key) { MarketState(key, content.good(key.good).referencePrice) }
        s.demandShiftPpm = ppm.coerceIn(0L, 3_000_000L)
    }

    fun setDemandShiftEverywhere(good: GoodId, ppm: Long) { for (r in population.regions()) setDemandShift(MarketKey(r, good), ppm) }

    // ---- clearing ------------------------------------------------------------------------------

    /**
     * Clears every market once. [budget] gives each region's spendable household money for the tick, or
     * null for unlimited. Necessities clear first so a thin budget goes to staples before luxuries.
     */
    fun step(tick: Tick, random: RandomProvider, budget: (RegionId) -> Money? = { null }): MarketReport {
        val report = MarketReport(tick)
        expire(tick, report)
        val season = Calendar.season(tick)
        val byKey = orders.values.groupBy { it.key }
        val goodsInOrder = content.allDemandProfiles.sortedWith(compareBy({ it.incomeElasticityPpm }, { it.good.key })).map { it.good }
        val otherGoods = (byKey.keys.map { it.good } + states.keys.map { it.good }).toSortedSet()

        for (region in population.regions().sortedBy { it.value }) {
            var remainingBudget: Long? = budget(region)?.raw
            val rnd = random.stream("markets", region.value)
            val goods = (goodsInOrder + otherGoods).distinct()
            for (good in goods) {
                val key = MarketKey(region, good)
                val def = content.goodOrNull(good) ?: continue
                val profile = content.demandProfile(good)
                val realOrders = byKey[key].orEmpty()
                if (profile == null && realOrders.isEmpty() && states[key] == null) continue
                val state = states.getOrPut(key) { MarketState(key, def.referencePrice) }
                val noise = rnd.nextInRange(-def.volatility.noisePpm, def.volatility.noisePpm)

                val bids = ArrayList<BookEntry>(); val asks = ArrayList<BookEntry>()
                for (o in realOrders) {
                    if (!o.isOpen) continue
                    val e = BookEntry(o, o.party, o.limitRaw, o.remaining.raw, orderSeq[o.id] ?: 0L, o.source)
                    if (o.side == Side.BUY) bids += e else asks += e
                }
                var syntheticDemand = false
                var syntheticSeq = 0L
                if (profile != null) {
                    val base = ConsumerDemand.baseQuantity(profile, population, region, season)
                    var steps = ConsumerDemand.ladder(profile, base, def.referencePrice.perUnit.raw, state.demandShiftPpm)
                    val rb = remainingBudget
                    if (rb != null && steps.isNotEmpty()) {
                        val cost = ConsumerDemand.spendAt(steps, state.last.perUnit.raw)
                        if (cost > rb) {
                            val scale = if (cost <= 0L) 0L else mulDiv(rb.coerceAtLeast(0L), 1_000_000L, cost)
                            steps = steps.map { DemandStep(it.limit, mulDiv(it.quantity, scale, 1_000_000L)) }
                        }
                    }
                    for (st in steps) if (st.quantity > 0) {
                        bids += BookEntry(null, Party.households(region), st.limit, st.quantity, Long.MAX_VALUE / 2 + syntheticSeq++, 0L)
                        syntheticDemand = true
                    }
                }
                if (bids.isEmpty() && asks.isEmpty()) {
                    settleIdle(state, def, noise, tick)
                    continue
                }

                val last = state.last.perUnit.raw
                val move = PriceRules.maxMovePpm(def, content.params)
                val band = PriceRules.band(last, move)
                val fc = PriceRules.floorAndCeiling(def, content.params)
                val lo = maxOf(band.first, fc.first); val hi = minOf(band.last, fc.last).coerceAtLeast(lo)
                val ref = (mulDiv(last, 1_000_000L + state.shockPpm + noise, 1_000_000L)).coerceIn(lo, hi)
                val result = Auction.clear(bids, asks, ref, lo, hi)
                report.marketsCleared++

                var volume = 0L
                for (m in result.matches) {
                    val qty = Quantity(m.quantity)
                    val trade = Trade(
                        ids.trade(), key, m.buy.party, m.sell.party, qty, Price(Money(result.price)), tick,
                        m.buy.order?.id, m.sell.order?.id, m.buy.source, m.sell.source,
                    )
                    report.trades += trade
                    report.tradedValue += trade.value
                    volume += m.quantity
                    m.buy.order?.let { it.restoreRemaining(it.remaining - qty) }
                    m.sell.order?.let { it.restoreRemaining(it.remaining - qty) }
                    if (m.buy.order == null) {
                        report.householdSpend[region] = (report.householdSpend[region] ?: Money.ZERO) + trade.value
                        remainingBudget = remainingBudget?.let { (it - trade.value.raw).coerceAtLeast(0L) }
                    }
                    tradeLog.addLast(trade)
                    while (tradeLog.size > params.tradeLogSize) tradeLog.removeFirst()
                }

                for (o in realOrders) if (!o.isOpen) { orders.remove(o.id); orderSeq.remove(o.id); report.filled += o }
                val open = realOrders.filter { it.isOpen && it.limit != null }
                val bestBid = open.filter { it.side == Side.BUY }.maxByOrNull { it.limitRaw }?.limit
                val bestAsk = open.filter { it.side == Side.SELL }.minByOrNull { it.limitRaw }?.limit
                state.bestBid = bestBid; state.bestAsk = bestAsk
                state.unmetDemand = Quantity(bids.filter { it.limit >= result.price }.sumOf { it.remaining })
                state.unsoldSupply = Quantity(asks.filter { it.limit <= result.price }.sumOf { it.remaining })

                val newPrice = if (volume > 0) Price(Money(result.price.coerceIn(fc.first, fc.last))) else {
                    val effectiveBid = bestBid?.perUnit?.raw ?: if (syntheticDemand) last else null
                    val moved = PriceRules.drift(last, effectiveBid, bestAsk?.perUnit?.raw, def.referencePrice.perUnit.raw, move)
                    Price(Money(moved.coerceIn(fc.first, fc.last)))
                }
                state.record(newPrice, Quantity(volume), tick.value)
                state.shockPpm = state.shockPpm * 8 / 10
            }
        }
        placedThisTick.clear()
        if (Calendar.isMonthStart(tick)) indexLog.record(tick, index.cpi(this), index.ppi(this))
        return report
    }

    private fun settleIdle(state: MarketState, def: app.many.economytycoon.content.GoodDef, noise: Long, tick: Tick) {
        val move = PriceRules.maxMovePpm(def, content.params)
        val fc = PriceRules.floorAndCeiling(def, content.params)
        val last = state.last.perUnit.raw
        val moved = PriceRules.drift(last, null, null, def.referencePrice.perUnit.raw, move) + mulDiv(last, noise, 4_000_000L)
        state.bestBid = null; state.bestAsk = null
        state.unmetDemand = Quantity.ZERO; state.unsoldSupply = Quantity.ZERO
        state.record(Price(Money(moved.coerceIn(fc.first, fc.last))), Quantity.ZERO, tick.value)
        state.shockPpm = state.shockPpm * 8 / 10
    }

    private fun expire(tick: Tick, report: MarketReport) {
        val dead = orders.values.filter { it.expires <= tick || !it.isOpen }
        for (o in dead) { orders.remove(o.id); orderSeq.remove(o.id); if (o.isOpen) report.expired += o }
    }

    // ---- persistence support -------------------------------------------------------------------

    internal fun clearAll() { orders.clear(); states.clear(); orderSeq.clear(); tradeLog.clear(); placedThisTick.clear(); seq = 0 }
    internal fun restoreState(s: MarketState) { states[s.key] = s }
    internal fun restoreOrder(o: Order, sequence: Long) { orders[o.id] = o; orderSeq[o.id] = sequence }
    internal fun sequenceOf(id: OrderId): Long = orderSeq[id] ?: 0L
    internal var sequenceCounter: Long
        get() = seq
        set(v) { seq = v }

    fun hashInto(h: CanonicalHasher) {
        h.section("markets")
        h.putInt(states.size)
        for (s in states.values) s.hashInto(h)
        h.putInt(orders.size)
        for (o in orders.values) {
            h.putLong(o.id.value).putString(o.key.good.key).putLong(o.key.region.value).putInt(o.side.ordinal).putInt(o.party.kind.ordinal).putLong(o.party.id)
                .putLong(o.limit?.perUnit?.raw ?: -1L).putQuantity(o.quantity).putQuantity(o.remaining).putLong(o.placed.value).putLong(o.expires.value).putLong(o.source)
        }
        indexLog.hashInto(h)
    }
}
