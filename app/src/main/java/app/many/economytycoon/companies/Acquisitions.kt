package app.many.economytycoon.companies

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.FinanceService
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.finance.Valuation

/** Supplies the share of an industry's sales a company holds, in ppm, for antitrust checks. */
fun interface AntitrustView {
    fun marketShare(company: CompanyId, industry: IndustryId): Long
}

object NoAntitrust : AntitrustView {
    override fun marketShare(company: CompanyId, industry: IndustryId) = 0L
}

data class AcquisitionParams(
    /** Earnings multiple used for fair value. */
    val earningsMultiple: Rate = Rate(12_000_000),
    /** Combined share above which a deal in the same industry is blocked. */
    val antitrustThreshold: Long = 400_000L,
    /** Premium over fair value an AI board needs to agree, ppm. */
    val requiredPremiumPpm: Long = 100_000L,
    val offerLifetimeTicks: Int = 15,
    val periodsPerYear: Long = 12,
    /** Days of results looked at when valuing a company's earnings. */
    val earningsWindowTicks: Int = 30,
    /** Fewest days of results that make an earnings value meaningful. */
    val minEarningsSpanTicks: Int = 10,
    val ticksPerYear: Long = 360,
)

enum class OfferState { PENDING, ACCEPTED, REJECTED, EXPIRED, WITHDRAWN }

class Offer(
    val id: Long,
    val acquirer: CompanyId,
    val target: CompanyId,
    /** Shares offered for, taken from the other holders pro rata. */
    val shares: Long,
    val pricePerShare: Money,
    val made: Tick,
    val expires: Tick,
) {
    var state: OfferState = OfferState.PENDING
        internal set

    val total: Money get() = pricePerShare * shares

    internal fun restore(state: OfferState) { this.state = state }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id).putLong(acquirer.value).putLong(target.value).putLong(shares).putMoney(pricePerShare)
            .putLong(made.value).putLong(expires.value).putInt(state.ordinal)
    }
}

class AcquisitionResult(val offer: Offer, val sharesBought: Long, val paid: Money, val controlGained: Boolean)

/**
 * Takeovers through the share register. Offers for AI targets are judged by their board against fair
 * value; player targets decide for themselves. Deals that would create a dominant firm are blocked.
 */
class AcquisitionService(
    private val finance: FinanceService,
    private val companies: CompanyService,
    val params: AcquisitionParams = AcquisitionParams(),
    var antitrust: AntitrustView = NoAntitrust,
) {
    private val offers = java.util.TreeMap<Long, Offer>()
    private var nextId = 1L

    fun offer(id: Long): Offer? = offers[id]
    fun offers(): List<Offer> = offers.values.toList()
    fun pendingFor(target: CompanyId): List<Offer> = offers.values.filter { it.target == target && it.state == OfferState.PENDING }

    /**
     * Fair value of the whole equity: the larger of book value and earnings value. Both come straight from
     * the ledger, so the answer is the same in a game that was just loaded as in one that never stopped.
     */
    fun fairValue(target: CompanyId, tick: Tick): Money {
        val c = companies.company(target) ?: return Money.ZERO
        val balance = finance.statements.balance(c.owner, tick)
        val book = Valuation.book(balance)
        val from = Tick((tick.value - params.earningsWindowTicks).coerceAtLeast(0L))
        val span = tick.value - from.value
        var earnings = Money.ZERO
        if (span >= params.minEarningsSpanTicks) {
            val income = finance.statements.income(c.owner, from, tick)
            // One-off gains booked as other income (a sold stake, a written-off debt) say nothing about what the
            // business earns; counting them would let trading in shares push up the price of the shares.
            val recurring = income.netIncome - income.otherIncome
            val annual = Money(mulDiv(recurring.raw, params.ticksPerYear, span))
            if (annual.raw > 0L) earnings = annual.times(params.earningsMultiple)
        }
        return Money.max(Money.max(book, earnings), Money.ZERO)
    }

    fun fairPricePerShare(target: CompanyId, tick: Tick): Money {
        val c = companies.company(target) ?: return Money.ZERO
        val shares = finance.equity.table(c.owner)?.totalOutstanding() ?: 0L
        return Valuation.perShare(fairValue(target, tick), shares)
    }

    /** Sharesholders other than the acquirer, with their holdings, that could sell. */
    private fun sellers(target: Company, acquirer: Company): List<Pair<Holder, Long>> {
        val table = finance.equity.table(target.owner) ?: return emptyList()
        return table.holders().filter { it != acquirer.holder }.map { it to table.heldTotal(it) }.filter { it.second > 0 }
    }

    fun makeOffer(acquirerId: CompanyId, targetId: CompanyId, shares: Long, pricePerShare: Money, tick: Tick): Outcome<Offer> {
        val acquirer = companies.company(acquirerId) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown acquirer")
        val target = companies.company(targetId) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown target")
        if (!acquirer.isActive || acquirer.status != CompanyStatus.ACTIVE) return Outcome.reject(Rules.WRONG_STATE, "acquirer cannot trade")
        if (!target.isActive) return Outcome.reject(Rules.WRONG_STATE, "target is no longer trading")
        if (acquirerId == targetId) return Outcome.reject(Rules.INVALID_ARGUMENT, "a company cannot buy itself")
        if (target.kind == CompanyKind.STATE) return Outcome.reject(Rules.REGULATION, "state enterprises are not for sale")
        if (shares <= 0 || pricePerShare.raw <= 0) return Outcome.reject(Rules.INVALID_ARGUMENT, "offer must be positive")
        val available = sellers(target, acquirer).sumOf { it.second }
        if (shares > available) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "only $available shares are available")
        if (groupsWouldCycle(acquirerId, targetId)) return Outcome.reject(Rules.OWNERSHIP_CYCLE, "the target already owns the acquirer")
        val total = pricePerShare * shares
        if (finance.ledger.cash(acquirer.owner) < total) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "acquirer cannot pay")
        checkAntitrust(acquirer, target)?.let { return Outcome.reject(Rules.ANTITRUST, it) }
        val offer = Offer(nextId++, acquirerId, targetId, shares, pricePerShare, tick, tick + params.offerLifetimeTicks.toLong())
        offers[offer.id] = offer
        return Outcome.ok(offer)
    }

    private fun groupsWouldCycle(acquirer: CompanyId, target: CompanyId): Boolean = companies.groups.wouldCycle(acquirer, target)

    private fun checkAntitrust(acquirer: Company, target: Company): String? {
        for (ind in acquirer.industries.intersect(target.industries)) {
            val combined = antitrust.marketShare(acquirer.id, ind) + antitrust.marketShare(target.id, ind)
            if (combined > params.antitrustThreshold) return "combined share in ${ind.key} would be ${combined / 10_000}%"
        }
        return null
    }

    fun withdraw(acquirer: CompanyId, offerId: Long): Outcome<Unit> {
        val o = offers[offerId] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown offer")
        if (o.acquirer != acquirer) return Outcome.reject(Rules.NOT_OWNER, "not your offer")
        if (o.state != OfferState.PENDING) return Outcome.reject(Rules.WRONG_STATE, "offer already decided")
        o.state = OfferState.WITHDRAWN
        return Outcome.ok(Unit)
    }

    /** The target's decision. For players this is a command; for AI boards [step] decides. */
    fun respond(targetId: CompanyId, offerId: Long, accept: Boolean, tick: Tick): Outcome<AcquisitionResult?> {
        val o = offers[offerId] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown offer")
        if (o.target != targetId) return Outcome.reject(Rules.NOT_OWNER, "offer is for another company")
        if (o.state != OfferState.PENDING) return Outcome.reject(Rules.WRONG_STATE, "offer already decided")
        if (!accept) { o.state = OfferState.REJECTED; return Outcome.ok(null) }
        return execute(o, tick).map { it }
    }

    private fun execute(o: Offer, tick: Tick): Outcome<AcquisitionResult?> {
        val acquirer = companies.company(o.acquirer)
        val target = companies.company(o.target)
        if (acquirer == null || target == null || !acquirer.isActive || !target.isActive) { o.state = OfferState.EXPIRED; return Outcome.reject(Rules.WRONG_STATE, "a party has ceased to trade") }
        val ledger = finance.ledger
        if (ledger.cash(acquirer.owner) < o.total) { o.state = OfferState.EXPIRED; return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "acquirer can no longer pay") }
        checkAntitrust(acquirer, target)?.let { o.state = OfferState.REJECTED; return Outcome.reject(Rules.ANTITRUST, it) }

        val pool = sellers(target, acquirer)
        val weights = LongArray(pool.size) { pool[it].second }
        val takes = app.many.economytycoon.core.splitLargestRemainder(o.shares, weights)
        val entries = java.util.TreeMap<AccountRef, Long>()
        fun add(a: AccountRef, v: Long) { entries[a] = (entries[a] ?: 0L) + v }
        var bought = 0L
        for (i in pool.indices) {
            val n = minOf(takes[i], pool[i].second)
            if (n <= 0) continue
            val money = o.pricePerShare.raw * n
            val payee = payeeFor(pool[i].first)
            add(AccountRef(acquirer.owner, Chart.CASH), -money); add(AccountRef(acquirer.owner, Chart.INVESTMENTS), money)
            add(AccountRef(payee, Chart.CASH), money); add(AccountRef(payee, Chart.REVENUE_OTHER), -money)
            bought += n
        }
        val tx = TransactionDraft("acq:${o.id}", tick, ReasonCode.TRANSFER, entries.filter { it.value != 0L }.map { Entry(it.key, Money(it.value)) }, "share purchase")
        val posted = ledger.post(tx)
        if (posted is Outcome.Rejected) { o.state = OfferState.EXPIRED; return Outcome.Rejected(posted.rejection) }
        val table = finance.equity.table(target.owner)!!
        for (i in pool.indices) {
            val n = minOf(takes[i], pool[i].second)
            if (n <= 0) continue
            moveShares(target, pool[i].first, acquirer.holder, n, table)
        }
        o.state = OfferState.ACCEPTED
        companies.refreshGroups()
        val control = companies.groups.controllerOf(target.id) == acquirer.id
        return Outcome.ok(AcquisitionResult(o, bought, Money(o.pricePerShare.raw * bought), control))
    }

    private fun moveShares(target: Company, from: Holder, to: Holder, count: Long, table: app.many.economytycoon.finance.CapTable) {
        var left = count
        for (cls in table.classes()) {
            if (left <= 0) break
            val have = table.held(from, cls.id)
            val n = minOf(have, left)
            if (n > 0) { finance.equity.transfer(target.owner, cls.id, from, to, n); left -= n }
        }
    }

    private fun payeeFor(holder: Holder): LedgerOwner =
        if (holder.kind == "owner") LedgerOwner(OwnerKind.COMPANY, holder.id) else finance.households

    /** Lets AI boards answer offers and expires stale ones. Returns completed deals. */
    fun step(tick: Tick): List<AcquisitionResult> {
        val done = ArrayList<AcquisitionResult>()
        for (o in offers.values.toList()) {
            if (o.state != OfferState.PENDING) continue
            if (tick >= o.expires) { o.state = OfferState.EXPIRED; continue }
            val target = companies.company(o.target) ?: continue
            if (target.kind == CompanyKind.PLAYER) continue
            val fair = fairPricePerShare(o.target, tick)
            val needed = Money(mulDiv(fair.raw, 1_000_000L + params.requiredPremiumPpm, 1_000_000L))
            if (o.pricePerShare >= needed) {
                val r = execute(o, tick)
                if (r is Outcome.Ok && r.value != null) done += r.value
            }
        }
        return done
    }

    internal fun restoreOffer(o: Offer) { offers[o.id] = o; if (o.id >= nextId) nextId = o.id + 1 }
    internal fun clearAll() { offers.clear(); nextId = 1 }
    internal val nextOfferId: Long get() = nextId

    fun hashInto(h: CanonicalHasher) {
        h.section("acquisitions")
        h.putInt(offers.size)
        for (o in offers.values) o.hashInto(h)
    }
}
