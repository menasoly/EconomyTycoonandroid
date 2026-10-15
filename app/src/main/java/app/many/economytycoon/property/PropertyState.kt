package app.many.economytycoon.property

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Tick

data class PropertyParams(
    /** Days a for-sale listing stays up. */
    val listingDays: Int = 60,
    /** The asking price may sit this far under or over the appraisal, in ppm of it. */
    val minAskPpm: Long = 600_000L,
    val maxAskPpm: Long = 2_500_000L,
    /** Paid by the buyer to the government on every sale, in ppm of the price. */
    val transferTaxPpm: Long = 20_000L,
    val maxListingsPerCompany: Int = 8,
    val leaseTerms: List<Int> = listOf(90, 180, 360),
    /** A fair year's rent as a share of the land's value. */
    val rentYieldPpm: Long = 60_000L,
    val minRentPpm: Long = 400_000L,
    val maxRentPpm: Long = 2_500_000L,
    /** Chance per day that a tenant takes an open offer asking exactly the fair rent. */
    val tenantChancePpm: Long = 60_000L,
    val offerDays: Int = 45,
    val rentEvery: Int = 30,
    val maxLeasesPerCompany: Int = 12,
    /** A lease ends after this many rent payments in a row go missing. */
    val missedLimit: Int = 2,
    /** Months of rent a landlord pays to end a lease early. */
    val earlyEndMonths: Int = 2,
    val historyLength: Int = 40,
)

class Listing(val parcel: ParcelId, val seller: CompanyId, val ask: Money, val listedAt: Tick, val expiresAt: Tick) {
    fun hashInto(h: CanonicalHasher) {
        h.putLong(parcel.value).putLong(seller.value).putLong(ask.raw).putLong(listedAt.value).putLong(expiresAt.value)
    }
}

class LeaseOffer(val parcel: ParcelId, val landlord: CompanyId, val termDays: Int, val monthlyRent: Money, val offeredAt: Tick, val expiresAt: Tick) {
    fun hashInto(h: CanonicalHasher) {
        h.putLong(parcel.value).putLong(landlord.value).putInt(termDays).putLong(monthlyRent.raw).putLong(offeredAt.value).putLong(expiresAt.value)
    }
}

class Lease(
    val id: Long,
    val parcel: ParcelId,
    val landlord: CompanyId,
    /** Picks the tenant's name from a fixed list; the tenant is a business, not a simulated company. */
    val tenant: Int,
    val monthlyRent: Money,
    val startedAt: Tick,
    val endsAt: Tick,
) {
    var nextRent: Tick = Tick(startedAt.value + 30)
        internal set
    var collected: Money = Money.ZERO
        internal set
    var missed: Int = 0
        internal set
    var endedAt: Tick? = null
        internal set

    val isActive get() = endedAt == null

    internal fun restore(nextRent: Tick, collected: Money, missed: Int, endedAt: Tick?) {
        this.nextRent = nextRent; this.collected = collected; this.missed = missed; this.endedAt = endedAt
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id).putLong(parcel.value).putLong(landlord.value).putInt(tenant).putLong(monthlyRent.raw)
        h.putLong(startedAt.value).putLong(endsAt.value).putLong(nextRent.value).putLong(collected.raw).putInt(missed).putLong(endedAt?.value ?: -1L)
    }
}

sealed class PropertyEvent {
    abstract val parcel: ParcelId
    data class Listed(override val parcel: ParcelId, val seller: CompanyId, val ask: Money, val tick: Tick) : PropertyEvent()
    data class Sold(override val parcel: ParcelId, val seller: CompanyId, val buyer: CompanyId, val price: Money, val tick: Tick) : PropertyEvent()
    data class ListingLapsed(override val parcel: ParcelId, val seller: CompanyId, val tick: Tick) : PropertyEvent()
    data class LeaseStarted(override val parcel: ParcelId, val landlord: CompanyId, val lease: Long, val tick: Tick) : PropertyEvent()
    data class RentMissed(override val parcel: ParcelId, val landlord: CompanyId, val lease: Long, val tick: Tick) : PropertyEvent()
    data class LeaseEnded(override val parcel: ParcelId, val landlord: CompanyId, val lease: Long, val collected: Money, val tick: Tick) : PropertyEvent()
}
