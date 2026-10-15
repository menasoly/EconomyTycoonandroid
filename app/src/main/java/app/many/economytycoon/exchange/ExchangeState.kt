package app.many.economytycoon.exchange

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Holder

/** The rules of the stock exchange. Rates are parts per million. */
data class ExchangeParams(
    /** Days a company must have traded before it can list. */
    val minAgeTicks: Int = 30,
    val minNetWorth: Money = Money.units(100_000),
    val minFloatPpm: Long = 50_000L,
    val maxFloatPpm: Long = 490_000L,
    /** Discount to fair value at which new shares are sold to the public. */
    val ipoDiscountPpm: Long = 100_000L,
    /** Share of the gap to fair value that closes each valuation round. */
    val reversionPpm: Long = 30_000L,
    /** Largest random move in a day, either way. */
    val volatilityPpm: Long = 12_000L,
    /** Total gap between buying and selling prices. */
    val spreadPpm: Long = 8_000L,
    /** Broker's fee on every trade. */
    val feePpm: Long = 2_000L,
    /** Tax on share purchases, paid to the investor's government. */
    val stampDutyPpm: Long = 1_000L,
    /** A trade of this multiple of the public's holding moves the price by its whole size; smaller trades pro rata. */
    val liquidityMultiple: Long = 4L,
    val maxImpactPpm: Long = 200_000L,
    /** Share of a day's buying or selling pressure that is still felt the next day. */
    val pressureKeepPpm: Long = 700_000L,
    /** How much of the pressure feeds into the day's price move. */
    val pressureWeightPpm: Long = 500_000L,
    val historyLength: Int = 120,
    /** Days between revaluations of the underlying business. */
    val valuationEvery: Int = 5,
    /** Most the public will pay for a new issue, as a share of what households hold. */
    val publicCapacityPpm: Long = 20_000L,
    val minPrice: Money = Money(100),
    val startingIndex: Long = 100_000L,
)

enum class TradeSide { BUY, SELL }

/** Why a trade could not go ahead. */
enum class TradeRefusal { NOT_LISTED, NO_SHARES, NO_PUBLIC_SHARES, NOT_ENOUGH_CASH, NOT_ENOUGH_HELD, TOO_SMALL }

/** What a trade would cost or pay, before anything moves. */
data class TradeQuote(
    val issuer: CompanyId,
    val side: TradeSide,
    val shares: Long,
    val averagePrice: Money,
    val gross: Money,
    val fee: Money,
    val duty: Money,
    val impactPpm: Long,
) {
    /** Cash that leaves the investor (buy) or arrives (sell). */
    val net: Money get() = if (side == TradeSide.BUY) gross + fee + duty else gross - fee
}

/** A company's shares on the exchange. */
class Listing(val issuer: CompanyId, val listedAt: Tick, price: Money) {
    var price: Money = price
        internal set
    var previousClose: Money = price
        internal set
    var fairValue: Money = price
        internal set
    /** Net buying (+) or selling (-) not yet absorbed into the price, ppm of the price. */
    var pressurePpm: Long = 0L
        internal set
    var volumeToday: Long = 0L
        internal set
    var turnoverToday: Money = Money.ZERO
        internal set
    var volumeTotal: Long = 0L
        internal set
    internal val closes = ArrayDeque<Long>()

    val history: List<Money> get() = closes.map { Money(it) }
    val changePpm: Long get() = if (previousClose.raw <= 0L) 0L else (price.raw - previousClose.raw) * 1_000_000L / previousClose.raw
    val high: Money get() = Money(maxOf(closes.maxOrNull() ?: price.raw, price.raw))
    val low: Money get() = Money(minOf(closes.minOrNull() ?: price.raw, price.raw))

    internal fun restore(price: Money, previousClose: Money, fair: Money, pressure: Long, volumeToday: Long, turnoverToday: Money, volumeTotal: Long, closes: List<Long>) {
        this.price = price; this.previousClose = previousClose; this.fairValue = fair; this.pressurePpm = pressure
        this.volumeToday = volumeToday; this.turnoverToday = turnoverToday; this.volumeTotal = volumeTotal
        this.closes.clear(); this.closes.addAll(closes)
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(issuer.value).putLong(listedAt.value).putLong(price.raw).putLong(previousClose.raw).putLong(fairValue.raw)
        h.putLong(pressurePpm).putLong(volumeToday).putLong(turnoverToday.raw).putLong(volumeTotal).putInt(closes.size)
        for (c in closes) h.putLong(c)
    }
}

/** Identifies one investor's holding in one company. */
data class PositionKey(val holder: Holder, val issuer: CompanyId) : Comparable<PositionKey> {
    override fun compareTo(other: PositionKey): Int {
        val h = holder.compareTo(other.holder)
        return if (h != 0) h else issuer.compareTo(other.issuer)
    }
}

/** The public: ordinary investors who take the other side of every trade. Their cash is the household pool. */
object PublicInvestors {
    val holder: Holder = Holder("public", 0L)
}

sealed class ExchangeEvent {
    abstract val issuer: CompanyId
    data class Listed(override val issuer: CompanyId, val tick: Tick, val price: Money, val shares: Long) : ExchangeEvent()
    data class Delisted(override val issuer: CompanyId, val tick: Tick) : ExchangeEvent()
    data class Surge(override val issuer: CompanyId, val tick: Tick, val changePpm: Long) : ExchangeEvent()
    data class Slump(override val issuer: CompanyId, val tick: Tick, val changePpm: Long) : ExchangeEvent()
}
