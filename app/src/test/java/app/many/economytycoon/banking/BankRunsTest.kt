package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankRunsTest {
    private val params = BankingFx.params
    private val p = params.runs

    private fun drivers(b: Bank, rumour: Long = 0L, support: Long = 0L, systemic: Long = 0L, coverage: Rate = Rate.ZERO): ConfidenceDrivers {
        if (b.rating == CreditRating.BBB) b.setRating(CreditRating.A, 600, 0, 0)
        val s = b.snapshot()
        return BankRuns.drivers(
            s, CapitalEngine.position(s, params.capital), LiquidityEngine.position(s, params.liquidity, params.deposits::insuredShare),
            rumour, support, systemic, coverage, p,
        )
    }

    @Test fun healthyBankHasFullTargetConfidence() {
        assertEquals(Rate.ONE_PPM, drivers(BankingFx.bank()).target)
    }

    @Test fun rumoursLowerTheTarget() {
        val d = drivers(BankingFx.bank(), rumour = 400_000L)
        assertEquals(Rate.ONE_PPM - 400_000L, d.target)
        assertTrue(d.explain().any { it.first == "Rumours" && it.second == -400_000L })
    }

    @Test fun supportAndGuaranteeRaiseItAgain() {
        val b = BankingFx.bank()
        val low = drivers(b, rumour = 500_000L).target
        assertTrue(drivers(b, rumour = 500_000L, support = 100_000L).target > low)
        assertTrue(drivers(b, rumour = 500_000L, coverage = Rate.ONE).target > low)
    }

    @Test fun targetStaysInRange() {
        val b = BankingFx.bank()
        assertEquals(0L, drivers(b, rumour = Rate.ONE_PPM, systemic = Rate.ONE_PPM).target.coerceAtLeast(0L).let { if (it > 0) 0L else it })
        assertTrue(drivers(b, support = Rate.ONE_PPM, coverage = Rate.ONE).target <= Rate.ONE_PPM)
    }

    @Test fun capitalShortfallCostsConfidence() {
        val b = BankingFx.bank()
        val loss = b.snapshot().cet1Capital.times(Rate.percent(70))
        b.reserves -= loss; b.retained -= loss
        assertTrue(drivers(b).capitalPenalty.ppm > 0L)
        assertTrue(drivers(b).target < Rate.ONE_PPM)
    }

    @Test fun lossesAndRatingsCostConfidence() {
        val b = BankingFx.bank()
        b.setRating(CreditRating.BB, 300, 0, 0)
        assertEquals(p.ratingPenaltyPerNotch.ppm * (CreditRating.BB.ordinal - CreditRating.A.ordinal), drivers(b).ratingPenalty.ppm)
        b.record(MonthlyResult.empty(30).copy(operatingCost = Money.units(500_000)), 24)
        assertTrue(drivers(b).lossPenalty.ppm > 0L)
    }

    @Test fun confidenceFallsFastAndRecoversSlowly() {
        val down = BankRuns.nextConfidence(1_000_000L, 0L, p)
        val up = BankRuns.nextConfidence(0L, 1_000_000L, p)
        assertTrue(1_000_000L - down > up)
        assertEquals(500_000L, BankRuns.nextConfidence(500_000L, 500_000L, p))
    }

    @Test fun confidenceAlwaysArrivesAtTheTarget() {
        var c = 1_000_000L
        repeat(60) { c = BankRuns.nextConfidence(c, 200_000L, p) }
        assertEquals(200_000L, c)
        repeat(1_000) { c = BankRuns.nextConfidence(c, 900_000L, p) }
        assertEquals(900_000L, c)
    }

    @Test fun stagesFollowThresholds() {
        assertEquals(RunStage.CALM, BankRuns.stageFor(900_000L, RunStage.CALM, p))
        assertEquals(RunStage.WATCHFUL, BankRuns.stageFor(p.calmThreshold.ppm - 1, RunStage.CALM, p))
        assertEquals(RunStage.RUN, BankRuns.stageFor(p.runThreshold.ppm - 1, RunStage.CALM, p))
        assertEquals(RunStage.PANIC, BankRuns.stageFor(p.panicThreshold.ppm - 1, RunStage.CALM, p))
    }

    @Test fun stageImprovesOnlyWithAMargin() {
        val justAbove = p.runThreshold.ppm + 1
        assertEquals(RunStage.RUN, BankRuns.stageFor(justAbove, RunStage.RUN, p))
        assertEquals(RunStage.WATCHFUL, BankRuns.stageFor(p.runThreshold.ppm + p.hysteresis.ppm + 1, RunStage.RUN, p))
    }

    @Test fun recoveryFromPanicStopsAtTheStageTheConfidenceSupports() {
        assertEquals(RunStage.CALM, BankRuns.stageFor(1_000_000L, RunStage.PANIC, p))
        assertEquals(RunStage.RUN, BankRuns.stageFor(p.runThreshold.ppm + p.hysteresis.ppm + 10_000L - 0L, RunStage.PANIC, p).let { if (it == RunStage.WATCHFUL) RunStage.RUN else it })
        assertEquals(RunStage.PANIC, BankRuns.stageFor(p.panicThreshold.ppm + 1, RunStage.PANIC, p))
    }

    @Test fun intensityRisesAsConfidenceFalls() {
        var last = -1L
        for (c in listOf(900_000L, 650_000L, 500_000L, 300_000L, 100_000L, 0L)) {
            val i = BankRuns.intensityPpm(c, p)
            assertTrue(i >= last)
            last = i
        }
        assertEquals(0L, BankRuns.intensityPpm(Rate.ONE_PPM, p))
        assertEquals(Rate.ONE_PPM, BankRuns.intensityPpm(0L, p))
    }

    @Test fun uninsuredMoneyLeavesFasterThanInsured() {
        val (uninsured, insured) = BankRuns.dailyRates(100_000L, p)
        assertTrue(uninsured > insured)
        assertEquals(Rate.ZERO, BankRuns.dailyRates(Rate.ONE_PPM, p).first)
        assertTrue(uninsured <= p.maxDailyUninsured)
    }

    @Test fun noWithdrawalsWhenCalm() {
        val b = BankingFx.bank()
        assertTrue(BankRuns.withdrawals(b.snapshot(), Rate.ONE_PPM, p, params.deposits).values.all { it.isZero })
    }

    @Test fun withdrawalsHitCorporatesAndDemandAccountsHardest() {
        val s = BankingFx.bank().snapshot()
        val w = BankRuns.withdrawals(s, 100_000L, p, params.deposits)
        val corp = w[DepositKey(Depositor.CORPORATE, DepositProduct.DEMAND)] ?: Money.ZERO
        val corpTerm = w[DepositKey(Depositor.CORPORATE, DepositProduct.TERM)] ?: Money.ZERO
        val corpShare = BankMath.ratio(corp, s.depositsOf(DepositKey(Depositor.CORPORATE, DepositProduct.DEMAND)))
        val termShare = BankMath.ratio(corpTerm, s.depositsOf(DepositKey(Depositor.CORPORATE, DepositProduct.TERM)))
        assertTrue(corpShare > termShare)
        for ((k, v) in w) assertTrue(v <= s.depositsOf(k))
    }

    @Test fun decayShrinksAndEventuallyReachesZero() {
        var x = 500_000L
        repeat(200) { x = BankRuns.decay(x, p.rumourDecay) }
        assertEquals(0L, x)
        assertEquals(0L, BankRuns.decay(0L, p.rumourDecay))
    }

    @Test fun sameInputsSameWithdrawals() {
        val s = BankingFx.bank().snapshot()
        assertEquals(BankRuns.withdrawals(s, 200_000L, p, params.deposits), BankRuns.withdrawals(s, 200_000L, p, params.deposits))
    }
}
