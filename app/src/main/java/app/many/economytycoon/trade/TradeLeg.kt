package app.many.economytycoon.trade

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId

/**
 * Buying a good in one region and selling it in another. Prices are per unit; freight and tariff are for the whole
 * [quantity]. Figures are estimates from current quotes and move as soon as the orders are placed.
 */
data class TradeLeg(
    val good: GoodId,
    val goodName: String,
    val from: RegionId,
    val fromName: String,
    val to: RegionId,
    val toName: String,
    val buyPrice: Money,
    val sellPrice: Money,
    val quantity: Quantity,
    val freight: Money,
    val tariff: Money,
    val days: Int,
    val crossesBorder: Boolean,
) {
    /** What the goods cost to buy. */
    val purchase: Money get() = buyPrice.valueOf(quantity)

    /** What they would fetch. */
    val revenue: Money get() = sellPrice.valueOf(quantity)

    /** Freight plus tariff. */
    val carriage: Money get() = freight + tariff

    /** Everything spent before the sale. */
    val outlay: Money get() = purchase + carriage

    val netProfit: Money get() = revenue - outlay

    /** Profit as a share of outlay, ppm; zero when nothing is spent. */
    val marginPpm: Long get() = if (outlay.raw <= 0L) 0L else netProfit.raw * 1_000_000L / outlay.raw

    /** Profit per day the goods are on the road; a one-day trip counts as one day. */
    val profitPerDay: Money get() = Money(netProfit.raw / days.coerceAtLeast(1))

    /** Price gap per unit before carriage. */
    val spread: Money get() = sellPrice - buyPrice

    /** Carriage per unit moved. */
    val carriagePerUnit: Money get() = if (quantity.raw <= 0L) Money.ZERO else Money(carriage.raw * Quantity.SCALE / quantity.raw)

    val isProfitable: Boolean get() = netProfit.raw > 0L
}

/** Ways to order the legs found. */
enum class TradeSort(val label: String) {
    PROFIT("Profit"),
    MARGIN("Margin"),
    PER_DAY("Per day"),
    SPREAD("Price gap"),
}

/** Sizing and ranking helpers kept apart from the scan so they can be tested on plain numbers. */
object TradeMath {
    /** How much can be bought with [cash] at [price], holding back [reserve], at most [limit]. */
    fun affordable(cash: Money, reserve: Money, price: Money, limit: Quantity): Quantity {
        if (price.raw <= 0L) return limit
        val free = cash.raw - reserve.raw
        if (free <= 0L) return Quantity.ZERO
        val units = free * Quantity.SCALE / price.raw
        return Quantity.min(Quantity(units), limit)
    }

    /**
     * Quantity at which carriage stops eating the spread, given a fixed charge and a per-unit one, or null when the
     * spread never covers the per-unit charge.
     */
    fun breakEven(spread: Money, fixedCarriage: Money, perUnitCarriage: Money): Quantity? {
        val net = spread.raw - perUnitCarriage.raw
        if (net <= 0L) return null
        if (fixedCarriage.raw <= 0L) return Quantity.ZERO
        val raw = (fixedCarriage.raw * Quantity.SCALE + net - 1) / net
        return Quantity(raw)
    }

    fun sorted(legs: List<TradeLeg>, by: TradeSort): List<TradeLeg> = when (by) {
        TradeSort.PROFIT -> legs.sortedWith(compareByDescending<TradeLeg> { it.netProfit.raw }.then(tieBreak))
        TradeSort.MARGIN -> legs.sortedWith(compareByDescending<TradeLeg> { it.marginPpm }.then(tieBreak))
        TradeSort.PER_DAY -> legs.sortedWith(compareByDescending<TradeLeg> { it.profitPerDay.raw }.then(tieBreak))
        TradeSort.SPREAD -> legs.sortedWith(compareByDescending<TradeLeg> { it.spread.raw }.then(tieBreak))
    }

    private val tieBreak: Comparator<TradeLeg> =
        compareBy<TradeLeg>({ it.goodName }, { it.from.value }, { it.to.value })

    /** Keeps the best leg for each good so one commodity does not fill the list. */
    fun bestPerGood(legs: List<TradeLeg>, by: TradeSort = TradeSort.PROFIT): List<TradeLeg> =
        sorted(legs, by).distinctBy { it.good }

    /** Scales a leg to a different quantity, scaling freight in proportion. Tariff scales with value. */
    fun resized(leg: TradeLeg, quantity: Quantity): TradeLeg {
        if (leg.quantity.raw <= 0L || quantity.raw == leg.quantity.raw) return leg.copy(quantity = quantity)
        val freight = Money(leg.freight.raw * quantity.raw / leg.quantity.raw)
        val tariff = Money(leg.tariff.raw * quantity.raw / leg.quantity.raw)
        return leg.copy(quantity = quantity, freight = freight, tariff = tariff)
    }
}
