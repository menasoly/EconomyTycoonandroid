package app.many.economytycoon.banking

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.CreditRating

/**
 * An immutable picture of a bank's books and standing. Analysis, the planners, stress tests and presenters all work
 * from snapshots, so none of them can change a bank by accident.
 *
 * The books balance when assets equal liabilities plus equity: [imbalance] is zero.
 */
data class BankSnapshot(
    val id: BankId,
    val name: String,
    val country: CountryId,
    val operator: BankOperator,
    val owner: CompanyId?,
    val status: BankStatus = BankStatus.ACTIVE,
    val reserves: Money = Money.ZERO,
    val interbankLent: Money = Money.ZERO,
    val interbankBorrowed: Money = Money.ZERO,
    val centralBankMarginal: Money = Money.ZERO,
    val centralBankEmergency: Money = Money.ZERO,
    val fixedAssets: Money = Money.ZERO,
    val intangibles: Money = Money.ZERO,
    val shareCapital: Money = Money.ZERO,
    val retained: Money = Money.ZERO,
    val oci: Money = Money.ZERO,
    val additionalTier1: Money = Money.ZERO,
    val tier2: Money = Money.ZERO,
    val wholesale: Money = Money.ZERO,
    val wholesaleRatePpm: Long = 0L,
    val allowance: Money = Money.ZERO,
    val loans: Map<PoolKey, LoanPool> = emptyMap(),
    val deposits: Map<DepositKey, DepositPool> = emptyMap(),
    val securities: Map<SecurityKind, SecurityHolding> = emptyMap(),
    val policy: BankPolicy = BankPolicy(),
    val confidencePpm: Long = Rate.ONE_PPM,
    val runStage: RunStage = RunStage.CALM,
    val rating: CreditRating = CreditRating.BBB,
    val rung: SupervisoryRung = SupervisoryRung.NORMAL,
    val history: List<MonthlyResult> = emptyList(),
) {
    val loansGross: Money get() = Money.sum(loans.values.map { it.principal })

    fun loansIn(stage: LoanStage): Money = Money.sum(loans.filter { it.key.stage == stage }.map { it.value.principal })

    fun loansOf(sector: LoanSector): Money = Money.sum(loans.filter { it.key.sector == sector }.map { it.value.principal })

    fun loansPerforming(): Money = loansIn(LoanStage.PERFORMING) + loansIn(LoanStage.WATCH)

    fun depositsOf(key: DepositKey): Money = deposits[key]?.balance ?: Money.ZERO

    val depositsTotal: Money get() = Money.sum(deposits.values.map { it.balance })

    fun depositsBy(depositor: Depositor): Money = Money.sum(deposits.filter { it.key.depositor == depositor }.map { it.value.balance })

    fun securitiesValue(): Money = Money.sum(securities.values.map { it.value })

    fun securityOf(kind: SecurityKind): Money = securities[kind]?.value ?: Money.ZERO

    val centralBankDebt: Money get() = centralBankMarginal + centralBankEmergency

    /** Assets net of the allowance for expected credit losses. */
    val totalAssets: Money
        get() = reserves + interbankLent + securitiesValue() + loansGross - allowance + fixedAssets + intangibles

    val totalLiabilities: Money
        get() = depositsTotal + wholesale + interbankBorrowed + centralBankDebt + additionalTier1 + tier2

    val bookEquity: Money get() = shareCapital + retained + oci

    /** Zero when the books balance. */
    val imbalance: Money get() = totalAssets - totalLiabilities - bookEquity

    /** Interest-earning assets: loans in stages 1 and 2, securities, reserves and interbank lending. */
    val earningAssets: Money get() = reserves + interbankLent + securitiesValue() + loansPerforming()

    /** Common equity net of intangibles; what absorbs losses first. */
    val cet1Capital: Money get() = bookEquity - intangibles

    val tier1Capital: Money get() = cet1Capital + additionalTier1

    val totalCapital: Money get() = tier1Capital + tier2

    /** Impaired loans over gross loans. */
    val nplRatio: Rate get() = if (loansGross.raw <= 0L) Rate.ZERO else Rate.ratio(loansIn(LoanStage.IMPAIRED).raw, loansGross.raw)

    /** Allowance over impaired loans. Full coverage is one. */
    val coverageRatio: Rate get() = loansIn(LoanStage.IMPAIRED).let { if (it.raw <= 0L) Rate.ONE else Rate.ratio(allowance.raw, it.raw) }

    val loanToDeposit: Rate get() = if (depositsTotal.raw <= 0L) Rate.ZERO else Rate.ratio(loansGross.raw, depositsTotal.raw)

    /** Share of funding (deposits, wholesale and borrowing) that is not customer deposits. */
    val wholesaleShare: Rate
        get() {
            val funding = depositsTotal + wholesale + interbankBorrowed + centralBankDebt
            return if (funding.raw <= 0L) Rate.ZERO else Rate.ratio((wholesale + interbankBorrowed).raw, funding.raw)
        }

    /** Share of the gross loan book each sector makes up. */
    fun sectorShare(sector: LoanSector): Rate =
        if (loansGross.raw <= 0L) Rate.ZERO else Rate.ratio(loansOf(sector).raw, loansGross.raw)

    /** Weighted average rate on performing loans, ppm. Zero with no loans. */
    fun averageLoanRatePpm(): Long {
        val perf = loans.filter { it.key.stage != LoanStage.IMPAIRED }
        val total = perf.values.sumOf { it.principal.raw }
        if (total <= 0L) return 0L
        var acc = java.math.BigInteger.ZERO
        for (p in perf.values) acc = acc.add(java.math.BigInteger.valueOf(p.principal.raw).multiply(java.math.BigInteger.valueOf(p.ratePpm)))
        return acc.divide(java.math.BigInteger.valueOf(total)).toLong()
    }

    /** The last twelve months' gross income, or fewer months scaled up. Never negative. */
    fun trailingGrossIncome(): Money {
        val months = history.takeLast(12)
        if (months.isEmpty()) return Money.ZERO
        val sum = Money.sum(months.map { it.grossIncome })
        val scaled = mulDiv(sum.raw, 12L, months.size.toLong())
        return Money(scaled.coerceAtLeast(0L))
    }

    val lastResult: MonthlyResult? get() = history.lastOrNull()

    val isOperating: Boolean get() = status.isOperating
}
