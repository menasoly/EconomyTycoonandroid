package app.many.economytycoon.workshop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopStatsTest {
    @Test fun anEmptyListSummarisesToZeros() {
        val s = WorkshopStats.summarize(emptyList())
        assertEquals(Summary.EMPTY, s)
        assertEquals("no data", s.describe())
    }

    @Test fun oneValueIsEverything() {
        val s = WorkshopStats.summarize(listOf(42L))
        assertEquals(Summary(1, 42, 42, 42, 42, 42, 42, 0), s)
        assertEquals(0L, s.variationPpm)
    }

    @Test fun orderDoesNotMatter() {
        assertEquals(WorkshopStats.summarize(listOf(5, 1, 9, 3, 7)), WorkshopStats.summarize(listOf(9, 7, 5, 3, 1)))
    }

    @Test fun meanMedianAndRangeOfAnOddCount() {
        val s = WorkshopStats.summarize(listOf(1, 2, 3, 4, 10))
        assertEquals(4L, s.mean)
        assertEquals(3L, s.median)
        assertEquals(1L, s.min)
        assertEquals(10L, s.max)
        assertEquals(9L, s.spread)
    }

    @Test fun anEvenCountTakesTheMiddlePairRoundedDown() {
        assertEquals(2L, WorkshopStats.summarize(listOf(1, 2, 3, 4)).median)
        assertEquals(-2L, WorkshopStats.median(listOf(-3L, -1L)))
    }

    @Test fun meansOfNegativeNumbersRoundDown() {
        assertEquals(-1L, WorkshopStats.summarize(listOf(-1, 0)).mean)
        assertEquals(0L, WorkshopStats.summarize(listOf(0, 1)).mean)
    }

    @Test fun standardDeviationIsTheRootOfTheMeanSquareDeviation() {
        // values 2,4,4,4,5,5,7,9: mean 5, population standard deviation 2
        val s = WorkshopStats.summarize(listOf(2, 4, 4, 4, 5, 5, 7, 9))
        assertEquals(5L, s.mean)
        assertEquals(2L, s.stdDev)
        assertEquals(400_000L, s.variationPpm)
    }

    @Test fun percentilesUseNearestRank() {
        val sorted = (1L..10L).toList()
        assertEquals(1L, WorkshopStats.percentile(sorted, 10))
        assertEquals(5L, WorkshopStats.percentile(sorted, 50))
        assertEquals(9L, WorkshopStats.percentile(sorted, 90))
        assertEquals(10L, WorkshopStats.percentile(sorted, 100))
        assertEquals(0L, WorkshopStats.percentile(emptyList(), 50))
    }

    @Test fun hugeMoneyValuesDoNotOverflow() {
        val s = WorkshopStats.summarize(listOf(4_000_000_000_000L, 6_000_000_000_000L))
        assertEquals(5_000_000_000_000L, s.mean)
        assertEquals(1_000_000_000_000L, s.stdDev)
    }

    @Test fun integerRootsAreExact() {
        for (v in listOf(0L, 1L, 2L, 3L, 4L, 15L, 16L, 17L, 1_000_000L, 999_999_999_999L)) {
            val r = WorkshopStats.isqrt(v)
            assertTrue("isqrt($v)=$r", r * r <= v && (r + 1) * (r + 1) > v)
        }
        assertEquals(0L, WorkshopStats.isqrt(-5))
    }

    @Test fun trendsReadTheDirectionOfAList() {
        assertEquals(Trend.RISING, WorkshopStats.trend(listOf(1, 2, 2, 5)))
        assertEquals(Trend.FALLING, WorkshopStats.trend(listOf(5, 4, 4, 1)))
        assertEquals(Trend.FLAT, WorkshopStats.trend(listOf(3, 3, 3)))
        assertEquals(Trend.MIXED, WorkshopStats.trend(listOf(1, 5, 2)))
        assertEquals(Trend.FLAT, WorkshopStats.trend(listOf(7)))
        assertEquals(Trend.FLAT, WorkshopStats.trend(emptyList()))
    }

    @Test fun aToleranceIgnoresSmallWiggles() {
        assertEquals(Trend.RISING, WorkshopStats.trend(listOf(10, 9, 20, 30), tolerance = 2))
        assertEquals(Trend.MIXED, WorkshopStats.trend(listOf(10, 9, 20, 30), tolerance = 0))
    }

    @Test fun sharesAreInPartsPerMillion() {
        assertEquals(250_000L, WorkshopStats.share(1, 4))
        assertEquals(0L, WorkshopStats.share(5, 0))
    }

    @Test fun summariesDescribeThemselves() {
        val s = WorkshopStats.summarize(listOf(1, 2, 3))
        assertEquals("median 2 EUR (mean 2, range 1 to 3, spread +-0)", s.describe(" EUR"))
    }
}
