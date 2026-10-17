package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Season
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadShapeTest {
    private val mild = GridWeather.mild()

    @Test fun everyCurveAveragesToExactlyOne() {
        for (c in CustomerClass.values()) assertEquals(c.name, 24_000_000L, LoadShape.curve(c).sum())
    }

    @Test fun householdsPeakInTheEveningAndSleepAtNight() {
        val h = LoadShape.curve(CustomerClass.HOUSEHOLD)
        assertEquals(19, h.indices.maxByOrNull { h[it] })
        assertEquals(3, h.indices.minByOrNull { h[it] })
        assertTrue(h[19] > 1_700_000L)
    }

    @Test fun commercialLoadFollowsOfficeHours() {
        val c = LoadShape.curve(CustomerClass.COMMERCIAL)
        assertTrue(c[11] > c[3] * 3)
        assertTrue(c[22] < c[11])
    }

    @Test fun industryIsNearlyFlat() {
        val i = LoadShape.curve(CustomerClass.INDUSTRIAL)
        assertTrue(i.max() - i.min() < 500_000L)
        assertEquals(LoadShape.curve(CustomerClass.INDUSTRIAL), LoadShape.curve(CustomerClass.INTERRUPTIBLE))
    }

    @Test fun mildWeatherLeavesTheShapeAlone() {
        val base = 100_000L
        for (hour in 0 until 24) {
            val expected = base * LoadShape.hourPpm(CustomerClass.HOUSEHOLD, hour) / 1_000_000L
            assertTrue(Math.abs(LoadShape.demandKw(base, CustomerClass.HOUSEHOLD, Tick(0), hour, mild) - expected) <= 1L)
        }
    }

    @Test fun aDayOfDemandAddsUpToTheBaseDrawTimesTwentyFourHours() {
        val day = LoadShape.dayKw(100_000L, CustomerClass.COMMERCIAL, Tick(0), mild)
        assertTrue(Math.abs(day.sum() - 2_400_000L) <= 24L)
    }

    @Test fun weekendsShiftDemandBetweenClasses() {
        val weekday = Tick(1)
        val saturday = Tick(5)
        assertTrue(!LoadShape.isWeekend(weekday) && LoadShape.isWeekend(saturday))
        assertTrue(LoadShape.weekdayPpm(CustomerClass.COMMERCIAL, saturday) < 1_000_000L)
        assertTrue(LoadShape.weekdayPpm(CustomerClass.HOUSEHOLD, saturday) > 1_000_000L)
        assertEquals(1_000_000L, LoadShape.weekdayPpm(CustomerClass.INDUSTRIAL, weekday))
    }

    @Test fun coldDaysRaiseHouseholdDemandMoreThanIndustrial() {
        val cold = mild.copy(thermalIndexPpm = 2_000_000L)
        val household = LoadShape.demandKw(100_000L, CustomerClass.HOUSEHOLD, Tick(0), 12, cold).toDouble() / LoadShape.demandKw(100_000L, CustomerClass.HOUSEHOLD, Tick(0), 12, mild)
        val industry = LoadShape.demandKw(100_000L, CustomerClass.INDUSTRIAL, Tick(0), 12, cold).toDouble() / LoadShape.demandKw(100_000L, CustomerClass.INDUSTRIAL, Tick(0), 12, mild)
        assertEquals(1.35, household, 0.001)
        assertEquals(1.06, industry, 0.001)
    }

    @Test fun noBaseDrawMeansNoDemand() {
        assertEquals(0L, LoadShape.demandKw(0L, CustomerClass.HOUSEHOLD, Tick(0), 19, mild))
        assertEquals(0L, LoadShape.demandKw(-5L, CustomerClass.HOUSEHOLD, Tick(0), 19, mild))
    }

    @Test(expected = IllegalArgumentException::class) fun hoursOutsideTheDayAreRefused() {
        LoadShape.hourPpm(CustomerClass.HOUSEHOLD, 24)
    }

    @Test fun seasonDoesNotChangeTheCurveItself() {
        val winter = mild.copy(season = Season.WINTER)
        assertEquals(LoadShape.demandKw(50_000L, CustomerClass.HOUSEHOLD, Tick(0), 8, mild), LoadShape.demandKw(50_000L, CustomerClass.HOUSEHOLD, Tick(0), 8, winter))
    }
}
