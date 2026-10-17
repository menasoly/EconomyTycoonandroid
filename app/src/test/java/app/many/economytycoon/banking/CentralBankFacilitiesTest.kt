package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CentralBankFacilitiesTest {
    private val p = BankingFx.params.facilities

    @Test fun corridorSurroundsThePolicyRate() {
        val c = CentralBankFacilities.corridor(Rate.percent(4), p)
        assertEquals(Rate.percent(4) - p.corridorHalfWidth, c.floor)
        assertEquals(Rate.percent(4) + p.corridorHalfWidth, c.ceiling)
        assertEquals(c.ceiling + p.emergencyPenalty, c.emergency)
    }

    @Test fun corridorFloorNeverGoesNegative() {
        val c = CentralBankFacilities.corridor(Rate.ZERO, p)
        assertEquals(Rate.ZERO, c.floor)
        assertEquals(Rate.ZERO, CentralBankFacilities.corridor(Rate.percent(-1), p).policy)
    }

    @Test fun collateralIsHaircut() {
        val s = BankingFx.bank().snapshot()
        val c = CentralBankFacilities.collateral(s, p)
        assertTrue(c.securities < s.securitiesValue())
        assertTrue(c.loans < s.loansPerforming())
        assertEquals(c.securities + c.loans, c.total)
    }

    @Test fun marginalFacilityLendsOnlyAgainstSecurities() {
        val b = BankingFx.bank()
        val cap = CentralBankFacilities.marginalCapacity(b.snapshot(), p)
        assertEquals(CentralBankFacilities.collateral(b.snapshot(), p).securities, cap)
        val got = CentralBankFacilities.borrowMarginal(b, cap + Money.units(1), p)
        assertEquals(cap, got)
        assertEquals(Money.ZERO, CentralBankFacilities.marginalCapacity(b.snapshot(), p))
    }

    @Test fun borrowingKeepsBooksBalancedAndReservesUp() {
        val b = BankingFx.bank()
        val before = b.reserves
        val got = CentralBankFacilities.borrowMarginal(b, Money.units(1_000), p)
        assertEquals(before + got, b.reserves)
        assertEquals(got, b.centralBankMarginal)
        BankingFx.assertBalanced(b)
    }

    @Test fun emergencyAssistanceNeedsASolventBank() {
        val b = BankingFx.bank()
        val ok = CentralBankFacilities.eligibleForEmergency(b.snapshot(), Rate.percent(8), p)
        assertTrue(ok)
        assertFalse(CentralBankFacilities.eligibleForEmergency(b.snapshot(), Rate.percent(1), p))
        assertEquals(Money.ZERO, CentralBankFacilities.borrowEmergency(b, Money.units(1_000), Rate.percent(1), p))
        assertEquals(Money.ZERO, b.centralBankEmergency)
    }

    @Test fun emergencyCapacityIncludesLoanCollateral() {
        val s = BankingFx.bank().snapshot()
        assertTrue(CentralBankFacilities.emergencyCapacity(s, p) > CentralBankFacilities.marginalCapacity(s, p))
    }

    @Test fun capacityShrinksAsDebtGrows() {
        val b = BankingFx.bank()
        val before = CentralBankFacilities.emergencyCapacity(b.snapshot(), p)
        val got = CentralBankFacilities.borrowEmergency(b, Money.units(5_000), Rate.percent(8), p)
        assertEquals(before - got, CentralBankFacilities.emergencyCapacity(b.snapshot(), p))
    }

    @Test fun repayingClearsEmergencyDebtFirst() {
        val b = BankingFx.bank()
        CentralBankFacilities.borrowMarginal(b, Money.units(1_000), p)
        CentralBankFacilities.borrowEmergency(b, Money.units(1_000), Rate.percent(8), p)
        val paid = CentralBankFacilities.repay(b, Money.units(1_000))
        assertEquals(Money.units(1_000), paid)
        assertEquals(Money.ZERO, b.centralBankEmergency)
        assertEquals(Money.units(1_000), b.centralBankMarginal)
        BankingFx.assertBalanced(b)
    }

    @Test fun repaymentIsLimitedByDebtAndReserves() {
        val b = BankingFx.bank()
        assertEquals(Money.ZERO, CentralBankFacilities.repay(b, Money.units(5)))
        CentralBankFacilities.borrowMarginal(b, Money.units(500), p)
        assertEquals(Money.units(500), CentralBankFacilities.repay(b, Money.units(90_000)))
        assertEquals(Money.ZERO, b.centralBankDebt)
    }

    @Test fun emergencyMoneyCostsMoreThanOvernight() {
        val b = BankingFx.bank()
        CentralBankFacilities.borrowMarginal(b, Money.units(10_000), p)
        val c = CentralBankFacilities.corridor(Rate.percent(4), p)
        val onlyMarginal = CentralBankFacilities.monthlyInterest(b.snapshot(), c)
        val e = BankingFx.bank()
        CentralBankFacilities.borrowEmergency(e, Money.units(10_000), Rate.percent(8), p)
        assertTrue(CentralBankFacilities.monthlyInterest(e.snapshot(), c) > onlyMarginal)
    }

    @Test fun excessReservesEarnTheFloorRate() {
        val b = BankingFx.bank()
        val c = CentralBankFacilities.corridor(Rate.percent(4), p)
        val required = LiquidityEngine.requiredReserves(b.snapshot(), BankingFx.params.liquidity)
        val interest = CentralBankFacilities.monthlyRemuneration(b.snapshot(), required, c)
        assertEquals(BankMath.monthlyInterest(b.reserves - required, c.floor.ppm), interest)
        assertEquals(Money.ZERO, CentralBankFacilities.monthlyRemuneration(b.snapshot(), b.reserves + Money.units(1), c))
    }
}
