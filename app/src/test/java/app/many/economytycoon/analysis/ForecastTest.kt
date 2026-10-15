package app.many.economytycoon.analysis

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastTest {
    private fun line(from: Long, step: Long, n: Int) = (0 until n).map { Money.units(from + step * it) }

    @Test fun aStraightLineProjectsStraight() {
        val f = Forecast.of(line(1_000, 100, 8), 5L, 200L)!!
        val p = f.at(50L)
        assertEquals(250L, p.day)
        assertEquals(Money.units(2_700), p.expected)
        assertEquals(p.expected, p.low)
        assertEquals(p.expected, p.high)
        assertTrue(f.reliable)
    }

    @Test fun trendPerDayDividesTheStepSlope() {
        val f = Forecast.of(line(1_000, 100, 8), 5L, 200L)!!
        assertEquals(Money.units(20), f.trendPerDay)
    }

    @Test fun projectingZeroDaysGivesTheFittedPresent() {
        val f = Forecast.of(line(500, 10, 7), 5L, 90L)!!
        assertEquals(Money.units(560), f.at(0).expected)
        assertEquals(90L, f.at(0).day)
    }

    @Test fun noisyHistoryGivesAWideningRange() {
        val noisy = listOf(1_000L, 1_400L, 900L, 1_500L, 1_000L, 1_600L, 1_100L, 1_700L).map { Money.units(it) }
        val f = Forecast.of(noisy, 5L, 100L)!!
        val near = f.at(10L)
        val far = f.at(200L)
        assertTrue(near.high.raw > near.low.raw)
        assertTrue(far.high.raw - far.low.raw > near.high.raw - near.low.raw)
    }

    @Test fun aJumpyHistoryIsNotReliable() {
        val noisy = listOf(100L, 900L, 50L, 950L, 120L, 880L, 60L, 910L).map { Money.units(it) }
        assertFalse(Forecast.of(noisy, 5L, 100L)!!.reliable)
    }

    @Test fun tooFewPointsAreNotReliableAndOneIsNotEnoughToFit() {
        assertFalse(Forecast.of(line(0, 10, 4), 5L, 20L)!!.reliable)
        assertNull(Forecast.of(listOf(Money.units(5)), 5L, 20L))
    }

    @Test fun daysUntilFindsWhenALevelIsReached() {
        val f = Forecast.of(line(1_000, 100, 8), 5L, 200L)!!
        // Now 1,700; the line gains 20 a day, so 2,000 is 15 days away.
        assertEquals(15L, f.daysUntil(Money.units(2_000)))
    }

    @Test fun daysUntilIsNullWhenTheLineMovesAway() {
        val up = Forecast.of(line(1_000, 100, 8), 5L, 200L)!!
        assertNull(up.daysUntil(Money.units(500)))
        val down = Forecast.of(line(1_000, -100, 8), 5L, 200L)!!
        assertNull(down.daysUntil(Money.units(5_000)))
        assertEquals(15L, down.daysUntil(Money.ZERO))
    }

    @Test fun aFlatLineNeverReachesAnotherLevel() {
        val flat = Forecast.of(line(1_000, 0, 8), 5L, 200L)!!
        assertNull(flat.daysUntil(Money.units(2_000)))
        assertEquals(Money.units(1_000), flat.at(100L).expected)
    }
}
