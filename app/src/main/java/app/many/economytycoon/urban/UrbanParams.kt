package app.many.economytycoon.urban

import app.many.economytycoon.core.Money

/** Fixed-point scale shared by the urban model: rates in parts per million, scores in permille (0..1000). */
object Scale {
    const val PPM = 1_000_000L
    const val PERMILLE = 1_000L
    const val MONTH_DAYS = 30L
    const val YEAR_DAYS = 360L
    const val MONTHS_PER_YEAR = 12
}

/**
 * Tunable constants of the urban model. Areas are square metres of floor or land, money is per month unless the name
 * says otherwise, and rates are ppm. Every scenario can supply its own copy; the defaults describe a mid-sized city.
 */
data class UrbanParams(
    /** Grid cells on a side of the city plan. */
    val gridSize: Int = 12,
    /** Cells on a side of one district. */
    val blockSize: Int = 4,
    /** Floor area one resident needs. */
    val sqmPerResident: Long = 35L,
    /** Floor area one job needs, by use. */
    val sqmPerCommercialJob: Long = 25L,
    val sqmPerIndustrialJob: Long = 60L,
    val sqmPerCivicJob: Long = 40L,
    /** Share of residents who work. */
    val participationPpm: Long = 480_000L,
    /** Share of income that a household is willing to spend on rent. */
    val rentShareOfIncomePpm: Long = 300_000L,
    /** Vacancy at which rents are flat. */
    val targetVacancyPpm: Long = 60_000L,
    /** Rent change per month, ppm of the gap to the target, applied as a moving average. */
    val rentAdjustPpm: Long = 150_000L,
    /** Cost to build one square metre of floor area. */
    val buildCostPerSqm: Money = Money.units(1_400),
    /** Capitalisation rate used to turn annual rent into a building value. */
    val capRatePpm: Long = 55_000L,
    /** Developer profit needed before they build, ppm of the cost. */
    val developerHurdlePpm: Long = 120_000L,
    /** Most floor area that may be started per month, ppm of the district's allowed floor area. */
    val maxStartsPpm: Long = 12_000L,
    /** Land value of a square metre at the reference wage with every factor neutral. */
    val baseLandValuePerSqm: Money = Money.units(900),
    val referenceWage: Money = Money.units(90),
    val monthsBetweenCouncilElections: Int = 48,
    val councilSize: Int = 9,
    val hearingNoticeDays: Int = 14,
    val voteDelayDays: Int = 7,
    val maxOpenPetitionsPerCity: Int = 12,
    val petitionFee: Money = Money.units(2_500),
    val lobbyingCapPerPetition: Money = Money.units(150_000),
    val historyMonths: Int = 24,
    val walkSpeedMetersPerSec: Long = 1L,
    val carFreeFlowMetersPerSec: Long = 11L,
    val busSpeedFactorPpm: Long = 650_000L,
    val metroSpeedMetersPerSec: Long = 14L,
    val railSpeedMetersPerSec: Long = 22L,
    /** Minutes of travel time worth one currency unit of fare. */
    val fareUnitsPerHourOfTime: Long = 18L,
    val peakHourShareOfTripsPpm: Long = 180_000L,
    val carOccupancyPermille: Long = 1_200L,
    val assignmentIterations: Int = 4,
    val debtLimitMonthsOfRevenue: Int = 18,
) {
    init {
        require(gridSize >= blockSize && gridSize % blockSize == 0) { "grid must be a whole number of districts" }
        require(gridSize / blockSize <= MAX_DISTRICTS_PER_SIDE) { "too many districts" }
        require(councilSize in 3..25 && councilSize % 2 == 1) { "council must be an odd size between 3 and 25" }
    }

    val districtsPerSide: Int get() = gridSize / blockSize
    val districtCount: Int get() = districtsPerSide * districtsPerSide

    companion object {
        const val MAX_DISTRICTS_PER_SIDE = 6
    }
}
