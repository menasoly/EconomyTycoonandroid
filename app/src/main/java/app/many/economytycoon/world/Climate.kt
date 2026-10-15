package app.many.economytycoon.world

import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Season
import app.many.economytycoon.core.Tick

/** Seasonal multipliers (ppm of 1.0) for one climate zone. */
data class SeasonProfile(val agriYield: Long, val heating: Long, val cooling: Long, val transportDelay: Long)

object ClimateTable {
    private fun p(a: Long, h: Long, c: Long, t: Long) = SeasonProfile(a, h, c, t)

    fun profile(zone: ClimateZone, season: Season): SeasonProfile = when (zone) {
        ClimateZone.TEMPERATE -> when (season) {
            Season.WINTER -> p(150_000, 1_600_000, 50_000, 1_250_000)
            Season.SPRING -> p(900_000, 900_000, 100_000, 1_050_000)
            Season.SUMMER -> p(1_300_000, 150_000, 800_000, 1_000_000)
            Season.AUTUMN -> p(1_100_000, 800_000, 150_000, 1_050_000)
        }
        ClimateZone.TROPICAL -> when (season) {
            Season.WINTER -> p(1_000_000, 100_000, 900_000, 1_100_000)
            Season.SPRING -> p(1_100_000, 100_000, 1_000_000, 1_050_000)
            Season.SUMMER -> p(1_200_000, 50_000, 1_300_000, 1_300_000)
            Season.AUTUMN -> p(1_100_000, 100_000, 1_000_000, 1_150_000)
        }
        ClimateZone.ARID -> when (season) {
            Season.WINTER -> p(400_000, 500_000, 100_000, 1_000_000)
            Season.SPRING -> p(500_000, 200_000, 500_000, 1_000_000)
            Season.SUMMER -> p(250_000, 50_000, 1_600_000, 1_100_000)
            Season.AUTUMN -> p(450_000, 250_000, 600_000, 1_000_000)
        }
        ClimateZone.COLD -> when (season) {
            Season.WINTER -> p(0, 2_200_000, 0, 1_500_000)
            Season.SPRING -> p(500_000, 1_400_000, 50_000, 1_250_000)
            Season.SUMMER -> p(900_000, 500_000, 250_000, 1_050_000)
            Season.AUTUMN -> p(600_000, 1_300_000, 50_000, 1_200_000)
        }
        ClimateZone.MEDITERRANEAN -> when (season) {
            Season.WINTER -> p(500_000, 900_000, 50_000, 1_100_000)
            Season.SPRING -> p(1_100_000, 500_000, 250_000, 1_000_000)
            Season.SUMMER -> p(1_000_000, 100_000, 1_200_000, 1_000_000)
            Season.AUTUMN -> p(1_100_000, 500_000, 400_000, 1_000_000)
        }
    }
}

enum class WeatherKind(val label: String, val agriPpm: Long, val transportPpm: Long, val demandHeatPpm: Long, val capacityPpm: Long) {
    DROUGHT("Drought", 450_000, 1_000_000, 1_000_000, 1_000_000),
    FLOOD("Flood", 600_000, 1_700_000, 1_000_000, 850_000),
    HEATWAVE("Heatwave", 800_000, 1_050_000, 600_000, 900_000),
    COLD_SNAP("Cold snap", 700_000, 1_300_000, 1_700_000, 950_000),
    STORM("Storm", 900_000, 1_800_000, 1_100_000, 700_000),
    PEST_OUTBREAK("Pest outbreak", 550_000, 1_000_000, 1_000_000, 1_000_000),
}

data class WeatherEvent(
    val region: RegionId,
    val kind: WeatherKind,
    val start: Tick,
    val durationTicks: Int,
    /** Scales how far multipliers deviate from 1.0; 1,000,000 is the nominal strength. */
    val severityPpm: Long,
) {
    val end: Tick get() = start + durationTicks.toLong()
    fun activeAt(tick: Tick) = tick >= start && tick < end

    /** Multiplier adjusted by severity: 1 - (1 - base) * severity. */
    fun scaled(base: Long): Long {
        val delta = base - 1_000_000L
        return 1_000_000L + delta * severityPpm / 1_000_000L
    }
}

/** Weather as a deterministic function of seeded streams; events are generated once per region and tick. */
class WeatherSystem(private val regions: List<Region>) {
    private val active = ArrayList<WeatherEvent>()
    private val history = ArrayList<WeatherEvent>()

    fun active(): List<WeatherEvent> = active.toList()
    fun history(): List<WeatherEvent> = history.toList()
    fun activeIn(region: RegionId, tick: Tick): List<WeatherEvent> = active.filter { it.region == region && it.activeAt(tick) }

    /** Yield multiplier for farming in [region] at [tick], combining season and events. */
    fun agriMultiplier(region: Region, tick: Tick): Long {
        var m = ClimateTable.profile(region.climate, app.many.economytycoon.core.Calendar.season(tick)).agriYield
        for (e in activeIn(region.id, tick)) m = m * e.scaled(e.kind.agriPpm) / 1_000_000L
        return m
    }

    fun transportMultiplier(region: Region, tick: Tick): Long {
        var m = ClimateTable.profile(region.climate, app.many.economytycoon.core.Calendar.season(tick)).transportDelay
        for (e in activeIn(region.id, tick)) m = m * e.scaled(e.kind.transportPpm) / 1_000_000L
        return m
    }

    fun heatingMultiplier(region: Region, tick: Tick): Long {
        var m = ClimateTable.profile(region.climate, app.many.economytycoon.core.Calendar.season(tick)).heating
        for (e in activeIn(region.id, tick)) m = m * e.scaled(e.kind.demandHeatPpm) / 1_000_000L
        return m
    }

    /** Drops expired events and rolls for new ones. Called once per tick with the "weather" stream. */
    fun advance(tick: Tick, stream: (Long) -> DeterministicRandom) {
        val it = active.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (!e.activeAt(tick)) { it.remove(); history.add(e) }
        }
        if (history.size > 200) history.subList(0, history.size - 200).clear()
        if (tick.value % 5L != 0L) return
        val season = app.many.economytycoon.core.Calendar.season(tick)
        for (r in regions) {
            val rnd = stream(r.id.value)
            // Base chance per five-tick roll, in ppm, depends on climate and season.
            val kinds = candidates(r.climate, season)
            for ((kind, chance) in kinds) {
                if (activeIn(r.id, tick).any { it.kind == kind }) continue
                if (rnd.nextLong(1_000_000L) < chance) {
                    val duration = 5 + rnd.nextInt(26)
                    val severity = 600_000L + rnd.nextLong(900_000L)
                    active.add(WeatherEvent(r.id, kind, tick, duration, severity))
                }
            }
        }
    }

    private fun candidates(zone: ClimateZone, season: Season): List<Pair<WeatherKind, Long>> = when (zone) {
        ClimateZone.ARID -> listOf(WeatherKind.DROUGHT to (if (season == Season.SUMMER) 9_000L else 3_000L), WeatherKind.HEATWAVE to (if (season == Season.SUMMER) 12_000L else 500L))
        ClimateZone.TROPICAL -> listOf(WeatherKind.STORM to 8_000L, WeatherKind.FLOOD to (if (season == Season.SUMMER) 8_000L else 2_000L), WeatherKind.PEST_OUTBREAK to 2_500L)
        ClimateZone.COLD -> listOf(WeatherKind.COLD_SNAP to (if (season == Season.WINTER) 12_000L else 1_000L), WeatherKind.STORM to 2_000L)
        ClimateZone.MEDITERRANEAN -> listOf(WeatherKind.DROUGHT to (if (season == Season.SUMMER) 6_000L else 1_000L), WeatherKind.HEATWAVE to (if (season == Season.SUMMER) 7_000L else 200L), WeatherKind.FLOOD to (if (season == Season.AUTUMN) 4_000L else 500L))
        ClimateZone.TEMPERATE -> listOf(WeatherKind.FLOOD to (if (season == Season.SPRING) 4_000L else 800L), WeatherKind.STORM to 3_000L, WeatherKind.COLD_SNAP to (if (season == Season.WINTER) 5_000L else 0L), WeatherKind.PEST_OUTBREAK to 1_500L)
    }.filter { it.second > 0 }

    internal fun restore(events: List<WeatherEvent>, past: List<WeatherEvent>) {
        active.clear(); active.addAll(events); history.clear(); history.addAll(past)
    }
}
