package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/**
 * Weights of the land value model. Each weight is the change in the value multiplier, in ppm, for one point (of 1000)
 * of the factor away from its neutral level. Accessibility and amenity are neutral at 500; pollution, noise and crime
 * are neutral at 0; neighbours are already signed.
 */
data class LandValueWeights(
    val accessibility: Long = 700L,
    val amenity: Long = 450L,
    val pollution: Long = 650L,
    val noise: Long = 350L,
    val crime: Long = 500L,
    val neighbours: Long = 500L,
    val centrality: Long = 90_000L,
    val minMultiplierPpm: Long = 150_000L,
    val maxMultiplierPpm: Long = 5_000_000L,
)

/** The factors behind the value of one cell, for the "why is this expensive" view. All in permille except the value. */
data class LandValueBreakdown(
    val cell: Cell,
    val zone: ZoneUse,
    val accessibility: Int,
    val amenity: Int,
    val pollution: Int,
    val noise: Int,
    val crime: Int,
    val neighbours: Int,
    val multiplierPpm: Long,
    val farFactorPpm: Long,
    val valuePerSqm: Money,
)

/** The land value of every cell, in raw money per square metre of land. */
class LandValueSurface(val values: Grid, private val breakdowns: (Cell) -> LandValueBreakdown) {
    fun valueAt(c: Cell): Money = Money(values[c])
    fun explain(c: Cell): LandValueBreakdown = breakdowns(c)

    /** Mean value per square metre over the cells of a district. */
    fun districtAverage(geometry: CityGeometry, district: Int): Money {
        val cells = geometry.cellsOf(district)
        var acc = 0L
        for (c in cells) acc += values[c]
        return Money(acc / cells.size)
    }

    /** Cells ordered from the dearest to the cheapest; ties are broken by position so the order is stable. */
    fun ranked(): List<Cell> = (0 until values.size).map { values.cellAt(it) }.sortedWith(compareBy<Cell>({ -values[it] }, { it }))

    /** Value of the dearest cell over the cheapest, in ppm (1.0 means a flat surface). */
    fun spreadPpm(): Long {
        val lo = values.min().coerceAtLeast(1L)
        return mulDiv(values.max(), Scale.PPM, lo)
    }
}

/**
 * Computes land value from accessibility, amenities, pollution, noise, crime and neighbours. The value of a square
 * metre is the base value of the zone, scaled by the city's price level and a multiplier built from the factors, and by
 * how much may be built on it.
 */
object LandValueModel {
    /** Value multiplier (ppm) for the given factor levels. */
    fun multiplierPpm(
        w: LandValueWeights, accessibility: Long, amenity: Long, pollution: Long, noise: Long, crime: Long, neighbours: Long, centrality: Long,
    ): Long {
        val m = Scale.PPM +
            w.accessibility * (accessibility - 500L) +
            w.amenity * (amenity - 500L) / 2L -
            w.pollution * pollution -
            w.noise * noise -
            w.crime * crime +
            w.neighbours * neighbours +
            w.centrality * centrality / 1000L
        return m.coerceIn(w.minMultiplierPpm, w.maxMultiplierPpm)
    }

    /** Buildable floor area raises the value of the land: a ratio of 2 is the reference. */
    fun farFactorPpm(farMilli: Int): Long = (600_000L + 200_000L * farMilli / 1000L).coerceIn(600_000L, 3_000_000L)

    /**
     * Price level of the city relative to the reference: wages relative to the reference wage, moved only half way,
     * so that a city twice as rich is about 1.5 times as dear.
     */
    fun priceLevelPpm(averageWage: Money, params: UrbanParams): Long {
        if (params.referenceWage.raw <= 0L) return Scale.PPM
        val ratio = mulDiv(averageWage.raw, Scale.PPM, params.referenceWage.raw).coerceIn(300_000L, 4_000_000L)
        return Scale.PPM + (ratio - Scale.PPM) / 2L
    }

    fun compute(
        geometry: CityGeometry,
        districts: List<District>,
        surfaces: ExternalitySurfaces,
        accessibility: Grid,
        priceLevelPpm: Long,
        params: UrbanParams,
        weights: LandValueWeights = LandValueWeights(),
    ): LandValueSurface {
        val n = geometry.gridSize
        val values = Grid(n, n, 0L)
        val far = Grid(n, n, 0L)
        val mult = Grid(n, n, 0L)
        val central = Grid(n, n, 0L)
        val maxFromCentre = (n - 1).coerceAtLeast(1)
        val all = ZoneUse.values()
        for (d in districts) {
            val farF = farFactorPpm(d.rules.effectiveFarMilli())
            for (c in geometry.cellsOf(d.index)) {
                val centrality = 1000L - mulDiv(geometry.cellsFromCentre(c).toLong(), 1000L, maxFromCentre.toLong()).coerceIn(0L, 1000L)
                val m = multiplierPpm(
                    weights, accessibility[c], surfaces.amenity[c], surfaces.pollution[c], surfaces.noise[c], surfaces.crime[c],
                    surfaces.neighbours[c], centrality,
                )
                var v = mulDiv(params.baseLandValuePerSqm.raw, d.zone.landValueFactorPpm, Scale.PPM)
                v = mulDiv(v, priceLevelPpm, Scale.PPM)
                v = mulDiv(v, m, Scale.PPM)
                v = mulDiv(v, farF, Scale.PPM)
                values[c] = v
                far[c] = farF
                mult[c] = m
                central[c] = centrality
            }
        }
        val explain = { c: Cell ->
            LandValueBreakdown(
                c, all[surfaces.zones[c].toInt()], accessibility[c].toInt(), surfaces.amenity[c].toInt(), surfaces.pollution[c].toInt(),
                surfaces.noise[c].toInt(), surfaces.crime[c].toInt(), surfaces.neighbours[c].toInt(), mult[c], far[c], Money(values[c]),
            )
        }
        return LandValueSurface(values, explain)
    }

    /**
     * Accessibility per cell: the score of the district the cell belongs to, with a bonus for cells within two steps of a
     * fast transit station (given as cells), which fades with distance.
     */
    fun accessibilityGrid(geometry: CityGeometry, districts: List<District>, stations: List<Cell>): Grid {
        val n = geometry.gridSize
        val base = Grid(n, n, 0L)
        for (d in districts) for (c in geometry.cellsOf(d.index)) base[c] = d.accessibilityPermille.toLong()
        if (stations.isEmpty()) return base
        val bonus = Grid(n, n, 0L)
        for (s in stations) bonus[s] = bonus[s] + 120L
        val spread = bonus.spread(Grid.decayKernel(500_000L, 2)).coerceIn(0L, 200L)
        return base.combine(spread) { a, b -> (a + b).coerceIn(0L, 1000L) }
    }
}
