package app.many.economytycoon.talent

import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.world.WorldState

/**
 * What the talent module needs to know about the world. A narrow interface keeps the module testable without a
 * procedurally built world, and lets the integration decide where each number comes from.
 */
interface TalentWorld {
    fun countries(): List<CountryId>
    fun regionsOf(country: CountryId): List<RegionId>
    fun countryOf(region: RegionId): CountryId

    /** People reaching the age to start school (or leave it) each year in the region. */
    fun youthCohort(region: RegionId): Long
    fun workingAge(region: RegionId): Long
    fun unemployed(region: RegionId): Long

    /** Average pay per day of people in work. */
    fun averageWage(region: RegionId): Money

    /** Share of the working population that is educated, 0..1,000,000. */
    fun educationPpm(region: RegionId): Long
    fun happinessPpm(region: RegionId): Long
    fun stabilityPpm(country: CountryId): Long

    fun allRegions(): List<RegionId> = countries().flatMap { regionsOf(it) }.sorted()
}

/** Reads the real world state. Regional figures are sums over the region's cities. */
class WorldTalentView(private val world: WorldState, private val entryRatePpm: Long = world.params.demography.childToWorkingPpm) : TalentWorld {
    override fun countries(): List<CountryId> = world.countries().map { it.id }.sorted()
    override fun regionsOf(country: CountryId): List<RegionId> = world.regionsOf(country).map { it.id }.sorted()
    override fun countryOf(region: RegionId): CountryId = world.region(region).country

    private fun pops(region: RegionId) = world.citiesIn(region).map { world.population(it.id) }

    override fun youthCohort(region: RegionId): Long = pops(region).sumOf { Ppm.of(it.children, entryRatePpm) }
    override fun workingAge(region: RegionId): Long = pops(region).sumOf { it.working }
    override fun unemployed(region: RegionId): Long = pops(region).sumOf { it.unemployed }

    override fun averageWage(region: RegionId): Money {
        val ps = pops(region)
        val heads = ps.sumOf { it.employed }
        if (heads == 0L) return world.params.referenceWage
        return Money(ps.sumOf { it.averageWage.raw * it.employed } / heads)
    }

    override fun educationPpm(region: RegionId): Long {
        val ps = pops(region)
        val w = ps.sumOf { it.working }
        return if (w == 0L) 0L else ps.sumOf { it.educationPpm * it.working } / w
    }

    override fun happinessPpm(region: RegionId): Long {
        val ps = pops(region)
        val t = ps.sumOf { it.total }
        return if (t == 0L) 600_000L else ps.sumOf { it.happinessPpm * it.total } / t
    }

    override fun stabilityPpm(country: CountryId): Long = world.country(country).stabilityPpm
}
