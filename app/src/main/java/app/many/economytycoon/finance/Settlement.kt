package app.many.economytycoon.finance

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick

/** Order in which payments are made when cash is short. Lower ordinal is paid first. */
enum class SettlementPriority { STATUTORY, SECURED_DEBT, WAGES, SUPPLIERS, OTHER }

/** One slice of a payment: how it hits the payer's and payee's books. */
data class PaymentLine(val payerDebit: Chart, val payeeCredit: Chart, val amount: Money) {
    init { require(amount.raw > 0) { "payment line must be positive" } }
}

/**
 * A request to move money from one owner to another. Engines emit intents; only the settlement
 * engine turns them into postings.
 */
data class SettlementIntent(
    val key: String,
    val payer: LedgerOwner,
    val payee: LedgerOwner,
    val priority: SettlementPriority,
    val reason: ReasonCode,
    val lines: List<PaymentLine>,
    /** When true an unpaid intent becomes accounts payable/receivable. Otherwise it is only reported. */
    val deferrable: Boolean = true,
    val memo: String = "",
) {
    init { require(lines.isNotEmpty()) { "intent $key has no lines" } }

    val total: Money get() = Money.sum(lines.map { it.amount })

    companion object {
        fun simple(
            key: String, payer: LedgerOwner, payee: LedgerOwner, amount: Money, priority: SettlementPriority,
            reason: ReasonCode, payerDebit: Chart, payeeCredit: Chart, deferrable: Boolean = true, memo: String = "",
        ) = SettlementIntent(key, payer, payee, priority, reason, listOf(PaymentLine(payerDebit, payeeCredit, amount)), deferrable, memo)
    }
}

enum class SettlementStatus { PAID, DEFERRED, UNPAID, DUPLICATE, INVALID }

data class SettlementResult(val intent: SettlementIntent, val status: SettlementStatus, val paid: Money)

/** An obligation that could not be paid in cash and now sits on the books as a payable. */
data class Arrear(
    val key: String,
    val payer: LedgerOwner,
    val payee: LedgerOwner,
    val priority: SettlementPriority,
    val reason: ReasonCode,
    val amount: Money,
    val since: Tick,
)

/**
 * Settles intents in priority order against available cash. Each intent is paid in full or not
 * at all; unpaid deferrable intents become arrears that are retried first on later ticks.
 */
class SettlementEngine(private val ledger: Ledger) {
    private val arrears = java.util.TreeMap<String, Arrear>()

    fun arrears(): List<Arrear> = arrears.values.sortedWith(compareBy({ it.priority }, { it.since }, { it.key }))
    fun arrearsOf(owner: LedgerOwner): List<Arrear> = arrears().filter { it.payer == owner }
    fun totalArrears(owner: LedgerOwner): Money = Money.sum(arrearsOf(owner).map { it.amount })
    fun restoreArrears(list: List<Arrear>) { arrears.clear(); for (a in list) arrears[a.key] = a }

    fun settle(tick: Tick, intents: List<SettlementIntent>): List<SettlementResult> {
        val results = ArrayList<SettlementResult>(intents.size)
        // Pay existing arrears before new obligations of the same priority class.
        val payOrder = arrears().toMutableList()
        val retried = payOrder.map { retry(tick, it) }
        results.addAll(retried)
        val ordered = intents.sortedWith(compareBy({ it.priority }, { it.payer }, { it.key }))
        for (intent in ordered) results.add(settleOne(tick, intent))
        return results
    }

    private fun retry(tick: Tick, a: Arrear): SettlementResult {
        val intent = SettlementIntent.simple(a.key, a.payer, a.payee, a.amount, a.priority, a.reason, Chart.PAYABLES, Chart.RECEIVABLES)
        val cash = ledger.cash(a.payer)
        if (cash < a.amount) return SettlementResult(intent, SettlementStatus.UNPAID, Money.ZERO)
        val tx = TransactionDraft(
            "arrears:${a.key}:${tick.value}", tick, ReasonCode.ARREARS,
            listOf(
                Entry(AccountRef(a.payer, Chart.PAYABLES), a.amount),
                Entry(AccountRef(a.payer, Chart.CASH), -a.amount),
                Entry(AccountRef(a.payee, Chart.CASH), a.amount),
                Entry(AccountRef(a.payee, Chart.RECEIVABLES), -a.amount),
            ),
            "arrears cleared",
        )
        return when (ledger.post(tx)) {
            is Outcome.Ok -> { arrears.remove(a.key); SettlementResult(intent, SettlementStatus.PAID, a.amount) }
            is Outcome.Rejected -> SettlementResult(intent, SettlementStatus.INVALID, Money.ZERO)
        }
    }

    private fun settleOne(tick: Tick, intent: SettlementIntent): SettlementResult {
        if (ledger.hasKey(intent.key) || arrears.containsKey(intent.key)) {
            return SettlementResult(intent, SettlementStatus.DUPLICATE, Money.ZERO)
        }
        val total = intent.total
        if (intent.payer == intent.payee) return SettlementResult(intent, SettlementStatus.INVALID, Money.ZERO)
        val cash = ledger.cash(intent.payer)
        if (cash >= total) {
            val entries = ArrayList<Entry>()
            for (line in intent.lines) {
                entries.add(Entry(AccountRef(intent.payer, line.payerDebit), line.amount))
                entries.add(Entry(AccountRef(intent.payee, line.payeeCredit), -line.amount))
            }
            entries.add(Entry(AccountRef(intent.payer, Chart.CASH), -total))
            entries.add(Entry(AccountRef(intent.payee, Chart.CASH), total))
            val r = ledger.post(TransactionDraft(intent.key, tick, intent.reason, mergeEntries(entries), intent.memo))
            return if (r.isOk) SettlementResult(intent, SettlementStatus.PAID, total)
            else SettlementResult(intent, SettlementStatus.INVALID, Money.ZERO)
        }
        if (!intent.deferrable) return SettlementResult(intent, SettlementStatus.UNPAID, Money.ZERO)
        // Accrue: expense and revenue are recognised, cash settles later.
        val entries = ArrayList<Entry>()
        for (line in intent.lines) {
            entries.add(Entry(AccountRef(intent.payer, line.payerDebit), line.amount))
            entries.add(Entry(AccountRef(intent.payee, line.payeeCredit), -line.amount))
        }
        entries.add(Entry(AccountRef(intent.payer, Chart.PAYABLES), -total))
        entries.add(Entry(AccountRef(intent.payee, Chart.RECEIVABLES), total))
        val r = ledger.post(TransactionDraft(intent.key, tick, intent.reason, mergeEntries(entries), intent.memo + " (accrued)"))
        if (!r.isOk) return SettlementResult(intent, SettlementStatus.INVALID, Money.ZERO)
        arrears[intent.key] = Arrear(intent.key, intent.payer, intent.payee, intent.priority, intent.reason, total, tick)
        return SettlementResult(intent, SettlementStatus.DEFERRED, Money.ZERO)
    }

    private fun mergeEntries(entries: List<Entry>): List<Entry> {
        val merged = java.util.TreeMap<AccountRef, Long>()
        for (e in entries) merged[e.account] = (merged[e.account] ?: 0L) + e.amount.raw
        return merged.filter { it.value != 0L }.map { Entry(it.key, Money(it.value)) }
    }
}
