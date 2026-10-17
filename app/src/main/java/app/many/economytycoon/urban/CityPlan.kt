package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** What the rest of the economy tells the urban model about a city. Supplied by the caller at every update. */
data class CityInputs(
    val population: Long,
    val averageWage: Money,
    val lowIncomeSharePpm: Long = 450_000L,
    val unemploymentPpm: Long = 60_000L,
    val happinessPpm: Long = 600_000L,
    val coastal: Boolean = false,
    val transferPerResident: Money = Money.units(15),
) {
    init {
        require(population >= 0L) { "population cannot be negative" }
        require(!averageWage.isNegative) { "wage cannot be negative" }
    }
}

/** The headline numbers of the last traffic assignment, kept with the plan so they can be shown and saved. */
data class TrafficSummary(
    val commuters: Long,
    val averageCommuteSeconds: Long,
    val carSharePpm: Long,
    val transitSharePpm: Long,
    val walkSharePpm: Long,
    val congestionPpm: Long,
) {
    companion object {
        val EMPTY = TrafficSummary(0L, 0L, 0L, 0L, Scale.PPM, 0L)

        fun of(r: TrafficReport) = TrafficSummary(
            r.commuters, r.averageCommuteSeconds, r.modeShares.car, r.modeShares.transit, r.modeShares.walk, r.congestionIndexPpm,
        )
    }
}

/**
 * Everything the urban model keeps about one city. The districts, network, budget, council, petitions, projects,
 * concessions and events are owned here and changed only by the [UrbanService].
 */
class CityPlan(
    val city: CityId,
    val country: CountryId,
    val name: String,
    val geometry: CityGeometry,
    val districts: List<District>,
    val council: Council,
) {
    init { require(districts.size == geometry.districtCount) { "one district per block" } }

    val network = TransitNetwork(geometry)
    val budget = MunicipalBudget()
    val petitions = PetitionBook()
    val projects = ProjectBook()
    val concessions = ConcessionBook()
    val events = EventBook()

    var inputs: CityInputs = CityInputs(0L, Money.ZERO)
        internal set
    var traffic: TrafficSummary = TrafficSummary.EMPTY
        internal set
    var lastMonthly: Tick = Tick(0)
        internal set
    /** Liveability index by month, oldest first, at most [HISTORY] entries. */
    internal val liveabilityHistory = ArrayList<Int>()
    /** Capital spent in the current month, folded into the month's accounts. */
    var capitalThisMonth: Money = Money.ZERO
        internal set
    /** Fees taken this month for permits, rezonings and fines. */
    var feesThisMonth: Money = Money.ZERO
        internal set

    fun district(i: Int): District = districts[i]
    fun history(): List<Int> = liveabilityHistory.toList()

    val population: Long get() = districts.sumOf { it.residents }
    val jobs: Long get() = districts.sumOf { it.jobs }
    val floorArea: Long get() = districts.sumOf { it.floorArea }
    val landSqm: Long get() = districts.sumOf { it.landSqm }

    /** Value the city taxes: buildings at depreciated replacement cost plus land. */
    fun assessedValue(params: UrbanParams): Money {
        var raw = 0L
        for (d in districts) {
            raw += d.stock.replacementValue(params.buildCostPerSqm).raw
            raw += d.landValuePerSqm.raw * d.landSqm
        }
        return Money(raw)
    }

    /** Floor area of shops, offices and factories, which the business tax falls on. */
    fun businessFloorArea(): Long = districts.sumOf { Zoning.commercialArea(it.zone, it.floorArea) + Zoning.industrialArea(it.zone, it.floorArea) }

    /** Total monthly wages of the working residents, at the city's average wage. */
    fun monthlyWages(params: UrbanParams): Money {
        val workers = mulDiv(population, params.participationPpm, Scale.PPM)
        val employed = workers - mulDiv(workers, inputs.unemploymentPpm, Scale.PPM)
        return inputs.averageWage * (employed * Scale.MONTH_DAYS)
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(city.value).putLong(country.value).putString(name)
        h.putInt(geometry.gridSize).putInt(geometry.blockSize).putLong(geometry.cellAreaSqm)
        for (d in districts) d.hashInto(h)
        network.hashInto(h)
        budget.hashInto(h)
        council.hashInto(h)
        petitions.hashInto(h)
        projects.hashInto(h)
        concessions.hashInto(h)
        events.hashInto(h)
        h.putLong(inputs.population).putMoney(inputs.averageWage).putLong(inputs.lowIncomeSharePpm).putLong(inputs.unemploymentPpm)
        h.putLong(inputs.happinessPpm).putBoolean(inputs.coastal).putMoney(inputs.transferPerResident)
        h.putLong(traffic.commuters).putLong(traffic.averageCommuteSeconds).putLong(traffic.carSharePpm).putLong(traffic.transitSharePpm)
        h.putLong(traffic.walkSharePpm).putLong(traffic.congestionPpm).putLong(lastMonthly.value)
        h.putInt(liveabilityHistory.size)
        for (v in liveabilityHistory) h.putInt(v)
        h.putMoney(capitalThisMonth).putMoney(feesThisMonth)
    }

    companion object {
        const val HISTORY = 24
    }
}
