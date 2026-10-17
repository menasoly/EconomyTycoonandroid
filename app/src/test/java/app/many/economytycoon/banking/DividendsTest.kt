package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DividendsTest {
    private val params = BankingFx.params

    private fun profitable(b: Bank, months: Int = 3, net: Long = 20_000L) {
        repeat(months) {
            b.record(MonthlyResult.empty(30L * (it + 1)).copy(loanInterest = Money.units(net), averageEarningAssets = Money.units(1_000_000)), 24)
        }
        b.retained += Money.units(net * months); b.reserves += Money.units(net * months)
    }

    private fun capacity(b: Bank): DividendCapacity {
        val s = b.snapshot()
        return Dividends.capacity(s, CapitalEngine.position(s, params.capital), LiquidityEngine.position(s, params.liquidity, params.deposits::insuredShare), params)
    }

    @Test fun noHistoryMeansNoDividend() {
        val c = capacity(BankingFx.bank())
        assertEquals(Money.ZERO, c.amount)
        assertEquals(DividendLimit.NO_PROFIT, c.limitedBy)
    }

    @Test fun paysThePayoutShareOfTheQuartersProfit() {
        val b = BankingFx.bank()
        profitable(b)
        val c = capacity(b)
        assertTrue(c.profit.isPositive)
        assertEquals(c.profit.times(b.policy.dividendPayout), c.policyAmount.coerceAtMost(c.profit.times(b.policy.dividendPayout)))
        assertTrue(c.amount.isPositive)
        assertTrue(c.amount <= c.policyAmount)
    }

    @Test fun supervisedBanksPayNothing() {
        val b = BankingFx.bank(); profitable(b)
        b.changeStatus(BankStatus.SUPERVISED, Tick(1))
        val c = capacity(b)
        assertEquals(Money.ZERO, c.amount)
        assertEquals(DividendLimit.SUPERVISOR, c.limitedBy)
    }

    @Test fun banksUnderRunPayNothing() {
        val b = BankingFx.bank(); profitable(b)
        b.applyConfidence(600_000L, RunStage.WATCHFUL)
        assertEquals(DividendLimit.SUPERVISOR, capacity(b).limitedBy)
    }

    @Test fun capitalBelowOwnTargetCutsTheDividend() {
        val b = BankingFx.bank(); profitable(b)
        b.changePolicy(b.policy.copy(capitalCushion = Rate.percent(15)))
        val c = capacity(b)
        assertTrue(c.amount < c.policyAmount)
        assertEquals(DividendLimit.CAPITAL_TARGET, c.limitedBy)
    }

    @Test fun lackOfSpareCashCutsTheDividend() {
        val b = BankingFx.bank(); profitable(b)
        val spare = b.reserves - LiquidityEngine.position(b.snapshot(), params.liquidity, params.deposits::insuredShare).desiredReserves
        b.reserves -= spare; b.retained -= spare
        val c = capacity(b)
        assertEquals(Money.ZERO, c.amount)
    }

    @Test fun lossesInTheQuarterMeanNoProfit() {
        val b = BankingFx.bank()
        repeat(3) { b.record(MonthlyResult.empty(30L * (it + 1)).copy(operatingCost = Money.units(50_000)), 24) }
        assertEquals(DividendLimit.NO_PROFIT, capacity(b).limitedBy)
    }

    @Test fun payingMovesCashOutOfRetainedEarnings() {
        val b = BankingFx.bank(); profitable(b)
        val reserves = b.reserves; val retained = b.retained
        val paid = Dividends.pay(b, Money.units(10_000))
        assertEquals(Money.units(10_000), paid)
        assertEquals(reserves - paid, b.reserves)
        assertEquals(retained - paid, b.retained)
        BankingFx.assertBalanced(b)
    }

    @Test fun payingIsLimitedByCashAndRetainedEarnings() {
        val b = BankingFx.bank()
        val paid = Dividends.pay(b, Money.units(900_000_000))
        assertTrue(paid <= b.snapshot().bookEquity.coerceAtLeast(Money.ZERO) + paid)
        assertTrue(b.reserves.raw >= 0L)
        BankingFx.assertBalanced(b)
        assertEquals(Money.ZERO, Dividends.pay(b, Money.ZERO))
    }

    @Test fun negativeRetainedEarningsPayNothing() {
        val b = BankingFx.bank()
        val x = b.retained + Money.units(10)
        b.retained -= x; b.reserves -= x
        assertEquals(Money.ZERO, Dividends.pay(b, Money.units(5)))
    }

    @Test fun periodIsAQuarter() { assertEquals(3, Dividends.PERIOD_MONTHS) }
}
