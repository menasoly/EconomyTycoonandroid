package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidityManagerTest {
    private val params = BankingFx.params
    private val mgr = LiquidityManager(params)

    private fun plan(b: Bank, share: Rate): Map<DepositKey, Money> =
        b.snapshot().deposits.mapValues { it.value.balance.times(share) }

    @Test fun payingWithdrawalsReducesDepositsAndReserves() {
        val b = BankingFx.bank()
        val deposits = b.snapshot().depositsTotal; val reserves = b.reserves
        val p = plan(b, Rate.percent(1))
        val paid = mgr.payWithdrawals(b, p)
        assertEquals(Money.sum(p.values), paid)
        assertEquals(deposits - paid, b.snapshot().depositsTotal)
        assertEquals(reserves - paid, b.reserves)
        assertEquals(paid, b.lastOutflow)
        BankingFx.assertBalanced(b)
    }

    @Test fun withdrawalsAreLimitedByWhatIsOnDeposit() {
        val b = BankingFx.bank()
        val key = DepositKey(Depositor.HOUSEHOLD, DepositProduct.DEMAND)
        val balance = b.depositPool(key).balance
        val paid = mgr.payWithdrawals(b, mapOf(key to balance + Money.units(1_000_000)))
        assertEquals(balance, paid)
        assertEquals(Money.ZERO, b.depositPool(key).balance)
    }

    @Test fun nothingToCoverWhenReservesAreAmple() {
        val b = BankingFx.bank()
        val day = mgr.cover(b, Money.ZERO, true)
        assertEquals(LiquidityDay.NONE.unmet, day.unmet)
        assertEquals(Money.ZERO, day.marginal + day.emergency + day.fireSold)
        assertFalse(day.failed)
    }

    @Test fun shortfallIsFirstCoveredAtTheOvernightFacility() {
        val b = BankingFx.bank()
        val required = LiquidityEngine.requiredReserves(b.snapshot(), params.liquidity)
        val take = b.reserves - required + Money.units(1_000)
        b.reserves -= take; b.retained -= take
        val day = mgr.cover(b, take, false)
        assertTrue(day.marginal.isPositive)
        assertFalse(day.failed)
        assertTrue(b.reserves >= required)
        BankingFx.assertBalanced(b)
    }

    @Test fun deepShortfallUsesEmergencyThenFireSale() {
        val b = BankingFx.bank()
        val big = b.reserves + b.snapshot().securitiesValue().times(Rate.percent(120))
        b.reserves -= big; b.retained -= big
        val day = mgr.cover(b, big, true)
        assertTrue(day.marginal.isPositive)
        assertTrue(day.emergency.isPositive || day.fireSold.isPositive)
        BankingFx.assertBalanced(b)
    }

    @Test fun fireSalesLoseMoney() {
        val b = BankingFx.bank()
        b.centralBankMarginal += Money.ZERO
        val retained = b.retained
        val need = b.reserves + Money.units(100_000)
        b.reserves -= need; b.retained -= need
        val before = b.retained
        val day = mgr.cover(b, need, true)
        if (day.fireSold.isPositive) assertTrue(b.retained < before)
        assertTrue(b.retained < retained)
    }

    @Test fun bankWithNothingToPledgeFails() {
        val b = BankingFx.bank()
        for (k in b.securities.keys.toList()) b.sellSecurities(k, Money.units(1_000_000_000), Rate.ZERO)
        for (k in b.loans.keys.toList()) b.removeLoans(k, Money.units(1_000_000_000))
        // With no securities and no loans there is nothing to pledge; the books are brought back into balance with equity.
        b.allowance = Money.ZERO
        b.retained -= b.snapshot().imbalance
        val huge = b.reserves + Money.units(10_000_000)
        b.reserves -= huge; b.retained -= huge
        val day = mgr.cover(b, huge, true)
        assertTrue(day.failed)
        assertTrue(day.unmet.isPositive)
    }

    @Test fun sweepRepaysFacilityDebtOutOfSpareCash() {
        val b = BankingFx.bank()
        CentralBankFacilities.borrowMarginal(b, Money.units(500), params.facilities)
        b.reserves += Money.units(5_000_000); b.retained += Money.units(5_000_000)
        val repaid = mgr.sweep(b)
        assertEquals(Money.units(500), repaid)
        assertEquals(Money.ZERO, b.centralBankDebt)
        BankingFx.assertBalanced(b)
    }

    @Test fun sweepDoesNothingWithoutDebtOrSpareCash() {
        val b = BankingFx.bank()
        assertEquals(Money.ZERO, mgr.sweep(b))
        CentralBankFacilities.borrowMarginal(b, Money.units(500), params.facilities)
        val tight = b.reserves - LiquidityEngine.position(b.snapshot(), params.liquidity, params.deposits::insuredShare).desiredReserves
        b.reserves -= tight + Money.units(10); b.retained -= tight + Money.units(10)
        assertEquals(Money.ZERO, mgr.sweep(b))
    }

    @Test fun aWholeRunDayStaysBalanced() {
        val b = BankingFx.bank()
        val paid = mgr.payWithdrawals(b, plan(b, Rate.percent(12)))
        mgr.cover(b, paid, true)
        BankingFx.assertBalanced(b)
    }
}
