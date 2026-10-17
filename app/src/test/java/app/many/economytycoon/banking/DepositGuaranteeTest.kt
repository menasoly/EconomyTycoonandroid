package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepositGuaranteeTest {
    private val params = BankingFx.params
    private val res = params.resolution
    private val dep = params.deposits
    private fun fund() = GuaranteeFund(BankingFx.country)

    @Test fun coveredDepositsFollowInsuredShares() {
        val s = BankingFx.bank().snapshot()
        val covered = GuaranteeScheme.coveredDeposits(s, dep)
        assertTrue(covered.isPositive && covered < s.depositsTotal)
        val manual = s.deposits.entries.fold(Money.ZERO) { a, (k, p) -> a + p.balance.times(dep.insuredShare(k.depositor)) }
        assertEquals(manual, covered)
    }

    @Test fun targetIsAShareOfCoveredDeposits() {
        assertEquals(Money.units(1_000).times(res.fundTarget), GuaranteeScheme.target(Money.units(1_000), res))
    }

    @Test fun riskFactorRisesWithWorseRatings() {
        var last = 0L
        for (r in CreditRating.values()) {
            val f = GuaranteeScheme.riskFactor(r, res).ppm
            assertTrue(f >= last)
            last = f
        }
        assertEquals(res.riskFactorBest, GuaranteeScheme.riskFactor(CreditRating.AAA, res))
        assertEquals(res.riskFactorWorst, GuaranteeScheme.riskFactor(CreditRating.CCC, res))
        assertEquals(res.riskFactorWorst, GuaranteeScheme.riskFactor(CreditRating.D, res))
    }

    @Test fun coverageIsFundOverCoveredAndCapped() {
        val f = fund()
        assertEquals(Rate.ZERO, GuaranteeScheme.coverage(f, Money.units(100)))
        f.balance = Money.units(50)
        assertEquals(Rate.percent(50), GuaranteeScheme.coverage(f, Money.units(100)))
        f.balance = Money.units(500)
        assertEquals(Rate.ONE, GuaranteeScheme.coverage(f, Money.units(100)))
        assertEquals(Rate.ONE, GuaranteeScheme.coverage(f, Money.ZERO))
    }

    @Test fun levyDaysComeEveryPeriod() {
        val f = fund()
        assertFalse(GuaranteeScheme.isLevyDay(f, 0L, res))
        assertFalse(GuaranteeScheme.isLevyDay(f, res.levyEvery - 1L, res))
        assertTrue(GuaranteeScheme.isLevyDay(f, res.levyEvery.toLong(), res))
        f.lastLevy = 100L
        assertFalse(GuaranteeScheme.isLevyDay(f, 150L, res))
    }

    @Test fun leviesAreProportionalToRiskAndStopAtTheTarget() {
        val banks = BankingFx.banks(2)
        banks[1].setRating(CreditRating.B, 300, 0, 0)
        val levies = GuaranteeScheme.levies(fund(), banks.map { it.snapshot() }, res, dep, 90L)
        assertEquals(2, levies.size)
        assertTrue(levies[1].amount > levies[0].amount)
        val f = fund(); f.balance = Money.units(900_000_000)
        assertTrue(GuaranteeScheme.levies(f, banks.map { it.snapshot() }, res, dep, 90L).all { it.amount.isZero })
    }

    @Test fun leviesNeverExceedTheRoomToTarget() {
        val banks = BankingFx.banks(3)
        val snaps = banks.map { it.snapshot() }
        val covered = Money.sum(snaps.map { GuaranteeScheme.coveredDeposits(it, dep) })
        val f = fund()
        f.balance = GuaranteeScheme.target(covered, res) - Money.units(10)
        val total = Money.sum(GuaranteeScheme.levies(f, snaps, res, dep, 360L).map { it.amount })
        assertTrue(total <= Money.units(10))
    }

    @Test fun noBanksMeansNoLevies() {
        assertTrue(GuaranteeScheme.levies(fund(), emptyList(), res, dep, 90L).isEmpty())
    }

    @Test fun collectingMovesCashFromBanksToTheFundKeepingBooksBalanced() {
        val banks = BankingFx.banks(2)
        val f = fund()
        val levies = GuaranteeScheme.levies(f, banks.map { it.snapshot() }, res, dep, 90L)
        val total = GuaranteeScheme.collect(f, banks, levies, 90L)
        assertEquals(Money.sum(levies.map { it.amount }), total)
        assertEquals(total, f.balance)
        assertEquals(total, f.totalLevied)
        assertEquals(90L, f.lastLevy)
        assertEquals(total, banks.fold(Money.ZERO) { a, b -> a + b.levyPaid })
        BankingFx.assertBalanced(banks)
    }

    @Test fun collectionsRepayTheStateFirst() {
        val banks = BankingFx.banks(2)
        val f = fund(); f.stateLoan = Money.units(5)
        val total = GuaranteeScheme.collect(f, banks, listOf(Levy(banks[0].id, Money.units(8))), 90L)
        assertEquals(Money.units(8), total)
        assertEquals(Money.ZERO, f.stateLoan)
        assertEquals(Money.units(3), f.balance)
    }

    @Test fun payoutTakesFromTheBalanceFirst() {
        val banks = BankingFx.banks(2)
        val f = fund(); f.balance = Money.units(100)
        val p = GuaranteeScheme.payOut(f, Money.units(60), banks, res, dep, 10L)
        assertEquals(Money.units(60), p.paid)
        assertEquals(Money.units(60), p.fromBalance)
        assertEquals(Money.ZERO, p.extraordinary + p.stateLoan)
        assertEquals(Money.units(40), f.balance)
        assertEquals(Money.units(60), f.totalPaid)
    }

    @Test fun shortfallLeviesTheBanksThenBorrowsFromTheState() {
        val banks = BankingFx.banks(2)
        val f = fund()
        val covered = Money.sum(banks.map { GuaranteeScheme.coveredDeposits(it.snapshot(), dep) })
        val cap = covered.times(res.extraordinaryCap)
        val ask = cap + Money.units(1_000)
        val p = GuaranteeScheme.payOut(f, ask, banks, res, dep, 10L)
        assertEquals(ask, p.paid)
        assertEquals(cap, p.extraordinary)
        assertEquals(Money.units(1_000), p.stateLoan)
        assertEquals(Money.units(1_000), f.stateLoan)
        BankingFx.assertBalanced(banks)
    }

    @Test fun extraordinaryLevyCapResetsEachYear() {
        val f = fund()
        f.extraordinaryThisYear = Money.units(5)
        GuaranteeScheme.rollYear(f, 100L)
        assertEquals(Money.units(5), f.extraordinaryThisYear)
        GuaranteeScheme.rollYear(f, BankMath.YEAR_TICKS.toLong())
        assertEquals(Money.ZERO, f.extraordinaryThisYear)
        assertEquals(BankMath.YEAR_TICKS.toLong(), f.yearStart)
    }

    @Test fun zeroPayoutIsANoOp() {
        val f = fund()
        val p = GuaranteeScheme.payOut(f, Money.ZERO, emptyList(), res, dep, 1L)
        assertEquals(Money.ZERO, p.paid)
        assertEquals(Money.ZERO, f.totalPaid)
    }

    @Test fun fundHashChangesWithItsState() {
        val a = fund(); val b = fund()
        val ha = app.many.economytycoon.core.CanonicalHasher().also { a.hashInto(it) }.finish()
        b.balance = Money.units(1)
        val hb = app.many.economytycoon.core.CanonicalHasher().also { b.hashInto(it) }.finish()
        assertFalse(ha == hb)
    }

    @Test fun restoreRebuildsAFund() {
        val f = fund()
        f.restore(Money.units(1), Money.units(2), Money.units(3), Money.units(4), 5L, Money.units(6), 7L)
        assertEquals(Money.units(2), f.stateLoan)
        assertEquals(7L, f.yearStart)
    }
}
