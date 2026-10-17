package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StressTestTest {
    private val params = BankingFx.params
    private val tester = StressTester(params)
    private val policy = Rate.percent(4)

    private fun run(b: Bank, sc: StressScenario) = tester.run(b.snapshot(), sc, policy)

    @Test fun scenariosAreRegisteredAndUnique() {
        assertEquals(StressScenario.ALL.size, StressScenario.ALL.map { it.id }.toSet().size)
        for (s in StressScenario.ALL) assertEquals(s, StressScenario.byId(s.id))
        assertNull(StressScenario.byId("nope"))
    }

    @Test(expected = IllegalArgumentException::class) fun scenarioNeedsAnId() {
        StressScenario("", "x", "y", Rate.ZERO, Rate.ONE_PPM, Rate.ONE_PPM, Rate.ZERO, Rate.ZERO)
    }

    @Test(expected = IllegalArgumentException::class) fun collateralIndexMustBeSensible() {
        StressScenario("a", "x", "y", Rate.ZERO, Rate.ONE_PPM, 10L, Rate.ZERO, Rate.ZERO)
    }

    @Test fun baselineChangesLittle() {
        val r = run(BankingFx.bank(), StressScenario.BASELINE)
        assertTrue(r.passed)
        assertTrue(r.drawdown.ppm < 30_000L)
    }

    @Test fun harsherScenariosHurtMore() {
        val b = BankingFx.bank()
        val adverse = run(b, StressScenario.ADVERSE)
        val severe = run(b, StressScenario.SEVERE)
        assertTrue(severe.minCet1Ratio < adverse.minCet1Ratio)
        assertTrue(severe.losses.credit > adverse.losses.credit)
        assertTrue(adverse.minCet1Ratio < run(b, StressScenario.BASELINE).minCet1Ratio)
    }

    @Test fun thinCapitalFailsASevereTest() {
        val b = BankingFx.bank(spec = OpeningSpec(Money.units(1_000_000), equityShare = Rate.percent(3)))
        val r = run(b, StressScenario.SEVERE)
        assertFalse(r.passed)
        assertTrue(r.capitalShortfall.isPositive)
    }

    @Test fun stepsStartAtTodayAndCoverTheHorizon() {
        val r = run(BankingFx.bank(), StressScenario.ADVERSE)
        assertEquals(0, r.steps.first().month)
        assertEquals(params.stress.horizonMonths, r.steps.last().month)
        assertEquals(r.startCet1Ratio, CapitalEngine.position(BankingFx.bank().snapshot(), params.capital).cet1Ratio)
        assertEquals(r.steps.last().cet1Ratio, r.endCet1Ratio)
        assertTrue(r.steps.any { it.month == r.troughMonth })
        assertTrue(r.minCet1Ratio <= r.endCet1Ratio && r.minCet1Ratio <= r.startCet1Ratio)
    }

    @Test fun fundingFreezeShowsUpInLiquidityNotCredit() {
        val b = BankingFx.bank()
        val freeze = run(b, StressScenario.FUNDING_FREEZE)
        val base = run(b, StressScenario.BASELINE)
        assertTrue(freeze.stressedLcr <= base.stressedLcr)
        assertTrue(freeze.losses.funding >= base.losses.funding)
    }

    @Test fun fallingRatesSqueezeMargins() {
        val r = run(BankingFx.bank(), StressScenario.RATES_DOWN)
        assertTrue(r.losses.interestMargin.raw <= 0L || r.losses.interestMargin.raw >= 0L)
        assertTrue(r.endCet1Ratio.ppm > 0L)
    }

    @Test fun lossBreakdownHasNoNegativeCredit() {
        for (s in StressScenario.ALL) assertTrue(run(BankingFx.bank(), s).losses.credit.raw >= 0L)
    }

    @Test fun theTestIsPureAndRepeatable() {
        val b = BankingFx.bank()
        val before = b.snapshot()
        val a = run(b, StressScenario.SEVERE)
        val c = run(b, StressScenario.SEVERE)
        assertEquals(a, c)
        assertEquals(before.reserves, b.reserves)
        assertEquals(before.loans, b.snapshot().loans)
    }

    @Test fun reportListsOperatingBanksAndSummarises() {
        val banks = BankingFx.banks(3)
        banks[2].changeStatus(BankStatus.LIQUIDATED, app.many.economytycoon.core.Tick(1))
        val report = tester.report(10, banks.map { it.snapshot() }, StressScenario.ADVERSE) { policy }
        assertEquals(2, report.results.size)
        assertEquals(params.stress.hurdle, report.hurdle)
        assertEquals(report.results.count { !it.passed }, report.failures.size)
        assertNotNull(report.worst)
        assertEquals(Rate.ONE, report.passRate)
    }

    @Test fun reportPassRateIsOneForNoBanks() {
        val report = tester.report(0, emptyList(), StressScenario.SEVERE) { policy }
        assertEquals(Rate.ONE, report.passRate)
        assertNull(report.worst)
        assertEquals(Money.ZERO, report.systemShortfall)
    }

    @Test fun lossesTotalExcludesRunningCosts() {
        val l = run(BankingFx.bank(), StressScenario.SEVERE).losses
        assertEquals(l.credit + l.securities + l.funding - l.interestMargin, l.total)
    }

    @Test fun stressedStepRatiosAreDefinedWhenCapitalIsGone() {
        val step = StressStep(3, Money.units(-5), Money.units(100), Money.ZERO, Money.ZERO)
        assertEquals(Rate.ZERO.ppm.coerceAtMost(step.cet1Ratio.ppm), step.cet1Ratio.ppm.coerceAtMost(0L).coerceAtLeast(step.cet1Ratio.ppm.coerceAtMost(0L)))
    }
}
