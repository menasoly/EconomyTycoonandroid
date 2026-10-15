package app.many.economytycoon.contracts

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.ContractId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick

/** The rules every supply contract lives by. */
data class ContractParams(
    /** Contracts a company can have running at once, counting both buying and selling. */
    val maxActivePerCompany: Int = 20,
    /** Proposals a company can have waiting for an answer. */
    val maxOpenProposals: Int = 10,
    /** Ticks a proposal stays open before it lapses. */
    val proposalLifetime: Int = 10,
    /** Consecutive missed deliveries that end a contract. */
    val strikesToBreach: Int = 3,
    /** Penalty for a delivery the seller could not make, as a share of its value, ppm. */
    val shortfallPenaltyPpm: Long = 100_000L,
    /** Fee for walking away from a running contract, as a share of one delivery's value, ppm. */
    val cancelFeePpm: Long = 250_000L,
    val minInterval: Int = 1,
    val maxInterval: Int = 90,
    val maxDeliveries: Int = 360,
    /** How far from the reference price a contract price may sit, ppm of that price. */
    val minPriceMultiplePpm: Long = 200_000L,
    val maxPriceMultiplePpm: Long = 5_000_000L,
)

enum class ContractStatus { PROPOSED, ACTIVE, FULFILLED, CANCELLED, DECLINED, EXPIRED, BREACHED }

/** Which side of the deal the proposer is on. */
enum class ContractSide { SELLER, BUYER }

/** What is being traded, how often and for how much. */
data class ContractTerms(
    val good: GoodId,
    val quantity: Quantity,
    val unitPrice: Price,
    val intervalTicks: Int,
    val deliveries: Int,
) {
    val deliveryValue: Money get() = unitPrice.valueOf(quantity)
    val totalValue: Money get() = Money(deliveryValue.raw * deliveries)
}

/** A standing agreement between two companies to move a good on a schedule at a fixed price. */
class SupplyContract(
    val id: ContractId,
    val proposer: CompanyId,
    val counterparty: CompanyId,
    val proposerSide: ContractSide,
    val proposerFacility: FacilityId,
    val terms: ContractTerms,
    val proposedAt: Tick,
) {
    var status: ContractStatus = ContractStatus.PROPOSED
        internal set
    /** The counterparty's facility, known once the offer is accepted. */
    var counterFacility: FacilityId? = null
        internal set
    var startedAt: Tick? = null
        internal set
    var nextDue: Tick? = null
        internal set
    var endedAt: Tick? = null
        internal set
    var delivered: Int = 0
        internal set
    var missed: Int = 0
        internal set
    var strikes: Int = 0
        internal set
    var paid: Money = Money.ZERO
        internal set
    var penalties: Money = Money.ZERO
        internal set

    val seller: CompanyId get() = if (proposerSide == ContractSide.SELLER) proposer else counterparty
    val buyer: CompanyId get() = if (proposerSide == ContractSide.SELLER) counterparty else proposer
    val sellerFacility: FacilityId? get() = if (proposerSide == ContractSide.SELLER) proposerFacility else counterFacility
    val buyerFacility: FacilityId? get() = if (proposerSide == ContractSide.BUYER) proposerFacility else counterFacility

    val isOpen: Boolean get() = status == ContractStatus.PROPOSED
    val isActive: Boolean get() = status == ContractStatus.ACTIVE
    val remainingDeliveries: Int get() = (terms.deliveries - delivered).coerceAtLeast(0)
    fun involves(c: CompanyId) = c == proposer || c == counterparty
    fun other(c: CompanyId): CompanyId = if (c == proposer) counterparty else proposer

    internal fun restore(
        status: ContractStatus, counterFacility: FacilityId?, startedAt: Tick?, nextDue: Tick?, endedAt: Tick?,
        delivered: Int, missed: Int, strikes: Int, paid: Money, penalties: Money,
    ) {
        this.status = status; this.counterFacility = counterFacility; this.startedAt = startedAt; this.nextDue = nextDue
        this.endedAt = endedAt; this.delivered = delivered; this.missed = missed; this.strikes = strikes
        this.paid = paid; this.penalties = penalties
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(proposer.value).putLong(counterparty.value).putInt(proposerSide.ordinal).putLong(proposerFacility.value)
        h.putString(terms.good.key).putLong(terms.quantity.raw).putLong(terms.unitPrice.perUnit.raw).putInt(terms.intervalTicks).putInt(terms.deliveries)
        h.putLong(proposedAt.value).putInt(status.ordinal).putLong(counterFacility?.value ?: -1L)
        h.putLong(startedAt?.value ?: -1L).putLong(nextDue?.value ?: -1L).putLong(endedAt?.value ?: -1L)
        h.putInt(delivered).putInt(missed).putInt(strikes).putLong(paid.raw).putLong(penalties.raw)
    }
}

sealed class ContractEvent {
    abstract val contract: ContractId
    data class Proposed(override val contract: ContractId, val tick: Tick) : ContractEvent()
    data class Accepted(override val contract: ContractId, val tick: Tick) : ContractEvent()
    data class Declined(override val contract: ContractId, val tick: Tick) : ContractEvent()
    data class Expired(override val contract: ContractId, val tick: Tick) : ContractEvent()
    data class Delivered(override val contract: ContractId, val tick: Tick, val quantity: Quantity, val value: Money) : ContractEvent()
    data class Missed(override val contract: ContractId, val tick: Tick, val reason: MissReason) : ContractEvent()
    data class Fulfilled(override val contract: ContractId, val tick: Tick) : ContractEvent()
    data class Breached(override val contract: ContractId, val tick: Tick, val at: CompanyId) : ContractEvent()
    data class Cancelled(override val contract: ContractId, val tick: Tick, val by: CompanyId) : ContractEvent()
}

enum class MissReason { SELLER_SHORT, BUYER_FULL, BUYER_BROKE, FACILITY_GONE }
