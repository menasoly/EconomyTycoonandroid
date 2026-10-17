package app.many.economytycoon.tourism

import app.many.economytycoon.core.Season
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.ClimateZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonalityTest {
    @Test fun summerIsWarmerThanWinterEverywhere() {
        for (z in ClimateZone.values()) assertTrue("$z", Seasonality.warmth(z, Season.SUMMER) >= Seasonality.warmth(z, Season.WINTER))
    }

    @Test fun comfortIsAProbabilityLikeValue() {
        for (z in ClimateZone.values()) for (s in Season.values()) for (seg in VisitorSegment.values()) {
            assertTrue(Seasonality.comfortPpm(z, s, seg) in 0..1_000_000L)
        }
    }

    @Test fun aWarmClimateSuitsLeisureTravellersInSummer() {
        val tropical = Seasonality.comfortPpm(ClimateZone.MEDITERRANEAN, Season.SUMMER, VisitorSegment.FAMILY)
        val cold = Seasonality.comfortPpm(ClimateZone.COLD, Season.SUMMER, VisitorSegment.FAMILY)
        assertTrue(tropical >= cold)
    }

    @Test fun monthIndexesAverageNearOne() {
        for (s in VisitorSegment.values()) {
            val mean = (1..12).sumOf { Seasonality.monthIndexPpm(s, it) } / 12
            assertTrue("$s $mean", mean in 900_000L..1_100_000L)
        }
    }

    @Test fun leisureTravelPeaksInSummerAndBusinessInAutumn() {
        assertTrue(Seasonality.peakMonths(VisitorSegment.FAMILY).any { it in 7..8 })
        assertTrue(Seasonality.peakMonths(VisitorSegment.BUSINESS).any { it in 9..11 })
    }

    @Test fun peakMonthsAreSortedAndSizedAsAsked() {
        val peaks = Seasonality.peakMonths(VisitorSegment.BUDGET, 4)
        assertEquals(4, peaks.size)
        assertEquals(peaks.sorted(), peaks)
    }

    @Test(expected = IllegalArgumentException::class) fun monthZeroIsRejected() {
        Seasonality.monthIndexPpm(VisitorSegment.BUDGET, 0)
    }

    @Test(expected = IllegalArgumentException::class) fun monthThirteenIsRejected() {
        Seasonality.monthIndexPpm(VisitorSegment.BUDGET, 13)
    }

    @Test fun tickOverloadUsesTheMonthOfTheDate() {
        // Tick 150 falls in month 6.
        assertEquals(Seasonality.monthIndexPpm(VisitorSegment.FAMILY, 6), Seasonality.monthIndexPpm(VisitorSegment.FAMILY, Tick(150)))
    }

    @Test fun weekendsAreTheLastTwoDays() {
        assertTrue(!Seasonality.isWeekend(Tick(0)))
        assertTrue(!Seasonality.isWeekend(Tick(4)))
        assertTrue(Seasonality.isWeekend(Tick(5)))
        assertTrue(Seasonality.isWeekend(Tick(6)))
        assertTrue(!Seasonality.isWeekend(Tick(7)))
    }

    @Test fun normalisedWeekdayFactorsAverageToOneOverAWeek() {
        for (s in VisitorSegment.values()) {
            val sum = (0 until 7).sumOf { Seasonality.normalisedWeekdayPpm(s, Tick(it.toLong())) }
            assertTrue("$s $sum", Math.abs(sum - 7_000_000L) <= 14L)
        }
    }

    @Test fun bookingShareClampsTheLead() {
        val s = VisitorSegment.FAMILY
        assertEquals(Seasonality.bookingShare(s, 13), Seasonality.bookingShare(s, 40))
        assertEquals(Seasonality.bookingShare(s, 0), Seasonality.bookingShare(s, -3))
    }

    @Test fun bookedBeforeLeadFallsFromAlmostEverythingToNothing() {
        for (s in VisitorSegment.values()) {
            assertEquals(1_000_000L - s.profile.bookingCurve[0], Seasonality.bookedBeforeLeadPpm(s, 0))
            assertEquals(0L, Seasonality.bookedBeforeLeadPpm(s, Seasonality.HORIZON - 1))
            var last = Long.MAX_VALUE
            for (lead in 0 until Seasonality.HORIZON) {
                val v = Seasonality.bookedBeforeLeadPpm(s, lead)
                assertTrue(v <= last)
                last = v
            }
        }
    }

    @Test fun blendedPickupIsBetweenTheSegmentExtremes() {
        val mix = longArrayOf(250_000L, 250_000L, 250_000L, 250_000L)
        for (lead in listOf(0, 3, 7)) {
            val values = VisitorSegment.values().map { Seasonality.bookedBeforeLeadPpm(it, lead) }
            val blend = Seasonality.bookedBeforeLeadPpm(mix, lead)
            assertTrue(blend >= values.min() && blend <= values.max())
        }
    }

    @Test fun blendWithNoWeightIsZero() {
        assertEquals(0L, Seasonality.bookedBeforeLeadPpm(longArrayOf(0, 0, 0, 0), 3))
    }
}
