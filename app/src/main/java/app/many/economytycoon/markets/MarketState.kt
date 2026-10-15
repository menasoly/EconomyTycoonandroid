package app.many.economytycoon.markets

import app.many.economytycoon.content.EconomyParams
import app.many.economytycoon.content.GoodDef
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.mulDiv

/** Rolling record of closing price and volume for charts and indices. */
class PriceHistory(val capacity: Int = 60) {
    private val closes = LongArray(capacity)
    private val volumes = LongArray(capacity)
    private var head = 0
    var size = 0
        private set

    fun push(close: Long, volume: Long) {
        closes[head] = close; volumes[head] = volume
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    /** Closing price [back] ticks ago; 0 is the most recent. */
    fun close(back: Int): Long? = if (back >= size) null else closes[(head - 1 - back + capacity * 2) % capacity]
    fun volume(back: Int): Long? = if (back >= size) null else volumes[(head - 1 - back + capacity * 2) % capacity]

    fun closesOldestFirst(): LongArray = LongArray(size) { closes[(head - size + it + capacity * 2) % capacity] }
    fun volumesOldestFirst(): LongArray = LongArray(size) { volumes[(head - size + it + capacity * 2) % capacity] }

    internal fun restore(closeValues: LongArray, volumeValues: LongArray) {
        head = 0; size = 0
        for (i in closeValues.indices) push(closeValues[i], volumeValues[i])
    }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(size)
        for (i in 0 until size) h.putLong(closes[(head - size + i + capacity * 2) % capacity]).putLong(volumes[(head - size + i + capacity * 2) % capacity])
    }
}

/** Everything the market remembers about one good in one region. */
class MarketState(val key: MarketKey, initial: Price) {
    var last: Price = initial
        internal set
    var previousClose: Price = initial
        internal set
    /** Exponential averages of the traded price, raw money units. */
    var fastAverage: Long = initial.perUnit.raw
        internal set
    var slowAverage: Long = initial.perUnit.raw
        internal set
    var lastVolume: Quantity = Quantity.ZERO
        internal set
    var volumeAverage: Long = 0
        internal set
    var lastTradeTick: Long = -1
        internal set
    var bestBid: Price? = null
        internal set
    var bestAsk: Price? = null
        internal set
    var unmetDemand: Quantity = Quantity.ZERO
        internal set
    var unsoldSupply: Quantity = Quantity.ZERO
        internal set
    /** Temporary price pressure from events, ppm of the price; decays each tick. */
    var shockPpm: Long = 0
        internal set
    /** Demand multiplier from events and policy, ppm; 1,000,000 is neutral. */
    var demandShiftPpm: Long = 1_000_000
        internal set
    val history = PriceHistory()

    /** Change over the last tick as a signed ppm. */
    val changePpm: Long
        get() = if (previousClose.perUnit.raw == 0L) 0L else mulDiv(last.perUnit.raw - previousClose.perUnit.raw, 1_000_000L, previousClose.perUnit.raw)

    /** How far the price sits from its slow average, signed ppm. */
    val deviationPpm: Long
        get() = if (slowAverage == 0L) 0L else mulDiv(last.perUnit.raw - slowAverage, 1_000_000L, slowAverage)

    internal fun record(price: Price, volume: Quantity, tick: Long) {
        previousClose = last
        last = price
        fastAverage += (price.perUnit.raw - fastAverage) / 5
        slowAverage += (price.perUnit.raw - slowAverage) / 30
        lastVolume = volume
        volumeAverage += (volume.raw - volumeAverage) / 10
        if (volume.raw > 0) lastTradeTick = tick
        history.push(price.perUnit.raw, volume.raw)
    }

    internal fun restore(
        last: Price, previous: Price, fast: Long, slow: Long, volume: Quantity, volumeAverage: Long, lastTrade: Long,
        unmet: Quantity, unsold: Quantity, shock: Long, shift: Long,
    ) {
        this.last = last; previousClose = previous; fastAverage = fast; slowAverage = slow; lastVolume = volume
        this.volumeAverage = volumeAverage; lastTradeTick = lastTrade; unmetDemand = unmet; unsoldSupply = unsold
        shockPpm = shock; demandShiftPpm = shift
    }

    fun hashInto(h: CanonicalHasher) {
        h.putString(key.good.key).putLong(key.region.value).putMoney(last.perUnit).putMoney(previousClose.perUnit)
            .putLong(fastAverage).putLong(slowAverage).putQuantity(lastVolume).putLong(volumeAverage).putLong(lastTradeTick)
            .putQuantity(unmetDemand).putQuantity(unsoldSupply).putLong(shockPpm).putLong(demandShiftPpm)
        history.hashInto(h)
    }
}

/** Price formation rules: how far a price may move in a tick and where it drifts without trades. */
object PriceRules {
    /** Largest one-tick move for the good, ppm. */
    fun maxMovePpm(good: GoodDef, params: EconomyParams): Long =
        minOf(good.volatility.maxMovePpm, params.defaultPriceMovePpm.coerceAtLeast(good.volatility.maxMovePpm))

    fun band(last: Long, maxMovePpm: Long): LongRange {
        val up = mulDiv(last, 1_000_000L + maxMovePpm, 1_000_000L)
        val down = mulDiv(last, 1_000_000L - maxMovePpm, 1_000_000L)
        return down..up
    }

    fun floorAndCeiling(good: GoodDef, params: EconomyParams): LongRange {
        val ref = good.referencePrice.perUnit.raw
        return mulDiv(ref, params.priceFloorPpm, 1_000_000L)..mulDiv(ref, params.priceCeilingPpm, 1_000_000L)
    }

    /**
     * Price after a tick without trades. Asks with no buyers push the price down, bids with no sellers
     * push it up, both sides present but not crossing pull it toward the midpoint, and an empty market
     * slowly returns to the reference price.
     */
    fun drift(last: Long, bestBid: Long?, bestAsk: Long?, reference: Long, maxMovePpm: Long): Long {
        val step = maxMovePpm / 5
        val target: Long = when {
            bestBid != null && bestAsk != null -> (bestBid + bestAsk) / 2
            bestAsk != null -> mulDiv(last, 1_000_000L - step, 1_000_000L)
            bestBid != null -> mulDiv(last, 1_000_000L + step, 1_000_000L)
            else -> last + (reference - last) / 100
        }
        val pull = if (bestBid != null && bestAsk != null) 4L else 1L
        val moved = last + (target - last) / pull
        val band = band(last, maxMovePpm)
        return moved.coerceIn(band.first, band.last)
    }
}

