package app.many.economytycoon.world

import app.many.economytycoon.content.IncomeBand
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv

/** Tunable demographic rates. All rates are annual and in ppm. */
data class DemographicParams(
    val birthRatePpm: Long = 12_000,
    val deathRateWorkingPpm: Long = 3_000,
    val deathRateSeniorPpm: Long = 55_000,
    val childToWorkingPpm: Long = 55_000,
    val workingToSeniorPpm: Long = 25_000,
    val participationPpm: Long = 680_000,
    /** Share of the working population that migrates per year at the maximum attractiveness gap. */
    val migrationPpm: Long = 30_000,
    val maxGrowthPerYearPpm: Long = 60_000,
)

/** Population of one city, split by age group and income band. */
class PopulationState(val city: CityId, children: Long, working: Long, seniors: Long) {
    var children: Long = children
        internal set
    var working: Long = working
        internal set
    var seniors: Long = seniors
        internal set

    /** Income band shares, ppm, summing to 1,000,000. */
    var lowShare: Long = 450_000
        internal set
    var middleShare: Long = 430_000
        internal set
    val highShare: Long get() = Rate.ONE_PPM - lowShare - middleShare

    var employed: Long = 0
        internal set
    var averageWage: Money = Money.units(80)
        internal set
    var educationPpm: Long = 400_000
        internal set
    var happinessPpm: Long = 600_000
        internal set
    var rentIndexPpm: Long = 1_000_000
        internal set

    private val carry = LongArray(6)

    val total: Long get() = children + working + seniors
    val laborForce: Long get() = mulDiv(working, participation, Rate.ONE_PPM)
    var participation: Long = 680_000
        internal set
    val unemployed: Long get() = (laborForce - employed).coerceAtLeast(0)
    val unemploymentPpm: Long get() = if (laborForce == 0L) 0L else mulDiv(unemployed, Rate.ONE_PPM, laborForce)

    fun inBand(band: IncomeBand): Long = when (band) {
        IncomeBand.LOW -> mulDiv(total, lowShare, Rate.ONE_PPM)
        IncomeBand.MIDDLE -> mulDiv(total, middleShare, Rate.ONE_PPM)
        IncomeBand.HIGH -> total - mulDiv(total, lowShare, Rate.ONE_PPM) - mulDiv(total, middleShare, Rate.ONE_PPM)
    }

    /** Aggregate purchasing power per tick: employed wages plus benefits for the rest. */
    fun incomePerTick(benefitPerTick: Money): Money =
        averageWage * employed + benefitPerTick * unemployed + benefitPerTick * seniors

    /**
     * Applies a fraction of a yearly flow with a remainder carry, so tiny populations still evolve and
     * large ones never drift.
     */
    internal fun flow(slot: Int, base: Long, ratePpm: Long, perYearTicks: Long, ticks: Long): Long {
        val numerator = base * ratePpm * ticks + carry[slot]
        val denominator = Rate.ONE_PPM * perYearTicks
        carry[slot] = numerator % denominator
        return numerator / denominator
    }

    internal fun carries(): LongArray = carry.copyOf()
    internal fun restoreCarries(values: LongArray) { for (i in values.indices.take(carry.size)) carry[i] = values[i] }

    internal fun restore(
        children: Long, working: Long, seniors: Long, low: Long, middle: Long, employed: Long, wage: Money,
        education: Long, happiness: Long, rent: Long, participation: Long,
    ) {
        this.children = children; this.working = working; this.seniors = seniors
        lowShare = low; middleShare = middle; this.employed = employed; averageWage = wage
        educationPpm = education; happinessPpm = happiness; rentIndexPpm = rent; this.participation = participation
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(city.value).putLong(children).putLong(working).putLong(seniors).putLong(lowShare).putLong(middleShare)
            .putLong(employed).putMoney(averageWage).putLong(educationPpm).putLong(happinessPpm).putLong(rentIndexPpm)
        for (c in carry) h.putLong(c)
    }
}

/** Population dynamics and quality-of-life scoring. */
object Demography {
    const val YEAR_TICKS = 360L

    /** Advances one city by [ticks] ticks of natural change. Returns the net change in total population. */
    fun naturalChange(p: PopulationState, params: DemographicParams, ticks: Long): Long {
        val before = p.total
        val births = p.flow(0, p.working, params.birthRatePpm * 2, YEAR_TICKS, ticks)
        val childDeaths = p.flow(1, p.children, 2_000, YEAR_TICKS, ticks)
        val adultDeaths = p.flow(2, p.working, params.deathRateWorkingPpm, YEAR_TICKS, ticks)
        val seniorDeaths = p.flow(3, p.seniors, params.deathRateSeniorPpm, YEAR_TICKS, ticks)
        val grow = p.flow(4, p.children, params.childToWorkingPpm, YEAR_TICKS, ticks).coerceAtMost(p.children)
        val retire = p.flow(5, p.working, params.workingToSeniorPpm, YEAR_TICKS, ticks).coerceAtMost(p.working)
        p.children = (p.children + births - childDeaths - grow).coerceAtLeast(0)
        p.working = (p.working + grow - adultDeaths - retire).coerceAtLeast(0)
        p.seniors = (p.seniors + retire - seniorDeaths).coerceAtLeast(0)
        return p.total - before
    }

    /**
     * Attractiveness score of a city in ppm: high wages, low unemployment, low rent and happiness all raise it.
     */
    fun attractiveness(p: PopulationState, referenceWage: Money): Long {
        val wageRatio = if (referenceWage.raw == 0L) 1_000_000L else mulDiv(p.averageWage.raw, 1_000_000L, referenceWage.raw)
        val wageScore = wageRatio.coerceIn(0L, 2_000_000L) // 0..2
        val jobScore = (1_000_000L - p.unemploymentPpm * 4).coerceIn(0L, 1_000_000L)
        val rentScore = (2_000_000L - p.rentIndexPpm).coerceIn(0L, 2_000_000L)
        return (wageScore * 35 + jobScore * 30 + rentScore * 15 + p.happinessPpm * 20 / 1 ) / 100
    }

    /**
     * Moves workers between cities in proportion to attractiveness gaps. Conserves total population
     * exactly: what leaves one city arrives in others.
     */
    fun migrate(cities: List<PopulationState>, params: DemographicParams, referenceWage: Money, ticks: Long) {
        if (cities.size < 2) return
        val scores = cities.map { attractiveness(it, referenceWage) }
        val mean = scores.sum() / scores.size
        val leaving = LongArray(cities.size)
        var pool = 0L
        for (i in cities.indices) {
            val gap = mean - scores[i]
            if (gap > 0) {
                val rate = mulDiv(params.migrationPpm, gap.coerceAtMost(500_000L), 500_000L) // annual ppm
                val n = mulDiv(cities[i].working, rate * ticks, Rate.ONE_PPM * YEAR_TICKS)
                leaving[i] = n.coerceAtMost(cities[i].working / 10)
                pool += leaving[i]
            }
        }
        if (pool == 0L) return
        val weights = LongArray(cities.size) { i -> (scores[i] - mean).coerceAtLeast(0L) }
        if (weights.all { it == 0L }) return
        val shares = app.many.economytycoon.core.splitLargestRemainder(pool, weights)
        for (i in cities.indices) {
            cities[i].working = cities[i].working - leaving[i] + shares[i]
        }
    }

    /** Shifts income-band shares toward a target implied by the wage level and inequality. */
    fun updateBands(p: PopulationState, referenceWage: Money, inequalityPpm: Long) {
        val ratio = if (referenceWage.raw == 0L) 1_000_000L else mulDiv(p.averageWage.raw, 1_000_000L, referenceWage.raw)
        // Higher wages push people up; inequality pulls the middle band into the extremes.
        var targetLow = (700_000L - ratio / 2 + inequalityPpm / 4).coerceIn(150_000L, 800_000L)
        var targetHigh = (50_000L + (ratio - 700_000L) / 6 + inequalityPpm / 8).coerceIn(20_000L, 400_000L)
        if (targetLow + targetHigh > 950_000L) targetHigh = 950_000L - targetLow
        val targetMid = 1_000_000L - targetLow - targetHigh
        p.lowShare += (targetLow - p.lowShare) / 12
        p.middleShare += (targetMid - p.middleShare) / 12
        p.lowShare = p.lowShare.coerceIn(100_000L, 850_000L)
        p.middleShare = p.middleShare.coerceIn(100_000L, 900_000L - p.lowShare)
    }

    /** Updates happiness from employment, wages relative to cost of living and rent. */
    fun updateHappiness(p: PopulationState, referenceWage: Money) {
        val wageRatio = if (referenceWage.raw == 0L) 1_000_000L else mulDiv(p.averageWage.raw, 1_000_000L, referenceWage.raw)
        val target = (400_000L + (wageRatio.coerceIn(0L, 2_000_000L) * 3 / 10) + (1_000_000L - p.unemploymentPpm * 3).coerceIn(0L, 1_000_000L) * 3 / 10 -
            (p.rentIndexPpm - 1_000_000L).coerceIn(-500_000L, 1_000_000L) / 8).coerceIn(0L, 1_000_000L)
        p.happinessPpm += (target - p.happinessPpm) / 10
    }

    /** Rent follows employment pressure and population density relative to housing. */
    fun updateRent(p: PopulationState, housingCapacity: Long) {
        val density = if (housingCapacity <= 0L) 1_500_000L else mulDiv(p.total, 1_000_000L, housingCapacity)
        val target = (600_000L + density * 6 / 10).coerceIn(500_000L, 2_500_000L)
        p.rentIndexPpm += (target - p.rentIndexPpm) / 15
    }
}
