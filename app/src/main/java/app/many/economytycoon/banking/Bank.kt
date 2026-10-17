package app.many.economytycoon.banking

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.CreditRating

/**
 * A commercial bank: its books, its management policy and its standing with depositors and the supervisor.
 *
 * Only the banking engines change a bank, and every change they make keeps the books in balance (see
 * [BankSnapshot.imbalance]). Everything else reads [snapshot].
 */
class Bank(
    val id: BankId,
    val name: String,
    val country: CountryId,
    val operator: BankOperator,
    val owner: CompanyId?,
    val foundedAt: Tick,
    val strategy: BankStrategy?,
) {
    var status: BankStatus = BankStatus.ACTIVE
        internal set
    var statusSince: Tick = foundedAt
        internal set

    var reserves: Money = Money.ZERO
        internal set
    var interbankLent: Money = Money.ZERO
        internal set
    var interbankBorrowed: Money = Money.ZERO
        internal set
    var centralBankMarginal: Money = Money.ZERO
        internal set
    var centralBankEmergency: Money = Money.ZERO
        internal set
    var fixedAssets: Money = Money.ZERO
        internal set
    var intangibles: Money = Money.ZERO
        internal set
    var shareCapital: Money = Money.ZERO
        internal set
    var retained: Money = Money.ZERO
        internal set
    var oci: Money = Money.ZERO
        internal set
    var additionalTier1: Money = Money.ZERO
        internal set
    var tier2: Money = Money.ZERO
        internal set
    var wholesale: Money = Money.ZERO
        internal set
    var wholesaleRatePpm: Long = 0L
        internal set
    var allowance: Money = Money.ZERO
        internal set

    /** Tax losses carried forward. */
    var taxLossCarry: Money = Money.ZERO
        internal set

    val centralBankDebt: Money get() = centralBankMarginal + centralBankEmergency

    internal val loans = java.util.TreeMap<PoolKey, LoanPool>()
    internal val deposits = java.util.TreeMap<DepositKey, DepositPool>()
    internal val securities = java.util.TreeMap<SecurityKind, SecurityHolding>()

    var policy: BankPolicy = if (strategy != null) BankPolicy.from(strategy) else BankPolicy()
        internal set

    /** 0 to 1,000,000: how much depositors trust the bank. */
    var confidencePpm: Long = Rate.ONE_PPM
        internal set
    var runStage: RunStage = RunStage.CALM
        internal set
    var rumourPpm: Long = 0L
        internal set

    /** Confidence given by public reassurance; fades with time. */
    var supportPpm: Long = 0L
        internal set
    var panicDays: Int = 0
        internal set

    /** Withdrawals of the latest day, for display. */
    var lastOutflow: Money = Money.ZERO
        internal set

    var rating: CreditRating = CreditRating.BBB
        internal set
    var ratingScore: Int = 500
        internal set
    var ratingPendingDirection: Int = 0
        internal set
    var ratingPendingCount: Int = 0
        internal set

    var rung: SupervisoryRung = SupervisoryRung.NORMAL
        internal set
    var rungSince: Tick = foundedAt
        internal set
    var cleanMonths: Int = 0
        internal set

    /** When a capital restoration plan runs out, or -1 when there is none. */
    var restorationDeadline: Long = -1L
        internal set
    var interventionReason: String = ""
        internal set
    var lastDividend: Tick? = null
        internal set
    var levyPaid: Money = Money.ZERO
        internal set
    var lastMonthClosed: Long = -1L
        internal set
    var monthsSincePlan: Int = 0
        internal set

    /** Interbank interest received and paid since the last monthly close. */
    var periodInterbankIncome: Money = Money.ZERO
        internal set
    var periodInterbankCost: Money = Money.ZERO
        internal set

    /** Gains and losses on securities sold since the last monthly close. */
    var periodTrading: Money = Money.ZERO
        internal set

    internal val historyList = ArrayList<MonthlyResult>()

    fun snapshot(): BankSnapshot = BankSnapshot(
        id, name, country, operator, owner, status, reserves, interbankLent, interbankBorrowed, centralBankMarginal,
        centralBankEmergency, fixedAssets, intangibles, shareCapital, retained, oci, additionalTier1, tier2, wholesale,
        wholesaleRatePpm, allowance, java.util.TreeMap(loans), java.util.TreeMap(deposits), java.util.TreeMap(securities),
        policy, confidencePpm, runStage, rating, rung, historyList.toList(),
    )

    // Loans -----------------------------------------------------------------------------------------------

    internal fun loanPool(key: PoolKey): LoanPool = loans[key] ?: LoanPool.EMPTY

    /** Adds [amount] to a pool, keeping the balance-weighted rate. Does not touch any other account. */
    internal fun addLoans(key: PoolKey, amount: Money, ratePpm: Long) {
        if (amount.raw <= 0L) return
        val old = loanPool(key)
        val total = old.principal + amount
        val weighted = if (old.principal.raw == 0L) ratePpm else {
            java.math.BigInteger.valueOf(old.principal.raw).multiply(java.math.BigInteger.valueOf(old.ratePpm))
                .add(java.math.BigInteger.valueOf(amount.raw).multiply(java.math.BigInteger.valueOf(ratePpm)))
                .divide(java.math.BigInteger.valueOf(total.raw)).toLong()
        }
        loans[key] = LoanPool(total, weighted)
    }

    /** Removes up to [amount] from a pool and returns what was removed. The rate of what remains is unchanged. */
    internal fun removeLoans(key: PoolKey, amount: Money): Money {
        val old = loans[key] ?: return Money.ZERO
        val take = Money.min(amount, old.principal)
        if (take.raw <= 0L) return Money.ZERO
        val left = old.principal - take
        if (left.isZero) loans.remove(key) else loans[key] = LoanPool(left, old.ratePpm)
        return take
    }

    internal fun setLoanRate(key: PoolKey, ratePpm: Long) {
        val old = loans[key] ?: return
        loans[key] = LoanPool(old.principal, ratePpm)
    }

    // Deposits ---------------------------------------------------------------------------------------------

    internal fun depositPool(key: DepositKey): DepositPool = deposits[key] ?: DepositPool(Money.ZERO, 0L)

    internal fun addDeposits(key: DepositKey, amount: Money) {
        if (amount.raw <= 0L) return
        val old = depositPool(key)
        deposits[key] = DepositPool(old.balance + amount, old.ratePpm)
    }

    /** Removes up to [amount] and returns what was removed. */
    internal fun removeDeposits(key: DepositKey, amount: Money): Money {
        val old = deposits[key] ?: return Money.ZERO
        val take = Money.min(amount, old.balance)
        if (take.raw <= 0L) return Money.ZERO
        deposits[key] = DepositPool(old.balance - take, old.ratePpm)
        return take
    }

    internal fun setDepositRate(key: DepositKey, ratePpm: Long) {
        val old = deposits[key] ?: DepositPool(Money.ZERO, 0L)
        deposits[key] = DepositPool(old.balance, ratePpm)
    }

    // Securities ---------------------------------------------------------------------------------------------

    internal fun security(kind: SecurityKind): SecurityHolding = securities[kind] ?: SecurityHolding(Money.ZERO, 0L, 0)

    internal fun putSecurity(kind: SecurityKind, holding: SecurityHolding) {
        if (holding.value.isZero) securities.remove(kind) else securities[kind] = holding
    }

    /** Buys securities at fair value with cash from reserves. Returns the amount bought, limited by reserves. */
    internal fun buySecurities(kind: SecurityKind, amount: Money, yieldPpm: Long, durationMonths: Int): Money {
        val spend = Money.min(amount, reserves)
        if (spend.raw <= 0L) return Money.ZERO
        val old = security(kind)
        val total = old.value + spend
        val blended = if (old.value.isZero) yieldPpm else mulDiv(old.value.raw, old.yieldPpm, total.raw) + mulDiv(spend.raw, yieldPpm, total.raw)
        putSecurity(kind, SecurityHolding(total, blended, if (old.value.isZero) durationMonths else durationMonths))
        reserves -= spend
        return spend
    }

    /**
     * Sells securities at fair value less [discount]. The loss, and a matching share of the unrealised gains or
     * losses held in equity, go through retained earnings. Returns the fair value sold.
     */
    internal fun sellSecurities(kind: SecurityKind, amount: Money, discount: Rate): Money {
        val h = securities[kind] ?: return Money.ZERO
        val sold = Money.min(amount, h.value)
        if (sold.raw <= 0L) return Money.ZERO
        val proceeds = sold - sold.times(discount)
        val loss = sold - proceeds
        val recycled = Money(mulDiv(oci.raw, sold.raw, securitiesValueTotal().raw.coerceAtLeast(1L)))
        putSecurity(kind, SecurityHolding(h.value - sold, h.yieldPpm, h.durationMonths))
        reserves += proceeds
        retained = retained - loss + recycled
        oci -= recycled
        periodTrading = periodTrading - loss + recycled
        return sold
    }

    internal fun securitiesValueTotal(): Money = Money.sum(securities.values.map { it.value })

    // History ----------------------------------------------------------------------------------------------------

    internal fun record(result: MonthlyResult, keep: Int) {
        historyList.add(result)
        while (historyList.size > keep) historyList.removeAt(0)
    }

    internal fun resetPeriod() {
        periodInterbankIncome = Money.ZERO; periodInterbankCost = Money.ZERO; periodTrading = Money.ZERO
    }

    internal fun setRating(r: CreditRating, score: Int, direction: Int, count: Int) {
        rating = r; ratingScore = score; ratingPendingDirection = direction; ratingPendingCount = count
    }

    internal fun changeStatus(next: BankStatus, tick: Tick) {
        if (status == next) return
        status = next
        statusSince = tick
    }

    internal fun changePolicy(next: BankPolicy) { policy = next }

    internal fun applyConfidence(value: Long, stage: RunStage) {
        confidencePpm = value.coerceIn(0L, Rate.ONE_PPM)
        runStage = stage
    }

    internal fun setRung(next: SupervisoryRung, tick: Tick) {
        if (rung != next) rungSince = tick
        rung = next
    }

    internal fun setRumour(value: Long) { rumourPpm = value.coerceIn(0L, Rate.ONE_PPM) }
    internal fun setSupport(value: Long) { supportPpm = value.coerceIn(0L, Rate.ONE_PPM) }
    internal fun setRestoration(deadline: Long) { restorationDeadline = deadline }
    internal fun setIntervention(reason: String) { interventionReason = reason }
    internal fun addLevy(amount: Money) { levyPaid += amount }
    internal fun setMonthClosed(tick: Long) { lastMonthClosed = tick }
    internal fun setTaxCarry(value: Money) { taxLossCarry = value }
    internal fun setWholesale(amount: Money, ratePpm: Long) { wholesale = amount; wholesaleRatePpm = ratePpm }
    internal fun setInterbank(lent: Money, borrowed: Money) { interbankLent = lent; interbankBorrowed = borrowed }
    internal fun setCentralBank(marginal: Money, emergency: Money) { centralBankMarginal = marginal; centralBankEmergency = emergency }
    internal fun setEquity(share: Money, retained: Money, oci: Money) { shareCapital = share; this.retained = retained; this.oci = oci }
    internal fun setCapitalInstruments(at1: Money, tier2: Money) { additionalTier1 = at1; this.tier2 = tier2 }
    internal fun setFixedAssets(fixed: Money, intangibles: Money) { fixedAssets = fixed; this.intangibles = intangibles }
}
