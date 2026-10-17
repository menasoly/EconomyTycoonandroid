package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.property.Lease
import app.many.economytycoon.property.LeaseOffer
import app.many.economytycoon.property.PropertyEvent
import app.many.economytycoon.world.LandPricing
import app.many.economytycoon.world.Parcel

/**
 * Land trading between companies and renting land out. Sales move cash from buyer to seller with a transfer
 * tax to the government; tenants are anonymous businesses that pay rent out of household money.
 */
class PropertyRunner(private val e: Economy) {
    private var seq = 0L

    // ---- values --------------------------------------------------------------------------------

    fun appraise(p: Parcel): Money {
        val city = p.city?.let { e.world.cityOrNull(it) }
        val people = city?.let { e.world.population(it.id).total } ?: 0L
        val value = LandPricing.price(p, people, city?.location, e.urbanRunner.landFactorPpm(p.city))
        return if (value.raw > 0L) value else p.lastPrice
    }

    private fun governmentOf(p: Parcel): LedgerOwner = LedgerOwner.government(e.world.region(p.region).country)

    private fun ownerOf(c: CompanyId): LedgerOwner = e.companies.company(c)?.owner ?: LedgerOwner.company(c)

    private fun post(key: String, tick: Tick, reason: ReasonCode, memo: String, entries: List<Entry>): Outcome<*> =
        e.finance.ledger.post(TransactionDraft(key, tick, reason, entries, memo))

    /** Why a parcel cannot be sold or let right now, or null when it can. */
    private fun blocker(company: CompanyId, p: Parcel): app.many.economytycoon.core.Rejection? = when {
        p.owner != company -> app.many.economytycoon.core.Rejection(Rules.NOT_OWNER, "you do not own that parcel")
        p.developed || e.production.facilities().any { it.parcel == p.id && it.status != FacilityStatus.DEMOLISHED } ||
            e.production.projects().any { it.parcel == p.id && it.isOpen } ->
            app.many.economytycoon.core.Rejection(Rules.WRONG_STATE, "there is a building on that parcel")
        e.property.encumbered(p.id) -> app.many.economytycoon.core.Rejection(Rules.WRONG_STATE, "the parcel is already listed or let")
        else -> null
    }

    private fun parcel(id: ParcelId): Parcel? = e.world.land.get(id)

    // ---- selling -------------------------------------------------------------------------------

    fun list(company: CompanyId, id: ParcelId, ask: Money, tick: Tick): Outcome<PropertyEvent.Listed> {
        val p = parcel(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown parcel")
        blocker(company, p)?.let { return Outcome.Rejected(it) }
        e.property.checkListing(company, ask, appraise(p))?.let { return Outcome.Rejected(it) }
        e.property.addListing(id, company, ask, tick)
        return Outcome.ok(PropertyEvent.Listed(id, company, ask, tick))
    }

    fun delist(company: CompanyId, id: ParcelId): Outcome<Unit> {
        val l = e.property.listing(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "that parcel is not listed")
        if (l.seller != company) return Outcome.reject(Rules.NOT_OWNER, "you did not list that parcel")
        e.property.removeListing(id)
        return Outcome.ok(Unit)
    }

    fun buy(buyer: CompanyId, id: ParcelId, tick: Tick): Outcome<PropertyEvent.Sold> {
        val l = e.property.listing(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "that parcel is not for sale")
        val p = parcel(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown parcel")
        val c = e.companies.company(buyer) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        if (!c.isActive) return Outcome.reject(Rules.WRONG_STATE, "company is not active")
        if (l.seller == buyer) return Outcome.reject(Rules.INVALID_ARGUMENT, "you cannot buy your own listing")
        if (p.owner != l.seller) { e.property.removeListing(id); return Outcome.reject(Rules.WRONG_STATE, "the parcel has changed hands") }
        val price = l.ask
        val tax = e.property.transferTax(price)
        if (e.finance.ledger.cash(c.owner) < price + tax) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "needs ${price + tax} with transfer tax")
        val seller = ownerOf(l.seller)
        val held = e.finance.ledger.balance(seller, Chart.INVESTMENTS)
        val basis = if (p.lastPrice < held) p.lastPrice else if (held.raw > 0L) held else Money.ZERO
        val gain = price - basis
        val entries = ArrayList<Entry>()
        entries += Entry(AccountRef(c.owner, Chart.INVESTMENTS), price)
        entries += Entry(AccountRef(c.owner, Chart.OTHER_EXPENSE), tax)
        entries += Entry(AccountRef(c.owner, Chart.CASH), -(price + tax))
        entries += Entry(AccountRef(seller, Chart.CASH), price)
        if (basis.raw > 0L) entries += Entry(AccountRef(seller, Chart.INVESTMENTS), -basis)
        if (gain.raw > 0L) entries += Entry(AccountRef(seller, Chart.REVENUE_OTHER), -gain)
        if (gain.raw < 0L) entries += Entry(AccountRef(seller, Chart.OTHER_EXPENSE), -gain)
        if (tax.raw > 0L) { val gov = governmentOf(p); entries += Entry(AccountRef(gov, Chart.CASH), tax); entries += Entry(AccountRef(gov, Chart.REVENUE_OTHER), -tax) }
        val posted = post("land:sale:${id.value}:${tick.value}:${seq++}", tick, ReasonCode.PURCHASE, "land sale", entries)
        if (posted is Outcome.Rejected) return Outcome.Rejected(posted.rejection)
        e.world.land.transfer(id, buyer, price)
        e.property.clearParcel(id)
        e.property.noteAcquired(id, tick)
        return Outcome.ok(PropertyEvent.Sold(id, l.seller, buyer, price, tick))
    }

    // ---- letting -------------------------------------------------------------------------------

    fun offerLease(company: CompanyId, id: ParcelId, termDays: Int, rent: Money, tick: Tick): Outcome<LeaseOffer> {
        val p = parcel(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown parcel")
        blocker(company, p)?.let { return Outcome.Rejected(it) }
        e.property.checkOffer(company, termDays, rent, appraise(p))?.let { return Outcome.Rejected(it) }
        return Outcome.ok(e.property.addOffer(id, company, termDays, rent, tick))
    }

    fun withdrawOffer(company: CompanyId, id: ParcelId): Outcome<Unit> {
        val o = e.property.offer(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "that parcel has no open offer")
        if (o.landlord != company) return Outcome.reject(Rules.NOT_OWNER, "that offer is not yours")
        e.property.removeOffer(id)
        return Outcome.ok(Unit)
    }

    /** The landlord ends a running lease early by paying the tenant a few months' rent. */
    fun endLease(company: CompanyId, id: ParcelId, tick: Tick): Outcome<Money> {
        val lease = e.property.leaseOf(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "that parcel is not let")
        if (lease.landlord != company) return Outcome.reject(Rules.NOT_OWNER, "that lease is not yours")
        val c = e.companies.company(company) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        val cost = e.property.earlyEndCost(lease)
        if (e.finance.ledger.cash(c.owner) < cost) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "needs $cost to end the lease early")
        val hh = e.finance.households
        val posted = post(
            "land:endlease:${lease.id}:${tick.value}", tick, ReasonCode.RENT, "lease buy-out",
            listOf(
                Entry(AccountRef(c.owner, Chart.OTHER_EXPENSE), cost), Entry(AccountRef(c.owner, Chart.CASH), -cost),
                Entry(AccountRef(hh, Chart.CASH), cost), Entry(AccountRef(hh, Chart.REVENUE_OTHER), -cost),
            ),
        )
        if (posted is Outcome.Rejected) return Outcome.Rejected(posted.rejection)
        e.property.endLease(lease, tick)
        return Outcome.ok(cost)
    }

    // ---- the day -------------------------------------------------------------------------------

    fun run(tick: Tick): List<PropertyEvent> {
        val out = ArrayList<PropertyEvent>()
        for (l in e.property.listings()) {
            val p = parcel(l.parcel)
            if (p == null || p.owner != l.seller) { e.property.removeListing(l.parcel); continue }
            if (tick >= l.expiresAt) { e.property.removeListing(l.parcel); out += PropertyEvent.ListingLapsed(l.parcel, l.seller, tick) }
        }
        for (o in e.property.offers()) {
            val p = parcel(o.parcel)
            if (p == null || p.owner != o.landlord || tick >= o.expiresAt) { e.property.removeOffer(o.parcel); continue }
            val chance = e.property.tenantChancePpm(o.monthlyRent, appraise(p))
            val random = e.random.stream("property", o.parcel.value)
            if (random.nextLong(1_000_000L) < chance) {
                val lease = e.property.startLease(o, random.nextInt(TENANTS), tick)
                out += PropertyEvent.LeaseStarted(o.parcel, o.landlord, lease.id, tick)
            }
        }
        for (lease in e.property.activeLeases()) {
            if (tick >= lease.endsAt) { finish(lease, tick, out); continue }
            if (!e.property.rentDue(lease, tick)) continue
            if (collect(lease, tick)) continue
            e.property.rentMissed(lease)
            out += PropertyEvent.RentMissed(lease.parcel, lease.landlord, lease.id, tick)
            if (e.property.shouldEvict(lease)) finish(lease, tick, out)
        }
        e.property.prune()
        return out
    }

    private fun finish(lease: Lease, tick: Tick, out: MutableList<PropertyEvent>) {
        e.property.endLease(lease, tick)
        out += PropertyEvent.LeaseEnded(lease.parcel, lease.landlord, lease.id, lease.collected, tick)
    }

    private fun collect(lease: Lease, tick: Tick): Boolean {
        val hh = e.finance.households
        if (e.finance.ledger.cash(hh) < lease.monthlyRent) return false
        val landlord = ownerOf(lease.landlord)
        val posted = post(
            "land:rent:${lease.id}:${tick.value}", tick, ReasonCode.RENT, "land rent",
            listOf(
                Entry(AccountRef(hh, Chart.RENT_EXPENSE), lease.monthlyRent), Entry(AccountRef(hh, Chart.CASH), -lease.monthlyRent),
                Entry(AccountRef(landlord, Chart.CASH), lease.monthlyRent), Entry(AccountRef(landlord, Chart.REVENUE_SERVICES), -lease.monthlyRent),
            ),
        )
        if (posted is Outcome.Rejected) return false
        e.property.rentPaid(lease, lease.monthlyRent)
        return true
    }

    /** A retiring company's listings, offers and leases are dropped. */
    fun forget(company: CompanyId, tick: Tick) {
        for (l in e.property.listingsOf(company)) e.property.removeListing(l.parcel)
        for (o in e.property.offersOf(company)) e.property.removeOffer(o.parcel)
        for (l in e.property.leasesOf(company)) e.property.endLease(l, tick)
    }

    /** Rent a company is booked to collect over the next thirty days. */
    fun monthlyIncome(company: CompanyId): Money = e.property.leasesOf(company).fold(Money.ZERO) { a, l -> a + l.monthlyRent }

    /** What a company's undeveloped land would fetch at appraisal. */
    fun landValue(company: CompanyId): Money =
        e.world.land.ownedBy(company).fold(Money.ZERO) { a, p -> a + appraise(p) }

    companion object { const val TENANTS = 12 }
}
