package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllocationTest {
    private val w = TourismWorld()
    private val even = longArrayOf(250_000L, 250_000L, 250_000L, 250_000L)

    @Test fun waterFillSharesByWeight() {
        val out = Allocation.waterFill(100, longArrayOf(3, 1), intArrayOf(1_000, 1_000))
        assertArrayEquals(intArrayOf(75, 25), out)
    }

    @Test fun waterFillNeverExceedsCapacityAndPassesTheOverflowOn() {
        val out = Allocation.waterFill(100, longArrayOf(9, 1), intArrayOf(20, 1_000))
        assertEquals(20, out[0])
        assertEquals(80, out[1])
    }

    @Test fun waterFillStopsWhenEverythingIsFull() {
        val out = Allocation.waterFill(500, longArrayOf(1, 1, 1), intArrayOf(10, 20, 30))
        assertArrayEquals(intArrayOf(10, 20, 30), out)
    }

    @Test fun waterFillIgnoresZeroWeightsAndRequests() {
        assertArrayEquals(intArrayOf(0, 0), Allocation.waterFill(0, longArrayOf(1, 1), intArrayOf(5, 5)))
        assertArrayEquals(intArrayOf(0, 10), Allocation.waterFill(10, longArrayOf(0, 1), intArrayOf(5, 50)))
        assertArrayEquals(intArrayOf(), Allocation.waterFill(10, longArrayOf(), intArrayOf()))
    }

    @Test fun waterFillAllocatesExactlyWhatFits() {
        for (requests in listOf(1L, 7L, 99L, 100L, 333L)) {
            val caps = intArrayOf(40, 25, 60)
            val out = Allocation.waterFill(requests, longArrayOf(5, 3, 2), caps)
            assertEquals(minOf(requests, caps.sum().toLong()), out.sum().toLong())
            for (i in out.indices) assertTrue(out[i] in 0..caps[i])
        }
    }

    @Test fun waterFillTiesGoToTheLowerIndex() {
        val out = Allocation.waterFill(1, longArrayOf(1, 1, 1), intArrayOf(5, 5, 5))
        assertArrayEquals(intArrayOf(1, 0, 0), out)
    }

    @Test(expected = IllegalArgumentException::class) fun waterFillNeedsMatchingArrays() {
        Allocation.waterFill(5, longArrayOf(1), intArrayOf(1, 2))
    }

    @Test fun starMatchFallsByDistanceWithAFloor() {
        assertEquals(1_000_000L, Allocation.starMatchPpm(4, 4))
        assertEquals(750_000L, Allocation.starMatchPpm(3, 4))
        assertEquals(750_000L, Allocation.starMatchPpm(5, 4))
        assertEquals(100_000L, Allocation.starMatchPpm(1, 5))
    }

    @Test fun valueFactorFavoursCheapRoomsByTheSegmentsSensitivity() {
        val fair = Money.units(100)
        assertEquals(1_000_000L, Allocation.valueFactorPpm(fair, fair, 1_000_000L))
        assertTrue(Allocation.valueFactorPpm(Money.units(70), fair, 1_000_000L) > 1_000_000L)
        assertTrue(Allocation.valueFactorPpm(Money.units(150), fair, 1_000_000L) < 1_000_000L)
        assertTrue(Allocation.valueFactorPpm(Money.units(70), fair, 1_400_000L) > Allocation.valueFactorPpm(Money.units(70), fair, 400_000L))
        assertEquals(1_000_000L, Allocation.valueFactorPpm(Money.ZERO, fair, 1_000_000L))
    }

    @Test fun valueFactorIsBounded() {
        assertEquals(2_500_000L, Allocation.valueFactorPpm(Money(1), Money.units(100), 2_000_000L))
        assertEquals(100_000L, Allocation.valueFactorPpm(Money.units(10_000), Money.units(100), 2_000_000L))
    }

    @Test fun fairRateScalesWithClassAndPriceLevel() {
        val p = w.hotel(stars = 3)
        val std = Allocation.fairRate(p, RoomClass.STANDARD, 1_000_000L)
        assertEquals(Money.units(105), std)
        assertTrue(Allocation.fairRate(p, RoomClass.SUITE, 1_000_000L) > std)
        assertEquals(std * 2L, Allocation.fairRate(p, RoomClass.STANDARD, 2_000_000L))
    }

    @Test fun biggerAndBetterReviewedPropertiesWeighMore() {
        val small = w.hotel(rooms = intArrayOf(20, 0, 0))
        val big = w.hotel(rooms = intArrayOf(200, 0, 0))
        val fair = Allocation.fairRate(small, RoomClass.STANDARD, 1_000_000L)
        assertTrue(Allocation.propertyWeight(big, VisitorSegment.FAMILY, fair, fair) > Allocation.propertyWeight(small, VisitorSegment.FAMILY, fair, fair))
        val before = Allocation.propertyWeight(big, VisitorSegment.FAMILY, fair, fair)
        big.reviews.restore(950_000L, 100L, 900_000L, emptyList())
        assertTrue(Allocation.propertyWeight(big, VisitorSegment.FAMILY, fair, fair) > before)
    }

    @Test fun luxuryGuestsFavourFiveStarsAndBudgetGuestsDoNot() {
        val two = w.hotel(stars = 2, rooms = intArrayOf(50, 0, 0))
        val five = w.hotel(stars = 5, rooms = intArrayOf(50, 0, 0))
        val fair2 = Allocation.fairRate(two, RoomClass.STANDARD, 1_000_000L)
        val fair5 = Allocation.fairRate(five, RoomClass.STANDARD, 1_000_000L)
        assertTrue(Allocation.propertyWeight(five, VisitorSegment.LUXURY, fair5, fair5) > Allocation.propertyWeight(two, VisitorSegment.LUXURY, fair2, fair2))
        assertTrue(Allocation.propertyWeight(two, VisitorSegment.BUDGET, fair2, fair2) > Allocation.propertyWeight(five, VisitorSegment.BUDGET, fair5, fair5))
    }

    @Test fun anEmptyHistoryForecastsTheDefaultOccupancy() {
        val p = w.hotel()
        assertEquals(DemandForecast.DEFAULT_OCCUPANCY_PPM, DemandForecast.recentOccupancyPpm(p))
    }

    @Test fun calendarRatioCompareTwoDatesAndStaysInBounds() {
        val summer = DemandForecast.calendarRatioPpm(even, 30, 7 * 30 + 3L)
        val winter = DemandForecast.calendarRatioPpm(even, 7 * 30 + 3L, 30)
        assertTrue(summer > 1_000_000L)
        assertTrue(winter < 1_000_000L)
        assertTrue(summer in 500_000L..1_600_000L)
        assertEquals(1_000_000L, DemandForecast.calendarRatioPpm(longArrayOf(0, 0, 0, 0), 5, 50))
    }

    @Test fun baselineOccupancyIsBoundedAndSeasonal() {
        val p = w.hotel()
        val high = DemandForecast.baselineOccupancyPpm(p, 30, 7 * 30 + 3L, even)
        val low = DemandForecast.baselineOccupancyPpm(p, 7 * 30 + 3L, 30, even)
        assertTrue(high > low)
        assertTrue(high <= 1_000_000L && low >= 50_000L)
    }

    @Test fun weeklyForecastHasOneEntryPerWeek() {
        val p = w.hotel()
        assertEquals(6, DemandForecast.weeklyOccupancyPpm(p, 100, even, 6).size)
        assertTrue(DemandForecast.weeklyOccupancyPpm(p, 100, even, 0).isEmpty())
    }

    @Test fun afterTradingTheForecastFollowsTheRecord() {
        val p = w.hotel()
        w.days(45)
        val recent = DemandForecast.recentOccupancyPpm(p)
        assertTrue(recent != DemandForecast.DEFAULT_OCCUPANCY_PPM)
        assertTrue(recent in 1..1_000_000L)
    }
}
