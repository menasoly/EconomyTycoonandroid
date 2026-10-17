package app.many.economytycoon.marketing

import app.many.economytycoon.content.IncomeBand
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.markets.WorldPopulationView
import app.many.economytycoon.world.WorldState

/**
 * The people a campaign can reach: everyone in a region, or in one city of it. [bandPpm] holds the share of the
 * audience in each income band, indexed by [IncomeBand] ordinal and summing to one million.
 */
data class Audience(
    val region: RegionId,
    val city: CityId?,
    val inhabitants: Long,
    /** People in the whole region; equals [inhabitants] unless a single city is targeted. */
    val regionInhabitants: Long,
    val bandPpm: List<Long>,
    /** Purchasing power against the world average; 1,000,000 is average. */
    val incomeIndexPpm: Long = Ppm.ONE,
) {
    init {
        require(inhabitants >= 0L && regionInhabitants >= inhabitants) { "audience cannot exceed its region" }
        require(bandPpm.size == IncomeBand.values().size) { "one share per income band" }
    }

    /** Share of the audience that falls in [segment]. */
    fun segmentShare(segment: TargetSegment): Long = segment.bands.sumOf { bandPpm[it.ordinal] }.coerceAtMost(Ppm.ONE)

    /** People in [segment]. */
    fun segmentSize(segment: TargetSegment): Long = mulDiv(inhabitants, segmentShare(segment), Ppm.ONE)

    /** Share of the whole region that [segment] of this audience makes up. */
    fun regionCoverage(segment: TargetSegment): Long = Ppm.ratio(segmentSize(segment), regionInhabitants)

    /** How well [spec] suits the people in [segment], weighted by how many of them are in each band. */
    fun fit(spec: ChannelSpec, segment: TargetSegment): Long {
        val share = segmentShare(segment)
        if (share <= 0L) return 0L
        var total = 0L
        for (band in segment.bands) total += mulDiv(bandPpm[band.ordinal], spec.fit(band), Ppm.ONE)
        return mulDiv(total, Ppm.ONE, share)
    }

    companion object {
        fun even(region: RegionId, inhabitants: Long, incomeIndexPpm: Long = Ppm.ONE, city: CityId? = null, regionInhabitants: Long = inhabitants) =
            Audience(region, city, inhabitants, regionInhabitants, listOf(450_000L, 430_000L, 120_000L), incomeIndexPpm)
    }
}

/** Where the marketing model learns who lives where. The world implementation reads [WorldState]. */
interface AudienceSource {
    fun regions(): List<RegionId>
    fun audience(region: RegionId, city: CityId? = null): Audience?
}

class WorldAudienceSource(private val world: WorldState) : AudienceSource {
    private val income = WorldPopulationView(world)

    override fun regions(): List<RegionId> = world.regions().map { it.id }

    override fun audience(region: RegionId, city: CityId?): Audience? {
        if (world.regions().none { it.id == region }) return null
        val cities = world.citiesIn(region)
        val regionPeople = cities.sumOf { world.population(it.id).total }
        val chosen = if (city == null) cities else cities.filter { it.id == city }
        if (city != null && chosen.isEmpty()) return null
        val people = chosen.sumOf { world.population(it.id).total }
        val low = chosen.sumOf { world.population(it.id).inBand(IncomeBand.LOW) }
        val middle = chosen.sumOf { world.population(it.id).inBand(IncomeBand.MIDDLE) }
        val bands = if (people <= 0L) listOf(450_000L, 430_000L, 120_000L) else {
            val lowPpm = mulDiv(low, Ppm.ONE, people)
            val midPpm = mulDiv(middle, Ppm.ONE, people)
            listOf(lowPpm, midPpm, Ppm.ONE - lowPpm - midPpm)
        }
        return Audience(region, city, people, regionPeople, bands, income.incomeIndexPpm(region))
    }
}

/** A fixed set of audiences, for tests and for tools that work from a snapshot. */
class FixedAudienceSource(audiences: List<Audience>) : AudienceSource {
    private val byRegion = java.util.TreeMap<RegionId, Audience>()
    private val byCity = java.util.TreeMap<CityId, Audience>()

    init {
        for (a in audiences) { val c = a.city; if (c == null) byRegion[a.region] = a else byCity[c] = a }
    }

    override fun regions(): List<RegionId> = byRegion.keys.toList()
    override fun audience(region: RegionId, city: CityId?): Audience? =
        if (city == null) byRegion[region] else byCity[city]?.takeIf { it.region == region }
}
