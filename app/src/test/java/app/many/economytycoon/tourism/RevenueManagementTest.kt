package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevenueManagementTest {
    private val w = TourismWorld()
    private val mix = longArrayOf(250_000L, 250_000L, 250_000L, 250_000L)

    private fun dynamic(p: Property, floor: Long = 600_000L, ceiling: Long = 1_800_000L, aggression: Long = 800_000L, target: Long = 780_000L) {
        w.service.setPricing(p.id, RatePolicy.DYNAMIC, p.settings.rackRate).getOrThrow()
        w.service.setPricingBand(p.id, floor, ceiling, aggression, target).getOrThrow()
    }

    @Test fun forecastWithNothingBookedLeansOnTheBaseline() {
        val soon = RevenueManagement.forecastOccupancyPpm(0, 100, 900_000L, 700_000L)
        assertEquals(700_000L * 100_000L / 1_000_000L, soon)
        val far = RevenueManagement.forecastOccupancyPpm(0, 100, 0L, 650_000L)
        assertEquals(650_000L, far)
    }

    @Test fun forecastNeverFallsBelowWhatIsAlreadyBooked() {
        for (booked in listOf(0, 10, 50, 90, 100)) {
            val f = RevenueManagement.forecastOccupancyPpm(booked, 100, 600_000L, 300_000L)
            assertTrue(f >= booked * 10_000L)
        }
    }

    @Test fun fastPickupRaisesTheForecastAboveBaseline() {
        val slow = RevenueManagement.forecastOccupancyPpm(20, 100, 500_000L, 600_000L)
        val fast = RevenueManagement.forecastOccupancyPpm(60, 100, 500_000L, 600_000L)
        assertTrue(fast > slow)
        assertTrue(fast > 600_000L)
    }

    @Test fun forecastOfAnEmptyPropertyIsZeroAndIsCapped() {
        assertEquals(0L, RevenueManagement.forecastOccupancyPpm(5, 0, 500_000L, 500_000L))
        assertTrue(RevenueManagement.forecastOccupancyPpm(500, 100, 300_000L, 500_000L) <= 1_500_000L)
    }

    @Test fun multiplierRisesWithForecastAndStaysInTheBand() {
        val s = RateSettings(RatePolicy.DYNAMIC, Money.units(100))
        val low = RevenueManagement.multiplierPpm(300_000L, s)
        val onTarget = RevenueManagement.multiplierPpm(s.targetOccupancyPpm, s)
        val high = RevenueManagement.multiplierPpm(1_000_000L, s)
        assertTrue(low < onTarget && onTarget < high)
        assertEquals(1_000_000L, onTarget)
        assertTrue(low >= s.floorPpm && high <= s.ceilingPpm)
        assertEquals(s.floorPpm, RevenueManagement.multiplierPpm(0L, s.copy(aggressionPpm = 2_000_000L)))
        assertEquals(s.ceilingPpm, RevenueManagement.multiplierPpm(1_500_000L, s.copy(aggressionPpm = 2_000_000L)))
    }

    @Test fun zeroAggressionNeverMovesThePrice() {
        val s = RateSettings(RatePolicy.DYNAMIC, Money.units(100), aggressionPpm = 0L)
        assertEquals(1_000_000L, RevenueManagement.multiplierPpm(0L, s))
        assertEquals(1_000_000L, RevenueManagement.multiplierPpm(1_000_000L, s))
    }

    @Test fun fixedPoliciesQuoteTheRackRateLessTheCancellationDiscount() {
        val p = w.hotel()
        val q = RevenueManagement.quote(p, RoomClass.STANDARD, 5, 0, 80, 600_000L, mix)
        assertEquals(p.rackRate(RoomClass.STANDARD), q.rate)
        assertEquals(1_000_000L, q.multiplierPpm)
        w.service.setCancellation(p.id, CancellationPolicy.NON_REFUNDABLE).getOrThrow()
        val cheaper = RevenueManagement.quote(p, RoomClass.STANDARD, 5, 0, 80, 600_000L, mix)
        assertEquals(p.rackRate(RoomClass.STANDARD).raw * 880_000L / 1_000_000L, cheaper.rate.raw)
    }

    @Test fun dynamicQuotesRiseAsTheNightFills() {
        val p = w.hotel()
        dynamic(p)
        val empty = RevenueManagement.quote(p, RoomClass.STANDARD, 2, 5, 80, 500_000L, mix)
        val full = RevenueManagement.quote(p, RoomClass.STANDARD, 2, 70, 80, 500_000L, mix)
        assertTrue(full.rate > empty.rate)
        assertTrue(full.forecastOccupancyPpm > empty.forecastOccupancyPpm)
    }

    @Test fun suitesCostMoreThanStandardRoomsAtTheSameMultiplier() {
        val p = w.hotel()
        dynamic(p)
        val std = RevenueManagement.quote(p, RoomClass.STANDARD, 4, 10, 80, 500_000L, mix)
        val suite = RevenueManagement.quote(p, RoomClass.SUITE, 4, 10, 80, 500_000L, mix)
        assertTrue(suite.rate > std.rate)
        assertEquals(std.multiplierPpm, suite.multiplierPpm)
    }

    @Test fun quotesFlagTheFloorAndCeiling() {
        val p = w.hotel()
        dynamic(p, floor = 900_000L, ceiling = 1_100_000L, aggression = 2_000_000L)
        val low = RevenueManagement.quote(p, RoomClass.STANDARD, 0, 0, 80, 0L, mix)
        val high = RevenueManagement.quote(p, RoomClass.STANDARD, 0, 80, 80, 1_000_000L, mix)
        assertTrue(low.atFloor && !low.atCeiling)
        assertTrue(high.atCeiling && !high.atFloor)
        assertEquals(900_000L, low.multiplierPpm)
        assertEquals(1_100_000L, high.multiplierPpm)
    }

    @Test fun revParIsRateTimesOccupancy() {
        assertEquals(Money.units(75), RevenueManagement.revPar(Money.units(100), 750_000L))
        assertEquals(Money.ZERO, RevenueManagement.revPar(Money.units(100), 0L))
    }

    @Test fun binomialWeightsSumToOneAndPeakNearTheMean() {
        for ((n, p) in listOf(1 to 500_000L, 10 to 900_000L, 100 to 920_000L, 300 to 850_000L, 50 to 1_000L)) {
            val w = RevenueManagement.binomialWeights(n, p)
            assertEquals(n + 1, w.size)
            assertEquals(1_000_000_000_000_000L, w.sum())
            val mode = w.indices.maxByOrNull { w[it] } ?: -1
            assertTrue("n=$n p=$p mode=$mode", Math.abs(mode - n * p / 1_000_000.0) <= 1.5)
        }
    }

    @Test fun binomialEdgeCases() {
        assertEquals(1_000_000_000_000_000L, RevenueManagement.binomialWeights(0, 500_000L)[0])
        assertEquals(1_000_000_000_000_000L, RevenueManagement.binomialWeights(5, 0L)[0])
        assertEquals(1_000_000_000_000_000L, RevenueManagement.binomialWeights(5, 1_000_000L)[5])
    }

    @Test fun nobodyIsWalkedWhenSoldWithinCapacity() {
        assertEquals(0L, RevenueManagement.expectedWalksMilli(100, 100, 900_000L))
        assertEquals(0L, RevenueManagement.expectedWalksMilli(80, 100, 950_000L))
    }

    @Test fun walksGrowWithOverselling() {
        var last = -1L
        for (sold in 100..120 step 4) {
            val walks = RevenueManagement.expectedWalksMilli(sold, 100, 900_000L)
            assertTrue(walks >= last)
            last = walks
        }
        assertTrue(last > 0L)
    }

    @Test fun sureShowsAreWalkedExactly() {
        assertEquals(5_000L, RevenueManagement.expectedWalksMilli(105, 100, 1_000_000L))
        assertEquals(100_000L, RevenueManagement.expectedUsedMilli(105, 100, 1_000_000L))
    }

    @Test fun expectedUseNeverExceedsCapacityOrShows() {
        for (sold in listOf(50, 100, 110, 130)) {
            val used = RevenueManagement.expectedUsedMilli(sold, 100, 880_000L)
            assertTrue(used <= 100_000L)
            assertTrue(used <= sold * 880L + 1L)
        }
    }

    @Test fun overbookingIsWorthlessWhenEveryoneShows() {
        assertEquals(0, RevenueManagement.bestOverbooking(100, 1_000_000L, 1_600_000L))
    }

    @Test fun lowerShowRatesSupportMoreOverbooking() {
        val high = RevenueManagement.bestOverbooking(200, 950_000L, 1_600_000L)
        val low = RevenueManagement.bestOverbooking(200, 800_000L, 1_600_000L)
        assertTrue(low > high)
        assertTrue(high >= 0)
    }

    @Test fun dearWalksCallForLessOverbooking() {
        val cheapWalk = RevenueManagement.bestOverbooking(200, 880_000L, 500_000L)
        val dearWalk = RevenueManagement.bestOverbooking(200, 880_000L, 6_000_000L)
        assertTrue(cheapWalk >= dearWalk)
    }

    @Test fun overbookingIsCappedAtTheAllowedShare() {
        val extra = RevenueManagement.bestOverbooking(100, 500_000L, 100_000L, maxExtraPpm = 100_000L)
        assertTrue(extra <= 10)
    }

    @Test fun suggestedAllowanceIsAShareOfCapacity() {
        val ppm = RevenueManagement.suggestedOverbookPpm(100, 880_000L)
        assertTrue(ppm in 0..300_000L)
        assertEquals(0L, RevenueManagement.suggestedOverbookPpm(0, 880_000L))
        assertFalse(RevenueManagement.suggestedOverbookPpm(100, 1_000_000L) > 0L)
    }

    @Test fun emptyCapacityNeverOverbooks() {
        assertEquals(0, RevenueManagement.bestOverbooking(0, 800_000L, 1_000_000L))
    }
}
