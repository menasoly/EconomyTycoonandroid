package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepositMarketTest {
    private val p = BankingFx.params.deposits

    private fun total(banks: List<Bank>) = Money.sum(banks.map { it.snapshot().depositsTotal })

    @Test fun marketRateFollowsBetaAndNeverGoesNegative() {
        val policy = Rate.percent(6)
        val demand = DepositMarket.marketRate(Depositor.HOUSEHOLD, DepositProduct.DEMAND, policy, p)
        val savings = DepositMarket.marketRate(Depositor.HOUSEHOLD, DepositProduct.SAVINGS, policy, p)
        val term = DepositMarket.marketRate(Depositor.HOUSEHOLD, DepositProduct.TERM, policy, p)
        assertTrue(demand < savings && savings < term)
        assertEquals(Rate.ZERO, DepositMarket.marketRate(Depositor.HOUSEHOLD, DepositProduct.SAVINGS, Rate.percent(-3), p))
        assertEquals(Rate.ZERO, DepositMarket.marketRate(Depositor.HOUSEHOLD, DepositProduct.DEMAND, Rate.ZERO, p))
    }

    @Test fun companiesBargainHarderThanHouseholds() {
        val policy = Rate.percent(5)
        assertTrue(DepositMarket.marketRate(Depositor.CORPORATE, DepositProduct.DEMAND, policy, p) > DepositMarket.marketRate(Depositor.HOUSEHOLD, DepositProduct.DEMAND, policy, p))
    }

    @Test fun offeredRateAddsPremiumWithinCaps() {
        val m = Rate.percent(2)
        assertEquals(Rate.percent(3), DepositMarket.offeredRate(m, 100, p, false))
        assertEquals(DepositMarket.offeredRate(m, p.maxPremiumBps, p, false), DepositMarket.offeredRate(m, 10_000, p, false))
        assertEquals(Rate.ZERO, DepositMarket.offeredRate(Rate.percent(1), -300, p, false))
    }

    @Test fun cappedBanksCannotPayAPremium() {
        val m = Rate.percent(2)
        assertEquals(m, DepositMarket.offeredRate(m, 200, p, true))
        assertTrue(DepositMarket.offeredRate(m, -100, p, true) < m)
    }

    @Test fun repricingSetsEveryBook() {
        val b = BankingFx.bank()
        DepositMarket.reprice(b, Rate.percent(6), p)
        for (d in Depositor.values()) for (pr in DepositProduct.values()) {
            val expected = DepositMarket.offeredRate(DepositMarket.marketRate(d, pr, Rate.percent(6), p), b.policy.premiumOf(pr), p, false)
            assertEquals(expected.ppm, b.depositPool(DepositKey(d, pr)).ratePpm)
        }
    }

    @Test fun supervisedSevereBanksAreCappedWhenRepricing() {
        val b = BankingFx.bank()
        b.changePolicy(b.policy.withPremium(DepositProduct.SAVINGS, 250))
        DepositMarket.reprice(b, Rate.percent(6), p)
        val rich = b.depositPool(DepositKey(Depositor.HOUSEHOLD, DepositProduct.SAVINGS)).ratePpm
        b.setRung(SupervisoryRung.SEVERE, app.many.economytycoon.core.Tick(1))
        DepositMarket.reprice(b, Rate.percent(6), p)
        assertTrue(b.depositPool(DepositKey(Depositor.HOUSEHOLD, DepositProduct.SAVINGS)).ratePpm < rich)
    }

    @Test fun weightRisesWithPremiumAndTrust() {
        val a = BankingFx.bank(1).snapshot()
        val b = BankingFx.bank(2)
        b.changePolicy(b.policy.withPremium(DepositProduct.SAVINGS, 150))
        assertTrue(DepositMarket.weight(b.snapshot(), DepositProduct.SAVINGS, p) > DepositMarket.weight(a, DepositProduct.SAVINGS, p))
        val c = BankingFx.bank(3); c.applyConfidence(500_000L, RunStage.WATCHFUL)
        assertTrue(DepositMarket.weight(c.snapshot(), DepositProduct.SAVINGS, p) < DepositMarket.weight(a, DepositProduct.SAVINGS, p))
        val d = BankingFx.bank(4); d.setRating(CreditRating.B, 300, 0, 0)
        assertTrue(DepositMarket.weight(d.snapshot(), DepositProduct.SAVINGS, p) < DepositMarket.weight(a, DepositProduct.SAVINGS, p))
    }

    @Test fun banksInARunAttractNoDeposits() {
        val b = BankingFx.bank(); b.applyConfidence(300_000L, RunStage.RUN)
        assertEquals(0L, DepositMarket.weight(b.snapshot(), DepositProduct.DEMAND, p))
    }

    @Test fun churnConservesMoneyAndBalances() {
        val banks = BankingFx.banks(3)
        banks[0].changePolicy(banks[0].policy.withPremium(DepositProduct.SAVINGS, 200))
        val before = total(banks)
        val moved = DepositMarket.churn(banks, Depositor.HOUSEHOLD, DepositProduct.SAVINGS, p)
        assertTrue(moved.isPositive)
        assertEquals(before, total(banks))
        BankingFx.assertBalanced(banks)
    }

    @Test fun aggressivePricingWinsDepositsOverTime() {
        val banks = BankingFx.banks(3)
        banks[0].changePolicy(banks[0].policy.withPremium(DepositProduct.SAVINGS, 250))
        val key = DepositKey(Depositor.HOUSEHOLD, DepositProduct.SAVINGS)
        val before = banks[0].depositPool(key).balance
        repeat(12) { DepositMarket.churn(banks, Depositor.HOUSEHOLD, DepositProduct.SAVINGS, p) }
        assertTrue(banks[0].depositPool(key).balance > before)
    }

    @Test fun churnNeedsAtLeastTwoBanks() {
        assertEquals(Money.ZERO, DepositMarket.churn(BankingFx.banks(1), Depositor.HOUSEHOLD, DepositProduct.SAVINGS, p))
    }

    @Test fun inflowIsSharedAndAddsReservesAndDeposits() {
        val banks = BankingFx.banks(3)
        val before = total(banks)
        val reserves = Money.sum(banks.map { it.reserves })
        val moved = DepositMarket.applyFlow(banks, Depositor.HOUSEHOLD, Money.units(300_000), p)
        assertEquals(Money.units(300_000), moved)
        assertEquals(before + moved, total(banks))
        assertEquals(reserves + moved, Money.sum(banks.map { it.reserves }))
        BankingFx.assertBalanced(banks)
    }

    @Test fun outflowTakesProportionallyAndIsLimitedByBalances() {
        val banks = BankingFx.banks(2)
        val before = total(banks)
        val moved = DepositMarket.applyFlow(banks, Depositor.CORPORATE, -Money.units(100_000), p)
        assertEquals(-Money.units(100_000), moved)
        assertEquals(before + moved, total(banks))
        val huge = DepositMarket.applyFlow(banks, Depositor.CORPORATE, -Money.units(900_000_000), p)
        assertTrue(huge.raw < 0L)
        BankingFx.assertBalanced(banks)
    }

    @Test fun zeroFlowOrNoBanksMoveNothing() {
        assertEquals(Money.ZERO, DepositMarket.applyFlow(BankingFx.banks(2), Depositor.HOUSEHOLD, Money.ZERO, p))
        assertEquals(Money.ZERO, DepositMarket.applyFlow(emptyList(), Depositor.HOUSEHOLD, Money.units(5), p))
    }

    @Test fun realisedBetaIsTheRatioOfChanges() {
        assertEquals(Rate.percent(50), DepositMarket.realisedBeta(Rate.percent(1), Rate.percent(2), Rate.percent(2), Rate.percent(4)))
        assertNull(DepositMarket.realisedBeta(Rate.percent(1), Rate.percent(2), Rate.percent(3), Rate.percent(3)))
    }
}
