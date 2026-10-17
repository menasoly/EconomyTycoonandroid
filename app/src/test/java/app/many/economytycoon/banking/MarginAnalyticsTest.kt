package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarginAnalyticsTest {
    private val params = BankingFx.params
    private val neutral: (LoanSector) -> Long = { Rate.ONE_PPM }

    private fun report(b: Bank) = MarginAnalytics.report(b.snapshot(), Rate.percent(4), params, neutral, Rate.ONE_PPM)

    @Test fun freshBankHasASensibleMargin() {
        val b = BankingFx.bank()
        assertEquals(Rate.ZERO, report(b).netInterestMargin)
        b.record(MonthlyResult.empty(30).copy(loanInterest = Money.units(5_000), depositInterest = Money.units(1_000), averageEarningAssets = Money.units(100_000)), 24)
        val r = report(b)
        assertEquals(Rate.percent(48).ppm, r.netInterestMargin.ppm)
        assertTrue(r.assetYield > r.fundingCost)
        assertTrue(r.loanYield > r.assetYield)
    }

    @Test fun everySectorAppearsOnceWithSharesAddingToOne() {
        val r = report(BankingFx.bank())
        assertEquals(LoanSector.values().size, r.bySector.size)
        assertEquals(r.bySector.map { it.sector }.toSet().size, r.bySector.size)
        val sum = r.bySector.sumOf { it.share.ppm }
        assertTrue(sum in 999_000L..1_001_000L)
        assertEquals(Money.sum(r.bySector.map { it.balance }), BankingFx.bank().snapshot().loansGross)
    }

    @Test fun riskAdjustedSpreadIsYieldLessFundingAndLoss() {
        val b = BankingFx.bank()
        val r = report(b)
        for (m in r.bySector) assertEquals(m.yield.ppm - r.fundingCost.ppm - m.expectedLoss.ppm, m.riskAdjustedSpread.ppm)
    }

    @Test fun recessionRaisesExpectedLossInTheReport() {
        val b = BankingFx.bank()
        val bad = MarginAnalytics.report(b.snapshot(), Rate.percent(4), params, { 3_000_000L }, 600_000L)
        for (i in bad.bySector.indices) assertTrue(bad.bySector[i].expectedLoss >= report(b).bySector[i].expectedLoss)
    }

    @Test fun fundingLinesCoverTheLiabilities() {
        val b = BankingFx.bank()
        val lines = MarginAnalytics.fundingLines(b.snapshot())
        assertTrue(lines.any { it.balance.isPositive })
        val s = b.snapshot()
        assertEquals(s.depositsTotal + s.wholesale + s.interbankBorrowed + s.centralBankDebt, Money.sum(lines.map { it.balance }))
    }

    @Test fun risingRatesHelpAssetSensitiveBanks() {
        val s = BankingFx.bank().snapshot()
        val up = MarginAnalytics.annualNiiChange(s, Rate.percent(1), params.deposits)
        val down = MarginAnalytics.annualNiiChange(s, Rate.percent(-1), params.deposits)
        assertTrue(up.raw != down.raw)
        assertEquals(Money.ZERO, MarginAnalytics.annualNiiChange(s, Rate.ZERO, params.deposits))
    }

    @Test fun sensitivityIsRoughlySymmetric() {
        val s = BankingFx.bank().snapshot()
        val up = MarginAnalytics.annualNiiChange(s, Rate.percent(1), params.deposits).raw
        val down = MarginAnalytics.annualNiiChange(s, Rate.percent(-1), params.deposits).raw
        assertTrue(up * down <= 0L || kotlin.math.abs(up + down) < kotlin.math.abs(up) + 1L)
    }

    @Test fun attributionSplitsChangeIntoVolumeAndRate() {
        val a = MonthlyResult.empty(30).copy(loanInterest = Money.units(100), depositInterest = Money.units(40), averageEarningAssets = Money.units(10_000))
        val b = a.copy(loanInterest = Money.units(150), depositInterest = Money.units(50), averageEarningAssets = Money.units(12_000))
        val at = MarginAnalytics.attribution(a, b)
        assertEquals(b.netInterestIncome - a.netInterestIncome, at.total)
        assertEquals(at.total, at.volume + at.rate + at.residual)
    }

    @Test fun attributionOfNoChangeIsZero() {
        val a = MonthlyResult.empty(30).copy(loanInterest = Money.units(100), averageEarningAssets = Money.units(1_000))
        val at = MarginAnalytics.attribution(a, a)
        assertEquals(Money.ZERO, at.total)
        assertEquals(Money.ZERO, at.volume)
        assertEquals(Money.ZERO, at.rate)
    }

    @Test fun trailingMarginUsesRecentMonths() {
        val b = BankingFx.bank()
        repeat(4) { b.record(MonthlyResult.empty(30L * (it + 1)).copy(loanInterest = Money.units(1_000), averageEarningAssets = Money.units(100_000)), 24) }
        val r = report(b)
        assertTrue(r.trailingMargin.ppm > 0L)
        assertEquals(4, r.monthlyMargin.size)
    }
}
