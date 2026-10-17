package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoyaltyTest {
    private val usage = UsageReport(Quantity.units(1_000), Money.units(100_000))

    @Test fun perUnitMultipliesUnits() {
        val t = RoyaltyTerms(RoyaltyBasis.PER_UNIT, perUnit = Money.units(3))
        assertEquals(Money.units(3_000), Royalty.running(t, usage))
    }

    @Test fun percentTakesAShareOfSales() {
        val t = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(4))
        assertEquals(Money.units(4_000), Royalty.running(t, usage))
    }

    @Test fun hybridAddsBoth() {
        val t = RoyaltyTerms(RoyaltyBasis.HYBRID, perUnit = Money.units(1), percent = Rate.percent(2))
        assertEquals(Money.units(1_000 + 2_000), Royalty.running(t, usage))
    }

    @Test fun lumpSumHasNoRunningRoyalty() {
        val t = RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = Money.units(50_000))
        assertEquals(Money.ZERO, Royalty.running(t, usage))
        assertEquals(Money.ZERO, Royalty.due(t, usage))
        assertTrue(!t.isRecurring)
    }

    @Test fun theMinimumHoldsWhenSalesAreLow() {
        val t = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(4), minimumPerPeriod = Money.units(10_000))
        assertEquals(Money.units(10_000), Royalty.due(t, usage))
        assertEquals(Money.units(10_000), Royalty.due(t, UsageReport.ZERO))
    }

    @Test fun theCapHoldsWhenSalesAreHigh() {
        val t = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(4), capPerPeriod = Money.units(2_500))
        assertEquals(Money.units(2_500), Royalty.due(t, usage))
    }

    @Test fun aCapCannotSitBelowTheMinimum() {
        var failed = false
        try { RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(4), minimumPerPeriod = Money.units(5), capPerPeriod = Money.units(2)) } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }

    @Test fun negativeTermsAreRefused() {
        var failed = false
        try { RoyaltyTerms(RoyaltyBasis.PER_UNIT, perUnit = Money.units(-1)) } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }

    @Test fun effectiveRateIsRoyaltyOverSales() {
        val t = RoyaltyTerms(RoyaltyBasis.PER_UNIT, perUnit = Money.units(5))
        assertEquals(Rate.percent(5), Royalty.effectiveRate(t, usage))
        assertEquals(Rate.ZERO, Royalty.effectiveRate(t, UsageReport.ZERO))
    }

    @Test fun committedAddsMinimumsAndUpfront() {
        val t = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(1), minimumPerPeriod = Money.units(100), upfront = Money.units(900))
        assertEquals(Money.units(1_300), Royalty.committed(t, 4))
        assertEquals(Money.units(70), Royalty.committed(RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = Money.units(70)), 4))
    }

    @Test fun signingAmountIncludesTheLumpSumOnlyForLumpSumLicences() {
        assertEquals(Money.units(30), Royalty.dueAtSigning(RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = Money.units(30))))
        assertEquals(Money.units(5), Royalty.dueAtSigning(RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(1), lumpSum = Money.units(30), upfront = Money.units(5))))
    }

    @Test fun theReasonableRateFollowsTheProfitShareRule() {
        val lp = LicenseParams()
        val r = Royalty.reasonablePercent(lp, 120_000L, 600_000L, 600_000L)
        // 25% of a 12% margin is 3%, scaled by a 60% contribution.
        assertEquals(18_000L, r.ppm)
    }

    @Test fun theReasonableRateIsBoundedBothWays() {
        val lp = LicenseParams()
        assertEquals(1_000L, Royalty.reasonablePercent(lp, 0L, 0L, 0L).ppm)
        assertEquals(lp.maxPercentPpm, Royalty.reasonablePercent(lp, 5_000_000L, 1_000_000L, 1_000_000L).ppm)
    }

    @Test fun scalingUsageScalesBothSides() {
        val half = Royalty.scaleUsage(usage, Rate.percent(50))
        assertEquals(Quantity.units(500), half.units)
        assertEquals(Money.units(50_000), half.sales)
        assertEquals(usage, Royalty.scaleUsage(usage, Rate.ONE))
    }

    @Test fun perUnitEquivalentOfAPercent() {
        assertEquals(Money.units(2), Royalty.perUnitFor(Rate.percent(4), Money.units(50)))
    }

    @Test fun usageMaxPrefersTheLargerSales() {
        val small = UsageReport(Quantity.units(1), Money.units(5))
        assertEquals(usage, small.max(usage))
        assertEquals(usage, usage.max(small))
        assertEquals(UsageReport(Quantity.units(1_001), Money.units(100_005)), usage + small)
    }
}
