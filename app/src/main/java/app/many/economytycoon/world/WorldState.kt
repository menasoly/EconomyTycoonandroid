package app.many.economytycoon.world

import app.many.economytycoon.content.IncomeBand
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Hashable
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

/** Global world parameters that vary by scenario. */
data class WorldParams(
    val seed: Long,
    val size: WorldSize,
    val demography: DemographicParams = DemographicParams(),
    val referenceWage: Money = Money.units(90),
    val inequalityPpm: Long = 350_000,
    val benefitPerTick: Money = Money.units(25),
    val monthTicks: Int = 30,
)

/**
 * The mutable world: static geography plus evolving population, resources, networks and weather.
 * Mutations go through the methods here, called from the simulation's world update phase.
 */
class WorldState(val params: WorldParams) : Hashable {
    private val countriesMap = java.util.TreeMap<CountryId, Country>()
    private val regionsMap = java.util.TreeMap<RegionId, Region>()
    private val citiesMap = java.util.TreeMap<CityId, City>()
    private val populations = java.util.TreeMap<CityId, PopulationState>()
    private val housing = java.util.TreeMap<CityId, Long>()

    val deposits = DepositRegistry()
    val network = TransportNetwork()
    val land = LandRegistry()
    var weather = WeatherSystem(emptyList())
        private set

    fun countries(): List<Country> = countriesMap.values.toList()
    fun regions(): List<Region> = regionsMap.values.toList()
    fun cities(): List<City> = citiesMap.values.toList()
    fun country(id: CountryId): Country = countriesMap[id] ?: error("unknown country $id")
    fun region(id: RegionId): Region = regionsMap[id] ?: error("unknown region $id")
    fun city(id: CityId): City = citiesMap[id] ?: error("unknown city $id")
    fun cityOrNull(id: CityId): City? = citiesMap[id]
    fun population(id: CityId): PopulationState = populations[id] ?: error("no population for $id")
    fun populations(): List<PopulationState> = populations.values.toList()
    fun housingCapacity(id: CityId): Long = housing[id] ?: 0L

    fun citiesIn(region: RegionId): List<City> = citiesMap.values.filter { it.region == region }
    fun regionsOf(country: CountryId): List<Region> = regionsMap.values.filter { it.country == country }
    fun citiesOf(country: CountryId): List<City> = regionsOf(country).flatMap { citiesIn(it.id) }
    fun countryOfCity(id: CityId): Country = country(region(city(id).region).country)
    fun regionOfCity(id: CityId): Region = region(city(id).region)

    fun totalPopulation(): Long = populations.values.sumOf { it.total }
    fun populationOf(country: CountryId): Long = citiesOf(country).sumOf { population(it.id).total }

    /** Consumers per income band across the whole world. */
    fun consumers(band: IncomeBand): Long = populations.values.sumOf { it.inBand(band) }
    fun consumers(city: CityId, band: IncomeBand): Long = population(city).inBand(band)

    fun distance(a: CityId, b: CityId): Int = city(a).location.distanceTo(city(b).location)

    fun nearestCity(point: Point, within: RegionId? = null): City? =
        citiesMap.values.filter { within == null || it.region == within }
            .minWithOrNull(compareBy<City>({ it.location.squaredDistanceTo(point) }, { it.id }))

    // ----- construction ---------------------------------------------------------------------

    internal fun addCountry(c: Country) { countriesMap[c.id] = c }
    /** Replaces a country's settings when its government changes the rules. */
    internal fun replaceCountry(c: Country) { require(countriesMap.containsKey(c.id)) { "unknown country ${c.id}" }; countriesMap[c.id] = c }
    internal fun addRegion(r: Region) { regionsMap[r.id] = r }
    internal fun addCity(c: City, pop: PopulationState, housingUnits: Long) {
        citiesMap[c.id] = c; populations[c.id] = pop; housing[c.id] = housingUnits
    }
    internal fun finishConstruction() { weather = WeatherSystem(regionsMap.values.toList()) }
    internal fun setHousing(city: CityId, units: Long) { housing[city] = units }

    // ----- feedback from the economy --------------------------------------------------------

    /** Records employment and wages computed by the workforce module for a city. */
    fun recordLabor(city: CityId, employed: Long, averageWage: Money) {
        val p = population(city)
        p.employed = employed.coerceIn(0L, p.laborForce)
        if (averageWage.isPositive) p.averageWage = averageWage
    }

    fun addHousing(city: CityId, units: Long) { housing[city] = housingCapacity(city) + units }

    // ----- updates --------------------------------------------------------------------------

    /** Per-tick world update: weather, resources regrowth, network wear. */
    fun updateTick(tick: Tick, random: RandomProvider) {
        weather.advance(tick) { partition -> random.stream("weather", partition) }
        deposits.regrowAll()
        network.endTick()
        if (tick.value > 0 && tick.value % params.monthTicks == 0L) monthlyUpdate(tick)
    }

    private fun monthlyUpdate(tick: Tick) {
        val pops = populations.values.toList()
        for (p in pops) {
            Demography.naturalChange(p, params.demography, params.monthTicks.toLong())
            Demography.updateBands(p, params.referenceWage, params.inequalityPpm)
            Demography.updateHappiness(p, params.referenceWage)
            Demography.updateRent(p, housingCapacity(p.city))
        }
        Demography.migrate(pops, params.demography, params.referenceWage, params.monthTicks.toLong())
    }

    override fun hashInto(hasher: CanonicalHasher) {
        hasher.section("world")
        hasher.putLong(params.seed)
        for (p in populations.values) p.hashInto(hasher)
        for ((id, units) in housing) hasher.putLong(id.value).putLong(units)
        for (d in deposits.all()) d.hashInto(hasher)
        for (p in land.all()) p.hashInto(hasher)
        network.hashInto(hasher)
        for (e in weather.active()) hasher.putLong(e.region.value).putInt(e.kind.ordinal).putLong(e.start.value).putInt(e.durationTicks).putLong(e.severityPpm)
    }
}
