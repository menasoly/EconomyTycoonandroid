package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.tourism.AllotmentStatus
import app.many.economytycoon.tourism.AttractionKind
import app.many.economytycoon.tourism.DayInputs
import app.many.economytycoon.tourism.Destination
import app.many.economytycoon.tourism.DestinationDrivers
import app.many.economytycoon.tourism.IntermediaryKind
import app.many.economytycoon.tourism.PropertyKind
import app.many.economytycoon.tourism.PropertyStatus
import app.many.economytycoon.tourism.SpendCategory
import app.many.economytycoon.tourism.Staffing
import app.many.economytycoon.tourism.TourismCommand
import app.many.economytycoon.world.City
import app.many.economytycoon.world.Point
import app.many.economytycoon.world.Region
import app.many.economytycoon.world.Terrain

/**
 * Connects tourism to the economy. Games begin without tourism; once [establish] has run, the world's attractive towns
 * are destinations with a few hotels, a booking platform, a travel agency and a tour operator, every region sends
 * visitors, and the module takes its turn each day. Until then nothing here does anything, nothing is saved or hashed
 * and no random number is drawn, so a game without tourism plays exactly as it did before.
 *
 * Everything the module needs from the world is read from it each day: weather and the transport network of the town,
 * the city's labour force for hospitality staff, its population for the local spending comparison, and the country's
 * minimum wage. Company commands are carried out and paid at once; the month's guest payments, wages, taxes and
 * commissions are settled on the last day of each month through the ordinary settlement batch. Households pay and are
 * paid through the shared households' pool, like the electricity grids.
 */
class TourismRunner(private val e: Economy) {
    private val state get() = e.tourism
    private val service get() = e.tourismService

    /** True once destinations exist. */
    val active: Boolean get() = !state.isEmpty

    /** Extra demand for consumer goods in a region from visitors' spending, ppm, worked out on the day. Held only for the rest of the tick. */
    private var lift: Map<RegionId, Long> = emptyMap()

    private fun fail(rule: String, message: String): Outcome<CommandResult> = Outcome.reject(rule, message)

    private fun cash(company: CompanyId): Money = e.finance.ledger.cash(LedgerOwner.company(company))

    // ---- setting up ----------------------------------------------------------------------------

    /**
     * Turns the world into a tourism market: up to two destinations in each region (coast, airports, ports and capitals
     * first, then the biggest towns), a home market for every region and one for overseas visitors, hotels
     * and attractions to start with, and the booking channels. Everything follows from the world, so the same
     * world always gets the same market.
     */
    fun establish(tick: Tick): Outcome<CommandResult> {
        if (active) return fail(Rules.WRONG_STATE, "tourism is already set up")
        val regions = e.world.regions().sortedBy { it.id }
        if (regions.isEmpty()) return fail(Rules.WRONG_STATE, "there are no regions to visit")
        val made = ArrayList<Destination>()
        for (r in regions) {
            for (city in chooseCities(r)) {
                val country = e.world.country(r.country)
                val d = service.addDestination(city.id, r.id, r.country, city.name, r.climate, city.location, city.coastal, driversOf(city, r), 1_000_000L).getOrNull() ?: continue
                service.setTaxes(d.id, Money.ZERO, 0L, country.salesTaxPpm.coerceIn(0L, 500_000L))
                made += d
            }
        }
        if (made.isEmpty()) return fail(Rules.WRONG_STATE, "no town is worth a visit")
        for (r in regions) service.addOrigin(r.name, r.country, r.center, outboundOf(r), 1_000_000L)
        val domestic = state.origins.sumOf { it.outboundPerDay }
        service.addOrigin("Overseas visitors", null, Point(0, 0), overseasOutbound(domestic), 1_100_000L)
        val platform = service.addIntermediary(IntermediaryKind.ONLINE_PLATFORM, null, "Voyage Online", 150_000L, 1_000_000L, Money.ZERO).getOrNull()
        val agency = service.addIntermediary(IntermediaryKind.AGENCY, null, "Main Street Travel", 100_000L, 1_000_000L, Money.ZERO).getOrNull()
        service.addOperator(null, "Sunway Holidays")
        val rivals = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.sortedBy { it.id }
        for ((i, d) in made.withIndex()) seedTown(d, listOfNotNull(platform?.id, agency?.id), tick, if (rivals.isEmpty()) null else rivals[i % rivals.size].id)
        return Outcome.ok(CommandResult(made.size.toLong(), "${made.size} tourist destinations set up"))
    }

    private fun people(city: City): Long = e.world.population(city.id).total

    private fun chooseCities(region: Region): List<City> =
        e.world.citiesIn(region.id)
            .sortedWith(compareByDescending<City> { score(it) }.thenByDescending { people(it) }.thenBy { it.id })
            .take(DESTINATIONS_PER_REGION)

    private fun score(c: City): Int = (if (c.coastal) 3 else 0) + (if (c.hasAirport) 2 else 0) + (if (c.hasPort) 1 else 0) + (if (c.isCapital) 2 else 0)

    private fun popScore(c: City): Long = mulDiv(people(c), 1_000_000L, BIG_TOWN).coerceIn(0L, 1_000_000L)

    /** What a town offers: access from its airport, port and links, safety from its country, culture and nightlife from its size, nature from its terrain. */
    private fun driversOf(city: City, region: Region): DestinationDrivers {
        val country = e.world.country(region.country)
        val modes = e.world.network.modesAt(city.id).size
        val infrastructure = 250_000L + (if (city.hasAirport) 300_000L else 0L) + (if (city.hasPort) 100_000L else 0L) + 60_000L * modes.toLong()
        val nature = when (region.terrain) {
            Terrain.MOUNTAINS -> 700_000L
            Terrain.HILLS -> 520_000L
            else -> 280_000L
        } + (if (city.coastal) 150_000L else 0L)
        val pop = popScore(city)
        return DestinationDrivers(
            infrastructurePpm = infrastructure.coerceIn(0L, 1_000_000L),
            safetyPpm = country.stabilityPpm.coerceIn(200_000L, 950_000L),
            culturePpm = (150_000L + mulDiv(450_000L, pop, 1_000_000L) + (if (city.isCapital) 200_000L else 0L)).coerceIn(0L, 1_000_000L),
            naturePpm = nature.coerceIn(0L, 1_000_000L),
            nightlifePpm = (150_000L + mulDiv(500_000L, pop, 1_000_000L) + (if (city.coastal) 100_000L else 0L)).coerceIn(0L, 1_000_000L),
        )
    }

    private fun outboundOf(region: Region): Long =
        mulDiv(e.world.citiesIn(region.id).sumOf { people(it) }, TRIPS_PER_PERSON_YEAR_PPM, 1_000_000L * Calendar.DAYS_PER_YEAR)

    private fun overseasOutbound(domestic: Long): Long = mulDiv(domestic, OVERSEAS_SHARE_PPM, 1_000_000L)

    /**
     * Rooms, attractions and listings a town starts with, in proportion to its people: a main three-star hotel (run by a
     * computer company when there are any, so the hoteliers have houses to manage), a cheaper inn and a four-star house, and a
     * museum or a beach club where the town has the setting for one. The rest of the capacity belongs to nobody.
     */
    private fun seedTown(d: Destination, channels: List<app.many.economytycoon.tourism.IntermediaryId>, tick: Tick, mainOwner: CompanyId?) {
        val city = e.world.cityOrNull(d.city) ?: return
        val rooms = (BASE_TOWN_ROOMS + people(city) / PEOPLE_PER_ROOM).coerceIn(MIN_TOWN_ROOMS, MAX_TOWN_ROOMS).toInt()
        val inn = rooms * 3 / 10
        val plaza = rooms * 2 / 10
        val builds = ArrayList<Triple<Int, Int, String>>()
        builds += Triple(3, rooms - (if (inn >= 20) inn else 0) - (if (plaza >= 20) plaza else 0), "${city.name} Grand Hotel")
        if (inn >= 20) builds += Triple(2, inn, "${city.name} Inn")
        if (plaza >= 20) builds += Triple(4, plaza, "${city.name} Plaza")
        for ((i, b) in builds.withIndex()) {
            val (stars, n, name) = b
            val classes = intArrayOf(n * 7 / 10, n * 2 / 10, n - n * 7 / 10 - n * 2 / 10)
            val owner = if (i == 0) mainOwner else null
            val p = service.addProperty(d.id, owner, null, PropertyKind.HOTEL, stars, classes, tick, name, null, instant = true).getOrNull()?.property ?: continue
            for (c in channels) service.listWith(p.id, c)
        }
        if (city.isCapital) service.addAttraction(d.id, null, null, AttractionKind.MUSEUM, "${city.name} Museum", tick, (rooms * 2).coerceIn(300, 6_000), instant = true)
        if (city.coastal) service.addAttraction(d.id, null, null, AttractionKind.BEACH_CLUB, "${city.name} Beach Club", tick, (rooms * 6 / 5).coerceIn(200, 3_000), instant = true)
    }

    /** Brings the slow-moving figures up to date: how many people set out from each region, and what each town offers. */
    private fun refresh() {
        val regions = e.world.regions().associateBy { it.id }
        var domestic = 0L
        for (o in state.origins) {
            val region = regions.values.firstOrNull { it.center == o.location && it.country == o.country } ?: continue
            val outbound = outboundOf(region)
            domestic += outbound
            service.setOutbound(o.id, outbound)
        }
        for (o in state.origins) if (o.country == null) service.setOutbound(o.id, overseasOutbound(domestic))
        for (d in state.destinations) {
            val city = e.world.cityOrNull(d.city) ?: continue
            val region = regions[d.region] ?: continue
            service.setDrivers(d.id, driversOf(city, region))
        }
    }

    // ---- commands ------------------------------------------------------------------------------

    /** Carries out one tourism command for its company, paying any capital cost at once. */
    fun execute(cmd: TourismCommand, tick: Tick): Outcome<CommandResult> {
        if (!active) return fail(Rules.WRONG_STATE, "this game has no tourist destinations")
        val cost = e.tourismExecutor.cost(cmd)
        if (cost.raw > 0L && cash(cmd.company) < cost) return fail(Rules.INSUFFICIENT_FUNDS, "that costs $cost")
        if (cmd is TourismCommand.LaunchCampaign && e.finance.ledger.hasKey("tour:mkt:${cmd.destination.value}:${tick.value}:${cmd.company.value}")) {
            return fail(Rules.WRONG_STATE, "you already ran a campaign there today")
        }
        val result = e.tourismExecutor.execute(cmd, tick)
        result.rejection?.let { return Outcome.Rejected(it) }
        pay(result.intents, tick)
        return Outcome.ok(CommandResult(message = result.message))
    }

    /** Settles the payments of a decision at once; one that cannot be paid in cash is booked as a payable, never lost. */
    fun pay(intents: List<SettlementIntent>, tick: Tick) {
        if (intents.isEmpty()) return
        e.finance.settlement.settle(tick, intents.map { toPool(it) })
    }

    // ---- the day -------------------------------------------------------------------------------

    /** Runs the day of every destination and works out what the visitors' spending adds to local demand. */
    fun advance(tick: Tick, summary: TickSummary) {
        lift = emptyMap()
        if (!active || tick.value <= state.lastTick) return
        if (tick.value % Calendar.DAYS_PER_MONTH == 0L) refresh()
        val destinations = state.destinations
        val inputs = DayInputs(
            weather = destinations.associate { it.id to e.world.weather.activeIn(it.region, tick) },
            transport = WorldTransport(e.world, state),
            staff = WorldStaffSupply(e.world, state),
            wagePerDay = destinations.associate { it.id to Staffing.defaultWage(e.world.country(it.country).minimumWagePerTick) },
            residents = destinations.associate { d -> d.id to (e.world.cityOrNull(d.city)?.let { people(it) } ?: 0L) },
        )
        val report = service.advanceDay(tick, inputs, e.random)
        summary.tourismReport = report
        lift = demandLift(report)
    }

    /**
     * What visitors add to the demand for goods in a region: the uplift of their shop spending over what residents spend,
     * weighted by the share of the region's people who live in the destination, and held to a ceiling. A pure function of the day's report.
     */
    private fun demandLift(report: app.many.economytycoon.tourism.DayReport): Map<RegionId, Long> {
        val out = java.util.TreeMap<RegionId, Long>()
        for (s in report.spending) {
            val d = state.destination(s.destination) ?: continue
            val city = e.world.cityOrNull(d.city) ?: continue
            val regionPeople = e.world.citiesIn(d.region).sumOf { people(it) }
            if (regionPeople <= 0L) continue
            val share = mulDiv(people(city), (s.uplifts.firstOrNull { it.category == SpendCategory.SHOPS }?.upliftPpm ?: 0L), regionPeople)
            out[d.region] = ((out[d.region] ?: 0L) + share).coerceAtMost(MAX_LIFT_PPM)
        }
        return out
    }

    /** The demand shift of a region with visitors' spending added; unchanged where there are no visitors or no tourism. */
    fun liftDemand(shiftPpm: Long, region: RegionId): Long {
        val extra = lift[region] ?: return shiftPpm
        return mulDiv(shiftPpm, 1_000_000L + extra, 1_000_000L)
    }

    // ---- settlement ----------------------------------------------------------------------------

    /** On the last day of a month, the month's bills for every property, attraction and operator. */
    fun settlementIntents(tick: Tick, summary: TickSummary): List<SettlementIntent> {
        if (!active) return emptyList()
        val month = tick.value / Calendar.DAYS_PER_MONTH
        if (tick.value % Calendar.DAYS_PER_MONTH != Calendar.DAYS_PER_MONTH - 1L || month <= state.lastSettledMonth) return emptyList()
        val run = service.settleMonth(tick)
        summary.tourismSettlement = run
        return run.intents.map { toPool(it) }
    }

    /**
     * Visitors from abroad, suppliers and agencies with no company behind them are not modelled as accounts of their own,
     * so what they pay and are paid goes through the households' pool, as the banks' customers do. Money is neither created
     * nor lost: the ledger's check of the money supply holds.
     */
    private fun toPool(i: SettlementIntent): SettlementIntent {
        val payer = if (i.payer.kind == OwnerKind.HOUSEHOLDS || i.payer.kind == OwnerKind.SYSTEM) e.finance.households else i.payer
        val payee = if (i.payee.kind == OwnerKind.HOUSEHOLDS || i.payee.kind == OwnerKind.SYSTEM) e.finance.households else i.payee
        return if (payer == i.payer && payee == i.payee) i else i.copy(payer = payer, payee = payee)
    }

    // ---- leaving -------------------------------------------------------------------------------

    /** A company that has ceased to exist closes its hotels and attractions and ends its contracts. */
    fun forget(company: CompanyId, tick: Tick) {
        if (!active) return
        for (a in state.allotments) {
            val party = state.property(a.property)?.owner == company || state.operator(a.operator)?.owner == company
            if (!party) continue
            when (a.status) {
                AllotmentStatus.PROPOSED -> service.declineAllotment(a.id, company)
                AllotmentStatus.ACTIVE -> service.terminateAllotment(a.id, company, tick)
                else -> Unit
            }
        }
        for (p in state.propertiesOf(company)) if (p.status != PropertyStatus.CLOSED) service.closeProperty(p.id)
        for (a in state.attractionsOf(company)) service.setAttractionOpen(a.id, false)
    }

    companion object {
        const val DESTINATIONS_PER_REGION = 2
        /** Trips a year that a person makes to somewhere in this world, ppm of a trip: a trip every five years. */
        const val TRIPS_PER_PERSON_YEAR_PPM = 200_000L
        const val OVERSEAS_SHARE_PPM = 150_000L
        /** A town of this many people counts as big when judging culture and nightlife. */
        const val BIG_TOWN = 200_000L
        const val PEOPLE_PER_ROOM = 1_000L
        const val BASE_TOWN_ROOMS = 200L
        const val MIN_TOWN_ROOMS = 100L
        const val MAX_TOWN_ROOMS = 1_500L
        /** The most that visitors can add to the demand for goods in a region, ppm. */
        const val MAX_LIFT_PPM = 150_000L
    }
}
