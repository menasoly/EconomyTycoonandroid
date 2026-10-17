package app.many.economytycoon.tourism

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentsTest {
    @Test fun everySegmentHasAProfile() {
        for (s in VisitorSegment.values()) assertTrue(s.profile.nightsTenths > 0)
        assertEquals(4, VisitorSegment.COUNT)
    }

    @Test fun bookingCurvesSumToOneAndHaveTheHorizonLength() {
        for (s in VisitorSegment.values()) {
            assertEquals(Seasonality.HORIZON, s.profile.bookingCurve.size)
            assertEquals(1_000_000L, s.profile.bookingCurve.sum())
        }
    }

    @Test fun classMixesSumToOne() {
        for (s in VisitorSegment.values()) assertEquals(1_000_000L, s.profile.classMixPpm.sum())
    }

    @Test fun channelMixesSumToOne() {
        for (s in VisitorSegment.values()) {
            val mix = Channel.mix(s)
            assertEquals(Channel.COUNT, mix.size)
            assertEquals(1_000_000L, mix.sum())
        }
    }

    @Test fun businessBooksLateAndFamiliesBookEarly() {
        val business = VisitorSegment.BUSINESS.profile.bookingCurve
        val family = VisitorSegment.FAMILY.profile.bookingCurve
        assertTrue(business[0] > family[0])
        assertTrue(family[Seasonality.HORIZON - 1] > business[Seasonality.HORIZON - 1])
    }

    @Test fun luxuryIsLeastPriceSensitiveAndBudgetMost() {
        val sens = VisitorSegment.values().sortedBy { it.profile.priceSensitivityPpm }
        assertEquals(VisitorSegment.LUXURY, sens.first())
        assertEquals(VisitorSegment.BUDGET, sens.last())
    }

    @Test fun preferredStarsRiseFromBudgetToLuxury() {
        assertTrue(VisitorSegment.BUDGET.profile.preferredStars < VisitorSegment.FAMILY.profile.preferredStars)
        assertTrue(VisitorSegment.FAMILY.profile.preferredStars < VisitorSegment.BUSINESS.profile.preferredStars)
        assertTrue(VisitorSegment.BUSINESS.profile.preferredStars < VisitorSegment.LUXURY.profile.preferredStars)
    }

    @Test fun businessTravelsOnWeekdaysAndLeisureOnWeekends() {
        assertTrue(VisitorSegment.BUSINESS.profile.weekdayPpm > VisitorSegment.BUSINESS.profile.weekendPpm)
        assertTrue(VisitorSegment.BUDGET.profile.weekendPpm > VisitorSegment.BUDGET.profile.weekdayPpm)
    }

    @Test fun nightsAreWholeNights() {
        assertEquals(3L, VisitorSegment.BUDGET.profile.nights)
        assertEquals(2L, VisitorSegment.BUSINESS.profile.nights)
    }

    @Test fun informalSharesAreSmallFractions() {
        for (s in VisitorSegment.values()) assertTrue(s.profile.informalPpm in 0..300_000L)
    }

    @Test fun cancellationAndNoShowRatesAreProbabilities() {
        for (s in VisitorSegment.values()) {
            assertTrue(s.profile.cancelPpm in 1..500_000L)
            assertTrue(s.profile.noShowPpm in 1..200_000L)
        }
    }
}
