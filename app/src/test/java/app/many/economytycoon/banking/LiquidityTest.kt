package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidityTest {
    private val rules = BankingFx.params.liquidity
    private val insured = BankingFx.params.deposits::insuredShare

    private fun pos(b: Bank) = LiquidityEngine.position(b.snapshot(), rules, insured)

    @Test fun freshBankMeetsEveryStandard() {
        val p = pos(BankingFx.bank())
        assertTrue(p.lcr >= Rate.ONE)
        assertTrue(p.nsfr >= Rate.ONE)
        assertTrue(p.meetsReserve)
        assertEquals(Money.ZERO, p.reserveShortfall)
    }

    @Test fun requiredReservesAreHigherForDemandDeposits() {
        val b = BankingFx.bank()
        val demand = b.snapshot().deposits.filterKeys { it.product == DepositProduct.DEMAND }.values.fold(Money.ZERO) { a, d -> a + d.balance }
        val required = LiquidityEngine.requiredReserves(b.snapshot(), rules)
        assertTrue(required >= demand.times(rules.reserveRatioDemand))
    }

    @Test fun reservesCountAsHighQualityLiquidAssets() {
        val b = BankingFx.bank()
        val before = pos(b)
        b.reserves += Money.units(10_000); b.buySecurities(SecurityKind.GOVERNMENT_SHORT, Money.ZERO, 0L, 6)
        b.retained += Money.units(10_000)
        assertTrue(pos(b).hqla > before.hqla)
    }

    @Test fun corporateBondsAreHaircutInTheBuffer() {
        val b = BankingFx.bank()
        val before = pos(b)
        val moved = b.reserves.times(Rate.percent(50))
        b.reserves -= moved
        b.putSecurity(SecurityKind.CORPORATE_BONDS, SecurityHolding(b.security(SecurityKind.CORPORATE_BONDS).value + moved, 50_000L, 60))
        assertTrue(pos(b).hqla < before.hqla)
    }

    @Test fun losingReservesLowersCoverageAndCreatesAShortfall() {
        val b = BankingFx.bank()
        val before = pos(b)
        val loss = b.reserves
        b.reserves -= loss; b.retained -= loss
        val after = pos(b)
        assertTrue(after.lcr < before.lcr)
        assertTrue(after.reserveShortfall.isPositive)
        assertFalse(after.meetsReserve)
        assertTrue(after.excessReserves.raw < 0L)
    }

    @Test fun moreUninsuredFundingMeansMoreOutflow() {
        val b = BankingFx.bank()
        val before = pos(b)
        val key = DepositKey(Depositor.CORPORATE, DepositProduct.DEMAND)
        val h = DepositKey(Depositor.HOUSEHOLD, DepositProduct.DEMAND)
        val shift = b.depositPool(h).balance.times(Rate.percent(30))
        b.removeDeposits(h, shift); b.addDeposits(key, shift)
        assertTrue(pos(b).outflows > before.outflows)
    }

    @Test fun wholesaleFundingStrainsStableFunding() {
        val b = BankingFx.bank()
        val before = pos(b)
        val extra = Money.units(200_000)
        b.setWholesale(b.wholesale + extra, 40_000L); b.reserves += extra
        assertTrue(pos(b).availableStableFunding.raw >= 0L)
        assertTrue(pos(b).outflows > before.outflows)
    }

    @Test fun bufferShortfallIsZeroWhenAboveTarget() {
        val p = pos(BankingFx.bank())
        assertEquals(Money.ZERO, LiquidityEngine.bufferShortfall(p, Rate.percent(100)))
        assertTrue(LiquidityEngine.bufferShortfall(p, Rate.percent(100_000)).isPositive)
    }

    @Test fun bankWithNoLiabilitiesIsUnboundedNotDividedByZero() {
        val empty = BankingFx.bank().snapshot().copy(deposits = java.util.TreeMap(), wholesale = Money.ZERO, interbankBorrowed = Money.ZERO)
        val p = LiquidityEngine.position(empty, rules, insured)
        assertTrue(p.lcr.ppm >= Rate.percent(100).ppm)
    }

    @Test fun desiredReservesNeverBelowRequirement() {
        val p = pos(BankingFx.bank())
        assertTrue(p.desiredReserves >= p.requiredReserves)
    }
}
