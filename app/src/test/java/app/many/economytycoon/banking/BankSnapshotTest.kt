package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSnapshotTest {
    @Test fun assetsEqualLiabilitiesPlusEquity() {
        val s = BankingFx.bank().snapshot()
        assertEquals(s.totalLiabilities + s.bookEquity, s.totalAssets)
        assertEquals(Money.ZERO, s.imbalance)
    }

    @Test fun anySingleStrayEntryShowsUpAsImbalance() {
        val b = BankingFx.bank()
        b.reserves += Money.units(3)
        assertEquals(Money.units(3), b.snapshot().imbalance)
    }

    @Test fun snapshotsDoNotChangeWhenTheBankDoes() {
        val b = BankingFx.bank()
        val before = b.snapshot()
        b.reserves += Money.units(10); b.addDeposits(DepositKey(Depositor.HOUSEHOLD, DepositProduct.DEMAND), Money.units(10))
        assertTrue(before.reserves < b.reserves)
        assertEquals(Money.ZERO, before.imbalance)
    }

    @Test fun loanTotalsAddUpByStageAndSector() {
        val s = BankingFx.bank().snapshot()
        assertEquals(s.loansGross, LoanStage.values().fold(Money.ZERO) { a, st -> a + s.loansIn(st) })
        assertEquals(s.loansGross, LoanSector.values().fold(Money.ZERO) { a, se -> a + s.loansOf(se) })
        assertEquals(s.loansIn(LoanStage.PERFORMING) + s.loansIn(LoanStage.WATCH), s.loansPerforming())
    }

    @Test fun depositTotalsAddUp() {
        val s = BankingFx.bank().snapshot()
        assertEquals(s.depositsTotal, Depositor.values().fold(Money.ZERO) { a, d -> a + s.depositsBy(d) })
        assertEquals(s.depositsTotal, s.deposits.keys.fold(Money.ZERO) { a, k -> a + s.depositsOf(k) })
    }

    @Test fun capitalTiersBuildUp() {
        val b = BankingFx.bank()
        b.additionalTier1 = Money.units(5); b.tier2 = Money.units(7)
        val s = b.snapshot()
        assertEquals(s.bookEquity - s.intangibles, s.cet1Capital)
        assertEquals(s.cet1Capital + Money.units(5), s.tier1Capital)
        assertEquals(s.tier1Capital + Money.units(7), s.totalCapital)
    }

    @Test fun badLoanRatiosAreSensible() {
        val s = BankingFx.bank().snapshot()
        assertTrue(s.nplRatio.ppm in 1L..200_000L)
        assertTrue(s.coverageRatio.ppm > 0L)
        assertTrue(s.loanToDeposit.ppm in 100_000L..2_000_000L)
        val clean = BankingFx.bank(spec = OpeningSpec(Money.units(1_000_000), watchShare = Rate.ZERO, impairedShare = Rate.ZERO)).snapshot()
        assertEquals(Rate.ZERO, clean.nplRatio)
        assertEquals(Rate.ONE, clean.coverageRatio)
    }

    @Test fun sectorSharesAddToOne() {
        val s = BankingFx.bank().snapshot()
        val total = LoanSector.values().sumOf { s.sectorShare(it).ppm }
        assertTrue(total in 999_000L..1_001_000L)
    }

    @Test fun averageLoanRateIsBetweenTheExtremes() {
        val s = BankingFx.bank().snapshot()
        val rates = s.loans.filterKeys { it.stage != LoanStage.IMPAIRED }.values.map { it.ratePpm }
        assertTrue(s.averageLoanRatePpm() in rates.min()..rates.max())
    }

    @Test fun wholesaleShareCountsMarketFunding() {
        val b = BankingFx.bank()
        val before = b.snapshot().wholesaleShare
        val extra = Money.units(1_000_000)
        b.setWholesale(b.wholesale + extra, 40_000L); b.reserves += extra
        assertTrue(b.snapshot().wholesaleShare > before)
    }

    @Test fun trailingIncomeAnnualisesTheHistory() {
        val b = BankingFx.bank()
        assertEquals(Money.ZERO, b.snapshot().trailingGrossIncome())
        repeat(6) { b.record(MonthlyResult.empty(30L * (it + 1)).copy(loanInterest = Money.units(100)), 24) }
        assertEquals(Money.units(1_200), b.snapshot().trailingGrossIncome())
    }

    @Test fun historyIsCappedAtTheKeepLimit() {
        val b = BankingFx.bank()
        repeat(40) { b.record(MonthlyResult.empty(30L * (it + 1)), 24) }
        assertEquals(24, b.snapshot().history.size)
        assertEquals(30L * 40, b.snapshot().history.last().tick)
        assertEquals(30L * 17, b.snapshot().history.first().tick)
    }

    @Test fun stoppedBanksAreNotOperating() {
        val b = BankingFx.bank()
        assertTrue(b.snapshot().isOperating)
        b.changeStatus(BankStatus.MERGED, Tick(4))
        assertFalse(b.snapshot().isOperating)
        assertEquals(Tick(4), b.statusSince)
        b.changeStatus(BankStatus.MERGED, Tick(9))
        assertEquals(Tick(4), b.statusSince)
    }

    @Test fun poolsKeepTheBalanceWeightedRate() {
        val b = BankingFx.bank()
        val key = PoolKey(LoanSector.CORPORATE, app.many.economytycoon.finance.CreditRating.AA, LoanStage.PERFORMING)
        val old = b.loanPool(key)
        b.addLoans(key, old.principal, old.ratePpm + 20_000L)
        assertEquals(old.ratePpm + 10_000L, b.loanPool(key).ratePpm)
        assertEquals(old.principal.times(Rate.percent(200)), b.loanPool(key).principal)
    }

    @Test fun removingMoreThanThePoolTakesTheWholePool() {
        val b = BankingFx.bank()
        val key = b.loans.keys.first()
        val whole = b.loanPool(key).principal
        assertEquals(whole, b.removeLoans(key, whole + Money.units(100)))
        assertFalse(b.loans.containsKey(key))
        assertEquals(Money.ZERO, b.removeLoans(key, Money.units(5)))
    }

    @Test fun monthlyResultFiguresFollowTheirParts() {
        val r = MonthlyResult.empty(30).copy(
            loanInterest = Money.units(1_000), depositInterest = Money.units(300), fees = Money.units(50), operatingCost = Money.units(400),
            provisionCharge = Money.units(100), tax = Money.units(40), averageEarningAssets = Money.units(120_000), averageEquity = Money.units(10_000), averageLoans = Money.units(100_000),
        )
        assertEquals(Money.units(700), r.netInterestIncome)
        assertEquals(Money.units(750), r.grossIncome)
        assertEquals(Money.units(350), r.preProvisionProfit)
        assertEquals(Money.units(250), r.preTaxProfit)
        assertEquals(Money.units(210), r.netIncome)
        assertEquals(Rate.percent(7).ppm, r.netInterestMargin.ppm)
        assertEquals(Rate.ratio(400, 750), r.costToIncome)
        assertEquals(Rate.percent(25).ppm + 2_000L, r.returnOnEquity.ppm)
    }

    @Test fun emptyResultHasZeroRatios() {
        val r = MonthlyResult.empty(1)
        assertEquals(Rate.ZERO, r.netInterestMargin)
        assertEquals(Rate.ZERO, r.returnOnEquity)
        assertEquals(Rate.ZERO, r.costToIncome)
        assertEquals(Rate.ZERO, r.costOfRisk)
    }
}
