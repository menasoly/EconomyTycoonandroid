package app.many.economytycoon.property

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * Who has put land up for sale or for rent, and on what terms. Appraisals come from the world and cash moves
 * through the property runner; this class holds the register and the rules about prices and terms.
 */
class PropertyService(val params: PropertyParams = PropertyParams()) {
    private val listings = java.util.TreeMap<ParcelId, Listing>()
    private val offers = java.util.TreeMap<ParcelId, LeaseOffer>()
    private val leases = java.util.TreeMap<Long, Lease>()
    private val since = java.util.TreeMap<ParcelId, Tick>()
    var leaseSequence: Long = 0L
        internal set

    fun listings(): List<Listing> = listings.values.toList()
    fun listing(parcel: ParcelId): Listing? = listings[parcel]
    fun listingsOf(company: CompanyId): List<Listing> = listings.values.filter { it.seller == company }
    fun offers(): List<LeaseOffer> = offers.values.toList()
    fun offer(parcel: ParcelId): LeaseOffer? = offers[parcel]
    fun offersOf(company: CompanyId): List<LeaseOffer> = offers.values.filter { it.landlord == company }
    fun leases(): List<Lease> = leases.values.toList()
    fun lease(id: Long): Lease? = leases[id]
    fun activeLeases(): List<Lease> = leases.values.filter { it.isActive }
    fun leaseOf(parcel: ParcelId): Lease? = leases.values.firstOrNull { it.isActive && it.parcel == parcel }
    fun leasesOf(company: CompanyId): List<Lease> = leases.values.filter { it.isActive && it.landlord == company }
    fun historyOf(company: CompanyId): List<Lease> = leases.values.filter { !it.isActive && it.landlord == company }

    /** True while a parcel is promised to someone and may not be built on, sold or let again. */
    fun encumbered(parcel: ParcelId): Boolean = parcel in listings || parcel in offers || leaseOf(parcel) != null

    // ---- holding periods -----------------------------------------------------------------------

    fun noteAcquired(parcel: ParcelId, tick: Tick) { since[parcel] = tick }
    fun heldSince(parcel: ParcelId): Tick? = since[parcel]
    fun heldParcels(): List<Pair<ParcelId, Tick>> = since.entries.map { it.key to it.value }
    fun heldDays(parcel: ParcelId, tick: Tick): Long = since[parcel]?.let { (tick.value - it.value).coerceAtLeast(0L) } ?: 0L

    // ---- prices --------------------------------------------------------------------------------

    fun minAsk(appraisal: Money) = Money(mulDiv(appraisal.raw, params.minAskPpm, 1_000_000L))
    fun maxAsk(appraisal: Money) = Money(mulDiv(appraisal.raw, params.maxAskPpm, 1_000_000L))
    fun transferTax(price: Money) = Money(mulDiv(price.raw, params.transferTaxPpm, 1_000_000L))

    /** What a month's rent should be for land worth [value]. */
    fun fairRent(value: Money): Money = Money(mulDiv(value.raw, params.rentYieldPpm, 12L * 1_000_000L))
    fun minRent(value: Money) = Money(mulDiv(fairRent(value).raw, params.minRentPpm, 1_000_000L))
    fun maxRent(value: Money) = Money(mulDiv(fairRent(value).raw, params.maxRentPpm, 1_000_000L))

    /** Daily chance of a tenant: highest for a cheap offer and falling away as the rent climbs past fair. */
    fun tenantChancePpm(rent: Money, value: Money): Long {
        val fair = fairRent(value).raw
        if (rent.raw <= 0L || fair <= 0L) return 0L
        val ratio = mulDiv(fair, 1_000_000L, rent.raw).coerceIn(0L, 2_000_000L)
        val over = if (rent.raw > fair) ratio * ratio / 1_000_000L else ratio
        return mulDiv(params.tenantChancePpm, over, 1_000_000L)
    }

    fun earlyEndCost(lease: Lease): Money = Money(lease.monthlyRent.raw * params.earlyEndMonths)

    // ---- checks --------------------------------------------------------------------------------

    fun checkListing(company: CompanyId, ask: Money, appraisal: Money): Rejection? = when {
        ask.raw <= 0L -> Rejection(Rules.INVALID_PRICE, "set a price")
        ask < minAsk(appraisal) || ask > maxAsk(appraisal) -> Rejection(Rules.INVALID_PRICE, "ask between ${minAsk(appraisal)} and ${maxAsk(appraisal)}")
        listingsOf(company).size >= params.maxListingsPerCompany -> Rejection(Rules.LIMIT_EXCEEDED, "too many listings")
        else -> null
    }

    fun checkOffer(company: CompanyId, termDays: Int, rent: Money, value: Money): Rejection? = when {
        termDays !in params.leaseTerms -> Rejection(Rules.INVALID_ARGUMENT, "choose a term of ${params.leaseTerms.joinToString(", ")} days")
        rent < minRent(value) || rent > maxRent(value) -> Rejection(Rules.INVALID_PRICE, "rent between ${minRent(value)} and ${maxRent(value)} a month")
        offersOf(company).size + leasesOf(company).size >= params.maxLeasesPerCompany -> Rejection(Rules.LIMIT_EXCEEDED, "too many lettings")
        else -> null
    }

    // ---- changes -------------------------------------------------------------------------------

    fun addListing(parcel: ParcelId, seller: CompanyId, ask: Money, tick: Tick): Listing {
        val l = Listing(parcel, seller, ask, tick, Tick(tick.value + params.listingDays))
        listings[parcel] = l
        return l
    }

    fun removeListing(parcel: ParcelId): Listing? = listings.remove(parcel)

    fun addOffer(parcel: ParcelId, landlord: CompanyId, termDays: Int, rent: Money, tick: Tick): LeaseOffer {
        val o = LeaseOffer(parcel, landlord, termDays, rent, tick, Tick(tick.value + params.offerDays))
        offers[parcel] = o
        return o
    }

    fun removeOffer(parcel: ParcelId): LeaseOffer? = offers.remove(parcel)

    fun startLease(offer: LeaseOffer, tenant: Int, tick: Tick): Lease {
        offers.remove(offer.parcel)
        val l = Lease(++leaseSequence, offer.parcel, offer.landlord, tenant, offer.monthlyRent, tick, Tick(tick.value + offer.termDays))
        leases[l.id] = l
        return l
    }

    fun rentDue(lease: Lease, tick: Tick) = lease.isActive && tick >= lease.nextRent

    fun rentPaid(lease: Lease, amount: Money) {
        lease.collected = lease.collected + amount
        lease.nextRent = Tick(lease.nextRent.value + params.rentEvery)
        lease.missed = 0
    }

    fun rentMissed(lease: Lease) {
        lease.missed += 1
        lease.nextRent = Tick(lease.nextRent.value + params.rentEvery)
    }

    fun shouldEvict(lease: Lease) = lease.missed >= params.missedLimit

    fun endLease(lease: Lease, tick: Tick) { lease.endedAt = tick }

    /** Forgets a parcel's records after it has changed hands. */
    fun clearParcel(parcel: ParcelId) { listings.remove(parcel); offers.remove(parcel) }

    fun prune() {
        val ended = leases.values.filter { !it.isActive }.sortedBy { it.endedAt!!.value }
        val extra = ended.size - params.historyLength
        if (extra > 0) for (l in ended.take(extra)) leases.remove(l.id)
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(leaseSequence).putInt(listings.size)
        for (l in listings.values) l.hashInto(h)
        h.putInt(offers.size)
        for (o in offers.values) o.hashInto(h)
        h.putInt(leases.size)
        for (l in leases.values) l.hashInto(h)
        h.putInt(since.size)
        for ((p, t) in since) h.putLong(p.value).putLong(t.value)
    }

    internal fun restore(l: Listing) { listings[l.parcel] = l }
    internal fun restore(o: LeaseOffer) { offers[o.parcel] = o }
    internal fun restore(l: Lease) { leases[l.id] = l }
    internal fun restoreSince(p: ParcelId, t: Tick) { since[p] = t }
    internal fun clearAll() { listings.clear(); offers.clear(); leases.clear(); since.clear(); leaseSequence = 0L }
}
