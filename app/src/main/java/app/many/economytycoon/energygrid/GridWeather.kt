package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.Season
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.world.ClimateTable
import app.many.economytycoon.world.ClimateZone
import app.many.economytycoon.world.WeatherEvent
import app.many.economytycoon.world.WeatherKind

/**
 * What the weather does to a grid on one day, as numbers the dispatch can use. All values are parts per million of
 * the nameplate or of the normal level.
 */
data class GridWeather(
    /** Heating plus cooling need relative to a mild day; 1,000,000 is mild. */
    val thermalIndexPpm: Long,
    /** Average wind capacity factor of the day. */
    val windDayPpm: Long,
    /** Hourly wind capacity factor, 24 values. */
    val windHourlyPpm: List<Long>,
    /** Share of the clear-sky solar output that gets through the clouds. */
    val solarClearPpm: Long,
    /** Share of hydro nameplate that the water allows. */
    val hydroPpm: Long,
    /** Capacity left to thermal plants after heat and floods; 1,000,000 is no loss. */
    val thermalDeratePpm: Long,
    /** Multiplier on the chance of a forced outage; 1,000,000 is normal. */
    val outageRiskPpm: Long,
    val season: Season,
) {
    init { require(windHourlyPpm.size == GridUnits.HOURS_PER_DAY) { "wind needs 24 hourly values" } }

    /** Kilowatts a unit of [tech] and [capacityKw] could deliver in [hour], before outages and ramping. */
    fun availableKw(tech: Technology, capacityKw: Long, hour: Int): Long = when (tech) {
        Technology.WIND -> mulDiv(capacityKw, windHourlyPpm[hour], Rate.ONE_PPM, RoundingMode.DOWN)
        Technology.SOLAR -> mulDiv(mulDiv(capacityKw, GridWeatherModel.solarShapePpm(season, hour), Rate.ONE_PPM, RoundingMode.DOWN), solarClearPpm, Rate.ONE_PPM, RoundingMode.DOWN)
        Technology.HYDRO -> mulDiv(capacityKw, hydroPpm, Rate.ONE_PPM, RoundingMode.DOWN)
        Technology.STORAGE -> capacityKw
        Technology.COAL, Technology.GAS, Technology.NUCLEAR -> mulDiv(capacityKw, thermalDeratePpm, Rate.ONE_PPM, RoundingMode.DOWN)
    }

    companion object {
        /** A calm, mild day with fixed wind and sun. Useful as a neutral input. */
        fun mild(season: Season = Season.AUTUMN, windPpm: Long = 300_000L, clearPpm: Long = 700_000L): GridWeather =
            GridWeather(Rate.ONE_PPM, windPpm, List(GridUnits.HOURS_PER_DAY) { windPpm }, clearPpm, 700_000L, Rate.ONE_PPM, Rate.ONE_PPM, season)
    }
}

/** Turns climate, season and active weather events into a [GridWeather], using one seeded stream per grid. */
object GridWeatherModel {
    private val SOLAR_SUMMER = longArrayOf(
        0, 0, 0, 0, 0, 30, 130, 300, 490, 670, 810, 920, 1000, 970, 880, 750, 570, 380, 190, 60, 0, 0, 0, 0,
    ).map { it * 1_000L }
    private val SOLAR_WINTER = longArrayOf(
        0, 0, 0, 0, 0, 0, 0, 40, 170, 380, 580, 730, 800, 730, 580, 380, 170, 40, 0, 0, 0, 0, 0, 0,
    ).map { it * 1_000L }
    private val SOLAR_MID = List(GridUnits.HOURS_PER_DAY) { (SOLAR_SUMMER[it] + SOLAR_WINTER[it]) / 2 }

    /** Wind blows a little harder at night: this shape averages one million over the day. */
    private val WIND_SHAPE = longArrayOf(
        1100, 1110, 1120, 1120, 1110, 1090, 1040, 980, 920, 880, 860, 850, 850, 860, 880, 910, 940, 970, 1000, 1030, 1050, 1070, 1090, 1100,
    ).let { raw -> app.many.economytycoon.core.splitLargestRemainder(24_000_000L, LongArray(raw.size) { raw[it] * 1_000L }) }

    /** Clear-sky output of solar, in ppm of nameplate, for an hour of a season. */
    fun solarShapePpm(season: Season, hour: Int): Long = when (season) {
        Season.SUMMER -> SOLAR_SUMMER[hour]
        Season.WINTER -> SOLAR_WINTER[hour]
        Season.SPRING, Season.AUTUMN -> SOLAR_MID[hour]
    }

    fun windMeanPpm(zone: ClimateZone, season: Season): Long = when (zone) {
        ClimateZone.TEMPERATE -> when (season) { Season.WINTER -> 400_000L; Season.SPRING -> 340_000L; Season.SUMMER -> 240_000L; Season.AUTUMN -> 330_000L }
        ClimateZone.COLD -> when (season) { Season.WINTER -> 440_000L; Season.SPRING -> 380_000L; Season.SUMMER -> 260_000L; Season.AUTUMN -> 380_000L }
        ClimateZone.TROPICAL -> 250_000L
        ClimateZone.ARID -> when (season) { Season.WINTER -> 280_000L; Season.SPRING -> 330_000L; Season.SUMMER -> 300_000L; Season.AUTUMN -> 270_000L }
        ClimateZone.MEDITERRANEAN -> when (season) { Season.WINTER -> 330_000L; Season.SPRING -> 330_000L; Season.SUMMER -> 260_000L; Season.AUTUMN -> 310_000L }
    }

    fun clearnessMeanPpm(zone: ClimateZone, season: Season): Long = when (zone) {
        ClimateZone.ARID -> 900_000L
        ClimateZone.MEDITERRANEAN -> if (season == Season.SUMMER) 850_000L else if (season == Season.WINTER) 650_000L else 750_000L
        ClimateZone.TEMPERATE -> if (season == Season.SUMMER) 700_000L else if (season == Season.WINTER) 450_000L else 580_000L
        ClimateZone.TROPICAL -> 650_000L
        ClimateZone.COLD -> if (season == Season.SUMMER) 650_000L else if (season == Season.WINTER) 350_000L else 500_000L
    }

    fun hydroMeanPpm(zone: ClimateZone, season: Season): Long = when (zone) {
        ClimateZone.TEMPERATE -> when (season) { Season.WINTER -> 600_000L; Season.SPRING -> 800_000L; Season.SUMMER -> 550_000L; Season.AUTUMN -> 600_000L }
        ClimateZone.COLD -> when (season) { Season.WINTER -> 400_000L; Season.SPRING -> 900_000L; Season.SUMMER -> 800_000L; Season.AUTUMN -> 500_000L }
        ClimateZone.TROPICAL -> when (season) { Season.WINTER -> 800_000L; Season.SPRING -> 850_000L; Season.SUMMER -> 900_000L; Season.AUTUMN -> 850_000L }
        ClimateZone.ARID -> when (season) { Season.WINTER -> 300_000L; Season.SPRING -> 350_000L; Season.SUMMER -> 200_000L; Season.AUTUMN -> 250_000L }
        ClimateZone.MEDITERRANEAN -> when (season) { Season.WINTER -> 600_000L; Season.SPRING -> 650_000L; Season.SUMMER -> 350_000L; Season.AUTUMN -> 500_000L }
    }

    private fun mul(a: Long, b: Long): Long = mulDiv(a, b, Rate.ONE_PPM, RoundingMode.HALF_EVEN)

    /**
     * The day's weather for a grid. [previousWindPpm] carries yesterday's wind forward so windy and calm spells last
     * several days. The stream is advanced by the same number of draws every day whatever the conditions are, so
     * adding an event never changes the draws that follow.
     */
    fun forDay(zone: ClimateZone, tick: Tick, events: List<WeatherEvent>, rnd: DeterministicRandom, previousWindPpm: Long): GridWeather {
        val season = Calendar.season(tick)
        val profile = ClimateTable.profile(zone, season)
        val active = events.filter { it.activeAt(tick) }
        fun scaled(kind: WeatherKind, base: Long): Long? = active.firstOrNull { it.kind == kind }?.scaled(base)

        var heating = profile.heating
        var cooling = profile.cooling
        scaled(WeatherKind.COLD_SNAP, WeatherKind.COLD_SNAP.demandHeatPpm)?.let { heating = mul(heating, it) }
        scaled(WeatherKind.HEATWAVE, 1_400_000L)?.let { cooling = mul(cooling, it) }

        val windNoise = rnd.nextGaussianPpm()
        val clearNoise = rnd.nextGaussianPpm()
        val gusts = LongArray(GridUnits.HOURS_PER_DAY) { rnd.nextGaussianPpm() }

        val mean = windMeanPpm(zone, season)
        var windDay = (previousWindPpm * 550L + mean * 450L) / 1_000L + windNoise * 110_000L / Rate.ONE_PPM
        windDay = windDay.coerceIn(20_000L, 950_000L)
        val storm = active.firstOrNull { it.kind == WeatherKind.STORM }
        if (storm != null) windDay = if (storm.severityPpm > 1_000_000L) 50_000L else (windDay * 13L / 10L).coerceAtMost(950_000L)
        val hourly = List(GridUnits.HOURS_PER_DAY) { h ->
            val shaped = mul(windDay, WIND_SHAPE[h])
            (shaped + gusts[h] * 40_000L / Rate.ONE_PPM * windDay / Rate.ONE_PPM).coerceIn(0L, 1_000_000L)
        }

        var clear = (clearnessMeanPpm(zone, season) + clearNoise * 120_000L / Rate.ONE_PPM).coerceIn(150_000L, 1_000_000L)
        for (kind in listOf(WeatherKind.STORM, WeatherKind.FLOOD)) scaled(kind, 450_000L)?.let { clear = mul(clear, it) }

        var hydro = hydroMeanPpm(zone, season)
        scaled(WeatherKind.DROUGHT, 400_000L)?.let { hydro = mul(hydro, it.coerceIn(0L, 1_000_000L)) }

        var derate = Rate.ONE_PPM
        scaled(WeatherKind.HEATWAVE, WeatherKind.HEATWAVE.capacityPpm)?.let { derate = mul(derate, it) }
        scaled(WeatherKind.FLOOD, WeatherKind.FLOOD.capacityPpm)?.let { derate = mul(derate, it) }

        var risk = Rate.ONE_PPM
        scaled(WeatherKind.STORM, 3_000_000L)?.let { risk = mul(risk, it.coerceAtLeast(Rate.ONE_PPM)) }
        scaled(WeatherKind.COLD_SNAP, 1_800_000L)?.let { risk = mul(risk, it.coerceAtLeast(Rate.ONE_PPM)) }
        scaled(WeatherKind.HEATWAVE, 1_500_000L)?.let { risk = mul(risk, it.coerceAtLeast(Rate.ONE_PPM)) }

        return GridWeather(heating + cooling, windDay, hourly, clear, hydro, derate.coerceIn(0L, Rate.ONE_PPM), risk.coerceAtMost(6_000_000L), season)
    }
}
