package app.many.economytycoon.finance

import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.LoanId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick

/** Terms offered by a lender before a loan is drawn. */
data class LoanOffer(
    val principal: Money,
    val annualRate: Rate,
    val termTicks: Int,
    val paymentInterval: Int,
    val type: RepaymentType,
    val collateral: List<Collateral> = emptyList(),
    val covenants: List<Covenant> = emptyList(),
)

data class LoanPolicy(
    /** Extra annual rate while a loan is in penalty after a missed payment. */
    val penaltyRate: Rate = Rate.percent(4),
    val maxMissedPayments: Int = 3,
    /** Fee charged on origination as a share of principal. */
    val originationFee: Rate = Rate.basisPoints(50),
    /** Minimum collateral cover required for secured loans. */
    val minCollateralCover: Rate = Rate.percent(100),
)

data class DebtService(val intents: List<SettlementIntent>, val dueLoans: List<LoanId>)

/** Registry and servicing logic for all loans in the world. */
class LoanBook(private val ledger: Ledger, private val ids: IdAllocators, val policy: LoanPolicy = LoanPolicy()) {
    private val loans = java.util.TreeMap<LoanId, Loan>()

    fun all(): List<Loan> = loans.values.toList()
    fun get(id: LoanId): Loan? = loans[id]
    fun active(): List<Loan> = loans.values.filter { it.status == LoanStatus.ACTIVE }
    fun forBorrower(owner: LedgerOwner): List<Loan> = loans.values.filter { it.borrower == owner }
    fun activeFor(owner: LedgerOwner): List<Loan> = forBorrower(owner).filter { it.status == LoanStatus.ACTIVE }

    /** Everything still owed, including loans in default that have not been written off. */
    fun debtOf(owner: LedgerOwner): Money = Money.sum(forBorrower(owner).filter { it.status != LoanStatus.PAID_OFF }.map { it.outstanding })
    fun unsettled(): List<Loan> = loans.values.filter { it.status != LoanStatus.PAID_OFF }
    fun interestAccruedOf(owner: LedgerOwner): Money = Money.sum(activeFor(owner).map { it.accruedInterest })

    /** Scheduled principal plus interest across the next [horizon] payment dates, ignoring prepayment. */
    fun scheduledServiceCost(owner: LedgerOwner): Money =
        Money.sum(activeFor(owner).map { it.scheduledPayment })

    /** Draws a loan: lender cash to borrower, debt recorded on both books, fee charged. */
    fun originate(tick: Tick, borrower: LedgerOwner, lender: LedgerOwner, offer: LoanOffer): Outcome<Loan> {
        if (offer.principal.raw <= 0) return Outcome.reject(Rules.INVALID_ARGUMENT, "principal must be positive")
        if (offer.termTicks <= 0 || offer.paymentInterval <= 0 || offer.termTicks % offer.paymentInterval != 0) {
            return Outcome.reject(Rules.INVALID_ARGUMENT, "term must be a multiple of the payment interval")
        }
        if (offer.collateral.isNotEmpty()) {
            val needed = offer.principal.times(policy.minCollateralCover)
            if (Money.sum(offer.collateral.map { it.value }) < needed) {
                return Outcome.reject(Rules.CREDIT_LIMIT, "collateral does not cover the loan")
            }
        }
        if (ledger.cash(lender) < offer.principal) {
            return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "lender has insufficient cash")
        }
        val id = ids.loan()
        val longTerm = offer.termTicks > 360
        val debtChart = if (longTerm) Chart.DEBT_LONG else Chart.DEBT_SHORT
        val fee = offer.principal.times(policy.originationFee)
        val entries = ArrayList<Entry>()
        entries.add(Entry(AccountRef(lender, Chart.CASH), -offer.principal))
        entries.add(Entry(AccountRef(lender, Chart.LOANS_RECEIVABLE), offer.principal))
        entries.add(Entry(AccountRef(borrower, Chart.CASH), offer.principal))
        entries.add(Entry(AccountRef(borrower, debtChart), -offer.principal))
        if (fee.isPositive) {
            entries.add(Entry(AccountRef(borrower, Chart.OTHER_EXPENSE), fee))
            entries.add(Entry(AccountRef(borrower, Chart.CASH), -fee))
            entries.add(Entry(AccountRef(lender, Chart.CASH), fee))
            entries.add(Entry(AccountRef(lender, Chart.REVENUE_OTHER), -fee))
        }
        val merged = merge(entries)
        val r = ledger.post(TransactionDraft("loan:draw:${id.value}", tick, ReasonCode.LOAN_DRAW, merged, "loan ${id.value}"))
        if (r is Outcome.Rejected) return r
        val loan = Loan(id, borrower, lender, offer.principal, offer.annualRate, tick, offer.termTicks, offer.paymentInterval,
            offer.type, offer.collateral, offer.covenants)
        loans[id] = loan
        return Outcome.ok(loan)
    }

    /** Accrues one tick of interest on every active loan. Interest is booked when it is paid. */
    fun accrueAll(): Money {
        var total = 0L
        for (l in loans.values) total += l.accrue(policy.penaltyRate).raw
        return Money(total)
    }

    /** Builds payment intents for every loan with a payment due on [tick]. */
    fun dueIntents(tick: Tick): DebtService {
        val intents = ArrayList<SettlementIntent>()
        val due = ArrayList<LoanId>()
        for (l in loans.values) {
            if (!l.isPaymentDue(tick)) continue
            due.add(l.id)
            val interest = l.accruedInterest
            val principal = l.scheduledPrincipal()
            val lines = ArrayList<PaymentLine>()
            val debtChart = if (l.termTicks > 360) Chart.DEBT_LONG else Chart.DEBT_SHORT
            if (principal.isPositive) lines.add(PaymentLine(debtChart, Chart.LOANS_RECEIVABLE, principal))
            if (interest.isPositive) lines.add(PaymentLine(Chart.INTEREST_EXPENSE, Chart.INTEREST_INCOME, interest))
            if (lines.isEmpty()) continue
            val priority = if (l.isSecured) SettlementPriority.SECURED_DEBT else SettlementPriority.SUPPLIERS
            intents.add(SettlementIntent(
                "loan:pay:${l.id.value}:${tick.value}", l.borrower, l.lender, priority, ReasonCode.LOAN_REPAYMENT, lines,
                deferrable = false, memo = "loan ${l.id.value}",
            ))
        }
        return DebtService(intents, due)
    }

    /** Applies settlement outcomes back to loans (paid reduces balance, unpaid records a miss). */
    fun applyResults(tick: Tick, results: List<SettlementResult>) {
        for (r in results) {
            val key = r.intent.key
            if (!key.startsWith("loan:pay:")) continue
            val id = LoanId(key.split(":")[2].toLong())
            val loan = loans[id] ?: continue
            when (r.status) {
                SettlementStatus.PAID -> {
                    val interest = r.intent.lines.firstOrNull { it.payerDebit == Chart.INTEREST_EXPENSE }?.amount ?: Money.ZERO
                    val principal = r.intent.lines.firstOrNull { it.payerDebit != Chart.INTEREST_EXPENSE }?.amount ?: Money.ZERO
                    loan.recordPayment(principal, interest)
                }
                SettlementStatus.UNPAID, SettlementStatus.DEFERRED -> loan.recordMiss(policy.maxMissedPayments)
                else -> Unit
            }
        }
    }

    /** Voluntary early repayment of principal. */
    fun prepay(tick: Tick, id: LoanId, amount: Money): Outcome<Money> {
        val loan = loans[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such loan")
        if (loan.status != LoanStatus.ACTIVE) return Outcome.reject(Rules.WRONG_STATE, "loan is not active")
        if (amount.raw <= 0) return Outcome.reject(Rules.INVALID_ARGUMENT, "amount must be positive")
        val pay = Money.min(amount, loan.outstanding)
        if (ledger.cash(loan.borrower) < pay) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "insufficient cash")
        val debtChart = if (loan.termTicks > 360) Chart.DEBT_LONG else Chart.DEBT_SHORT
        val tx = TransactionDraft(
            "loan:prepay:${id.value}:${tick.value}:${pay.raw}", tick, ReasonCode.LOAN_REPAYMENT,
            merge(listOf(
                Entry(AccountRef(loan.borrower, debtChart), pay),
                Entry(AccountRef(loan.borrower, Chart.CASH), -pay),
                Entry(AccountRef(loan.lender, Chart.CASH), pay),
                Entry(AccountRef(loan.lender, Chart.LOANS_RECEIVABLE), -pay),
            )), "prepayment",
        )
        val r = ledger.post(tx)
        if (r is Outcome.Rejected) return Outcome.Rejected(r.rejection)
        loan.recordPrepayment(pay)
        return Outcome.ok(pay)
    }

    /** Removes the debt of a defaulted loan from the lender's books as a write-off. */
    fun writeOff(tick: Tick, id: LoanId): Outcome<Money> {
        val loan = loans[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such loan")
        if (loan.status != LoanStatus.DEFAULTED) return Outcome.reject(Rules.WRONG_STATE, "loan is not in default")
        val loss = loan.outstanding
        if (loss.isZero) return Outcome.ok(Money.ZERO)
        val debtChart = if (loan.termTicks > 360) Chart.DEBT_LONG else Chart.DEBT_SHORT
        val tx = TransactionDraft(
            "loan:writeoff:${id.value}", tick, ReasonCode.ADJUSTMENT,
            merge(listOf(
                Entry(AccountRef(loan.lender, Chart.OTHER_EXPENSE), loss),
                Entry(AccountRef(loan.lender, Chart.LOANS_RECEIVABLE), -loss),
                Entry(AccountRef(loan.borrower, debtChart), loss),
                Entry(AccountRef(loan.borrower, Chart.REVENUE_OTHER), -loss),
            )), "write-off",
        )
        val r = ledger.post(tx)
        if (r is Outcome.Rejected) return Outcome.Rejected(r.rejection)
        loan.recordPrepayment(loss)
        loan.status = LoanStatus.PAID_OFF
        return Outcome.ok(loss)
    }

    /** Covenants that the borrower currently breaches given the supplied ratios. */
    fun breaches(loan: Loan, cashRatio: Rate, debtToEquity: Rate, interestCoverage: Rate): List<Covenant> =
        loan.covenants.filter {
            when (it) {
                is Covenant.MinCashRatio -> cashRatio < it.minimum
                is Covenant.MaxDebtToEquity -> debtToEquity > it.maximum
                is Covenant.MinInterestCoverage -> interestCoverage < it.minimum
            }
        }

    fun restore(list: List<Loan>) { loans.clear(); for (l in list) loans[l.id] = l }

    private fun merge(entries: List<Entry>): List<Entry> {
        val m = java.util.TreeMap<AccountRef, Long>()
        for (e in entries) m[e.account] = (m[e.account] ?: 0L) + e.amount.raw
        return m.filter { it.value != 0L }.map { Entry(it.key, Money(it.value)) }
    }
}
