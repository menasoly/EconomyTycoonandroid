package app.many.economytycoon.tenders

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.TenderId
import app.many.economytycoon.core.Tick

/** How governments buy in bulk. Rates are parts per million. */
data class TenderParams(
    /** Days between a government's new tenders. */
    val issueEvery: Int = 12,
    /** Chance a government actually issues one when the time comes. */
    val issueChancePpm: Long = 750_000L,
    val maxOpenPerCountry: Int = 6,
    /** Days bids are accepted. */
    val biddingDays: Int = 8,
    /** Days the winner has to deliver after the bidding closes. */
    val deliveryDays: Int = 25,
    /** The most a government will pay, as a share of the good's reference price. */
    val ceilingPpm: Long = 1_300_000L,
    val floorPpm: Long = 500_000L,
    /** Value of a tender, in whole currency units, drawn between these. */
    val minValueUnits: Long = 15_000L,
    val maxValueUnits: Long = 120_000L,
    /** Penalty for failing to deliver, as a share of the contract value. */
    val penaltyPpm: Long = 150_000L,
    /** Failures within the memory window after which a company is barred from bidding. */
    val barAfterFailures: Int = 3,
    val failureMemoryDays: Int = 180,
    /** A government commits at most this many days of its public spending to open and awarded tenders. */
    val budgetDays: Int = 20,
    /** Days finished tenders stay on record. */
    val keepClosedDays: Int = 120,
    val maxBidsPerCompany: Int = 12,
)

enum class TenderStatus { OPEN, AWARDED, DELIVERED, FAILED, NO_BIDS }

data class Bid(val company: CompanyId, val facility: FacilityId, val unitPrice: Money, val placedAt: Tick, val sequence: Long)

/** A government's call for a fixed quantity of one good. */
class Tender(
    val id: TenderId,
    val country: CountryId,
    val good: GoodId,
    val quantity: Quantity,
    val ceiling: Money,
    val issuedAt: Tick,
    val closesAt: Tick,
    val deliverBy: Tick,
) {
    var status: TenderStatus = TenderStatus.OPEN
        internal set
    var winner: CompanyId? = null
        internal set
    var winnerFacility: FacilityId? = null
        internal set
    var awardedPrice: Money = Money.ZERO
        internal set
    var endedAt: Tick? = null
        internal set
    var penalty: Money = Money.ZERO
        internal set
    internal val bidMap = java.util.TreeMap<CompanyId, Bid>()

    val bids: List<Bid> get() = bidMap.values.sortedWith(compareBy({ it.unitPrice.raw }, { it.sequence }))
    val isOpen: Boolean get() = status == TenderStatus.OPEN
    val isAwarded: Boolean get() = status == TenderStatus.AWARDED
    val ceilingValue: Money get() = Money(app.many.economytycoon.core.mulDiv(ceiling.raw, quantity.raw, Quantity.SCALE))
    val awardedValue: Money get() = Money(app.many.economytycoon.core.mulDiv(awardedPrice.raw, quantity.raw, Quantity.SCALE))
    fun bidOf(company: CompanyId): Bid? = bidMap[company]
    fun leadingBid(): Bid? = bids.firstOrNull()

    internal fun restore(status: TenderStatus, winner: CompanyId?, facility: FacilityId?, price: Money, endedAt: Tick?, penalty: Money, bids: List<Bid>) {
        this.status = status; this.winner = winner; winnerFacility = facility; awardedPrice = price; this.endedAt = endedAt; this.penalty = penalty
        bidMap.clear(); for (b in bids) bidMap[b.company] = b
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(country.value).putString(good.key).putLong(quantity.raw).putLong(ceiling.raw).putLong(issuedAt.value)
        h.putLong(closesAt.value).putLong(deliverBy.value).putInt(status.ordinal).putLong(winner?.value ?: -1L).putLong(winnerFacility?.value ?: -1L)
        h.putLong(awardedPrice.raw).putLong(endedAt?.value ?: -1L).putLong(penalty.raw).putInt(bidMap.size)
        for (b in bidMap.values) h.putLong(b.company.value).putLong(b.facility.value).putLong(b.unitPrice.raw).putLong(b.placedAt.value).putLong(b.sequence)
    }
}

sealed class TenderEvent {
    abstract val tender: TenderId
    data class Issued(override val tender: TenderId, val country: CountryId, val good: GoodId, val tick: Tick) : TenderEvent()
    data class Awarded(override val tender: TenderId, val winner: CompanyId, val price: Money, val tick: Tick) : TenderEvent()
    data class NoBids(override val tender: TenderId, val tick: Tick) : TenderEvent()
    data class Delivered(override val tender: TenderId, val winner: CompanyId, val paid: Money, val tick: Tick) : TenderEvent()
    data class Failed(override val tender: TenderId, val winner: CompanyId, val penalty: Money, val tick: Tick) : TenderEvent()
}
