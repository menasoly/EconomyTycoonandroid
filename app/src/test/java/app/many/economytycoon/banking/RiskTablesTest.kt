package app.many.economytycoon.banking

import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskTablesTest {
    @Test fun lendableExcludesDefault() {
        assertFalse(RiskTables.LENDABLE.contains(CreditRating.D))
        assertEquals(CreditRating.values().size - 1, RiskTables.LENDABLE.size)
    }

    @Test fun defaultProbabilityGrowsAsRatingWorsens() {
        var last = -1L
        for (r in CreditRating.values()) {
            val pd = RiskTables.annualPd(r).ppm
            assertTrue("$r", pd > last)
            last = pd
        }
        assertEquals(Rate.ONE_PPM, RiskTables.annualPd(CreditRating.D).ppm)
    }

    @Test fun stressedPdScalesAndCaps() {
        val base = RiskTables.annualPd(CreditRating.BBB).ppm
        assertEquals(base, RiskTables.stressedPd(CreditRating.BBB, Rate.ONE_PPM).ppm)
        assertEquals(base * 2, RiskTables.stressedPd(CreditRating.BBB, 2_000_000L).ppm)
        assertEquals(Rate.ONE_PPM, RiskTables.stressedPd(CreditRating.CCC, 10_000_000L).ppm)
        assertEquals(0L, RiskTables.stressedPd(CreditRating.BBB, 0L).ppm)
    }

    @Test(expected = IllegalArgumentException::class) fun negativeMultiplierIsRejected() {
        RiskTables.stressedPd(CreditRating.A, -1L)
    }

    @Test fun cumulativePdIsMonotoneInTime() {
        val annual = RiskTables.annualPd(CreditRating.BB)
        assertEquals(0L, RiskTables.cumulativePd(annual, 0).ppm)
        var last = 0L
        for (m in 1..60) {
            val p = RiskTables.cumulativePd(annual, m).ppm
            assertTrue(p > last)
            last = p
        }
        assertTrue(last < Rate.ONE_PPM)
    }

    @Test fun cumulativePdOfCertainDefaultIsCertainWithinAYear() {
        assertTrue(RiskTables.cumulativePd(Rate.ONE, 12).ppm > 600_000L)
    }

    @Test fun lossGivenDefaultRisesWhenCollateralFalls() {
        for (s in LoanSector.values()) {
            val normal = RiskTables.lgd(s).ppm
            assertTrue("$s", RiskTables.lgd(s, 600_000L).ppm >= normal)
            assertTrue("$s", RiskTables.lgd(s, 1_300_000L).ppm <= normal)
            assertTrue(RiskTables.lgd(s, 0L).ppm <= Rate.ONE_PPM)
            assertTrue(RiskTables.lgd(s, 5_000_000L).ppm >= RiskTables.baseLgd(s).ppm / 3L)
        }
    }

    @Test fun propertyIsMoreSensitiveToCollateralThanTradeFinance() {
        val mortgage = RiskTables.lgd(LoanSector.MORTGAGE, 600_000L).ppm - RiskTables.lgd(LoanSector.MORTGAGE).ppm
        val consumer = RiskTables.lgd(LoanSector.CONSUMER, 600_000L).ppm - RiskTables.lgd(LoanSector.CONSUMER).ppm
        assertTrue(mortgage >= consumer)
    }

    @Test fun mortgagesRunLongerThanConsumerLoans() {
        assertTrue(RiskTables.termMonths(LoanSector.MORTGAGE) > RiskTables.termMonths(LoanSector.CONSUMER))
        assertTrue(RiskTables.durationMonths(LoanSector.MORTGAGE) > RiskTables.durationMonths(LoanSector.TRADE_FINANCE))
        for (s in LoanSector.values()) assertTrue(RiskTables.durationMonths(s) in 2..48)
    }

    @Test fun runoffAndFloatingSharesAreRates() {
        for (s in LoanSector.values()) {
            assertTrue(RiskTables.monthlyRunoff(s).ppm in 1L..Rate.ONE_PPM)
            assertTrue(RiskTables.floatingShare(s).ppm in 0L..Rate.ONE_PPM)
            assertTrue(RiskTables.spreadFactor(s).ppm > 0L)
        }
    }

    @Test fun riskWeightsFollowRatingAndStage() {
        for (s in LoanSector.values()) {
            var last = 0L
            for (r in RiskTables.LENDABLE) {
                val w = RiskTables.riskWeight(s, r, LoanStage.PERFORMING).ppm
                assertTrue("$s $r", w >= last)
                last = w
                assertTrue(RiskTables.riskWeight(s, r, LoanStage.WATCH).ppm >= w)
                assertEquals(Rate.percent(150).ppm, RiskTables.riskWeight(s, r, LoanStage.IMPAIRED).ppm)
            }
        }
    }

    @Test fun governmentPaperCarriesNoWeight() {
        assertEquals(0L, RiskTables.securityRiskWeight(SecurityKind.GOVERNMENT_SHORT).ppm)
        assertEquals(0L, RiskTables.securityRiskWeight(SecurityKind.GOVERNMENT_LONG).ppm)
        assertTrue(RiskTables.securityRiskWeight(SecurityKind.CORPORATE_BONDS).ppm > 0L)
    }

    @Test fun applicantMixesSumToOneHundred() {
        for (s in LoanSector.values()) {
            assertEquals(100L, RiskTables.applicantMix(s).sum())
            assertEquals(RiskTables.LENDABLE.size, RiskTables.applicantMix(s).size)
        }
    }

    @Test fun applicantMixUnderStressShiftsTowardsWeakerBorrowersButKeepsTotal() {
        for (s in LoanSector.values()) {
            val normal = RiskTables.applicantMixUnder(s, Rate.ONE_PPM)
            val bad = RiskTables.applicantMixUnder(s, 3_000_000L)
            assertEquals(10_000L, normal.sum())
            assertEquals(10_000L, bad.sum())
            assertTrue(bad.last() > normal.last())
            assertTrue(bad.first() <= normal.first())
            assertEquals(normal.toList(), RiskTables.applicantMixUnder(s, 500_000L).toList())
        }
    }

    @Test fun migrationRatesStayInRange() {
        for (m in listOf(0L, 500_000L, 1_000_000L, 3_000_000L, 20_000_000L)) {
            assertTrue(RiskTables.watchToImpaired(m).ppm in 0L..600_000L)
            assertTrue(RiskTables.watchCure(m).ppm in 10_000L..200_000L)
        }
        assertTrue(RiskTables.watchToImpaired(2_000_000L) > RiskTables.watchToImpaired(1_000_000L))
        assertTrue(RiskTables.watchCure(2_000_000L) < RiskTables.watchCure(1_000_000L))
    }
}
