package app.many.economytycoon.tenders

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.TenderId
import app.many.economytycoon.core.Tick

/**
 * The register of public tenders and the rules for bidding. Moving goods and money is left to the tender
 * runner; this class decides who may bid, who wins and when a tender is over.
 */
class TenderService(private val ids: IdAllocators, val params: TenderParams = TenderParams()) {
    private val tenders = java.util.TreeMap<TenderId, Tender>()
    private val failures = java.util.TreeMap<CompanyId, MutableList<Long>>()
    var bidSequence: Long = 0L
        internal set

    fun all(): List<Tender> = tenders.values.toList()
    fun get(id: TenderId): Tender? = tenders[id]
    fun open(): List<Tender> = tenders.values.filter { it.isOpen }
    fun openIn(country: CountryId): List<Tender> = open().filter { it.country == country }
    fun awarded(): List<Tender> = tenders.values.filter { it.isAwarded }
    fun awardedTo(company: CompanyId): List<Tender> = awarded().filter { it.winner == company }
    fun biddingBy(company: CompanyId): List<Tender> = open().filter { it.bidOf(company) != null }
    fun history(): List<Tender> = tenders.values.filter { !it.isOpen && !it.isAwarded }
    fun wonBy(company: CompanyId): List<Tender> = tenders.values.filter { it.winner == company }

    /** Money the government has promised across tenders still running. */
    fun committed(country: CountryId): Money =
        tenders.values.filter { it.country == country }.fold(Money.ZERO) { a, t ->
            when (t.status) {
                TenderStatus.OPEN -> a + t.ceilingValue
                TenderStatus.AWARDED -> a + t.awardedValue
                else -> a
            }
        }

    fun failuresOf(company: CompanyId, tick: Tick): Int =
        failures[company]?.count { tick.value - it < params.failureMemoryDays } ?: 0

    fun isBarred(company: CompanyId, tick: Tick): Boolean = failuresOf(company, tick) >= params.barAfterFailures

    /** Checks the shape of a tender before it is issued. */
    fun checkNew(quantity: Quantity, ceiling: Money): Rejection? = when {
        quantity.raw <= 0L -> Rejection(Rules.INVALID_QUANTITY, "a tender must ask for something")
        ceiling.raw <= 0L -> Rejection(Rules.INVALID_PRICE, "a tender needs a price ceiling")
        else -> null
    }

    fun issue(country: CountryId, good: GoodId, quantity: Quantity, ceiling: Money, tick: Tick): Outcome<Tender> {
        checkNew(quantity, ceiling)?.let { return Outcome.Rejected(it) }
        if (openIn(country).size >= params.maxOpenPerCountry) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many open tenders")
        val closes = Tick(tick.value + params.biddingDays)
        val t = Tender(ids.tender(), country, good, quantity, ceiling, tick, closes, Tick(closes.value + params.deliveryDays))
        tenders[t.id] = t
        return Outcome.ok(t)
    }

    /** Places or replaces a bid. The caller has already checked the facility. */
    fun bid(id: TenderId, company: CompanyId, facility: FacilityId, unitPrice: Money, floor: Money, tick: Tick): Outcome<Bid> {
        val t = tenders[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown tender")
        if (!t.isOpen) return Outcome.reject(Rules.WRONG_STATE, "bidding on that tender has closed")
        if (tick >= t.closesAt) return Outcome.reject(Rules.WRONG_STATE, "bidding on that tender has closed")
        if (isBarred(company, tick)) return Outcome.reject(Rules.REGULATION, "after repeated failures to deliver you may not bid for now")
        if (unitPrice.raw <= 0L) return Outcome.reject(Rules.INVALID_PRICE, "the price must be above zero")
        if (unitPrice > t.ceiling) return Outcome.reject(Rules.INVALID_PRICE, "the government will pay at most ${t.ceiling} a unit")
        if (unitPrice < floor) return Outcome.reject(Rules.INVALID_PRICE, "bids below $floor a unit are not accepted")
        if (t.bidOf(company) == null && biddingBy(company).size >= params.maxBidsPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many bids in play")
        val b = Bid(company, facility, unitPrice, tick, bidSequence++)
        t.bidMap[company] = b
        return Outcome.ok(b)
    }

    fun withdraw(id: TenderId, company: CompanyId): Outcome<Unit> {
        val t = tenders[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown tender")
        if (!t.isOpen) return Outcome.reject(Rules.WRONG_STATE, "bidding on that tender has closed")
        if (t.bidMap.remove(company) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "you have no bid on that tender")
        return Outcome.ok(Unit)
    }

    /** Closes bidding on every tender whose time is up. The lowest price wins; ties go to whoever bid first. */
    fun closeBidding(tick: Tick, eligible: (Bid) -> Boolean): List<TenderEvent> {
        val out = ArrayList<TenderEvent>()
        for (t in tenders.values) {
            if (!t.isOpen || tick < t.closesAt) continue
            val winner = t.bids.firstOrNull(eligible)
            if (winner == null) {
                t.status = TenderStatus.NO_BIDS
                t.endedAt = tick
                out += TenderEvent.NoBids(t.id, tick)
            } else {
                t.status = TenderStatus.AWARDED
                t.winner = winner.company
                t.winnerFacility = winner.facility
                t.awardedPrice = winner.unitPrice
                out += TenderEvent.Awarded(t.id, winner.company, winner.unitPrice, tick)
            }
        }
        return out
    }

    fun delivered(t: Tender, tick: Tick, paid: Money): TenderEvent {
        t.status = TenderStatus.DELIVERED
        t.endedAt = tick
        return TenderEvent.Delivered(t.id, t.winner!!, paid, tick)
    }

    fun failed(t: Tender, tick: Tick, penalty: Money): TenderEvent {
        t.status = TenderStatus.FAILED
        t.endedAt = tick
        t.penalty = penalty
        failures.getOrPut(t.winner!!) { ArrayList() }.add(tick.value)
        return TenderEvent.Failed(t.id, t.winner!!, penalty, tick)
    }

    /** Forgets old finished tenders and old failures. */
    fun prune(tick: Tick) {
        val cutoff = tick.value - params.keepClosedDays
        tenders.values.removeAll { !it.isOpen && !it.isAwarded && (it.endedAt?.value ?: 0L) < cutoff }
        for (list in failures.values) list.removeAll { tick.value - it >= params.failureMemoryDays }
        failures.values.removeAll { it.isEmpty() }
    }

    /** Withdraws a company's bids and abandons awards it can no longer honour. */
    fun endAllOf(company: CompanyId, tick: Tick): List<TenderEvent> {
        val out = ArrayList<TenderEvent>()
        for (t in tenders.values) {
            if (t.isOpen) t.bidMap.remove(company)
            if (t.isAwarded && t.winner == company) out += failed(t, tick, Money.ZERO)
        }
        failures.remove(company)
        return out
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(bidSequence).putInt(tenders.size)
        for (t in tenders.values) t.hashInto(h)
        h.putInt(failures.size)
        for ((c, list) in failures) { h.putLong(c.value).putInt(list.size); for (x in list) h.putLong(x) }
    }

    internal fun restore(t: Tender) { tenders[t.id] = t }
    internal fun restoreFailures(company: CompanyId, ticks: List<Long>) { failures[company] = ticks.toMutableList() }
    internal fun failureLog(): List<Pair<CompanyId, List<Long>>> = failures.map { it.key to it.value.toList() }
    internal fun clearAll() { tenders.clear(); failures.clear(); bidSequence = 0L }
}
