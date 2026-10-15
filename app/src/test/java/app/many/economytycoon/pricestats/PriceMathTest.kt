package app.many.economytycoon.pricestats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceMathTest {
    private fun s(vararg v: Long) = v

    @Test fun tooFewPointsGiveNothing() {
        assertNull(PriceMath.stats(s(100, 110)))
        assertNull(PriceMath.stats(s()))
    }

    @Test fun basicRangeFigures() {
        val st = PriceMath.stats(s(100, 120, 90, 110))!!
        assertEquals(90L, st.low); assertEquals(120L, st.high); assertEquals(110L, st.last)
        assertEquals(105L, st.mean)
        assertEquals(100_000L, st.changePpm)
        assertEquals(4, st.count)
    }

    @Test fun rangePositionAndZones() {
        assertEquals(RangeZone.NEAR_HIGH, PriceMath.stats(s(10, 20, 30, 40, 50))!!.zone)
        assertEquals(RangeZone.NEAR_LOW, PriceMath.stats(s(50, 40, 30, 20, 10))!!.zone)
        assertEquals(RangeZone.MIDDLE, PriceMath.stats(s(10, 50, 30))!!.zone)
        assertEquals(500_000L, PriceMath.stats(s(7, 7, 7))!!.rangePositionPpm)
    }

    @Test fun trendUsesAFlatBand() {
        assertEquals(Trend.RISING, PriceMath.stats(s(100, 105, 110))!!.trend)
        assertEquals(Trend.FALLING, PriceMath.stats(s(110, 105, 100))!!.trend)
        assertEquals(Trend.FLAT, PriceMath.stats(s(1000, 1100, 1005))!!.trend)
    }

    @Test fun drawdownIsMeasuredFromTheRunningPeak() {
        assertEquals(0L, PriceMath.drawdown(s(1, 2, 3, 4)))
        assertEquals(500_000L, PriceMath.drawdown(s(100, 200, 100, 150)))
        assertEquals(250_000L, PriceMath.drawdown(s(100, 80, 100, 75, 90)))
    }

    @Test fun volatilityIsZeroForSteadyOrStraightLines() {
        assertEquals(0L, PriceMath.volatility(s(100, 100, 100, 100)))
        assertEquals(0L, PriceMath.volatility(s(100)))
    }

    @Test fun volatilityGrowsWithSwings() {
        val calm = PriceMath.volatility(s(100, 101, 100, 101, 100, 101))
        val wild = PriceMath.volatility(s(100, 130, 90, 140, 80, 150))
        assertTrue("wild $wild vs calm $calm", wild > calm * 5)
    }

    @Test fun upDaysIgnoreUnchangedDays() {
        assertEquals(500_000L, PriceMath.upDays(s(10, 11, 11, 10)))
        assertEquals(0L, PriceMath.upDays(s(5, 5, 5)))
        assertEquals(1_000_000L, PriceMath.upDays(s(1, 2, 3)))
    }

    @Test fun movingAverageOverTheTail() {
        assertEquals(40L, PriceMath.movingAverage(s(10, 20, 30, 40, 50), 3))
        assertNull(PriceMath.movingAverage(s(1, 2), 3))
    }

    @Test fun movingAverageLineWarmsUp() {
        val line = PriceMath.movingAverageLine(s(10, 20, 30, 40), 2)
        assertEquals(listOf(10L, 15L, 25L, 35L), line.toList())
    }

    @Test fun strengthRunsFromZeroToOneHundred() {
        assertEquals(100, PriceMath.strength(s(1, 2, 3, 4, 5)))
        assertEquals(0, PriceMath.strength(s(5, 4, 3, 2, 1)))
        assertEquals(50, PriceMath.strength(s(10, 20, 10)))
        assertNull(PriceMath.strength(s(3, 3, 3)))
    }

    @Test fun strengthOnlyLooksAtTheWindow() {
        val closes = s(1, 100, 99, 98, 97)
        assertEquals(0, PriceMath.strength(closes, 3))
        assertTrue(PriceMath.strength(closes, 10)!! > 90)
    }

    @Test fun daysSinceHighAndLow() {
        val c = s(5, 9, 7, 3, 4)
        assertEquals(3, PriceMath.daysSinceHigh(c))
        assertEquals(1, PriceMath.daysSinceLow(c))
        assertEquals(0, PriceMath.daysSinceHigh(s(1, 2, 3)))
    }

    @Test fun correlationOfIdenticalAndOpposedSeries() {
        val a = s(100, 110, 105, 120, 118, 130)
        assertEquals(1_000_000L, PriceMath.correlationPpm(a, a))
        val mirrored = LongArray(a.size) { 400 - a[it] }
        val c = PriceMath.correlationPpm(a, mirrored)!!
        assertTrue("got $c", c < -900_000L)
    }

    @Test fun correlationNeedsMovement() {
        assertNull(PriceMath.correlationPpm(s(1, 1, 1, 1), s(1, 2, 3, 4)))
        assertNull(PriceMath.correlationPpm(s(1, 2), s(1, 2)))
    }

    @Test fun stretchedPriceAgainstItsAverage() {
        assertTrue(PriceMath.stretched(130, 100, 200_000))
        assertTrue(!PriceMath.stretched(110, 100, 200_000))
        assertTrue(!PriceMath.stretched(5, 0, 1))
    }

    @Test fun spreadIsRelativeToTheMean() {
        val st = PriceMath.stats(s(90, 110, 100))!!
        assertEquals(200_000L, st.spreadPpm)
    }

    @Test fun zeroPricesDoNotDivideByZero() {
        val st = PriceMath.stats(s(0, 0, 0))!!
        assertEquals(0L, st.changePpm); assertEquals(0L, st.volatilityPpm); assertEquals(0L, st.drawdownPpm)
    }
}
