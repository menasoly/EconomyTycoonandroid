package app.many.economytycoon.finance

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Hashable
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick

data class FinanceConfig(
    val loanPolicy: LoanPolicy = LoanPolicy(),
    val defaultTax: TaxPolicy = TaxPolicy(),
    val baseRate: Rate = Rate.percent(4),
    val distress: DistressThresholds = DistressThresholds(),
    /** Ticks between depreciation postings, ratings and distress reviews. */
    val closeIntervalTicks: Int = 30,
    /** Ticks between corporate tax assessments. */
    val taxIntervalTicks: Int = 90,
)

/** Cached statement bundle for one company, rebuilt at each close. */
data class StatementSet(
    val tick: Tick,
    val income: IncomeStatement,
    val balance: BalanceSheet,
    val cashFlow: CashFlowStatement,
    val ratios: Ratios,
)

data class CompanyFinance(
    val owner: LedgerOwner,
    val government: LedgerOwner,
    val founded: Tick,
    val tax: TaxAccount,
    val rating: RatingTracker,
    val distress: DistressMonitor,
)

/**
 * Facade over the financial engine. Other modules talk to this class (or to the ledger through it)
 * and never construct postings by hand.
 */
class FinanceService(val ids: IdAllocators, val config: FinanceConfig = FinanceConfig()) : Hashable {
    val ledger = Ledger(ids)
    val settlement = SettlementEngine(ledger)
    val loans = LoanBook(ledger, ids, config.loanPolicy)
    val assets = AssetRegister()
    val equity = EquityService(ledger)
    val statements = StatementBuilder(ledger)

    private val companies = java.util.TreeMap<LedgerOwner, CompanyFinance>()
    private val policies = java.util.TreeMap<LedgerOwner, TaxPolicy>()
    private val latest = java.util.TreeMap<LedgerOwner, StatementSet>()
    private var lastCloseTick = Tick(0)
    private var lastTaxTick = Tick(0)

    /** Policy rate that credit terms are priced from. The macro layer moves it; it starts at the configured value. */
    var baseRate: Rate = config.baseRate

    val bank: LedgerOwner = LedgerOwner.bank()
    val households: LedgerOwner = LedgerOwner.households()

    fun company(owner: LedgerOwner): CompanyFinance? = companies[owner]
    fun companies(): List<CompanyFinance> = companies.values.toList()
    fun latestStatements(owner: LedgerOwner): StatementSet? = latest[owner]
    fun taxPolicy(government: LedgerOwner): TaxPolicy = policies[government] ?: config.defaultTax
    fun setTaxPolicy(government: LedgerOwner, policy: TaxPolicy) { policies[government] = policy }
    fun policyFor(owner: LedgerOwner): TaxPolicy = taxPolicy(companies[owner]?.government ?: defaultGovernment())
    fun defaultGovernment(): LedgerOwner = policies.keys.firstOrNull() ?: LedgerOwner(OwnerKind.GOVERNMENT, 1L)

    // ----- money creation ---------------------------------------------------------------------

    /** Central-bank money creation; the only way (with founding capital) cash supply grows. */
    fun mint(tick: Tick, to: LedgerOwner, amount: Money, key: String): Outcome<Transaction> {
        if (amount.raw <= 0) return Outcome.reject(Rules.INVALID_ARGUMENT, "amount must be positive")
        return ledger.post(TransactionDraft(
            "mint:$key", tick, ReasonCode.MINT,
            listOf(Entry(AccountRef(to, Chart.CASH), amount), Entry(AccountRef(LedgerOwner.SYSTEM, Chart.RETAINED_EARNINGS), -amount)),
            "money creation",
        ))
    }

    // ----- companies --------------------------------------------------------------------------

    fun registerCompany(
        owner: LedgerOwner, government: LedgerOwner, founder: Holder, startingCapital: Money, shares: Long, tick: Tick,
    ): Outcome<CompanyFinance> {
        if (companies.containsKey(owner)) return Outcome.reject(Rules.DUPLICATE, "company already registered")
        val inc = equity.incorporate(owner, founder, shares, startingCapital, tick)
        if (inc is Outcome.Rejected) return Outcome.Rejected(inc.rejection)
        val cf = CompanyFinance(owner, government, tick, TaxAccount(owner), RatingTracker(), DistressMonitor(config.distress))
        companies[owner] = cf
        return Outcome.ok(cf)
    }

    fun removeCompany(owner: LedgerOwner) {
        companies.remove(owner)
        latest.remove(owner)
    }

    // ----- direct bookkeeping helpers ---------------------------------------------------------

    /** Records a sale between two owners already paid in cash. */
    fun recordSale(
        key: String, tick: Tick, seller: LedgerOwner, buyer: LedgerOwner, amount: Money, goodsCost: Money,
        revenueChart: Chart = Chart.REVENUE_SALES, buyerChart: Chart = Chart.INVENTORY,
    ): Outcome<Transaction> {
        if (amount.raw <= 0) return Outcome.reject(Rules.INVALID_PRICE, "sale amount must be positive")
        if (ledger.cash(buyer) < amount) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "buyer cannot pay")
        val e = ArrayList<Entry>()
        e.add(Entry(AccountRef(buyer, buyerChart), amount))
        e.add(Entry(AccountRef(buyer, Chart.CASH), -amount))
        e.add(Entry(AccountRef(seller, Chart.CASH), amount))
        e.add(Entry(AccountRef(seller, revenueChart), -amount))
        if (goodsCost.isPositive) {
            e.add(Entry(AccountRef(seller, Chart.COGS), goodsCost))
            e.add(Entry(AccountRef(seller, Chart.INVENTORY), -goodsCost))
        }
        return ledger.post(TransactionDraft(key, tick, ReasonCode.SALE, merge(e), "sale"))
    }

    /** Moves value into inventory (production output or purchase booked elsewhere). */
    fun adjustInventory(key: String, tick: Tick, owner: LedgerOwner, delta: Money, offset: Chart = Chart.COGS): Outcome<Transaction> {
        if (delta.isZero) return Outcome.reject(Rules.INVALID_ARGUMENT, "no inventory change")
        return ledger.post(TransactionDraft(
            key, tick, ReasonCode.INVENTORY,
            listOf(Entry(AccountRef(owner, Chart.INVENTORY), delta), Entry(AccountRef(owner, offset), -delta)),
            "inventory adjustment",
        ))
    }

    /** Buys a fixed asset from [seller] for cash and registers it for depreciation. */
    fun purchaseAsset(
        key: String, tick: Tick, owner: LedgerOwner, seller: LedgerOwner, cls: AssetClass, label: String, cost: Money,
    ): Outcome<FixedAsset> {
        if (cost.raw <= 0) return Outcome.reject(Rules.INVALID_PRICE, "cost must be positive")
        if (ledger.cash(owner) < cost) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot afford $label")
        val r = ledger.post(TransactionDraft(
            key, tick, ReasonCode.PURCHASE,
            merge(listOf(
                Entry(AccountRef(owner, Chart.FIXED_ASSETS), cost),
                Entry(AccountRef(owner, Chart.CASH), -cost),
                Entry(AccountRef(seller, Chart.CASH), cost),
                Entry(AccountRef(seller, Chart.REVENUE_SALES), -cost),
            )), label,
        ))
        if (r is Outcome.Rejected) return Outcome.Rejected(r.rejection)
        return Outcome.ok(assets.add(owner, cls, label, cost, tick))
    }

    /** Sells or scraps an asset at [proceeds]; the difference to book value is a gain or loss. */
    fun disposeAsset(tick: Tick, id: Long, buyer: LedgerOwner, proceeds: Money): Outcome<Money> {
        val a = assets.get(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such asset")
        if (a.disposed) return Outcome.reject(Rules.WRONG_STATE, "asset already disposed")
        if (proceeds.raw < 0) return Outcome.reject(Rules.INVALID_PRICE, "proceeds cannot be negative")
        if (proceeds.isPositive && ledger.cash(buyer) < proceeds) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "buyer cannot pay")
        val book = a.bookValue
        val gain = proceeds - book
        val e = ArrayList<Entry>()
        e.add(Entry(AccountRef(a.owner, Chart.ACCUMULATED_DEPRECIATION), a.accumulated + a.impairment))
        e.add(Entry(AccountRef(a.owner, Chart.FIXED_ASSETS), -a.cost))
        if (proceeds.isPositive) {
            e.add(Entry(AccountRef(a.owner, Chart.CASH), proceeds))
            e.add(Entry(AccountRef(buyer, Chart.CASH), -proceeds))
            e.add(Entry(AccountRef(buyer, Chart.FIXED_ASSETS), proceeds))
        }
        if (gain.isPositive) e.add(Entry(AccountRef(a.owner, Chart.REVENUE_OTHER), -gain))
        else if (gain.isNegative) e.add(Entry(AccountRef(a.owner, Chart.OTHER_EXPENSE), -gain))
        val r = ledger.post(TransactionDraft("asset:dispose:$id", tick, ReasonCode.ADJUSTMENT, merge(e), "disposal of ${a.label}"))
        if (r is Outcome.Rejected) return Outcome.Rejected(r.rejection)
        assets.dispose(id)
        return Outcome.ok(gain)
    }

    /** Writes down an asset after a shock (fire, obsolescence). Reduces book value, books an expense. */
    fun impairAsset(key: String, tick: Tick, id: Long, amount: Money): Outcome<Money> {
        val a = assets.get(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such asset")
        val applied = Money.min(amount, a.bookValue)
        if (applied.raw <= 0) return Outcome.reject(Rules.INVALID_ARGUMENT, "nothing to impair")
        val r = ledger.post(TransactionDraft(
            key, tick, ReasonCode.ADJUSTMENT,
            listOf(Entry(AccountRef(a.owner, Chart.OTHER_EXPENSE), applied), Entry(AccountRef(a.owner, Chart.ACCUMULATED_DEPRECIATION), -applied)),
            "impairment of ${a.label}",
        ))
        if (r is Outcome.Rejected) return Outcome.Rejected(r.rejection)
        assets.impair(id, applied)
        return Outcome.ok(applied)
    }

    // ----- the tick -----------------------------------------------------------------------------

    /**
     * Financial settlement phase. Accrues loan interest, adds due debt service to the supplied intents,
     * settles everything in priority order and feeds the outcomes back to the loan book.
     */
    fun settleTick(tick: Tick, intents: List<SettlementIntent>): List<SettlementResult> {
        loans.accrueAll()
        val service = loans.dueIntents(tick)
        val results = settlement.settle(tick, intents + service.intents)
        loans.applyResults(tick, results)
        return results
    }

    /** Runs periodic closing work when [tick] falls on a close boundary. Returns true if a close ran. */
    fun closeIfDue(tick: Tick, governmentOf: (LedgerOwner) -> LedgerOwner = { defaultGovernment() }): Boolean {
        if (tick.value == 0L || tick.value % config.closeIntervalTicks != 0L) return false
        val from = lastCloseTick
        val period = tick.value - from.value
        val taxDue = tick.value % config.taxIntervalTicks == 0L
        val taxIntents = ArrayList<SettlementIntent>()
        for (cf in companies.values) {
            val dep = assets.depreciate(cf.owner, period)
            if (dep.isPositive) {
                ledger.post(TransactionDraft(
                    "dep:${cf.owner.id}:${tick.value}", tick, ReasonCode.DEPRECIATION,
                    listOf(Entry(AccountRef(cf.owner, Chart.DEPRECIATION_EXPENSE), dep), Entry(AccountRef(cf.owner, Chart.ACCUMULATED_DEPRECIATION), -dep)),
                    "depreciation",
                ))
            }
            if (taxDue) {
                val fromTax = lastTaxTick
                val pbt = statements.income(cf.owner, fromTax, tick).profitBeforeTax
                val gov = governmentOf(cf.owner)
                val tax = cf.tax.assess(tick, pbt, taxPolicy(gov))
                if (tax.isPositive) {
                    taxIntents.add(TaxIntents.corporate("tax:corp:${cf.owner.id}:${tick.value}", cf.owner, gov, tax))
                }
            }
        }
        if (taxIntents.isNotEmpty()) {
            val res = settlement.settle(tick, taxIntents)
            for (r in res) if (r.status == SettlementStatus.PAID) companies[r.intent.payer]?.tax?.recordPaid(r.paid)
        }
        for (cf in companies.values) review(cf, from, tick)
        lastCloseTick = tick
        if (taxDue) lastTaxTick = tick
        return true
    }

    private fun review(cf: CompanyFinance, from: Tick, to: Tick) {
        val income = statements.income(cf.owner, from, to)
        val balance = statements.balance(cf.owner, to)
        val periodsPerYear = (360L / config.closeIntervalTicks).coerceAtLeast(1L)
        val ratios = Ratios.of(income, balance, periodsPerYear)
        val missed = loans.activeFor(cf.owner).sumOf { it.missedPayments }
        val arrears = settlement.totalArrears(cf.owner)
        val age = to.value - cf.founded.value
        cf.rating.review(CreditInputs(ratios, age, missed, arrears, income.revenue, balance.netWorth))
        cf.distress.evaluate(to, ratios, balance, arrears, missed)
        latest[cf.owner] = StatementSet(to, income, balance, statements.cashFlow(cf.owner, from, to), ratios)
    }

    /** The figures behind the company's latest rating review, or null before the first close. */
    fun creditInputs(owner: LedgerOwner, now: Tick): CreditInputs? {
        val cf = companies[owner] ?: return null
        val s = latest[owner] ?: return null
        val missed = loans.activeFor(owner).sumOf { it.missedPayments }
        return CreditInputs(s.ratios, now.value - cf.founded.value, missed, settlement.totalArrears(owner), s.income.revenue, s.balance.netWorth)
    }

    /** Terms a lender would offer this company right now. */
    fun creditTerms(owner: LedgerOwner, tick: Tick): CreditTerms {
        val cf = companies[owner] ?: return CreditTerms(baseRate + CreditRating.CCC.spread, Money.ZERO)
        val s = latest[owner]
        val ebitdaAnnual = (s?.income?.ebitda ?: Money.ZERO) * (360L / config.closeIntervalTicks)
        return CreditPolicy.terms(cf.rating.rating, baseRate, ebitdaAnnual, loans.debtOf(owner), statements.balance(owner, tick).netWorth)
    }

    /** Draws a loan at the company's current credit terms if it fits the limit. */
    fun borrow(tick: Tick, owner: LedgerOwner, amount: Money, termTicks: Int, type: RepaymentType = RepaymentType.AMORTIZING, collateral: List<Collateral> = emptyList()): Outcome<Loan> {
        val terms = creditTerms(owner, tick)
        val total = amount
        if (collateral.isEmpty() && total > terms.limit) return Outcome.reject(Rules.CREDIT_LIMIT, "exceeds credit limit")
        val rating = companies[owner]?.rating?.rating ?: CreditRating.CCC
        if (rating == CreditRating.D) return Outcome.reject(Rules.CREDIT_LIMIT, "borrower is in default")
        val rate = if (collateral.isNotEmpty()) terms.rate - Rate.basisPoints(100) else terms.rate
        return loans.originate(tick, owner, bank, LoanOffer(amount, rate, termTicks, 30, type, collateral))
    }

    // ----- integrity ----------------------------------------------------------------------------

    fun invariants(): List<String> = FinanceInvariants.check(this)

    override fun hashInto(hasher: CanonicalHasher) {
        ledger.hashInto(hasher)
        hasher.section("loans")
        for (l in loans.all()) {
            hasher.putLong(l.id.value).putMoney(l.outstanding).putMoney(l.accruedInterest).putInt(l.status.ordinal).putInt(l.missedPayments)
        }
        hasher.section("assets")
        for (a in assets.all()) hasher.putLong(a.id).putMoney(a.cost).putMoney(a.accumulated).putBoolean(a.disposed)
        hasher.section("companies-finance")
        for (c in companies.values) {
            hasher.putLong(c.owner.id).putInt(c.rating.rating.ordinal).putInt(c.distress.state.ordinal)
                .putMoney(c.tax.totalCarriedLoss()).putMoney(c.tax.lifetimeTaxPaid)
        }
        hasher.section("arrears")
        for (a in settlement.arrears()) hasher.putString(a.key).putMoney(a.amount)
    }

    internal fun lastCloseTickValue() = lastCloseTick
    internal fun lastTaxTickValue() = lastTaxTick
    internal fun setCloseTicks(close: Tick, tax: Tick) { lastCloseTick = close; lastTaxTick = tax }
    internal fun restoreCompanies(list: List<CompanyFinance>) { companies.clear(); for (c in list) companies[c.owner] = c }
    internal fun restorePolicies(map: Map<LedgerOwner, TaxPolicy>) { policies.clear(); policies.putAll(map) }
    internal fun policiesView(): Map<LedgerOwner, TaxPolicy> = policies

    private fun merge(entries: List<Entry>): List<Entry> {
        val m = java.util.TreeMap<AccountRef, Long>()
        for (e in entries) m[e.account] = (m[e.account] ?: 0L) + e.amount.raw
        return m.filter { it.value != 0L }.map { Entry(it.key, Money(it.value)) }
    }
}

/** Consistency checks run every tick in debug and at checkpoints in release. */
object FinanceInvariants {
    private val creation = setOf(ReasonCode.MINT, ReasonCode.CAPITAL, ReasonCode.OPENING)

    fun check(f: FinanceService): List<String> {
        val out = ArrayList<String>()
        // INV-01: the ledger balances.
        val tb = f.ledger.trialBalance()
        if (tb != 0L) out.add("INV-01 trial balance is $tb")
        // INV-02: cash is created only by designated transactions.
        for (tx in f.ledger.transactions()) {
            var cashDelta = 0L
            for (e in tx.entries) if (e.account.chart == Chart.CASH && e.account.owner.kind != OwnerKind.SYSTEM) cashDelta += e.amount.raw
            if (cashDelta != 0L && tx.reason !in creation) out.add("INV-02 ${tx.key} changed the money supply by $cashDelta")
        }
        // Companies never hold negative cash.
        for (c in f.companies()) {
            val cash = f.ledger.cash(c.owner)
            if (cash.isNegative) out.add("INV-03 ${c.owner} has negative cash $cash")
        }
        // Loan balances agree with the ledger.
        var loanTotal = 0L
        for (l in f.loans.unsettled()) loanTotal += l.outstanding.raw
        var ledgerDebt = 0L
        var ledgerReceivable = 0L
        for (ref in f.ledger.accounts()) {
            // Government borrowing is in bonds, not in the loan book.
            if ((ref.chart == Chart.DEBT_SHORT || ref.chart == Chart.DEBT_LONG) && ref.owner.kind != OwnerKind.GOVERNMENT) ledgerDebt -= f.ledger.balance(ref).raw
            if (ref.chart == Chart.LOANS_RECEIVABLE) ledgerReceivable += f.ledger.balance(ref).raw
        }
        if (ledgerDebt != loanTotal) out.add("INV-04 loan book $loanTotal differs from ledger debt $ledgerDebt")
        if (ledgerReceivable != loanTotal) out.add("INV-04 loan book $loanTotal differs from lender receivables $ledgerReceivable")
        return out
    }
}
