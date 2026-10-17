package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/**
 * What the grid's retailer charges. Customers pay a share of the recent wholesale price plus a margin, a network
 * charge and the capacity levy for each megawatt-hour, and a fixed charge for each kilowatt of connection per month.
 * Interruptible customers get a discount on the energy part in return for being cut off first. A regulator can cap the
 * all-in price households pay, and then the retailer carries the difference.
 */
data class TariffSchedule(
    /** Share of the wholesale price passed on to customers. */
    val passThroughPpm: Long = Rate.ONE_PPM,
    val retailMarginPerMwh: Money = Money.units(6),
    val networkChargePerMwh: Money = Money.units(14),
    val fixedChargePerKwMonth: Money = Money(5_000L),
    /** Discount on the energy and margin parts for interruptible customers. */
    val interruptibleDiscountPpm: Long = 150_000L,
    /** The most a household pays per megawatt-hour, all in; null for no cap. */
    val householdCapPerMwh: Money? = null,
) {
    init {
        require(passThroughPpm in 0L..Rate.ONE_PPM) { "pass-through is a share" }
        require(interruptibleDiscountPpm in 0L..Rate.ONE_PPM) { "discount is a share" }
        require(householdCapPerMwh == null || householdCapPerMwh.raw > 0L) { "a price cap must be positive" }
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(passThroughPpm).putMoney(retailMarginPerMwh).putMoney(networkChargePerMwh).putMoney(fixedChargePerKwMonth)
        h.putLong(interruptibleDiscountPpm).putLong(householdCapPerMwh?.raw ?: -1L)
    }
}

/** The price one class of customer pays per megawatt-hour, broken down. */
data class TariffQuote(
    val customer: CustomerClass,
    val energy: Money,
    val margin: Money,
    val network: Money,
    val levy: Money,
    /** What the customer actually pays, after any cap. */
    val perMwh: Money,
    /** What the customer would pay without the cap. */
    val uncappedPerMwh: Money,
) {
    val capped: Boolean get() = perMwh < uncappedPerMwh
    /** What the retailer gives up on each megawatt-hour because of the cap. */
    val shortfallPerMwh: Money get() = uncappedPerMwh - perMwh
}

object Tariffs {
    /** The quote for [customer] when wholesale power has recently cost [wholesale] and the capacity levy is [levy]. */
    fun quote(schedule: TariffSchedule, customer: CustomerClass, wholesale: Money, levy: Money): TariffQuote {
        var energy = Money(mulDiv(wholesale.raw.coerceAtLeast(0L), schedule.passThroughPpm, Rate.ONE_PPM, RoundingMode.HALF_EVEN))
        var margin = schedule.retailMarginPerMwh
        if (customer == CustomerClass.INTERRUPTIBLE) {
            val keep = Rate.ONE_PPM - schedule.interruptibleDiscountPpm
            energy = Money(mulDiv(energy.raw, keep, Rate.ONE_PPM, RoundingMode.HALF_EVEN))
            margin = Money(mulDiv(margin.raw, keep, Rate.ONE_PPM, RoundingMode.HALF_EVEN))
        }
        val uncapped = energy + margin + schedule.networkChargePerMwh + levy
        val cap = schedule.householdCapPerMwh
        val paid = if (customer == CustomerClass.HOUSEHOLD && cap != null && uncapped > cap) cap else uncapped
        return TariffQuote(customer, energy, margin, schedule.networkChargePerMwh, levy, paid, uncapped)
    }

    /** Monthly fixed charge for a connection of [baseKw]. */
    fun fixedCharge(schedule: TariffSchedule, baseKw: Long): Money = Money(mulDiv(schedule.fixedChargePerKwMonth.raw, baseKw, 1L))

    /** Bill for [servedKwh] of energy at a quote, without the fixed charge. */
    fun energyBill(quote: TariffQuote, servedKwh: Long): Money = GridUnits.cost(quote.perMwh, servedKwh)

    /** What the retailer gives up on [servedKwh] because of a household cap. */
    fun capShortfall(quote: TariffQuote, servedKwh: Long): Money = GridUnits.cost(quote.shortfallPerMwh, servedKwh)
}
