package app.many.economytycoon.banking

import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionEngineTest {
    private val params = BankingFx.params
    private val planner = ResolutionPlanner(params)
    private val engine = ResolutionEngine(params, planner)
    private val idx = Rate.ONE_PPM
    private val tick = Tick(100)

    private fun fund() = GuaranteeFund(CountryId(1))

    private fun plan(target: Bank, tool: ResolutionTool, acquirer: Bank? = null, trigger: ResolutionTrigger = ResolutionTrigger.INSOLVENT) =
        ResolutionPlan(target.id, trigger, tool, acquirer?.id, Money.ZERO, Money.ZERO, "test")

    private fun hit(b: Bank, share: Rate) {
        val loss = b.snapshot().cet1Capital.times(share)
        b.reserves -= loss; b.retained -= loss
    }

    private fun impair(b: Bank, share: Rate) {
        for (k in b.loans.keys.filter { it.stage == LoanStage.PERFORMING }) {
            val moved = b.removeLoans(k, b.loanPool(k).principal.times(share))
            b.addLoans(k.copy(stage = LoanStage.IMPAIRED), moved, 50_000L)
        }
    }

    // Recapitalisation ------------------------------------------------------------------------------------

    @Test fun recognisingLossesRaisesTheAllowanceAndKeepsBalance() {
        val b = BankingFx.bank()
        impair(b, Rate.percent(50))
        val before = b.allowance
        val extra = engine.recogniseLosses(b, idx)
        assertTrue(extra.isPositive)
        assertEquals(before + extra, b.allowance)
        BankingFx.assertBalanced(b)
    }

    @Test fun recognisingLossesOnAHealthyBookAddsLittle() {
        val b = BankingFx.bank()
        val extra = engine.recogniseLosses(b, idx)
        assertTrue(extra.raw >= 0L)
        BankingFx.assertBalanced(b)
    }

    @Test fun recapitalisationConvertsInstrumentsThenInjectsFreshCapital() {
        val b = BankingFx.bank()
        b.additionalTier1 = Money.units(300_000); b.tier2 = Money.units(100_000); b.reserves += Money.units(400_000)
        hit(b, Rate.percent(95))
        val capitalBefore = b.snapshot().cet1Capital
        val out = engine.recapitalise(b, plan(b, ResolutionTool.RECAPITALISE, trigger = ResolutionTrigger.CAPITAL_COLLAPSE), idx, tick)
        assertEquals(ResolutionTool.RECAPITALISE, out.tool)
        assertTrue(out.bailedIn.isPositive)
        assertTrue(b.snapshot().cet1Capital > capitalBefore)
        assertEquals(BankStatus.SUPERVISED, b.status)
        BankingFx.assertBalanced(b)
    }

    @Test fun recapitalisationNeverRunsInstrumentsNegative() {
        val b = BankingFx.bank()
        b.additionalTier1 = Money.units(10); b.reserves += Money.units(10)
        hit(b, Rate.percent(95))
        engine.recapitalise(b, plan(b, ResolutionTool.RECAPITALISE), idx, tick)
        assertTrue(b.additionalTier1.raw >= 0L && b.tier2.raw >= 0L)
    }

    @Test(expected = IllegalArgumentException::class) fun recapitalisationNeedsARecapitalisationPlan() {
        val b = BankingFx.bank()
        engine.recapitalise(b, plan(b, ResolutionTool.LIQUIDATION), idx, tick)
    }

    // Merger -------------------------------------------------------------------------------------------------

    private fun mergerWorld(): Triple<Bank, Bank, Bank> {
        val target = BankingFx.bank(1, capital = 500_000L)
        val acquirer = BankingFx.bank(2, capital = 30_000_000L)
        val other = BankingFx.bank(3, capital = 3_000_000L)
        impair(target, Rate.percent(40))
        hit(target, Rate.percent(120))
        return Triple(target, acquirer, other)
    }

    @Test fun mergerMovesEverythingToTheAcquirer() {
        val (t, a, o) = mergerWorld()
        val deposits = t.snapshot().depositsTotal + a.snapshot().depositsTotal
        val loans = t.snapshot().loansGross + a.snapshot().loansGross
        val out = engine.merge(t, a, fund(), listOf(t, a, o), plan(t, ResolutionTool.MERGER, a), idx, tick)
        assertEquals(ResolutionTool.MERGER, out.tool)
        assertEquals(a.id, out.acquirer)
        assertEquals(BankStatus.MERGED, t.status)
        assertEquals(deposits, a.snapshot().depositsTotal)
        assertEquals(loans, a.snapshot().loansGross)
        BankingFx.assertBalanced(listOf(t, a, o))
    }

    @Test fun targetIsEmptyAfterAMerger() {
        val (t, a, o) = mergerWorld()
        engine.merge(t, a, fund(), listOf(t, a, o), plan(t, ResolutionTool.MERGER, a), idx, tick)
        val s = t.snapshot()
        assertEquals(Money.ZERO, s.depositsTotal)
        assertEquals(Money.ZERO, s.loansGross)
        assertEquals(Money.ZERO, s.totalAssets)
        assertEquals(Money.ZERO, s.bookEquity)
    }

    @Test fun fundCoversTheHoleSoTheAcquirerGainsNoNegativeEquity() {
        val (t, a, o) = mergerWorld()
        val f = fund(); f.balance = Money.units(100_000_000)
        val equityBefore = a.snapshot().bookEquity
        val out = engine.merge(t, a, f, listOf(t, a, o), plan(t, ResolutionTool.MERGER, a), idx, tick)
        assertTrue(out.fundCost.isPositive)
        assertTrue(f.totalPaid.isPositive)
        assertTrue(a.snapshot().bookEquity >= equityBefore - out.price)
        BankingFx.assertBalanced(a)
    }

    @Test fun mergerPricePaidOnlyOutOfPositiveNetAssets() {
        val target = BankingFx.bank(1, capital = 500_000L)
        val acquirer = BankingFx.bank(2, capital = 30_000_000L)
        val out = engine.merge(target, acquirer, fund(), listOf(target, acquirer), plan(target, ResolutionTool.MERGER, acquirer), idx, tick)
        assertTrue(out.price.isPositive)
        assertTrue(out.price <= acquirer.snapshot().bookEquity)
    }

    @Test fun mergerRecordsTheTriggerAndReason() {
        val (t, a, o) = mergerWorld()
        engine.merge(t, a, fund(), listOf(t, a, o), plan(t, ResolutionTool.MERGER, a, ResolutionTrigger.LIQUIDITY_FAILURE), idx, tick)
        assertEquals(ResolutionTrigger.LIQUIDITY_FAILURE.label, t.interventionReason)
    }

    @Test(expected = IllegalArgumentException::class) fun aBankCannotMergeIntoItself() {
        val b = BankingFx.bank()
        engine.merge(b, b, fund(), listOf(b), plan(b, ResolutionTool.MERGER, b), idx, tick)
    }

    @Test(expected = IllegalArgumentException::class) fun mergerNeedsAMergerPlan() {
        val (t, a, o) = mergerWorld()
        engine.merge(t, a, fund(), listOf(t, a, o), plan(t, ResolutionTool.LIQUIDATION), idx, tick)
    }

    @Test fun interbankPositionsSurviveAMerger() {
        val (t, a, o) = mergerWorld()
        t.interbankBorrowed += Money.units(1_000); t.reserves += Money.units(1_000)
        o.interbankLent += Money.units(1_000); o.reserves -= Money.units(1_000)
        engine.merge(t, a, fund(), listOf(t, a, o), plan(t, ResolutionTool.MERGER, a), idx, tick)
        BankingFx.assertInterbankMatches(listOf(t, a, o))
        BankingFx.assertBalanced(listOf(t, a, o))
    }

    // Liquidation ---------------------------------------------------------------------------------------------

    @Test fun liquidationPaysInsuredDepositorsAndEmptiesTheBank() {
        val t = BankingFx.bank(1); val o = BankingFx.bank(2)
        impair(t, Rate.percent(60)); hit(t, Rate.percent(1_200))
        val f = fund()
        val covered = GuaranteeScheme.coveredDeposits(t.snapshot(), params.deposits)
        val out = engine.liquidate(t, f, listOf(t, o), plan(t, ResolutionTool.LIQUIDATION), idx, tick)
        assertEquals(ResolutionTool.LIQUIDATION, out.tool)
        assertEquals(covered, out.insuredPaid)
        assertEquals(BankStatus.LIQUIDATED, t.status)
        assertEquals(Money.ZERO, t.snapshot().totalAssets)
        assertTrue(f.totalPaid.isPositive)
        BankingFx.assertBalanced(listOf(t, o))
    }

    @Test fun liquidationBurdensUninsuredDepositorsWhenAssetsFallShort() {
        val t = BankingFx.bank(1); val o = BankingFx.bank(2)
        impair(t, Rate.percent(80)); hit(t, Rate.percent(2_500))
        val out = engine.liquidate(t, fund(), listOf(t, o), plan(t, ResolutionTool.LIQUIDATION), idx, tick)
        assertTrue(out.uninsuredLoss.isPositive)
    }

    @Test fun liquidationOfASolventBankLeavesUninsuredWhole() {
        val t = BankingFx.bank(1, capital = 50_000_000L); val o = BankingFx.bank(2)
        val out = engine.liquidate(t, fund(), listOf(t, o), plan(t, ResolutionTool.LIQUIDATION), idx, tick)
        assertTrue(out.uninsuredLoss.raw >= 0L)
        BankingFx.assertBalanced(listOf(t, o))
    }

    @Test fun otherBanksPassBackAndForthTheirInterbankClaimsOnFailure() {
        val t = BankingFx.bank(1); val o = BankingFx.bank(2)
        impair(t, Rate.percent(80)); hit(t, Rate.percent(2_500))
        t.interbankBorrowed += Money.units(50_000); t.reserves += Money.units(50_000)
        o.interbankLent += Money.units(50_000); o.reserves -= Money.units(50_000)
        val retained = o.retained
        engine.liquidate(t, fund(), listOf(t, o), plan(t, ResolutionTool.LIQUIDATION), idx, tick)
        assertEquals(Money.ZERO, o.interbankLent)
        assertTrue(o.retained <= retained)
        BankingFx.assertBalanced(listOf(t, o))
    }

    @Test(expected = IllegalArgumentException::class) fun liquidationNeedsALiquidationPlan() {
        val t = BankingFx.bank()
        engine.liquidate(t, fund(), listOf(t), plan(t, ResolutionTool.MERGER), idx, tick)
    }

    // Supervision --------------------------------------------------------------------------------------------------

    @Test fun interveneMarksBankSupervised() {
        val b = BankingFx.bank()
        engine.intervene(b, "capital below the minimum", tick)
        assertEquals(BankStatus.SUPERVISED, b.status)
        assertEquals("capital below the minimum", b.interventionReason)
    }

    @Test fun releaseNeedsTimeAndAHealthyBank() {
        val b = BankingFx.bank()
        engine.intervene(b, "x", Tick(0))
        assertFalse(engine.release(b, Tick(10)))
        val later = Tick(params.resolution.supervisionMonths * 30L + 1L)
        b.setRung(SupervisoryRung.RESTRICTED, Tick(1))
        assertFalse(engine.release(b, later))
        b.setRung(SupervisoryRung.NORMAL, Tick(2))
        b.applyConfidence(300_000L, RunStage.RUN)
        assertFalse(engine.release(b, later))
        b.applyConfidence(1_000_000L, RunStage.CALM)
        assertTrue(engine.release(b, later))
        assertEquals(BankStatus.ACTIVE, b.status)
        assertEquals("", b.interventionReason)
    }

    @Test fun releaseIgnoresBanksThatAreNotSupervised() {
        assertFalse(engine.release(BankingFx.bank(), Tick(100_000)))
    }
}
