package app.many.economytycoon.finance

import app.many.economytycoon.core.LoanId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.Calendar
import java.math.BigInteger

enum class RepaymentType { AMORTIZING, INTEREST_ONLY, BULLET }

enum class LoanStatus { ACTIVE, PAID_OFF, DEFAULTED }

/** A promise to keep a ratio within bounds. Breaching a covenant raises the loan's rate or calls it. */
sealed class Covenant {
    abstract val name: String
    data class MinCashRatio(val minimum: Rate) : Covenant() { override val name get() = "min_cash_ratio" }
    data class MaxDebtToEquity(val maximum: Rate) : Covenant() { override val name get() = "max_debt_to_equity" }
    data class MinInterestCoverage(val minimum: Rate) : Covenant() { override val name get() = "min_interest_coverage" }
}

data class Collateral(val description: String, val value: Money)

/**
 * A term loan. Interest accrues per tick on the outstanding balance with a remainder carry so no
 * fraction of a unit is ever lost or invented.
 */
class Loan(
    val id: LoanId,
    val borrower: LedgerOwner,
    val lender: LedgerOwner,
    val principal: Money,
    val annualRate: Rate,
    val startTick: Tick,
    val termTicks: Int,
    val paymentInterval: Int,
    val type: RepaymentType,
    val collateral: List<Collateral> = emptyList(),
    val covenants: List<Covenant> = emptyList(),
) {
    init {
        require(principal.raw > 0) { "principal must be positive" }
        require(termTicks > 0 && paymentInterval > 0 && termTicks % paymentInterval == 0) { "term must be a multiple of the interval" }
    }

    var outstanding: Money = principal
        internal set
    var accruedInterest: Money = Money.ZERO
        internal set
    var status: LoanStatus = LoanStatus.ACTIVE
        internal set
    var missedPayments: Int = 0
        internal set
    var penaltyActive: Boolean = false
        internal set
    internal var carry: Long = 0L
    internal var paymentsMade: Int = 0
    internal var scheduledPayment: Money = Money.ZERO

    val periods: Int get() = termTicks / paymentInterval
    val maturity: Tick get() = startTick + termTicks.toLong()
    val isSecured: Boolean get() = collateral.isNotEmpty()
    val collateralValue: Money get() = Money.sum(collateral.map { it.value })
    val paymentsRemaining: Int get() = periods - paymentsMade

    /** Effective annual rate including the default penalty surcharge. */
    fun effectiveRate(penalty: Rate): Rate = if (penaltyActive) annualRate + penalty else annualRate

    init { scheduledPayment = LoanMath.payment(principal, annualRate, paymentInterval, periods, type) }

    fun isPaymentDue(tick: Tick): Boolean {
        if (status != LoanStatus.ACTIVE || tick <= startTick) return false
        val elapsed = tick - startTick
        return elapsed % paymentInterval == 0L && elapsed <= termTicks
    }

    /** Accrues one tick of interest and returns the amount. */
    fun accrue(penalty: Rate): Money {
        if (status != LoanStatus.ACTIVE) return Money.ZERO
        val rate = effectiveRate(penalty).ppm
        val numerator = outstanding.raw.toBigInteger().multiply(BigInteger.valueOf(rate)).add(BigInteger.valueOf(carry))
        val denominator = BigInteger.valueOf(Rate.ONE_PPM).multiply(BigInteger.valueOf(Calendar.DAYS_PER_YEAR.toLong()))
        val qr = numerator.divideAndRemainder(denominator)
        carry = qr[1].toLong()
        val interest = Money(qr[0].toLong())
        accruedInterest += interest
        return interest
    }

    /** Principal part of the scheduled payment at this point in the schedule. */
    fun scheduledPrincipal(): Money {
        val last = paymentsRemaining <= 1
        return when (type) {
            RepaymentType.BULLET -> if (last) outstanding else Money.ZERO
            RepaymentType.INTEREST_ONLY -> if (last) outstanding else Money.ZERO
            RepaymentType.AMORTIZING -> {
                if (last) outstanding
                else {
                    val p = scheduledPayment - accruedInterest
                    if (p.raw < 0) Money.ZERO else Money.min(p, outstanding)
                }
            }
        }
    }

    internal fun recordPayment(principalPaid: Money, interestPaid: Money) {
        outstanding -= principalPaid
        accruedInterest -= interestPaid
        paymentsMade++
        if (penaltyActive) penaltyActive = false
        if (outstanding.isZero && accruedInterest.isZero) status = LoanStatus.PAID_OFF
    }

    internal fun recordMiss(maxMisses: Int) {
        missedPayments++
        penaltyActive = true
        if (missedPayments >= maxMisses) status = LoanStatus.DEFAULTED
    }

    internal fun recordPrepayment(amount: Money) {
        outstanding -= amount
        if (outstanding.isZero && accruedInterest.isZero) status = LoanStatus.PAID_OFF
    }

    internal fun restoreDynamic(outstanding: Money, accrued: Money, carry: Long, made: Int, missed: Int, penalty: Boolean, status: LoanStatus) {
        this.outstanding = outstanding; this.accruedInterest = accrued; this.carry = carry
        this.paymentsMade = made; this.missedPayments = missed; this.penaltyActive = penalty; this.status = status
    }

    internal val carryValue get() = carry
    internal val paymentsMadeValue get() = paymentsMade
}

/** Exact payment arithmetic with big integers, so schedules are identical on every device. */
object LoanMath {
    /** Level payment per interval for [type]. For interest-only/bullet loans this is the periodic interest guess. */
    fun payment(principal: Money, annual: Rate, interval: Int, periods: Int, type: RepaymentType): Money {
        val perPeriodNum = BigInteger.valueOf(annual.ppm).multiply(BigInteger.valueOf(interval.toLong()))
        val den = BigInteger.valueOf(Rate.ONE_PPM).multiply(BigInteger.valueOf(Calendar.DAYS_PER_YEAR.toLong()))
        val p = BigInteger.valueOf(principal.raw)
        return when (type) {
            RepaymentType.BULLET -> Money.ZERO
            RepaymentType.INTEREST_ONLY -> Money(roundDiv(p.multiply(perPeriodNum), den).toLong())
            RepaymentType.AMORTIZING -> {
                if (annual.ppm == 0L) return Money(roundDiv(p, BigInteger.valueOf(periods.toLong())).toLong())
                // payment = P * r * A / (den_r * (A - B)) where r = num/den, A = (den+num)^n, B = den^n
                val a = den.add(perPeriodNum).pow(periods)
                val b = den.pow(periods)
                val top = p.multiply(perPeriodNum).multiply(a)
                val bottom = den.multiply(a.subtract(b))
                Money(roundDiv(top, bottom).toLong())
            }
        }
    }

    fun roundDiv(a: BigInteger, b: BigInteger): BigInteger {
        val two = BigInteger.valueOf(2)
        return a.multiply(two).add(b).divide(b.multiply(two))
    }

    /** Total interest paid over the life of a loan, for display and affordability checks. */
    fun totalInterest(principal: Money, annual: Rate, interval: Int, periods: Int, type: RepaymentType): Money {
        var balance = principal.raw
        var total = 0L
        val pay = payment(principal, annual, interval, periods, type).raw
        val perPeriodNum = BigInteger.valueOf(annual.ppm).multiply(BigInteger.valueOf(interval.toLong()))
        val den = BigInteger.valueOf(Rate.ONE_PPM).multiply(BigInteger.valueOf(Calendar.DAYS_PER_YEAR.toLong()))
        for (i in 1..periods) {
            val interest = roundDiv(BigInteger.valueOf(balance).multiply(perPeriodNum), den).toLong()
            total += interest
            val principalPart = when (type) {
                RepaymentType.AMORTIZING -> if (i == periods) balance else (pay - interest).coerceIn(0L, balance)
                else -> if (i == periods) balance else 0L
            }
            balance -= principalPart
        }
        return Money(total)
    }
}
