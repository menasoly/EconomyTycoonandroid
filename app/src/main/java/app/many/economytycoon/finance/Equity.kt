package app.many.economytycoon.finance

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.splitLargestRemainder

/** A class of shares with its voting and dividend rights. */
data class ShareClass(
    val id: String,
    val votesPerShare: Int,
    /** Preferred classes are paid first, up to their fixed yield. */
    val preferredYield: Rate? = null,
)

/** Who owns shares: a company, the player, a fund or the public float. */
data class Holder(val kind: String, val id: Long) : Comparable<Holder> {
    override fun compareTo(other: Holder): Int {
        val k = kind.compareTo(other.kind)
        return if (k != 0) k else id.compareTo(other.id)
    }
    override fun toString() = "$kind:$id"

    companion object {
        fun owner(owner: LedgerOwner) = Holder("owner", owner.id)
        val PUBLIC_FLOAT = Holder("float", 0L)
        fun player(id: Long) = Holder("player", id)
    }
}

/** Cap table of one company: shares outstanding by class and holder. */
class CapTable(val issuer: LedgerOwner) {
    private val classes = java.util.TreeMap<String, ShareClass>()
    private val holdings = java.util.TreeMap<String, java.util.TreeMap<Holder, Long>>()
    private val issued = java.util.TreeMap<String, Long>()
    private val treasury = java.util.TreeMap<String, Long>()

    fun classes(): List<ShareClass> = classes.values.toList()

    fun addClass(c: ShareClass) {
        require(c.id !in classes) { "share class ${c.id} exists" }
        classes[c.id] = c
        holdings[c.id] = java.util.TreeMap()
        issued[c.id] = 0L
        treasury[c.id] = 0L
    }

    fun outstanding(classId: String): Long = (issued[classId] ?: 0L) - (treasury[classId] ?: 0L)
    fun totalOutstanding(): Long = classes.keys.sumOf { outstanding(it) }
    fun held(holder: Holder, classId: String): Long = holdings[classId]?.get(holder) ?: 0L
    fun heldTotal(holder: Holder): Long = classes.keys.sumOf { held(holder, it) }

    fun holders(): List<Holder> = holdings.values.flatMap { it.keys }.distinct().sorted()

    /** Votes held, after treasury shares are excluded. */
    fun votes(holder: Holder): Long = classes.values.sumOf { held(holder, it.id) * it.votesPerShare }
    fun totalVotes(): Long = classes.values.sumOf { outstanding(it.id) * it.votesPerShare }

    /** Share of voting power in ppm. */
    fun votingShare(holder: Holder): Rate {
        val total = totalVotes()
        return if (total == 0L) Rate.ZERO else Rate.ratio(votes(holder), total)
    }

    fun ownershipShare(holder: Holder): Rate {
        val total = totalOutstanding()
        return if (total == 0L) Rate.ZERO else Rate.ratio(heldTotal(holder), total)
    }

    fun controller(threshold: Rate = Rate.percent(50)): Holder? =
        holders().firstOrNull { votingShare(it) > threshold }

    internal fun issue(classId: String, to: Holder, shares: Long) {
        val map = holdings[classId] ?: error("unknown class $classId")
        map[to] = (map[to] ?: 0L) + shares
        issued[classId] = (issued[classId] ?: 0L) + shares
    }

    internal fun transfer(classId: String, from: Holder, to: Holder, shares: Long): Boolean {
        val map = holdings[classId] ?: return false
        val have = map[from] ?: 0L
        if (have < shares || shares <= 0) return false
        if (have == shares) map.remove(from) else map[from] = have - shares
        map[to] = (map[to] ?: 0L) + shares
        return true
    }

    internal fun buyBack(classId: String, from: Holder, shares: Long): Boolean {
        val map = holdings[classId] ?: return false
        val have = map[from] ?: 0L
        if (have < shares || shares <= 0) return false
        if (have == shares) map.remove(from) else map[from] = have - shares
        treasury[classId] = (treasury[classId] ?: 0L) + shares
        return true
    }

    internal fun snapshot(): List<Triple<String, Holder, Long>> =
        holdings.flatMap { (c, m) -> m.map { Triple(c, it.key, it.value) } }

    internal fun issuedOf(classId: String) = issued[classId] ?: 0L
    internal fun treasuryOf(classId: String) = treasury[classId] ?: 0L
    internal fun restore(issuedMap: Map<String, Long>, treasuryMap: Map<String, Long>, list: List<Triple<String, Holder, Long>>) {
        for ((k, v) in issuedMap) issued[k] = v
        for ((k, v) in treasuryMap) treasury[k] = v
        for ((c, h, n) in list) holdings.getOrPut(c) { java.util.TreeMap() }[h] = n
    }
}

/** Corporate equity actions: issuance, dividends, buybacks, with the ledger entries they imply. */
class EquityService(private val ledger: Ledger) {
    private val tables = java.util.TreeMap<LedgerOwner, CapTable>()

    fun table(issuer: LedgerOwner): CapTable? = tables[issuer]
    fun tables(): List<CapTable> = tables.values.toList()

    fun incorporate(issuer: LedgerOwner, founder: Holder, shares: Long, paidIn: Money, tick: Tick): Outcome<CapTable> {
        if (tables.containsKey(issuer)) return Outcome.reject(Rules.DUPLICATE, "already incorporated")
        if (shares <= 0) return Outcome.reject(Rules.INVALID_QUANTITY, "shares must be positive")
        val table = CapTable(issuer)
        table.addClass(ShareClass("common", 1))
        table.issue("common", founder, shares)
        tables[issuer] = table
        if (paidIn.isPositive) {
            val r = ledger.post(TransactionDraft(
                "equity:founding:${issuer.id}", tick, ReasonCode.CAPITAL,
                listOf(Entry(AccountRef(issuer, Chart.CASH), paidIn), Entry(AccountRef(issuer, Chart.SHARE_CAPITAL), -paidIn)),
                "founding capital",
            ))
            if (r is Outcome.Rejected) return Outcome.Rejected(r.rejection)
        }
        return Outcome.ok(table)
    }

    /** Sells new shares to [buyer] at [pricePerShare]; the buyer's cash goes to the issuer's capital. */
    fun issue(issuer: LedgerOwner, classId: String, buyer: Holder, buyerCash: LedgerOwner?, shares: Long, pricePerShare: Money, tick: Tick): Outcome<Money> {
        val table = tables[issuer] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no cap table")
        if (shares <= 0) return Outcome.reject(Rules.INVALID_QUANTITY, "shares must be positive")
        if (table.classes().none { it.id == classId }) return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such class")
        val proceeds = pricePerShare * shares
        if (buyerCash != null) {
            if (ledger.cash(buyerCash) < proceeds) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "buyer cannot pay")
            val r = ledger.post(TransactionDraft(
                "equity:issue:${issuer.id}:${tick.value}:${buyer}:$shares", tick, ReasonCode.ISSUANCE,
                listOf(
                    Entry(AccountRef(buyerCash, Chart.CASH), -proceeds),
                    Entry(AccountRef(buyerCash, Chart.INVESTMENTS), proceeds),
                    Entry(AccountRef(issuer, Chart.CASH), proceeds),
                    Entry(AccountRef(issuer, Chart.SHARE_CAPITAL), -proceeds),
                ),
                "share issuance",
            ))
            if (r is Outcome.Rejected) return Outcome.Rejected(r.rejection)
        }
        table.issue(classId, buyer, shares)
        return Outcome.ok(proceeds)
    }

    fun transfer(issuer: LedgerOwner, classId: String, from: Holder, to: Holder, shares: Long): Outcome<Unit> {
        val table = tables[issuer] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no cap table")
        return if (table.transfer(classId, from, to, shares)) Outcome.ok(Unit)
        else Outcome.reject(Rules.INSUFFICIENT_STOCK, "holder lacks shares")
    }

    fun buyBack(issuer: LedgerOwner, classId: String, from: Holder, fromCash: LedgerOwner?, shares: Long, pricePerShare: Money, tick: Tick): Outcome<Money> {
        val table = tables[issuer] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no cap table")
        if (table.held(from, classId) < shares || shares <= 0) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "holder lacks shares")
        val cost = pricePerShare * shares
        if (ledger.cash(issuer) < cost) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "issuer cannot pay")
        if (fromCash != null) {
            val r = ledger.post(TransactionDraft(
                "equity:buyback:${issuer.id}:${tick.value}:${from}:$shares", tick, ReasonCode.BUYBACK,
                listOf(
                    Entry(AccountRef(issuer, Chart.TREASURY_SHARES), cost),
                    Entry(AccountRef(issuer, Chart.CASH), -cost),
                    Entry(AccountRef(fromCash, Chart.CASH), cost),
                    Entry(AccountRef(fromCash, Chart.INVESTMENTS), -cost),
                ),
                "share buyback",
            ))
            if (r is Outcome.Rejected) return Outcome.Rejected(r.rejection)
        }
        table.buyBack(classId, from, shares)
        return Outcome.ok(cost)
    }

    /**
     * Largest dividend the issuer may pay: no more than its retained earnings (lifetime profit net of past
     * dividends) and no more than cash above a reserve.
     */
    fun maxDividend(issuer: LedgerOwner, statements: StatementBuilder, asOf: Tick, cashReserve: Money): Money {
        val bs = statements.balance(issuer, asOf)
        val distributable = bs.retainedEarnings + bs.earningsToDate - bs.dividends
        val cashRoom = bs.cash - cashReserve
        return Money.max(Money.ZERO, Money.min(distributable, cashRoom))
    }

    /**
     * Declares and pays a dividend to common shareholders pro rata. Holders that map to a ledger owner are
     * credited in cash; everyone else is paid into the household pool.
     */
    fun payDividend(
        issuer: LedgerOwner, total: Money, tick: Tick, statements: StatementBuilder, cashReserve: Money,
        payee: (Holder) -> LedgerOwner?,
    ): Outcome<Money> {
        val table = tables[issuer] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no cap table")
        if (total.raw <= 0) return Outcome.reject(Rules.INVALID_ARGUMENT, "dividend must be positive")
        val cap = maxDividend(issuer, statements, Tick(tick.value - 1), cashReserve)
        if (total > cap) return Outcome.reject(Rules.LIMIT_EXCEEDED, "dividend exceeds distributable funds")
        val shareholders = table.holders()
        if (shareholders.isEmpty()) return Outcome.reject(Rules.WRONG_STATE, "no shareholders")
        var remaining = total
        val perHolder = java.util.TreeMap<Holder, Long>()
        val commonClasses = table.classes().filter { it.preferredYield == null }
        val weights = LongArray(shareholders.size) { idx -> commonClasses.sumOf { table.held(shareholders[idx], it.id) } }
        if (weights.all { it == 0L }) return Outcome.reject(Rules.WRONG_STATE, "no common shares outstanding")
        val parts = splitLargestRemainder(remaining.raw, weights)
        for (i in shareholders.indices) perHolder[shareholders[i]] = parts[i]
        val entries = ArrayList<Entry>()
        entries.add(Entry(AccountRef(issuer, Chart.DIVIDENDS), total))
        entries.add(Entry(AccountRef(issuer, Chart.CASH), -total))
        var distributed = 0L
        for ((holder, amt) in perHolder) {
            if (amt == 0L) continue
            val owner = payee(holder) ?: LedgerOwner.households(0L)
            entries.add(Entry(AccountRef(owner, Chart.CASH), Money(amt)))
            entries.add(Entry(AccountRef(owner, Chart.REVENUE_OTHER), Money(-amt)))
            distributed += amt
        }
        remaining -= Money(distributed)
        check(remaining.isZero) { "dividend split lost money" }
        val merged = java.util.TreeMap<AccountRef, Long>()
        for (e in entries) merged[e.account] = (merged[e.account] ?: 0L) + e.amount.raw
        val r = ledger.post(TransactionDraft(
            "equity:dividend:${issuer.id}:${tick.value}", tick, ReasonCode.DIVIDEND,
            merged.filter { it.value != 0L }.map { Entry(it.key, Money(it.value)) }, "dividend",
        ))
        if (r is Outcome.Rejected) return Outcome.Rejected(r.rejection)
        return Outcome.ok(total)
    }

    internal fun restore(list: List<CapTable>) { tables.clear(); for (t in list) tables[t.issuer] = t }
}

/** Deterministic valuation of a company from its statements. */
object Valuation {
    fun book(bs: BalanceSheet): Money = bs.totalEquity

    /** Earnings multiple on annualised net income; falls back to book value for loss makers. */
    fun earningsMultiple(income: IncomeStatement, periodsPerYear: Long, multiple: Rate, bs: BalanceSheet): Money {
        val annual = income.netIncome * periodsPerYear
        return if (annual.raw > 0) annual.times(multiple) else Money.max(Money.ZERO, bs.totalEquity)
    }

    /**
     * Discounted cash flow with a constant annual growth rate over [years] and a terminal value using
     * a perpetuity growth. All arithmetic is in fixed point.
     */
    fun discountedCashFlow(annualFreeCashFlow: Money, growth: Rate, discount: Rate, terminalGrowth: Rate, years: Int, netDebt: Money): Money {
        require(discount > terminalGrowth) { "discount rate must exceed terminal growth" }
        var cash = annualFreeCashFlow
        var total = Money.ZERO
        var discountFactor = Rate.ONE.ppm
        val step = Rate.ONE.ppm + discount.ppm
        for (y in 1..years) {
            cash = cash + cash.times(growth)
            discountFactor = app.many.economytycoon.core.mulDiv(discountFactor, Rate.ONE_PPM, step)
            total += Money(app.many.economytycoon.core.mulDiv(cash.raw, discountFactor, Rate.ONE_PPM))
        }
        val terminalCash = cash + cash.times(terminalGrowth)
        val terminal = Money(app.many.economytycoon.core.mulDiv(terminalCash.raw, Rate.ONE_PPM, discount.ppm - terminalGrowth.ppm))
        total += Money(app.many.economytycoon.core.mulDiv(terminal.raw, discountFactor, Rate.ONE_PPM))
        return total - netDebt
    }

    fun perShare(equityValue: Money, shares: Long): Money =
        if (shares <= 0) Money.ZERO else Money(equityValue.raw / shares)
}
