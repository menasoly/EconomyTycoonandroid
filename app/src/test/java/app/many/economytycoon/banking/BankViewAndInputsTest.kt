package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankViewAndInputsTest {
    private val params = BankingFx.params
    private val c = BankingFx.country

    @Test fun staticYieldCurveRunsFromShortToLong() {
        val i = StaticBankingInputs(shortYield = Rate.percent(2), longYield = Rate.percent(6))
        assertEquals(Rate.percent(2), i.sovereignYield(c, 0))
        assertEquals(Rate.percent(6), i.sovereignYield(c, 120))
        assertEquals(Rate.percent(6), i.sovereignYield(c, 500))
        assertEquals(Rate.percent(4), i.sovereignYield(c, 60))
        assertEquals(Rate.percent(2), i.sovereignYield(c, -4))
    }

    @Test fun staticDefaultsAreCalm() {
        val i = StaticBankingInputs()
        for (s in LoanSector.values()) {
            assertEquals(Rate.ONE_PPM, i.defaultMultiplier(c, s))
            assertTrue(i.loanDemand(c, s).isPositive)
        }
        assertEquals(Rate.ONE_PPM, i.collateralIndex(c))
        assertTrue(i.savingsFlow(c, Depositor.HOUSEHOLD) > i.savingsFlow(c, Depositor.CORPORATE))
    }

    @Test fun recessionHurtsEveryChannel() {
        val calm = StaticBankingInputs()
        val bad = calm.recession(800_000L)
        assertTrue(bad.outputGap(c) < calm.outputGap(c))
        assertTrue(bad.unemployment(c) > calm.unemployment(c))
        assertTrue(bad.collateralIndex(c) < calm.collateralIndex(c))
        for (s in LoanSector.values()) {
            assertTrue(bad.defaultMultiplier(c, s) > calm.defaultMultiplier(c, s))
            assertTrue(bad.loanDemand(c, s) < calm.loanDemand(c, s))
        }
    }

    @Test fun recessionCollateralNeverCollapsesBelowItsFloor() {
        assertTrue(StaticBankingInputs().recession(10_000_000L).collateralIndex(c) >= 300_000L)
    }

    @Test fun viewComputesEachFigureOnceAndConsistently() {
        val b = BankingFx.bank().snapshot()
        val v = BankView(b, BankingFx.calm, params)
        assertTrue(v.capital === v.capital)
        assertTrue(v.liquidity === v.liquidity)
        assertTrue(v.margin === v.margin)
        assertEquals(CapitalEngine.position(b, params.capital), v.capital)
    }

    @Test fun cushionHeadroomIsHeadroomLessTheBanksOwnCushion() {
        val v = BankView(BankingFx.bank().snapshot(), BankingFx.calm, params)
        assertEquals(v.capital.headroom.ppm - v.bank.policy.capitalCushion.ppm, v.cushionHeadroom.ppm)
    }

    @Test fun securitiesShareIsAShareOfAssets() {
        val b = BankingFx.bank().snapshot()
        val v = BankView(b, BankingFx.calm, params)
        assertEquals(BankMath.ratio(b.securitiesValue(), b.totalAssets), v.securitiesShare)
    }

    @Test fun depositMomentumIsZeroWithoutHistoryAndPositiveWhenDepositsFall() {
        val b = BankingFx.bank()
        assertEquals(Rate.ZERO, BankView(b.snapshot(), BankingFx.calm, params).depositMomentum)
        b.record(MonthlyResult.empty(30).copy(averageDeposits = b.snapshot().depositsTotal + Money.units(100_000)), 24)
        assertTrue(BankView(b.snapshot(), BankingFx.calm, params).depositMomentum.ppm > 0L)
    }

    @Test fun policyRateComesFromTheInputs() {
        val v = BankView(BankingFx.bank().snapshot(), StaticBankingInputs(policy = Rate.percent(7)), params)
        assertEquals(Rate.percent(7), v.policyRate)
    }

    @Test fun paramsAreInternallyConsistent() {
        assertTrue(params.capital.minCet1 < params.capital.minTier1 && params.capital.minTier1 < params.capital.minTotal)
        assertTrue(params.capital.criticalCet1 < params.capital.severeCet1 && params.capital.severeCet1 < params.capital.minCet1)
        assertTrue(params.runs.panicThreshold < params.runs.runThreshold && params.runs.runThreshold < params.runs.calmThreshold)
        assertTrue(params.runs.fallSpeed > params.runs.recoverSpeed)
        assertTrue(params.maxPlayerBanks <= params.maxBanksPerCountry)
        assertTrue(params.deposits.insuredHousehold > params.deposits.insuredCorporate)
        assertTrue(params.deposits.betaDemand < params.deposits.betaSavings && params.deposits.betaSavings < params.deposits.betaTerm)
    }
}
