package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrandTest {
    private val lp = LicenseParams()

    @Test fun theCapIsSharedByTheEssentialStake() {
        assertEquals(Rate(25_000), Frand.fairCap(lp, 1, 2))
        assertEquals(Rate(50_000), Frand.fairCap(lp, 4, 4))
        assertEquals(Rate(10_000), Frand.fairCap(lp, 1, 5))
    }

    @Test fun noStakeMeansNoCap() {
        assertEquals(Rate.ZERO, Frand.fairCap(lp, 0, 5))
        assertEquals(Rate.ZERO, Frand.fairCap(lp, 1, 0))
    }

    @Test fun aTinyStakeStillGetsAFloor() {
        assertEquals(Rate(1_000), Frand.fairCap(LicenseParams(frandCapPpm = 100), 1, 100))
    }

    @Test fun anOfferAtOrBelowTheCapPasses() {
        assertTrue(Frand.check(lp, LicenseKind.NON_EXCLUSIVE, Rate(25_000), Rate(25_000), emptyList()).isEmpty())
    }

    @Test fun anOfferAboveTheCapIsUnfair() {
        val v = Frand.check(lp, LicenseKind.NON_EXCLUSIVE, Rate(30_000), Rate(25_000), emptyList())
        assertEquals(listOf(Frand.UNFAIR), v.map { it.code })
    }

    @Test fun exclusivityIsNotAllowed() {
        val v = Frand.check(lp, LicenseKind.EXCLUSIVE, Rate(1_000), Rate(25_000), emptyList())
        assertEquals(listOf(Frand.EXCLUSIVE), v.map { it.code })
        assertEquals(listOf(Frand.EXCLUSIVE), Frand.check(lp, LicenseKind.SOLE, Rate(1_000), Rate(25_000), emptyList()).map { it.code })
    }

    @Test fun payingMoreThanAComparableLicenseeIsDiscriminatory() {
        val v = Frand.check(lp, LicenseKind.NON_EXCLUSIVE, Rate(22_500), Rate(25_000), listOf(Rate(20_000), Rate(24_000)))
        assertEquals(listOf(Frand.DISCRIMINATORY), v.map { it.code })
    }

    @Test fun aSmallPremiumWithinToleranceIsFine() {
        assertTrue(Frand.check(lp, LicenseKind.NON_EXCLUSIVE, Rate(21_900), Rate(25_000), listOf(Rate(20_000))).isEmpty())
        assertFalse(Frand.check(lp, LicenseKind.NON_EXCLUSIVE, Rate(22_100), Rate(25_000), listOf(Rate(20_000))).isEmpty())
    }

    @Test fun severalViolationsAreAllReported() {
        val v = Frand.check(lp, LicenseKind.EXCLUSIVE, Rate(40_000), Rate(25_000), listOf(Rate(10_000)))
        assertEquals(setOf(Frand.EXCLUSIVE, Frand.UNFAIR, Frand.DISCRIMINATORY), v.map { it.code }.toSet())
    }

    @Test fun offeredRateReadsTheRunningRoyaltyOnASample() {
        val sample = UsageReport(Quantity.units(100), Money.units(10_000))
        assertEquals(Rate.percent(3), Frand.offeredRate(RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(3)), sample))
        assertEquals(Rate.percent(5), Frand.offeredRate(RoyaltyTerms(RoyaltyBasis.PER_UNIT, perUnit = Money.units(5)), sample))
        assertEquals(Rate.ZERO, Frand.offeredRate(RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = Money.units(5)), sample))
        assertEquals(Rate.ZERO, Frand.offeredRate(RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(3)), UsageReport.ZERO))
    }

    @Test fun aWillingLicenseeCannotBeShutOutOfAPledgedPatent() {
        assertFalse(Frand.injunctionAllowed(patentPledged = true, defendantWilling = true))
        assertTrue(Frand.injunctionAllowed(patentPledged = true, defendantWilling = false))
        assertTrue(Frand.injunctionAllowed(patentPledged = false, defendantWilling = true))
    }
}
