package app.many.economytycoon.urban

import app.many.economytycoon.core.mulDiv

/** How well city services currently work, permille, as they bear on the surfaces of the city. */
data class ServiceEffects(
    val policePermille: Int = 500,
    val sanitationPermille: Int = 500,
    val parksPermille: Int = 500,
) {
    companion object {
        val NEUTRAL = ServiceEffects()
    }
}

/** The spread of nuisances and benefits over the city, one value per cell. */
class ExternalitySurfaces(
    val zones: Grid,
    val intensity: Grid,
    val pollution: Grid,
    val noise: Grid,
    val crime: Grid,
    val amenity: Grid,
    /** Signed neighbour compatibility, -500..500. */
    val neighbours: Grid,
)

/**
 * Builds the surfaces that feed land value: where it is dirty, loud, unsafe or pleasant, and how well each cell's
 * neighbours suit it. Everything is integer arithmetic over the city grid.
 */
object Externalities {
    private val POLLUTION_KERNEL = Grid.decayKernel(520_000L, 3)
    private val NOISE_KERNEL = Grid.decayKernel(430_000L, 2)
    private val CRIME_KERNEL = Grid.decayKernel(450_000L, 2)
    private val AMENITY_KERNEL = Grid.decayKernel(600_000L, 3)

    /** How built-up a district is relative to what its density limit allows, permille. Green space is always full. */
    fun intensityPermille(d: District): Int {
        if (d.zone == ZoneUse.GREEN) return 1000
        val cap = d.rules.maxFloorArea(d.landSqm)
        if (cap <= 0L) return 0
        return mulDiv(d.floorArea, 1000L, cap).coerceIn(0L, 1000L).toInt()
    }

    fun zoneGrid(geometry: CityGeometry, districts: List<District>): Grid {
        val g = Grid(geometry.gridSize, geometry.gridSize, 0L)
        for (d in districts) for (c in geometry.cellsOf(d.index)) g[c] = d.zone.ordinal.toLong()
        return g
    }

    fun compute(geometry: CityGeometry, districts: List<District>, services: ServiceEffects = ServiceEffects.NEUTRAL): ExternalitySurfaces {
        val n = geometry.gridSize
        val zones = zoneGrid(geometry, districts)
        val intensity = Grid(n, n, 0L)
        val pollutionSrc = Grid(n, n, 0L)
        val noiseSrc = Grid(n, n, 0L)
        val crimeSrc = Grid(n, n, 0L)
        val amenitySrc = Grid(n, n, 0L)
        val parksBoost = 800L + services.parksPermille.toLong() * 400L / 1000L // 0.8..1.2 of the base amenity
        for (d in districts) {
            val inten = intensityPermille(d).toLong()
            val traffic = d.trafficLoadPermille.toLong().coerceIn(0L, 1000L)
            val deprivation = d.lowIncomeSharePpm / 1000L
            for (c in geometry.cellsOf(d.index)) {
                intensity[c] = inten
                pollutionSrc[c] = d.zone.pollution * inten / 1000L + traffic * 80L / 1000L
                noiseSrc[c] = d.zone.noise * inten / 1000L + traffic * 300L / 1000L
                crimeSrc[c] = d.zone.crimeExposure * inten / 1000L + deprivation * 250L / 1000L
                val a = d.zone.amenity.toLong()
                amenitySrc[c] = (if (d.zone == ZoneUse.GREEN || d.zone == ZoneUse.CIVIC) a * parksBoost / 1000L else a * (300L + inten * 7L / 10L) / 1000L) + d.amenityBonus
            }
        }
        val sanitation = 1000L - services.sanitationPermille.toLong() * 350L / 1000L // 0.65..1.0 of the pollution
        val policing = 1000L - services.policePermille.toLong() * 600L / 1000L // 0.4..1.0 of the crime
        val pollution = pollutionSrc.spread(POLLUTION_KERNEL).map { it * sanitation / 1000L }.coerceIn(0L, 1000L)
        val noise = noiseSrc.spread(NOISE_KERNEL).coerceIn(0L, 1000L)
        val crime = crimeSrc.spread(CRIME_KERNEL).map { it * policing / 1000L }.coerceIn(0L, 1000L)
        val amenity = amenitySrc.spread(AMENITY_KERNEL).map { it / 2L }.coerceIn(0L, 1000L)
        return ExternalitySurfaces(zones, intensity, pollution, noise, crime, amenity, neighbourGrid(zones))
    }

    /** Mean compatibility of every cell with its (up to four) neighbours. */
    fun neighbourGrid(zones: Grid): Grid {
        val out = Grid(zones.width, zones.height, 0L)
        val all = ZoneUse.values()
        for (y in 0 until zones.height) for (x in 0 until zones.width) {
            val me = all[zones[x, y].toInt()]
            var acc = 0L
            var n = 0L
            for ((dx, dy) in Grid.NEIGHBOURS4) {
                if (!zones.inBounds(x + dx, y + dy)) continue
                acc += Zoning.compatibility(me, all[zones[x + dx, y + dy].toInt()])
                n++
            }
            out[x, y] = if (n == 0L) 0L else Math.floorDiv(acc, n)
        }
        return out
    }
}
