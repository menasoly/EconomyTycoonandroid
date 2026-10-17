package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PpaTest {
    private val seller = CompanyId(1)
    private val buyer = CompanyId(2)

    private fun baseload(volume: Long = 1_000L, strike: Long = 50, penalty: Long = 200_000L, start: Long = 0L, end: Long = 360L) =
        Ppa(PpaId(1), GridId(1), seller, buyer, PpaProfile.BASELOAD, volume, 0L, null, Money.units(strike), start, end, penalty, 0L)

    private fun produced(share: Long = 500_000L) =
        Ppa(PpaId(2), GridId(1), seller, buyer, PpaProfile.AS_PRODUCED, 0L, share, UnitId(9), Money.units(40), 0L, 100L, 0L, 0L)

    @Test fun deliveredEnergyIsSettledAgainstThePool() {
        val p = baseload()
        // 600 MWh delivered while the pool paid 40
        repeat(600) { p.accrueHour(1_000L, 0L, Money.units(40)) }
        val s = PpaSettler.settle(p)
        assertEquals(600_000L, s.deliveredKwh)
        assertEquals(Money.units(6_000), s.difference)
        assertEquals(Money.ZERO, s.penalty)
        assertEquals(Money.units(6_000), s.netToSeller)
    }

    @Test fun whenThePoolPaysMoreThanTheStrikeTheSellerPaysBack() {
        val p = baseload()
        repeat(100) { p.accrueHour(1_000L, 0L, Money.units(80)) }
        assertEquals(Money.units(-3_000), PpaSettler.settle(p).netToSeller)
    }

    @Test fun shortfallCostsAPenaltyAndTheReplacementPremium() {
        val p = baseload()
        repeat(600) { p.accrueHour(1_000L, 0L, Money.units(40)) }
        repeat(100) { p.accrueHour(0L, 1_000L, Money.units(90)) }
        val s = PpaSettler.settle(p)
        assertEquals(100_000L, s.shortfallKwh)
        // 100 MWh of shortfall at a strike of 50 is 5,000; 20% penalty is 1,000; replacing it at 90 costs 4,000 more
        assertEquals(Money.units(1_000), s.penalty)
        assertEquals(Money.units(4_000), s.replacementPremium)
        assertEquals(Money.units(1_000), s.netToSeller)
    }

    @Test fun cheapReplacementPowerCostsNoPremium() {
        val p = baseload()
        repeat(10) { p.accrueHour(0L, 1_000L, Money.units(30)) }
        assertEquals(Money.ZERO, PpaSettler.settle(p).replacementPremium)
    }

    @Test fun clearingAnAccrualKeepsTheLifetimeTotals() {
        val p = baseload()
        repeat(10) { p.accrueHour(1_000L, 200L, Money.units(40)) }
        p.clearAccrual()
        assertEquals(0L, p.deliveredKwh)
        assertEquals(0L, p.shortfallKwh)
        assertEquals(Money.ZERO, p.marketValue)
        assertEquals(10_000L, p.totalDeliveredKwh)
        assertEquals(2_000L, p.totalShortfallKwh)
    }

    @Test fun anAgreementRunsOnlyBetweenItsDatesWhenActive() {
        val p = baseload(start = 10L, end = 20L)
        assertFalse(p.runsAt(15L))
        p.status = PpaStatus.ACTIVE
        assertFalse(p.runsAt(9L))
        assertTrue(p.runsAt(10L))
        assertTrue(p.runsAt(19L))
        assertFalse(p.runsAt(20L))
    }

    @Test fun exitFeeIsAShareOfTheStrikeValueStillToCome() {
        val p = baseload(volume = 1_000L, strike = 50)
        // 24 MWh a day at 50 is 1,200 a day; 100 days remain; 10% is 12,000
        assertEquals(Money.units(12_000), p.exitFee(260L, 100_000L))
        assertEquals(Money.ZERO, p.exitFee(360L, 100_000L))
        assertEquals(100L, p.remainingDays(260L))
    }

    @Test fun anAsProducedAgreementPricesAnExitFromWhatItHasDelivered() {
        val p = produced()
        assertEquals(0L, p.expectedDailyKwh())
        p.status = PpaStatus.ACTIVE
        p.accrueHour(10_000L, 0L, Money.units(40))
        p.daysDelivering = 1
        assertEquals(10_000L, p.expectedDailyKwh())
    }

    @Test(expected = IllegalArgumentException::class) fun noContractsWithYourself() {
        Ppa(PpaId(1), GridId(1), seller, seller, PpaProfile.BASELOAD, 100L, 0L, null, Money.units(50), 0L, 10L, 0L, 0L)
    }

    @Test(expected = IllegalArgumentException::class) fun aBaseloadAgreementNeedsAVolume() { baseload(volume = 0L) }

    @Test(expected = IllegalArgumentException::class) fun anAsProducedAgreementNeedsAUnit() {
        Ppa(PpaId(1), GridId(1), seller, buyer, PpaProfile.AS_PRODUCED, 0L, 500_000L, null, Money.units(50), 0L, 10L, 0L, 0L)
    }

    @Test(expected = IllegalArgumentException::class) fun anAgreementNeedsADuration() { baseload(start = 10L, end = 10L) }

    @Test(expected = IllegalArgumentException::class) fun thePriceMustBePositive() { baseload(strike = 0) }

    @Test fun hashCoversAccrualsAndStatus() {
        fun hash(p: Ppa) = app.many.economytycoon.core.CanonicalHasher().also { p.hashInto(it) }.finish()
        val a = baseload()
        val b = baseload()
        assertEquals(hash(a), hash(b))
        b.accrueHour(1L, 0L, Money.units(1))
        assertTrue(hash(a) != hash(b))
        val c = baseload()
        c.status = PpaStatus.ACTIVE
        assertTrue(hash(a) != hash(c))
    }
}
