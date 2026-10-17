package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.GameDate
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Season
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.ClimateZone
import app.many.economytycoon.world.WeatherEvent
import app.many.economytycoon.world.WeatherKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridWeatherTest {
    private val region = RegionId(1)
    private val winterDay = Calendar.tickOf(GameDate(2001, 1, 10))
    private val summerDay = Calendar.tickOf(GameDate(2001, 7, 10))

    private fun day(zone: ClimateZone, tick: Tick, events: List<WeatherEvent> = emptyList(), seed: Long = 7L, wind: Long = 300_000L): GridWeather =
        GridWeatherModel.forDay(zone, tick, events, RandomProvider(seed).stream("energy.weather", 1), wind)

    private fun event(kind: WeatherKind, tick: Tick, severity: Long = 1_000_000L) = WeatherEvent(region, kind, tick, 10, severity)

    @Test fun sameSeedAndDayGiveTheSameWeather() {
        assertEquals(day(ClimateZone.TEMPERATE, winterDay), day(ClimateZone.TEMPERATE, winterDay))
    }

    @Test fun differentSeedsDiffer() {
        assertNotEquals(day(ClimateZone.TEMPERATE, winterDay, seed = 1), day(ClimateZone.TEMPERATE, winterDay, seed = 2))
    }

    @Test fun eventsDoNotChangeHowManyDrawsAreTaken() {
        val a = RandomProvider(3L).stream("w", 1)
        val b = RandomProvider(3L).stream("w", 1)
        GridWeatherModel.forDay(ClimateZone.COLD, winterDay, emptyList(), a, 300_000L)
        GridWeatherModel.forDay(ClimateZone.COLD, winterDay, listOf(event(WeatherKind.STORM, winterDay), event(WeatherKind.COLD_SNAP, winterDay)), b, 300_000L)
        assertEquals(a.state, b.state)
    }

    @Test fun winterHeatsAndSummerCoolsBothDrawingPower() {
        val cold = day(ClimateZone.COLD, winterDay)
        val hot = day(ClimateZone.ARID, summerDay)
        val mild = day(ClimateZone.TEMPERATE, Calendar.tickOf(GameDate(2001, 4, 10)))
        assertTrue(cold.thermalIndexPpm > 2_000_000L)
        assertTrue(hot.thermalIndexPpm > 1_500_000L)
        assertTrue(mild.thermalIndexPpm < 1_100_000L)
    }

    @Test fun aColdSnapRaisesDemandFurther() {
        val plain = day(ClimateZone.TEMPERATE, winterDay)
        val snap = day(ClimateZone.TEMPERATE, winterDay, listOf(event(WeatherKind.COLD_SNAP, winterDay)))
        assertTrue(snap.thermalIndexPpm > plain.thermalIndexPpm)
        assertTrue(snap.outageRiskPpm > plain.outageRiskPpm)
    }

    @Test fun aHeatwaveCostsThermalCapacity() {
        val hw = day(ClimateZone.MEDITERRANEAN, summerDay, listOf(event(WeatherKind.HEATWAVE, summerDay)))
        assertEquals(900_000L, hw.thermalDeratePpm)
        assertEquals(1_000_000L, day(ClimateZone.MEDITERRANEAN, summerDay).thermalDeratePpm)
        assertEquals(90_000L, hw.availableKw(Technology.GAS, 100_000L, 12))
    }

    @Test fun weakerSeverityCostsLess() {
        val full = day(ClimateZone.TEMPERATE, summerDay, listOf(event(WeatherKind.HEATWAVE, summerDay, 1_000_000L)))
        val half = day(ClimateZone.TEMPERATE, summerDay, listOf(event(WeatherKind.HEATWAVE, summerDay, 500_000L)))
        assertTrue(half.thermalDeratePpm > full.thermalDeratePpm)
    }

    @Test fun droughtStarvesHydro() {
        val dry = day(ClimateZone.TEMPERATE, summerDay, listOf(event(WeatherKind.DROUGHT, summerDay)))
        val normal = day(ClimateZone.TEMPERATE, summerDay)
        assertEquals(550_000L, normal.hydroPpm)
        assertEquals(220_000L, dry.hydroPpm)
    }

    @Test fun aSevereStormStopsTheTurbines() {
        val storm = day(ClimateZone.TROPICAL, summerDay, listOf(event(WeatherKind.STORM, summerDay, 1_300_000L)))
        assertEquals(50_000L, storm.windDayPpm)
        assertTrue(storm.outageRiskPpm >= 3_000_000L)
        assertTrue(storm.solarClearPpm < day(ClimateZone.TROPICAL, summerDay).solarClearPpm)
    }

    @Test fun anEventThatHasEndedHasNoEffect() {
        val old = WeatherEvent(region, WeatherKind.HEATWAVE, Tick(0), 5, 1_000_000L)
        assertEquals(1_000_000L, day(ClimateZone.ARID, summerDay, listOf(old)).thermalDeratePpm)
    }

    @Test fun windPersistsFromDayToDay() {
        val calm = day(ClimateZone.TEMPERATE, winterDay, wind = 50_000L)
        val windy = day(ClimateZone.TEMPERATE, winterDay, wind = 900_000L)
        assertTrue(windy.windDayPpm > calm.windDayPpm)
    }

    @Test fun windStaysWithinPhysicalBounds() {
        for (seed in 1L..40L) {
            val w = day(ClimateZone.COLD, winterDay, seed = seed)
            assertTrue(w.windDayPpm in 20_000L..950_000L)
            for (h in w.windHourlyPpm) assertTrue(h in 0L..1_000_000L)
        }
    }

    @Test fun hourlyWindAveragesCloseToTheDailyFigure() {
        for (seed in 1L..10L) {
            val w = day(ClimateZone.TEMPERATE, winterDay, seed = seed)
            val mean = w.windHourlyPpm.sum() / 24
            assertTrue("mean $mean day ${w.windDayPpm}", Math.abs(mean - w.windDayPpm) < 60_000L)
        }
    }

    @Test fun sunIsOutAtNoonAndGoneAtMidnight() {
        val w = GridWeather.mild(Season.SUMMER)
        assertEquals(0L, w.availableKw(Technology.SOLAR, 100_000L, 0))
        assertEquals(70_000L, w.availableKw(Technology.SOLAR, 100_000L, 12))
        assertTrue(w.availableKw(Technology.SOLAR, 100_000L, 12) > w.availableKw(Technology.SOLAR, 100_000L, 7))
    }

    @Test fun summerDaysAreLongerThanWinterDays() {
        val summer = (0 until 24).count { GridWeatherModel.solarShapePpm(Season.SUMMER, it) > 0L }
        val winter = (0 until 24).count { GridWeatherModel.solarShapePpm(Season.WINTER, it) > 0L }
        assertTrue(summer > winter)
        assertEquals(63_000L, GridWeather.mild(Season.AUTUMN).availableKw(Technology.SOLAR, 100_000L, 12))
    }

    @Test fun mildDayAvailabilityByTechnology() {
        val w = GridWeather.mild()
        assertEquals(30_000L, w.availableKw(Technology.WIND, 100_000L, 5))
        assertEquals(70_000L, w.availableKw(Technology.HYDRO, 100_000L, 5))
        assertEquals(100_000L, w.availableKw(Technology.COAL, 100_000L, 5))
        assertEquals(100_000L, w.availableKw(Technology.STORAGE, 100_000L, 5))
    }

    @Test fun climatesDiffer() {
        assertTrue(GridWeatherModel.clearnessMeanPpm(ClimateZone.ARID, Season.SUMMER) > GridWeatherModel.clearnessMeanPpm(ClimateZone.COLD, Season.WINTER))
        assertTrue(GridWeatherModel.windMeanPpm(ClimateZone.COLD, Season.WINTER) > GridWeatherModel.windMeanPpm(ClimateZone.TEMPERATE, Season.SUMMER))
        assertTrue(GridWeatherModel.hydroMeanPpm(ClimateZone.COLD, Season.SPRING) > GridWeatherModel.hydroMeanPpm(ClimateZone.ARID, Season.SPRING))
    }
}
