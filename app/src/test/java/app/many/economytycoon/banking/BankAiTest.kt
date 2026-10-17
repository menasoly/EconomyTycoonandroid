package app.many.economytycoon.banking

import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankAiTest {
    private val params = BankingFx.params
    private val ai = BankAiPlanner(params)
    private val strategy = BankStrategies.of(BankArchetype.BALANCED)

    private fun view(b: Bank) = BankView(b.snapshot(), BankingFx.calm, params)
    private fun plan(b: Bank, st: BankStrategy = strategy, seed: Long = 1L) = ai.plan(view(b), st, DeterministicRandom(seed))

    @Test fun restingPremiumFollowsAggression() {
        val calm = BankStrategies.of(BankArchetype.CONSERVATIVE_RETAIL)
        val hungry = BankStrategies.of(BankArchetype.AGGRESSIVE_GROWTH)
        for (p in DepositProduct.values()) assertTrue(ai.restingPremium(hungry, p) >= ai.restingPremium(calm, p))
        assertTrue(ai.restingPremium(hungry, DepositProduct.SAVINGS) > ai.restingPremium(hungry, DepositProduct.DEMAND))
    }

    @Test fun planningHappensEveryThinkInterval() {
        val s = BankingFx.bank().snapshot()
        val period = strategy.thinkMonths * 30L
        assertTrue(ai.isDue(s, strategy, period, 0L))
        assertFalse(ai.isDue(s, strategy, period + 1L, 0L))
        assertTrue(ai.isDue(s, strategy, period + 7L, period - 7L))
    }

    @Test fun deadBanksPlanNothing() {
        val b = BankingFx.bank()
        b.changeStatus(BankStatus.LIQUIDATED, Tick(1))
        assertTrue(plan(b).isEmpty())
    }

    @Test fun planningIsDeterministic() {
        val b = BankingFx.bank()
        assertEquals(plan(b, seed = 5L), plan(b, seed = 5L))
    }

    @Test fun everyActionTargetsTheBankItself() {
        val b = BankingFx.bank()
        assertTrue(plan(b).all { it.bank == b.id })
    }

    @Test fun capitalShortageSlowsGrowthAndStopsPayouts() {
        val b = BankingFx.bank()
        val loss = b.snapshot().cet1Capital.times(Rate.percent(45))
        b.reserves -= loss; b.retained -= loss
        val actions = plan(b)
        assertTrue(actions.any { it is BankAction.SetCapitalPolicy && it.dividendPayout == Rate.ZERO })
        assertTrue(actions.any { it is BankAction.SetGrowthAppetite })
    }

    @Test fun severeShortageRaisesCapital() {
        val b = BankingFx.bank()
        val s = b.snapshot()
        val target = CapitalEngine.rwa(s, params.capital).total.times(Rate.percent(6))
        val loss = s.cet1Capital - target
        b.reserves -= loss; b.retained -= loss
        val raise = plan(b).filterIsInstance<BankAction.RaiseCapital>()
        assertTrue(raise.isNotEmpty())
        assertTrue(raise.all { it.amount.isPositive })
    }

    @Test fun cashRichBanksBuyGovernmentPaper() {
        val b = BankingFx.bank()
        val extra = b.snapshot().totalAssets.times(Rate.percent(30))
        b.reserves += extra; b.retained += extra
        val buys = plan(b).filterIsInstance<BankAction.BuySecurities>()
        assertTrue(buys.isNotEmpty())
        assertTrue(buys.all { it.kind != SecurityKind.CORPORATE_BONDS })
    }

    @Test fun lowLiquidityBuildsTheBuffer() {
        val b = BankingFx.bank()
        val st = strategy.copy(liquidityCushion = Rate.percent(3_000))
        val actions = plan(b, st)
        assertTrue(actions.any { it is BankAction.SetFundingPolicy })
    }

    @Test fun nervousDepositorsGetReassurance() {
        val b = BankingFx.bank(); b.applyConfidence(600_000L, RunStage.WATCHFUL)
        assertTrue(plan(b).any { it is BankAction.ReassureDepositors })
        assertFalse(plan(BankingFx.bank(2)).any { it is BankAction.ReassureDepositors })
    }

    @Test fun aSqueezedMarginRaisesLoanPrices() {
        val b = BankingFx.bank()
        repeat(4) { b.record(MonthlyResult.empty(30L * (it + 1)).copy(loanInterest = Money.units(1_000), depositInterest = Money.units(990), averageEarningAssets = Money.units(1_000_000)), 24) }
        assertTrue(plan(b).any { it is BankAction.SetLoanMargin && it.basisPoints > b.policy.loanMarginBps })
    }

    @Test fun overConcentratedSectorsAreClosed() {
        val b = BankingFx.bank()
        b.changePolicy(b.policy.copy(maxSectorShare = Rate.percent(10)))
        assertTrue(plan(b).any { it is BankAction.SetSectorAppetite && it.appetite == Rate.ZERO })
    }

    @Test fun plannedActionsAreAcceptedByTheService() {
        val s = BankingService()
        val r = app.many.economytycoon.core.RandomProvider(3L)
        val ids = s.seedRivals(BankingFx.country, 3, BankingFx.calm, Tick(0), r)
        for (t in 1L..120L) s.step(Tick(t), BankingFx.calm, r)
        for (id in ids) {
            val v = s.view(id, BankingFx.calm)!!
            val actions = ai.plan(v, s.strategyOf(id)!!, DeterministicRandom(id.value))
            for (a in actions) {
                val result = s.apply(a, Tick(121), BankingFx.calm, null)
                // Actions may be turned down for good reasons, but never for being malformed.
                val rule = result.rejectionOrNull()?.rule
                assertTrue("$a -> $rule", rule == null || rule != app.many.economytycoon.core.Rules.INVALID_ARGUMENT)
            }
        }
    }
}
