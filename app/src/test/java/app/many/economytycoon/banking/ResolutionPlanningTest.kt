package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionPlanningTest {
    private val params = BankingFx.params
    private val planner = ResolutionPlanner(params)
    private val idx = Rate.ONE_PPM

    private fun hit(b: Bank, share: Rate) {
        val loss = b.snapshot().cet1Capital.times(share)
        b.reserves -= loss; b.retained -= loss
    }

    private fun trigger(b: Bank, liquidityFailed: Boolean = false, months: Int = 0): ResolutionTrigger? {
        val s = b.snapshot()
        return planner.trigger(s, CapitalEngine.position(s, params.capital), liquidityFailed, months)
    }

    @Test fun waterfallPaysInOrderUntilMoneyRunsOut() {
        val claims = listOf(
            BankClaim(ClaimRank.UNINSURED_DEPOSITS, Money.units(50)), BankClaim(ClaimRank.INSURED_DEPOSITS, Money.units(100)),
            BankClaim(ClaimRank.SENIOR_WHOLESALE, Money.units(30)),
        )
        val d = BankWaterfall.distribute(Money.units(120), claims)
        assertEquals(ClaimRank.values().size, d.size)
        assertEquals(Money.units(100), d.first { it.rank == ClaimRank.INSURED_DEPOSITS }.paid)
        assertEquals(Money.units(20), d.first { it.rank == ClaimRank.UNINSURED_DEPOSITS }.paid)
        assertEquals(Money.ZERO, d.first { it.rank == ClaimRank.SENIOR_WHOLESALE }.paid)
        assertEquals(Money.units(120), Money.sum(d.map { it.paid }))
    }

    @Test fun recoveryIsFullForUnclaimedRanks() {
        val d = BankWaterfall.distribute(Money.units(10), emptyList())
        assertTrue(d.all { it.recovery == Rate.ONE })
        val e = BankWaterfall.distribute(Money.ZERO, listOf(BankClaim(ClaimRank.CENTRAL_BANK, Money.units(4))))
        assertEquals(Rate.ZERO, e.first().recovery)
        assertEquals(Money.units(4), e.first().loss)
    }

    @Test(expected = IllegalArgumentException::class) fun waterfallRejectsNegativeProceeds() {
        BankWaterfall.distribute(Money.units(-1), emptyList())
    }

    @Test fun claimsMatchTheBalanceSheet() {
        val b = BankingFx.bank()
        CentralBankFacilities.borrowMarginal(b, Money.units(100), params.facilities)
        val s = b.snapshot()
        val claims = BankWaterfall.claimsOf(s, params.deposits)
        assertEquals(s.centralBankDebt, claims.first { it.rank == ClaimRank.CENTRAL_BANK }.amount)
        val deposits = claims.filter { it.rank == ClaimRank.INSURED_DEPOSITS || it.rank == ClaimRank.UNINSURED_DEPOSITS }
        assertEquals(s.depositsTotal, Money.sum(deposits.map { it.amount }))
    }

    @Test fun goingConcernBeatsLiquidationValue() {
        val v = planner.valuation(BankingFx.bank().snapshot(), idx)
        assertTrue(v.goingConcern > v.liquidation)
    }

    @Test fun collateralCrashLowersBothValues() {
        val s = BankingFx.bank().snapshot()
        val ok = planner.valuation(s, idx)
        val bad = planner.valuation(s, 500_000L)
        assertTrue(bad.goingConcern < ok.goingConcern)
        assertTrue(bad.liquidation < ok.liquidation)
    }

    @Test fun healthyBankHasNoTrigger() {
        assertNull(trigger(BankingFx.bank()))
    }

    @Test fun liquidityFailureTriggersResolution() {
        assertEquals(ResolutionTrigger.LIQUIDITY_FAILURE, trigger(BankingFx.bank(), liquidityFailed = true))
    }

    @Test fun negativeEquityIsInsolvent() {
        val b = BankingFx.bank()
        hit(b, Rate.percent(2_000))
        assertEquals(ResolutionTrigger.INSOLVENT, trigger(b))
    }

    @Test fun veryThinCapitalCollapses() {
        val b = BankingFx.bank()
        val s = b.snapshot()
        val target = CapitalEngine.rwa(s, params.capital).total.times(Rate.basisPoints(100))
        val loss = s.cet1Capital - target
        b.reserves -= loss; b.retained -= loss
        assertEquals(ResolutionTrigger.CAPITAL_COLLAPSE, trigger(b))
    }

    @Test fun failedRestorationPlanTriggers() {
        val b = BankingFx.bank()
        val s = b.snapshot()
        val target = CapitalEngine.rwa(s, params.capital).total.times(Rate.basisPoints(350))
        val loss = s.cet1Capital - target
        b.reserves -= loss; b.retained -= loss
        assertNull(trigger(b, months = 1))
        assertEquals(ResolutionTrigger.RESTORATION_FAILED, trigger(b, months = params.capital.restorationMonths))
    }

    @Test fun resolvedBanksAreNotTriggeredAgain() {
        val b = BankingFx.bank()
        b.changeStatus(BankStatus.MERGED, Tick(1))
        assertNull(trigger(b, liquidityFailed = true))
    }

    @Test fun strongBankCanAbsorbAWeakOne() {
        val strong = BankingFx.bank(1, capital = 5_000_000L)
        val weak = BankingFx.bank(2, capital = 500_000L)
        assertTrue(planner.canAbsorb(strong.snapshot(), weak.snapshot(), idx))
    }

    @Test fun weakOrBusyBanksCannotAbsorb() {
        val weak = BankingFx.bank(1, capital = 500_000L)
        val big = BankingFx.bank(2, capital = 5_000_000L)
        assertFalse(planner.canAbsorb(weak.snapshot(), big.snapshot(), idx))
        val running = BankingFx.bank(3, capital = 5_000_000L); running.applyConfidence(400_000L, RunStage.RUN)
        assertFalse(planner.canAbsorb(running.snapshot(), weak.snapshot(), idx))
    }

    @Test fun acquirerIsTheBestCapitalisedEligibleBankInTheSameCountry() {
        val target = BankingFx.bank(1, capital = 300_000L)
        val a = BankingFx.bank(2, capital = 6_000_000L)
        val b = BankingFx.bank(3, capital = 8_000_000L)
        val picked = planner.pickAcquirer(target.snapshot(), listOf(a.snapshot(), b.snapshot(), target.snapshot()), idx)
        assertNotNull(picked)
        assertTrue(picked!!.id != target.id)
        assertNull(planner.pickAcquirer(target.snapshot(), emptyList(), idx))
    }

    @Test fun mildLossesFitInsideCapitalInstrumentsAndAreRecapitalised() {
        val b = BankingFx.bank()
        b.additionalTier1 = Money.units(200_000); b.reserves += Money.units(200_000)
        hit(b, Rate.percent(95))
        val plan = planner.plan(b.snapshot(), ResolutionTrigger.CAPITAL_COLLAPSE, emptyList(), idx)
        assertEquals(ResolutionTool.RECAPITALISE, plan.tool)
        assertTrue(plan.rationale.isNotBlank())
    }

    @Test fun insolventBanksAreMergedWhenSomeoneCanTakeThemOver() {
        val weak = BankingFx.bank(1, capital = 300_000L)
        hit(weak, Rate.percent(1_500))
        val strong = BankingFx.bank(2, capital = 30_000_000L)
        val plan = planner.plan(weak.snapshot(), ResolutionTrigger.INSOLVENT, listOf(strong.snapshot()), idx)
        assertTrue(plan.tool == ResolutionTool.MERGER || plan.tool == ResolutionTool.LIQUIDATION)
        if (plan.tool == ResolutionTool.MERGER) assertEquals(strong.id, plan.acquirer)
    }

    @Test fun withNoBuyerTheBankIsWoundUp() {
        val weak = BankingFx.bank(1)
        hit(weak, Rate.percent(1_500))
        val plan = planner.plan(weak.snapshot(), ResolutionTrigger.INSOLVENT, emptyList(), idx)
        assertEquals(ResolutionTool.LIQUIDATION, plan.tool)
        assertNull(plan.acquirer)
        assertEquals(planner.liquidationCost(weak.snapshot(), idx), plan.estimatedFundCost)
    }

    private fun impair(b: Bank, share: Rate) {
        for (k in b.loans.keys.filter { it.stage == LoanStage.PERFORMING }) {
            val moved = b.removeLoans(k, b.loanPool(k).principal.times(share))
            b.addLoans(k.copy(stage = LoanStage.IMPAIRED), moved, 50_000L)
        }
    }

    @Test fun costsGrowAsTheLoanBookDeteriorates() {
        val a = BankingFx.bank(1); val b = BankingFx.bank(2)
        impair(a, Rate.percent(20)); impair(b, Rate.percent(60))
        assertTrue(planner.mergerCost(b.snapshot(), idx) >= planner.mergerCost(a.snapshot(), idx))
        assertTrue(planner.liquidationCost(b.snapshot(), idx) >= planner.liquidationCost(a.snapshot(), idx))
        assertTrue(planner.valuation(b.snapshot(), idx).goingConcern < planner.valuation(a.snapshot(), idx).goingConcern)
    }
}
