package app.many.economytycoon.contracts

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.ContractId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick

/**
 * Keeps the register of supply contracts and enforces how they change state. Moving goods and money is
 * left to the caller (see the contract runner); this class only decides what is allowed.
 */
class ContractService(private val ids: IdAllocators, val params: ContractParams = ContractParams()) {
    private val contracts = java.util.TreeMap<ContractId, SupplyContract>()

    fun all(): List<SupplyContract> = contracts.values.toList()
    fun get(id: ContractId): SupplyContract? = contracts[id]
    fun of(company: CompanyId): List<SupplyContract> = contracts.values.filter { it.involves(company) }
    fun active(): List<SupplyContract> = contracts.values.filter { it.isActive }
    fun activeOf(company: CompanyId): List<SupplyContract> = of(company).filter { it.isActive }
    fun proposalsFor(company: CompanyId): List<SupplyContract> = contracts.values.filter { it.isOpen && it.counterparty == company }
    fun proposalsBy(company: CompanyId): List<SupplyContract> = contracts.values.filter { it.isOpen && it.proposer == company }

    /** Contracts that should deliver on [tick] or earlier, in a fixed order. */
    fun due(tick: Tick): List<SupplyContract> = contracts.values.filter { it.isActive && (it.nextDue ?: Tick.ZERO) <= tick }

    /** Checks the terms on their own, before any company or facility is looked at. */
    fun checkTerms(t: ContractTerms, referenceUnitPrice: Money?): app.many.economytycoon.core.Rejection? {
        if (t.quantity.raw <= 0L) return app.many.economytycoon.core.Rejection(Rules.INVALID_QUANTITY, "each delivery must be more than zero")
        if (t.unitPrice.perUnit.raw <= 0L) return app.many.economytycoon.core.Rejection(Rules.INVALID_PRICE, "the price must be above zero")
        if (t.intervalTicks !in params.minInterval..params.maxInterval) {
            return app.many.economytycoon.core.Rejection(Rules.INVALID_ARGUMENT, "deliveries must be ${params.minInterval} to ${params.maxInterval} days apart")
        }
        if (t.deliveries !in 1..params.maxDeliveries) return app.many.economytycoon.core.Rejection(Rules.INVALID_ARGUMENT, "a contract covers 1 to ${params.maxDeliveries} deliveries")
        if (referenceUnitPrice != null && referenceUnitPrice.raw > 0L) {
            val low = app.many.economytycoon.core.mulDiv(referenceUnitPrice.raw, params.minPriceMultiplePpm, 1_000_000L)
            val high = app.many.economytycoon.core.mulDiv(referenceUnitPrice.raw, params.maxPriceMultiplePpm, 1_000_000L)
            if (t.unitPrice.perUnit.raw !in low..high) return app.many.economytycoon.core.Rejection(Rules.INVALID_PRICE, "the price is too far from what the good is worth")
        }
        return null
    }

    fun propose(
        proposer: CompanyId, counterparty: CompanyId, side: ContractSide, facility: FacilityId, terms: ContractTerms, tick: Tick,
    ): Outcome<SupplyContract> {
        if (proposer == counterparty) return Outcome.reject(Rules.INVALID_ARGUMENT, "a company cannot contract with itself")
        if (proposalsBy(proposer).size >= params.maxOpenProposals) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many proposals are waiting for an answer")
        if (activeOf(proposer).size >= params.maxActivePerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many running contracts")
        val c = SupplyContract(ids.contract(), proposer, counterparty, side, facility, terms, tick)
        contracts[c.id] = c
        return Outcome.ok(c)
    }

    fun accept(id: ContractId, by: CompanyId, facility: FacilityId, tick: Tick): Outcome<SupplyContract> {
        val c = contracts[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown contract")
        if (!c.isOpen) return Outcome.reject(Rules.WRONG_STATE, "that offer is no longer open")
        if (c.counterparty != by) return Outcome.reject(Rules.NOT_OWNER, "the offer is not addressed to you")
        if (activeOf(by).size >= params.maxActivePerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many running contracts")
        c.status = ContractStatus.ACTIVE
        c.counterFacility = facility
        c.startedAt = tick
        c.nextDue = Tick(tick.value + c.terms.intervalTicks)
        return Outcome.ok(c)
    }

    fun decline(id: ContractId, by: CompanyId, tick: Tick): Outcome<SupplyContract> {
        val c = contracts[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown contract")
        if (!c.isOpen) return Outcome.reject(Rules.WRONG_STATE, "that offer is no longer open")
        if (c.counterparty != by) return Outcome.reject(Rules.NOT_OWNER, "the offer is not addressed to you")
        c.status = ContractStatus.DECLINED
        c.endedAt = tick
        return Outcome.ok(c)
    }

    /** The proposer withdraws an open offer, or either side leaves a running contract. */
    fun cancel(id: ContractId, by: CompanyId, tick: Tick): Outcome<SupplyContract> {
        val c = contracts[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown contract")
        if (!c.involves(by)) return Outcome.reject(Rules.NOT_OWNER, "you are not part of that contract")
        when (c.status) {
            ContractStatus.PROPOSED -> if (c.proposer != by) return Outcome.reject(Rules.NOT_OWNER, "only the proposer can withdraw an offer; decline it instead")
            ContractStatus.ACTIVE -> Unit
            else -> return Outcome.reject(Rules.WRONG_STATE, "that contract has already ended")
        }
        c.status = ContractStatus.CANCELLED
        c.endedAt = tick
        return Outcome.ok(c)
    }

    /** Fee the company leaving a running contract owes the other side. */
    fun cancelFee(c: SupplyContract): Money = c.terms.deliveryValue.scale(params.cancelFeePpm, 1_000_000L)

    /** Lets unanswered proposals lapse. */
    fun expire(tick: Tick): List<ContractEvent> {
        val out = ArrayList<ContractEvent>()
        for (c in contracts.values) {
            if (c.isOpen && tick.value - c.proposedAt.value >= params.proposalLifetime) {
                c.status = ContractStatus.EXPIRED
                c.endedAt = tick
                out += ContractEvent.Expired(c.id, tick)
            }
        }
        return out
    }

    // ---- delivery outcomes, reported by the runner ----------------------------------------------

    fun recordDelivery(c: SupplyContract, tick: Tick, value: Money): ContractEvent {
        c.delivered++
        c.strikes = 0
        c.paid += value
        c.nextDue = Tick(tick.value + c.terms.intervalTicks)
        if (c.delivered >= c.terms.deliveries) {
            c.status = ContractStatus.FULFILLED
            c.endedAt = tick
            return ContractEvent.Fulfilled(c.id, tick)
        }
        return ContractEvent.Delivered(c.id, tick, c.terms.quantity, value)
    }

    /** A delivery that did not happen. Returns the miss and, when it was the last straw, the breach. */
    fun recordMiss(c: SupplyContract, tick: Tick, reason: MissReason): List<ContractEvent> {
        c.missed++
        c.strikes++
        c.nextDue = Tick(tick.value + c.terms.intervalTicks)
        val out = ArrayList<ContractEvent>()
        out += ContractEvent.Missed(c.id, tick, reason)
        if (c.strikes >= params.strikesToBreach) {
            c.status = ContractStatus.BREACHED
            c.endedAt = tick
            val at = if (reason == MissReason.SELLER_SHORT || reason == MissReason.FACILITY_GONE) c.seller else c.buyer
            out += ContractEvent.Breached(c.id, tick, at)
        }
        return out
    }

    fun addPenalty(c: SupplyContract, amount: Money) { c.penalties += amount }

    /** Ends everything a company is part of, for when it ceases to exist. */
    fun endAllOf(company: CompanyId, tick: Tick): List<ContractEvent> {
        val out = ArrayList<ContractEvent>()
        for (c in of(company)) {
            if (c.status == ContractStatus.PROPOSED || c.status == ContractStatus.ACTIVE) {
                c.status = ContractStatus.CANCELLED
                c.endedAt = tick
                out += ContractEvent.Cancelled(c.id, tick, company)
            }
        }
        return out
    }

    /** Drops contracts that ended long ago so the register does not grow without bound. */
    fun prune(tick: Tick, keepTicks: Int = 720): Int {
        val old = contracts.values.filter { !it.isOpen && !it.isActive && (it.endedAt?.value ?: 0L) + keepTicks < tick.value }
        for (c in old) contracts.remove(c.id)
        return old.size
    }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(contracts.size)
        for (c in contracts.values) c.hashInto(h)
    }

    internal fun restore(c: SupplyContract) { contracts[c.id] = c }
    internal fun clearAll() = contracts.clear()
}
