package app.many.economytycoon.world

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.isqrt

/** Integer map position. The world is a square grid of [WorldMap.SIZE] units on a side. */
data class Point(val x: Int, val y: Int) : Comparable<Point> {
    override fun compareTo(other: Point): Int {
        val c = x.compareTo(other.x)
        return if (c != 0) c else y.compareTo(other.y)
    }

    /** Euclidean distance rounded down, in map units. */
    fun distanceTo(other: Point): Int {
        val dx = (x - other.x).toLong()
        val dy = (y - other.y).toLong()
        return isqrt(dx * dx + dy * dy).toInt()
    }

    fun squaredDistanceTo(other: Point): Long {
        val dx = (x - other.x).toLong()
        val dy = (y - other.y).toLong()
        return dx * dx + dy * dy
    }
}

object WorldMap {
    const val SIZE = 1000
    fun inBounds(p: Point) = p.x in 0 until SIZE && p.y in 0 until SIZE
}

enum class Terrain(val buildCostPpm: Long, val farmlandPpm: Long) {
    PLAINS(1_000_000, 1_000_000),
    HILLS(1_250_000, 600_000),
    MOUNTAINS(1_800_000, 120_000),
    DESERT(1_300_000, 80_000),
    FOREST(1_150_000, 300_000),
    COAST(1_100_000, 700_000),
    WETLAND(1_400_000, 400_000),
}

enum class ClimateZone { TEMPERATE, TROPICAL, ARID, COLD, MEDITERRANEAN }

enum class ZoneType { AGRICULTURAL, INDUSTRIAL, COMMERCIAL, RESIDENTIAL, MIXED, MINING, PROTECTED }

/** Static country settings that regulation and tax systems read. Rates are in ppm. */
data class Country(
    val id: CountryId,
    val name: String,
    val capital: CityId?,
    val corporateTaxPpm: Long,
    val salesTaxPpm: Long,
    val payrollTaxPpm: Long,
    val minimumWagePerTick: Long,
    /** 0..1,000,000: how harsh environmental rules are. */
    val environmentalStrictnessPpm: Long,
    /** Tariff on goods imported from other countries. */
    val tariffPpm: Long,
    val stabilityPpm: Long,
)

data class Region(
    val id: RegionId,
    val country: CountryId,
    val name: String,
    val center: Point,
    val terrain: Terrain,
    val climate: ClimateZone,
    /** Land area in square map units; used for parcel supply. */
    val area: Long,
    val fertilityPpm: Long,
    val coastal: Boolean,
)

data class City(
    val id: CityId,
    val region: RegionId,
    val name: String,
    val location: Point,
    val coastal: Boolean,
    val foundedPopulation: Long,
    val hasPort: Boolean,
    val hasAirport: Boolean,
    val isCapital: Boolean = false,
)

/** Sizes offered when starting a new game. */
enum class WorldSize(val countries: Int, val regionsPerCountry: Int, val citiesPerRegion: Int) {
    SMALL(1, 5, 3),
    MEDIUM(3, 5, 3),
    LARGE(6, 5, 4),
    HUGE(8, 8, 4),
}
