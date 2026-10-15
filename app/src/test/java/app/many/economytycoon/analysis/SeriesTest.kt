package app.many.economytycoon.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesTest {
    @Test fun meanAndMedianOfEmptyAreZero() {
        assertEquals(0L, Series.mean(emptyList()))
        assertEquals(0L, Series.median(emptyList()))
    }

    @Test fun meanDoesNotOverflow() {
        val big = Long.MAX_VALUE / 2
        assertEquals(big, Series.mean(listOf(big, big, big, big)))
    }

    @Test fun medianOfEvenCountAveragesTheMiddlePair() {
        assertEquals(25L, Series.median(listOf(40L, 10L, 20L, 30L)))
        assertEquals(30L, Series.median(listOf(50L, 10L, 30L)))
    }

    @Test fun percentileUsesNearestRank() {
        val v = (1L..11L).toList()
        assertEquals(1L, Series.percentile(v, 0))
        assertEquals(6L, Series.percentile(v, 500_000))
        assertEquals(11L, Series.percentile(v, 1_000_000))
        assertEquals(11L, Series.percentile(v, 5_000_000))
    }

    @Test fun rankCountsTiesAsHalf() {
        assertEquals(500_000L, Series.rankPpm(emptyList(), 5))
        assertEquals(1_000_000L, Series.rankPpm(listOf(1L, 2L, 3L), 9L))
        assertEquals(0L, Series.rankPpm(listOf(1L, 2L, 3L), 0L))
        assertEquals(500_000L, Series.rankPpm(listOf(1L, 2L, 3L), 2L))
        assertEquals(500_000L, Series.rankPpm(listOf(4L, 4L), 4L))
    }

    @Test fun movingAverageStartsShortAndThenSlides() {
        assertEquals(listOf(10L, 15L, 20L, 30L), Series.movingAverage(listOf(10L, 20L, 30L, 40L), 3))
        assertEquals(listOf(10L, 15L, 20L, 30L, 40L), Series.movingAverage(listOf(10L, 20L, 30L, 40L, 50L), 3))
    }

    @Test fun movingAverageOfWindowOneIsTheSeries() {
        assertEquals(listOf(3L, 1L, 2L), Series.movingAverage(listOf(3L, 1L, 2L), 1))
    }

    @Test fun volatilityIsZeroForFlatSeries() {
        assertEquals(0L, Series.volatilityPpm(listOf(100L, 100L, 100L)))
        assertEquals(0L, Series.volatilityPpm(listOf(100L)))
    }

    @Test fun volatilityGrowsWithSwings() {
        val calm = Series.volatilityPpm(listOf(100L, 101L, 99L, 100L))
        val wild = Series.volatilityPpm(listOf(100L, 160L, 40L, 100L))
        assertTrue("$calm < $wild", calm < wild)
    }

    @Test fun growthIsRelativeToTheStart() {
        assertEquals(500_000L, Series.growthPpm(listOf(100L, 120L, 150L)))
        assertEquals(-500_000L, Series.growthPpm(listOf(100L, 50L)))
        assertNull(Series.growthPpm(listOf(0L, 10L)))
        assertNull(Series.growthPpm(listOf(10L)))
    }

    @Test fun growthFromANegativeStartKeepsItsSign() {
        // From -100 to -50 the position improved by half the starting size.
        assertEquals(500_000L, Series.growthPpm(listOf(-100L, -50L)))
    }

    @Test fun streakCountsTheRunAtTheEnd() {
        assertEquals(3, Series.streak(listOf(5L, 1L, 2L, 3L, 4L)))
        assertEquals(-2, Series.streak(listOf(1L, 5L, 4L, 3L)))
        assertEquals(0, Series.streak(listOf(1L, 1L)))
        assertEquals(0, Series.streak(listOf(7L)))
        assertEquals(1, Series.streak(listOf(3L, 2L, 2L, 3L)))
    }

    @Test fun fitFindsAnExactLine() {
        val fit = Series.fit(listOf(10L, 20L, 30L, 40L, 50L))!!
        assertEquals(10L, fit.slope)
        assertEquals(10L, fit.intercept)
        assertEquals(1_000_000L, fit.fitPpm)
        assertEquals(0L, fit.spread)
        assertEquals(60L, fit.at(5))
    }

    @Test fun fitOfAFlatSeriesIsPerfectWithNoSlope() {
        val fit = Series.fit(listOf(7L, 7L, 7L, 7L))!!
        assertEquals(0L, fit.slopeScaled)
        assertEquals(1_000_000L, fit.fitPpm)
    }

    @Test fun fitNeedsTwoPoints() {
        assertNull(Series.fit(emptyList()))
        assertNull(Series.fit(listOf(1L)))
    }

    @Test fun noisyDataFitsWorseThanCleanData() {
        val clean = Series.fit(listOf(10L, 20L, 30L, 40L, 50L, 60L))!!
        val noisy = Series.fit(listOf(10L, 45L, 20L, 60L, 30L, 70L))!!
        assertTrue(noisy.fitPpm < clean.fitPpm)
        assertTrue(noisy.spread > 0L)
        assertTrue(noisy.slope > 0L)
    }

    @Test fun fitHandlesAFallingLine() {
        val fit = Series.fit(listOf(100L, 90L, 80L, 70L))!!
        assertEquals(-10L, fit.slope)
        assertEquals(60L, fit.at(4))
    }
}
