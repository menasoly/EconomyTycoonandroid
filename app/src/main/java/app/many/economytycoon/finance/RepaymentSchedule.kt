package app.many.economytycoon.finance

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate

/** One payment in a plan. */
data class Instalment(
    val number: Int,
    /** Game day it falls due. */
    val day: Long,
    val payment: Money,
    val interest: Money,
    val principal: Money,
    /** What is still owed after this payment. */
    val balance: Money,
)

/** A plan of payments with the totals a borrower cares about. */
data class RepaymentPlan(val instalments: List<Instalment>) {
    val count: Int get() = instalments.size
    val totalPaid: Money get() = Money.sum(instalments.map { it.payment })
    val totalInterest: Money get() = Money.sum(instalments.map { it.interest })
    val totalPrincipal: Money get() = Money.sum(instalments.map { it.principal })
    val first: Instalment? get() = instalments.firstOrNull()
    val last: Instalment? get() = instalments.lastOrNull()

    /** The biggest single payment, which is what cash has to be able to meet. */
    val peak: Money get() = instalments.maxOfOrNull { it.payment.raw }?.let { Money(it) } ?: Money.ZERO

    /** Balance still owed on [day] after every payment due on or before it. */
    fun balanceOn(day: Long, start: Money): Money = instalments.lastOrNull { it.day <= day }?.balance ?: start

    /** Interest as a share of what is borrowed, ppm. */
    fun interestShare(principal: Money): Long = if (principal.raw <= 0L) 0L else totalInterest.raw * 1_000_000L / principal.raw
}

/**
 * Lays out the payments of a loan from its terms. Interest each period is the balance times the annual rate over the
 * days in the period, rounded to the nearest unit of money; the last payment always clears what is left. The result is
 * for showing a borrower what to expect and may differ from the ledger by rounding.
 */
object RepaymentSchedule {
    fun build(
        outstanding: Money,
        annual: Rate,
        paymentsLeft: Int,
        interval: Int,
        type: RepaymentType,
        firstDay: Long,
    ): RepaymentPlan {
        require(interval > 0) { "interval must be positive" }
        if (paymentsLeft <= 0 || outstanding.raw <= 0L) return RepaymentPlan(emptyList())
        val level = LoanMath.payment(outstanding, annual, interval, paymentsLeft, type)
        val out = ArrayList<Instalment>(paymentsLeft)
        var balance = outstanding.raw
        for (i in 1..paymentsLeft) {
            val interest = periodInterest(balance, annual, interval)
            val last = i == paymentsLeft
            val principal = when {
                last -> balance
                type == RepaymentType.AMORTIZING -> (level.raw - interest).coerceIn(0L, balance)
                else -> 0L
            }
            balance -= principal
            out += Instalment(i, firstDay + (i - 1L) * interval, Money(principal + interest), Money(interest), Money(principal), Money(balance))
        }
        return RepaymentPlan(out)
    }

    fun periodInterest(balance: Long, annual: Rate, interval: Int): Long {
        val num = java.math.BigInteger.valueOf(balance).multiply(java.math.BigInteger.valueOf(annual.ppm)).multiply(java.math.BigInteger.valueOf(interval.toLong()))
        val den = java.math.BigInteger.valueOf(Rate.ONE_PPM).multiply(java.math.BigInteger.valueOf(Calendar.DAYS_PER_YEAR.toLong()))
        return LoanMath.roundDiv(num, den).toLong()
    }

    /** The plan for what is left on a running loan. */
    fun of(loan: Loan): RepaymentPlan {
        val made = loan.periods - loan.paymentsRemaining
        val first = loan.startTick.value + (made + 1L) * loan.paymentInterval
        return build(loan.outstanding, loan.annualRate, loan.paymentsRemaining, loan.paymentInterval, loan.type, first)
    }

    /** Plans for the same sum over different term lengths, so they can be compared. */
    fun compare(principal: Money, annual: Rate, interval: Int, termsInPeriods: List<Int>, type: RepaymentType = RepaymentType.AMORTIZING): Map<Int, RepaymentPlan> =
        termsInPeriods.associateWith { build(principal, annual, it, interval, type, interval.toLong()) }
}
