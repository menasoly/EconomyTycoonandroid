package app.many.economytycoon.simulation

import app.many.economytycoon.contracts.ContractEvent
import app.many.economytycoon.contracts.MissReason
import app.many.economytycoon.contracts.SupplyContract
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.production.FacilityStatus

/**
 * Carries out the deliveries that supply contracts call for. A delivery is all or nothing: the seller must
 * have the whole quantity free, the buyer must have room and the money. Whatever goes wrong is counted as a
 * missed delivery against the contract rather than leaving goods or cash half moved.
 */
class ContractRunner(private val e: Economy) {
    private var seq = 0L

    fun run(tick: Tick): List<ContractEvent> {
        val events = ArrayList<ContractEvent>()
        events += e.contracts.expire(tick)
        for (c in e.contracts.due(tick)) events += deliver(c, tick)
        e.contracts.prune(tick)
        return events
    }

    private fun deliver(c: SupplyContract, tick: Tick): List<ContractEvent> {
        val sf = c.sellerFacility?.let { e.production.facility(it) }
        val bf = c.buyerFacility?.let { e.production.facility(it) }
        if (sf == null || sf.owner != c.seller || sf.status == FacilityStatus.DEMOLISHED || bf == null || bf.owner != c.buyer || bf.status == FacilityStatus.DEMOLISHED) return e.contracts.recordMiss(c, tick, MissReason.FACILITY_GONE)
        val good = c.terms.good
        val qty = c.terms.quantity
        val value = c.terms.deliveryValue
        if (sf.inventory.available(good) < qty) {
            val events = e.contracts.recordMiss(c, tick, MissReason.SELLER_SHORT)
            val fine = value.scale(e.contracts.params.shortfallPenaltyPpm, 1_000_000L)
            val paid = penalise(c, c.seller, c.buyer, fine, tick, "late delivery")
            if (paid.raw > 0L) e.contracts.addPenalty(c, paid)
            return events
        }
        if (bf.inventory.room(good) < qty) return e.contracts.recordMiss(c, tick, MissReason.BUYER_FULL)
        val buyerOwner = LedgerOwner.company(c.buyer)
        if (e.finance.ledger.cash(buyerOwner) < value) return e.contracts.recordMiss(c, tick, MissReason.BUYER_BROKE)

        val cost = (sf.inventory.remove(good, qty) as? Outcome.Ok)?.value ?: return e.contracts.recordMiss(c, tick, MissReason.SELLER_SHORT)
        val booked = e.finance.recordSale("contract:${c.id.value}:${c.delivered}", tick, LedgerOwner.company(c.seller), buyerOwner, value, cost)
        if (booked is Outcome.Rejected) {
            sf.inventory.add(good, qty, cost, allowOverflow = true)
            return e.contracts.recordMiss(c, tick, MissReason.BUYER_BROKE)
        }
        bf.inventory.add(good, qty, value, allowOverflow = true)
        return listOf(e.contracts.recordDelivery(c, tick, value))
    }

    /** Moves up to [amount] from one company to another as a fee; returns what was actually paid. */
    fun penalise(c: SupplyContract, from: CompanyId, to: CompanyId, amount: Money, tick: Tick, memo: String): Money {
        if (amount.raw <= 0L) return Money.ZERO
        val payer = LedgerOwner.company(from)
        val pay = Money.min(amount, e.finance.ledger.cash(payer).coerceAtLeast(Money.ZERO))
        if (pay.raw <= 0L) return Money.ZERO
        val payee = LedgerOwner.company(to)
        val posted = e.finance.ledger.post(
            TransactionDraft(
                "contractfee:${c.id.value}:${tick.value}:${seq++}", tick, ReasonCode.FEE,
                listOf(
                    Entry(AccountRef(payer, Chart.OTHER_EXPENSE), pay), Entry(AccountRef(payer, Chart.CASH), -pay),
                    Entry(AccountRef(payee, Chart.CASH), pay), Entry(AccountRef(payee, Chart.REVENUE_OTHER), -pay),
                ),
                memo,
            ),
        )
        return if (posted is Outcome.Ok) pay else Money.ZERO
    }
}
