package app.many.economytycoon.finance

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepaymentScheduleTest {
    private val ten = Rate(100_000L)
    private fun plan(type: RepaymentType = RepaymentType.AMORTIZING, rate: Rate = ten, n: Int = 12, amount: Long = 120_000) =
        RepaymentSchedule.build(Money.units(amount), rate, n, 30, type, 30L)

    @Test fun anAmortisingLoanEndsAtZero() {
        val p = plan()
        assertEquals(12, p.count)
        assertEquals(Money.ZERO, p.last!!.balance)
        assertEquals(Money.units(120_000), p.totalPrincipal)
    }

    @Test fun paymentsAreEvenExceptTheLast() {
        val p = plan()
        val level = p.instalments.first().payment.raw
        for (i in p.instalments.dropLast(1)) assertTrue("payment ${i.number}", kotlin.math.abs(i.payment.raw - level) <= 2L)
        assertTrue(kotlin.math.abs(p.last!!.payment.raw - level) < level / 100)
    }

    @Test fun interestFallsAsTheBalanceFalls() {
        val p = plan()
        val interest = p.instalments.map { it.interest.raw }
        assertEquals(interest.sortedDescending(), interest)
        assertTrue(interest.first() > interest.last())
    }

    @Test fun balancesNeverRise() {
        val b = plan().instalments.map { it.balance.raw }
        assertEquals(b.sortedDescending(), b)
    }

    @Test fun daysStepByTheInterval() {
        val d = plan().instalments.map { it.day }
        assertEquals(30L, d.first())
        for (i in 1 until d.size) assertEquals(30L, d[i] - d[i - 1])
    }

    @Test fun anInterestOnlyLoanRepaysAtTheEnd() {
        val p = plan(RepaymentType.INTEREST_ONLY)
        assertTrue(p.instalments.dropLast(1).all { it.principal.raw == 0L })
        assertEquals(Money.units(120_000), p.last!!.principal)
        assertEquals(Money.ZERO, p.last!!.balance)
        assertEquals(p.peak, p.last!!.payment)
    }

    @Test fun aBulletLoanPaysInterestOnlyAtTheEndToo() {
        val p = plan(RepaymentType.BULLET, n = 4)
        assertEquals(Money.units(120_000), p.totalPrincipal)
        assertEquals(Money.ZERO, p.last!!.balance)
    }

    @Test fun aZeroRateLoanCostsNothingExtra() {
        val p = plan(rate = Rate(0L))
        assertEquals(Money.ZERO, p.totalInterest)
        assertEquals(p.totalPaid, Money.units(120_000))
    }

    @Test fun totalsAddUp() {
        val p = plan()
        assertEquals(p.totalPaid.raw, p.totalInterest.raw + p.totalPrincipal.raw)
        assertTrue(p.totalInterest.raw > 0L)
        assertTrue(p.interestShare(Money.units(120_000)) in 1..1_000_000)
    }

    @Test fun nothingLeftMeansAnEmptyPlan() {
        assertEquals(0, RepaymentSchedule.build(Money.ZERO, ten, 5, 30, RepaymentType.AMORTIZING, 1).count)
        assertEquals(0, RepaymentSchedule.build(Money.units(5), ten, 0, 30, RepaymentType.AMORTIZING, 1).count)
        assertEquals(Money.ZERO, RepaymentPlan(emptyList()).peak)
        assertEquals(0L, RepaymentPlan(emptyList()).interestShare(Money.units(5)))
    }

    @Test fun balanceOnADayLooksAtPaymentsMade() {
        val p = plan()
        val start = Money.units(120_000)
        assertEquals(start, p.balanceOn(10, start))
        assertEquals(p.instalments[0].balance, p.balanceOn(30, start))
        assertEquals(p.instalments[2].balance, p.balanceOn(100, start))
        assertEquals(Money.ZERO, p.balanceOn(10_000, start))
    }

    @Test fun longerTermsCostMoreInterestAndLessPerPayment() {
        val m = RepaymentSchedule.compare(Money.units(100_000), ten, 30, listOf(6, 12, 24))
        assertEquals(listOf(6, 12, 24), m.keys.toList())
        assertTrue(m.getValue(24).totalInterest.raw > m.getValue(6).totalInterest.raw)
        assertTrue(m.getValue(24).peak.raw < m.getValue(6).peak.raw)
    }

    @Test fun matchesTheLedgerScheduleToWithinRounding() {
        val level = LoanMath.payment(Money.units(120_000), ten, 30, 12, RepaymentType.AMORTIZING)
        assertTrue(kotlin.math.abs(plan().first!!.payment.raw - level.raw) <= 2L)
    }

    @Test fun periodInterestUsesDaysOverTheYear() {
        assertEquals(Money.units(100).raw, RepaymentSchedule.periodInterest(Money.units(12_000).raw, ten, 30))
        assertEquals(Money.units(1_000).raw, RepaymentSchedule.periodInterest(Money.units(120_000).raw, ten, 30))
    }
}
