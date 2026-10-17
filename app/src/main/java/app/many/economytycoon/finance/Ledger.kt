package app.many.economytycoon.finance

import app.many.economytycoon.core.AccountId
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Hashable
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.TransactionId

/** One signed line of a transaction. Positive is a debit, negative a credit. */
data class Entry(val account: AccountRef, val amount: Money)

/** A request to post, before an id is assigned. */
data class TransactionDraft(
    val key: String,
    val tick: Tick,
    val reason: ReasonCode,
    val entries: List<Entry>,
    val memo: String = "",
)

data class Transaction(
    val id: TransactionId,
    val key: String,
    val tick: Tick,
    val reason: ReasonCode,
    val entries: List<Entry>,
    val memo: String,
)

/** Saved form of one account. */
class AccountImage(val ref: AccountRef, val id: AccountId, val anchors: List<Pair<Long, Long>>, val balance: Long)

/** Saved form of a ledger; see [Ledger.image]. */
class LedgerImage(val accounts: List<AccountImage>, val tail: List<Transaction>, val lastTick: Long)

/** A balance snapshot point: the running balance after a posting. */
internal class Posting(val tick: Long, val amount: Long, val running: Long)

/**
 * Double-entry ledger. Posting is the only way to change a balance, every transaction sums to zero,
 * and a key can be posted only once so replays and retries are harmless.
 */
class Ledger(private val ids: IdAllocators) : Hashable {
    private val accountIds = java.util.TreeMap<AccountRef, AccountId>()
    private val refsById = java.util.TreeMap<AccountId, AccountRef>()
    private val balances = java.util.TreeMap<AccountRef, Long>()
    private val postings = java.util.TreeMap<AccountRef, ArrayList<Posting>>()
    private val journal = ArrayList<Transaction>()
    private val keys = HashSet<String>()
    private var lastTick = Long.MIN_VALUE

    val transactionCount: Int get() = journal.size

    /** Returns the account id for [ref], creating the account if needed. */
    fun open(ref: AccountRef): AccountId = accountIds.getOrPut(ref) {
        val id = ids.account()
        refsById[id] = ref
        id
    }

    fun refOf(id: AccountId): AccountRef? = refsById[id]
    fun exists(ref: AccountRef) = accountIds.containsKey(ref)
    fun accounts(): Set<AccountRef> = accountIds.keys

    fun accountsOf(owner: LedgerOwner): List<AccountRef> =
        accountIds.keys.filter { it.owner == owner }

    fun owners(): List<LedgerOwner> = accountIds.keys.map { it.owner }.distinct()

    /** Signed balance (debit positive). */
    fun balance(ref: AccountRef): Money = Money(balances[ref] ?: 0L)

    fun balance(owner: LedgerOwner, chart: Chart) = balance(AccountRef(owner, chart))

    /** Balance in the account's natural direction: positive means more of the thing for display. */
    fun natural(ref: AccountRef): Money {
        val b = balance(ref)
        return if (ref.chart.naturalSign > 0) b else -b
    }

    fun cash(owner: LedgerOwner) = balance(owner, Chart.CASH)

    /** Signed balance as of the end of [tick], found by binary search over postings. */
    fun balanceAt(ref: AccountRef, tick: Tick): Money {
        val list = postings[ref] ?: return Money.ZERO
        var lo = 0
        var hi = list.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].tick <= tick.value) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return if (found < 0) Money.ZERO else Money(list[found].running)
    }

    /** Net signed activity in (fromExclusive, toInclusive]. */
    fun activity(ref: AccountRef, fromExclusive: Tick, toInclusive: Tick): Money =
        balanceAt(ref, toInclusive) - balanceAt(ref, fromExclusive)

    fun hasKey(key: String) = key in keys

    fun transactions(): List<Transaction> = journal

    fun transactionsFor(owner: LedgerOwner, from: Tick, to: Tick): List<Transaction> =
        journal.filter { it.tick >= from && it.tick <= to && it.entries.any { e -> e.account.owner == owner } }

    /** Validates and posts atomically. A rejected draft changes nothing. */
    fun post(draft: TransactionDraft): Outcome<Transaction> {
        if (draft.key in keys) return Outcome.reject(Rules.DUPLICATE, "transaction ${draft.key} already posted")
        if (draft.entries.size < 2) return Outcome.reject(Rules.INVALID_ARGUMENT, "transaction needs at least two entries")
        if (draft.tick.value < lastTick) return Outcome.reject(Rules.INVALID_ARGUMENT, "posting is behind the ledger clock")
        var sum = 0L
        try {
            for (e in draft.entries) sum = Math.addExact(sum, e.amount.raw)
        } catch (ex: ArithmeticException) {
            return Outcome.reject(Rules.INVALID_ARGUMENT, "entry overflow")
        }
        if (sum != 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "entries sum to $sum, expected zero")
        if (draft.entries.all { it.amount.isZero }) return Outcome.reject(Rules.INVALID_ARGUMENT, "all entries are zero")
        for (e in draft.entries) open(e.account)
        // Check overflow of resulting balances before mutating anything.
        val pending = java.util.TreeMap<AccountRef, Long>()
        try {
            for (e in draft.entries) {
                val base = pending[e.account] ?: (balances[e.account] ?: 0L)
                pending[e.account] = Math.addExact(base, e.amount.raw)
            }
        } catch (ex: ArithmeticException) {
            return Outcome.reject(Rules.INVALID_ARGUMENT, "balance overflow")
        }
        val tx = Transaction(ids.transaction(), draft.key, draft.tick, draft.reason, draft.entries.toList(), draft.memo)
        for ((ref, bal) in pending) balances[ref] = bal
        for (e in draft.entries) {
            val list = postings.getOrPut(e.account) { ArrayList() }
            val running = (list.lastOrNull()?.running ?: 0L) + e.amount.raw
            list.add(Posting(draft.tick.value, e.amount.raw, running))
        }
        keys.add(draft.key)
        journal.add(tx)
        lastTick = draft.tick.value
        return Outcome.ok(tx)
    }

    /** Sum of every balance in the ledger. Zero by construction; checked by invariants. */
    fun trialBalance(): Long {
        var total = 0L
        for (v in balances.values) total = Math.addExact(total, v)
        return total
    }

    /** Total cash held by every owner of the given kind. */
    fun cashHeldBy(kind: OwnerKind): Money {
        var total = 0L
        for ((ref, v) in balances) if (ref.owner.kind == kind && ref.chart == Chart.CASH) total += v
        return Money(total)
    }

    /** All cash in circulation, which only MINT transactions may change. */
    fun moneySupply(): Money {
        var total = 0L
        for ((ref, v) in balances) if (ref.chart == Chart.CASH && ref.owner != LedgerOwner.SYSTEM) total += v
        return Money(total)
    }

    /** Drops posting history before [tick] while keeping balances exact. */
    fun compact(before: Tick) {
        for (list in postings.values) {
            var keep = 0
            while (keep < list.size && list[keep].tick < before.value) keep++
            if (keep > 1) {
                val anchor = list[keep - 1]
                val tail = ArrayList<Posting>(list.size - keep + 1)
                tail.add(Posting(anchor.tick, anchor.running, anchor.running))
                for (i in keep until list.size) tail.add(list[i])
                list.clear()
                list.addAll(tail)
            }
        }
    }

    /**
     * Compact image for saving: per-account balances at the given anchor ticks plus the journal tail from
     * [tailFrom]. Older history is dropped, so statements are exact only for periods starting at an anchor.
     */
    fun image(anchors: List<Tick>, tailFrom: Tick): LedgerImage {
        val sorted = anchors.map { it.value }.distinct().sorted()
        val accounts = accountIds.map { (ref, id) ->
            val points = ArrayList<Pair<Long, Long>>()
            for (t in sorted) points.add(t to balanceAt(ref, Tick(t)).raw)
            AccountImage(ref, id, points, balances[ref] ?: 0L)
        }
        val tail = journal.filter { it.tick >= tailFrom }
        return LedgerImage(accounts, tail, lastTick)
    }

    /** Tick of the latest posting, or a very low value for a ledger nothing has been posted to. */
    fun latestTick(): Long = lastTick

    /**
     * Image that keeps every balance change from [historyFrom] on, so statements over any period inside that
     * window are exact after a reload. Earlier history is folded into one opening balance per account.
     */
    fun windowImage(historyFrom: Tick, tailFrom: Tick): LedgerImage {
        val start = historyFrom.value.coerceAtLeast(0L)
        val accounts = accountIds.map { (ref, id) ->
            val points = ArrayList<Pair<Long, Long>>()
            if (start > 0L) points.add((start - 1) to balanceAt(ref, Tick(start - 1)).raw)
            for (p in postings[ref].orEmpty()) {
                if (p.tick < start) continue
                if (points.isNotEmpty() && points.last().first == p.tick) points[points.size - 1] = p.tick to p.running
                else points.add(p.tick to p.running)
            }
            AccountImage(ref, id, points, balances[ref] ?: 0L)
        }
        return LedgerImage(accounts, journal.filter { it.tick >= tailFrom }, lastTick)
    }

    /** Rebuilds a ledger from an image. Allocators must be restored by the caller afterwards. */
    fun restore(image: LedgerImage) {
        accountIds.clear(); refsById.clear(); balances.clear(); postings.clear(); journal.clear(); keys.clear()
        for (a in image.accounts) {
            accountIds[a.ref] = a.id
            refsById[a.id] = a.ref
            balances[a.ref] = a.balance
            val list = ArrayList<Posting>()
            var prev = 0L
            for ((tick, bal) in a.anchors) {
                if (bal != prev || list.isEmpty()) { list.add(Posting(tick, bal - prev, bal)); prev = bal }
            }
            if (prev != a.balance) list.add(Posting(image.lastTick.coerceAtLeast(0L), a.balance - prev, a.balance))
            postings[a.ref] = list
        }
        for (t in image.tail) { journal.add(t); keys.add(t.key) }
        lastTick = image.lastTick
    }

    override fun hashInto(hasher: CanonicalHasher) {
        hasher.section("ledger")
        hasher.putInt(balances.size)
        for ((ref, v) in balances) {
            hasher.putLong(ref.owner.kind.ordinal.toLong()).putLong(ref.owner.id).putInt(ref.chart.code).putLong(v)
        }
    }

    /** Raw restore used by persistence: replays a journal in order. */
    fun replay(transactions: List<Transaction>) {
        for (t in transactions) {
            val r = post(TransactionDraft(t.key, t.tick, t.reason, t.entries, t.memo))
            check(r.isOk) { "replay failed for ${t.key}: ${r.rejectionOrNull()}" }
        }
    }
}
