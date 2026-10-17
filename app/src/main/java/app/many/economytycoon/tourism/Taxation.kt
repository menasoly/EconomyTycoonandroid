package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/** What a property owes the government for one stretch of trading. */
data class StayTax(val vat: Money, val levy: Money) {
    val total: Money get() = vat + levy
}

/**
 * Tax on tourism: value added tax that is already inside the price a guest pays, and a tourism levy that the
 * destination adds on top, per guest and night and as a share of room revenue.
 */
object TourismTax {
    /** The VAT inside a gross price: price x rate / (1 + rate). */
    fun vatInside(gross: Money, vatPpm: Long): Money =
        if (vatPpm <= 0L || gross.raw <= 0L) Money.ZERO else Money(mulDiv(gross.raw, vatPpm, 1_000_000L + vatPpm))

    /** The levy for a number of guest-nights and a revenue. */
    fun levy(guestNights: Long, perGuestNight: Money, roomRevenue: Money, sharePpm: Long): Money {
        val flat = perGuestNight * guestNights.coerceAtLeast(0L)
        val share = if (sharePpm <= 0L) Money.ZERO else Money(mulDiv(roomRevenue.raw.coerceAtLeast(0L), sharePpm, 1_000_000L))
        return flat + share
    }

    fun owed(grossRevenue: Money, guestNights: Long, d: Destination): StayTax =
        StayTax(vatInside(grossRevenue, d.vatPpm), levy(guestNights, d.levyPerGuestNight, grossRevenue, d.levyRevenuePpm))

    /**
     * How much dearer a stay feels because of the levy: the levy per guest-night over the typical nightly cost of a
     * visit (a room plus daily spending), ppm. Used as the extra price level in the destination's appeal.
     */
    fun priceFeelPpm(d: Destination, referenceNightCost: Money, guestsPerRoomTenths: Int = 18): Long {
        if (referenceNightCost.raw <= 0L) return 0L
        val perRoomLevy = d.levyPerGuestNight.raw * guestsPerRoomTenths / 10L
        val flat = mulDiv(perRoomLevy, 1_000_000L, referenceNightCost.raw)
        return flat + d.levyRevenuePpm
    }
}
