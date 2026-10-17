package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.core.splitLargestRemainder

/** The mean of each externality over a district's cells, permille. */
data class DistrictFactors(val accessibility: Int, val amenity: Int, val pollution: Int, val noise: Int, val crime: Int)

/** Where a city's residents live, and who could not be housed. */
data class Allocation(val placed: LongArray, val unplaced: Long) {
    override fun equals(other: Any?) = other is Allocation && placed.contentEquals(other.placed) && unplaced == other.unplaced
    override fun hashCode() = 31 * placed.contentHashCode() + unplaced.hashCode()
}

/** Summary of the housing market of a city for one month. */
data class HousingReport(
    val capacity: Long,
    val residents: Long,
    val unhoused: Long,
    val vacancyPpm: Long,
    val meanRentPerSqm: Money,
    /** Rent of a typical home as a share of a typical household's income, ppm. */
    val rentBurdenPpm: Long,
    /** Same for a low-income household. */
    val lowIncomeBurdenPpm: Long,
    /** Housing demand over supply; above 1.0 the market is tight. */
    val pressurePpm: Long,
)

/**
 * Housing supply, demand, vacancy and rents. Residents are placed in districts in proportion to how attractive they are
 * (reachable, pleasant, clean, safe and not too dear) up to the housing each district has; vacancy then moves rents
 * toward a level where a normal share of homes stand empty.
 */
object Housing {
    fun districtFactors(geometry: CityGeometry, index: Int, surfaces: ExternalitySurfaces, accessibility: Grid): DistrictFactors {
        val cells = geometry.cellsOf(index)
        val k = cells.size.toLong()
        fun mean(g: Grid) = (cells.sumOf { g[it] } / k).toInt()
        return DistrictFactors(mean(accessibility), mean(surfaces.amenity), mean(surfaces.pollution), mean(surfaces.noise), mean(surfaces.crime))
    }

    /** Weight of a district when people choose where to live; always positive. */
    fun attractiveness(f: DistrictFactors, rentRatioPpm: Long): Long {
        val score = 1000L + (f.accessibility - 500L) + (f.amenity - 500L) / 2L - f.pollution - f.noise / 2L - f.crime
        val base = score.coerceIn(60L, 2400L)
        val priceDrag = rentRatioPpm.coerceIn(500_000L, 2_000_000L)
        return (mulDiv(base * 1000L, Scale.PPM, priceDrag)).coerceAtLeast(1L)
    }

    /**
     * Shares [total] over places in proportion to [weights] without exceeding [caps]. What does not fit is returned as
     * unplaced. Exact: the placed numbers plus the unplaced always add up to [total].
     */
    fun allocate(total: Long, weights: LongArray, caps: LongArray): Allocation {
        require(weights.size == caps.size) { "one cap per weight" }
        val placed = LongArray(weights.size)
        var remaining = total.coerceAtLeast(0L)
        var active = weights.indices.filter { weights[it] > 0L && caps[it] > 0L }
        while (remaining > 0L && active.isNotEmpty()) {
            val shares = splitLargestRemainder(remaining, LongArray(active.size) { weights[active[it]] })
            var overflow = 0L
            val stillOpen = ArrayList<Int>()
            for ((k, i) in active.withIndex()) {
                val room = caps[i] - placed[i]
                val put = minOf(shares[k], room)
                placed[i] += put
                overflow += shares[k] - put
                if (placed[i] < caps[i]) stillOpen += i
            }
            remaining = overflow
            active = stillOpen
        }
        return Allocation(placed, remaining)
    }

    /** Rent a household can afford for a square metre of an average home, given the city's average wage. */
    fun anchorRent(averageWage: Money, params: UrbanParams): Money {
        val perResidentIncome = averageWage.raw * Scale.MONTH_DAYS
        val monthly = mulDiv(perResidentIncome, params.participationPpm, Scale.PPM)
        val affordable = mulDiv(monthly, params.rentShareOfIncomePpm, Scale.PPM)
        return Money(affordable / params.sqmPerResident)
    }

    /** Target rent for a district from the market anchor, its location and how many homes stand empty. */
    fun targetRent(anchor: Money, locationRatioPpm: Long, vacancyPpm: Long, params: UrbanParams): Money {
        val gap = params.targetVacancyPpm - vacancyPpm // positive: shortage
        val tight = Scale.PPM + gap * 6L
        val market = tight.coerceIn(400_000L, 2_500_000L)
        val location = locationRatioPpm.coerceIn(400_000L, 2_800_000L)
        return Money(mulDiv(mulDiv(anchor.raw, location, Scale.PPM), market, Scale.PPM))
    }

    /** Moves the current rent a fraction of the way to the target. Never below 1 raw unit while a target exists. */
    fun stepRent(current: Money, target: Money, params: UrbanParams): Money {
        if (current.raw <= 0L) return target
        val moved = current.raw + mulDiv(target.raw - current.raw, params.rentAdjustPpm, Scale.PPM)
        return Money(moved.coerceAtLeast(if (target.raw > 0L) 1L else 0L))
    }

    /** Vacancy of the housing in a district, ppm; a district without housing counts as full. */
    fun vacancy(capacity: Long, residents: Long): Long =
        if (capacity <= 0L) 0L else mulDiv((capacity - residents).coerceAtLeast(0L), Scale.PPM, capacity)

    /** The share of a household's income that goes on rent, ppm, for a home of the standard size. */
    fun burdenPpm(rentPerSqm: Money, incomePerResidentMonthly: Money, params: UrbanParams): Long {
        if (incomePerResidentMonthly.raw <= 0L) return Scale.PPM
        return mulDiv(rentPerSqm.raw * params.sqmPerResident, Scale.PPM, incomePerResidentMonthly.raw)
    }

    fun report(districts: List<District>, residentsToHouse: Long, averageWage: Money, params: UrbanParams): HousingReport {
        val capacity = districts.sumOf { Zoning.capacity(it.zone, it.floorArea, params) }
        val residents = districts.sumOf { it.residents }
        var weighted = 0L
        for (d in districts) weighted += d.rentPerSqm.raw * d.residents
        val meanRent = if (residents == 0L) Money.ZERO else Money(weighted / residents)
        val income = Money(mulDiv(averageWage.raw * Scale.MONTH_DAYS, params.participationPpm, Scale.PPM))
        val burden = burdenPpm(meanRent, income, params)
        val lowIncome = Money(income.raw / 2L)
        return HousingReport(
            capacity = capacity,
            residents = residents,
            unhoused = (residentsToHouse - residents).coerceAtLeast(0L),
            vacancyPpm = vacancy(capacity, residents),
            meanRentPerSqm = meanRent,
            rentBurdenPpm = burden,
            lowIncomeBurdenPpm = burdenPpm(meanRent, lowIncome, params),
            pressurePpm = if (capacity <= 0L) 2 * Scale.PPM else mulDiv(residentsToHouse, Scale.PPM, capacity),
        )
    }
}
