package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.core.splitLargestRemainder
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.urban.CityFactory
import app.many.economytycoon.urban.CityInputs
import app.many.economytycoon.urban.ConcessionStatus
import app.many.economytycoon.urban.LobbyingHook
import app.many.economytycoon.urban.PetitionStatus
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.ZoneUse
import app.many.economytycoon.world.City
import app.many.economytycoon.world.ZoneType

/**
 * Connects city planning to the economy. Games begin without it; once [establish] has run, every city of the world has a
 * plan of nine districts whose zoning follows the land of the real city, whose people, wages and housing come from the
 * world, and the module takes its turn each day. Until then nothing here does anything, nothing is saved or hashed and no
 * random number is drawn, so a game without urban planning plays exactly as it did before.
 *
 * The plan reaches back into the economy in three narrow, bounded ways, each only for a city that has a plan: the
 * housing the city can hold and the rent index it pays (read by the next population update), the commute that trims the
 * share of people who can work, and a factor on what a parcel in the city is worth. Money flows only through settlement
 * intents: a company pays its petition fees at once, the month's concession payments join the day's settlement batch,
 * and the government of the city's country is the counterparty on the ledger. The city's own budget is a memo kept by the model.
 */
class UrbanRunner(private val e: Economy) {
    private val state get() = e.urban
    private val service get() = e.urbanService

    /** True once cities have plans. */
    val active: Boolean get() = state.size > 0

    /** Payments produced in the course of today's turn, taken into the day's settlement batch. Never outlives the tick. */
    private val pending = ArrayList<SettlementIntent>()

    init {
        service.addLobbyingHook(LobbyingHook { petition, _, _ -> standing(petition.applicant) })
    }

    private fun fail(rule: String, message: String): Outcome<CommandResult> = Outcome.reject(rule, message)

    private fun cash(company: CompanyId): Money = e.finance.ledger.cash(LedgerOwner.company(company))

    // ---- setting up ----------------------------------------------------------------------------

    /**
     * Gives every city of the world a plan. Districts are zoned from the land of the city (the share of each use among its
     * parcels, the commercial core in the middle and industry on the edge), start with the people and wages the world
     * reports, and get a building stock to match. The same world always gets the same plans.
     */
    fun establish(tick: Tick): Outcome<CommandResult> {
        if (active) return fail(Rules.WRONG_STATE, "city planning is already set up")
        val cities = e.world.cities().sortedBy { it.id }
        if (cities.isEmpty()) return fail(Rules.WRONG_STATE, "there are no cities to plan")
        var made = 0
        for (city in cities) {
            val inputs = inputsFor(city.id) ?: continue
            val country = e.world.countryOfCity(city.id).id
            if (service.addCity(city.id, country, city.name, inputs, tick, layoutOf(city)) is Outcome.Ok) made++
        }
        if (made == 0) return fail(Rules.WRONG_STATE, "no city is big enough to plan")
        return Outcome.ok(CommandResult(made.toLong(), "$made cities now have a plan"))
    }

    /** What the world says about a city today; null when it has no people to plan for. */
    fun inputsFor(city: CityId): CityInputs? {
        val c = e.world.cityOrNull(city) ?: return null
        val p = e.world.population(city)
        if (p.total <= 0L) return null
        return CityInputs(p.total, p.averageWage, p.lowShare, p.unemploymentPpm, p.happinessPpm, c.coastal)
    }

    /**
     * Zones for the districts from the parcels of a city: the middle district is the commercial core, the rest take the
     * other uses in proportion to the land that has them, with shops and mixed use nearest the middle and industry
     * furthest out. Null when the city has no land on record.
     */
    private fun layoutOf(city: City): List<ZoneUse>? {
        val params = state.params
        val area = LongArray(ZoneUse.values().size)
        for (p in e.world.land.inCity(city.id)) area[useOf(p.effectiveZone).ordinal] += p.area.toLong()
        if (area.sum() <= 0L) return null
        val side = params.districtsPerSide
        val outer = (0 until params.districtCount).filter { CityFactory.ring(it, side) > 0 }.sortedWith(compareBy({ CityFactory.ring(it, side) }, { it }))
        if (outer.isEmpty()) return null
        val counts = splitLargestRemainder(outer.size.toLong(), area)
        val uses = ArrayList<ZoneUse>()
        for (use in ZoneUse.values().sortedBy { RANK.getValue(it) }) repeat(counts[use.ordinal].toInt()) { uses += use }
        val layout = MutableList(params.districtCount) { ZoneUse.COMMERCIAL }
        for ((i, d) in outer.withIndex()) layout[d] = uses[i]
        return layout
    }

    private fun useOf(zone: ZoneType): ZoneUse = when (zone) {
        ZoneType.COMMERCIAL -> ZoneUse.COMMERCIAL
        ZoneType.RESIDENTIAL -> ZoneUse.RESIDENTIAL
        ZoneType.MIXED -> ZoneUse.MIXED
        ZoneType.INDUSTRIAL, ZoneType.MINING -> ZoneUse.INDUSTRIAL
        ZoneType.AGRICULTURAL, ZoneType.PROTECTED -> ZoneUse.GREEN
    }

    // ---- government ----------------------------------------------------------------------------

    /** The cities that a player's company has its headquarters in, which is how a player comes to govern one. */
    private fun governedCities(): Set<CityId> =
        e.companies.all().filter { it.kind == CompanyKind.PLAYER && it.isActive }.mapNotNull { it.hq }.toSet()

    /** True when [company] is a player's company with its headquarters in [city]. */
    fun governs(company: CompanyId, city: CityId): Boolean {
        val c = e.companies.company(company) ?: return false
        return c.kind == CompanyKind.PLAYER && c.isActive && c.hq == city
    }

    // ---- commands ------------------------------------------------------------------------------

    /** The company an order is given on behalf of, or null for the orders of a city government. */
    private fun actingCompany(cmd: UrbanCommand): CompanyId? = when (cmd) {
        is UrbanCommand.FilePetition -> cmd.applicant
        is UrbanCommand.WithdrawPetition -> cmd.applicant
        is UrbanCommand.LobbyPetition -> cmd.company
        is UrbanCommand.BidConcession -> cmd.company
        else -> null
    }

    /**
     * Carries out one order for [company]. A company deals with the city through its own petitions, lobbying and bids; the
     * orders of the city government (taxes, services, roads, lines, works, loans, tenders) are for the player whose
     * headquarters stands in that city. Fees are paid at once, and an order the company cannot pay for is refused.
     */
    fun execute(company: CompanyId, cmd: UrbanCommand, tick: Tick): Outcome<CommandResult> {
        if (!active) return fail(Rules.WRONG_STATE, "this game has no city plans")
        val plan = state.plan(cmd.city) ?: return fail(Rules.UNKNOWN_ENTITY, "that city has no plan")
        val acting = actingCompany(cmd)
        if (acting != null) {
            if (acting != company) return fail(Rules.NOT_OWNER, "that order names another company")
        } else if (!governs(company, cmd.city)) {
            return fail(Rules.NOT_OWNER, "only the government of ${plan.name} can order that; you govern the city your headquarters is in")
        }
        val cost = service.companyCost(cmd)
        if (cost.raw > 0L && cash(company) < cost) return fail(Rules.INSUFFICIENT_FUNDS, "that costs $cost")
        val receipt = when (val r = service.execute(cmd, tick)) {
            is Outcome.Rejected -> return Outcome.Rejected(r.rejection)
            is Outcome.Ok -> r.value
        }
        pay(receipt.intents, tick)
        return Outcome.ok(CommandResult(receipt.created, receipt.message))
    }

    /** Settles the payments of a decision at once; one that cannot be paid in cash is booked as a payable, never lost. */
    fun pay(intents: List<SettlementIntent>, tick: Tick) {
        if (intents.isEmpty()) return
        e.finance.settlement.settle(tick, intents.map { toPool(it) })
    }

    /**
     * Households and the system account have no ledger account of their own here, so what they pay and are paid goes
     * through the households' pool, as for the banks' customers and the tourists. Money is neither created nor lost.
     */
    private fun toPool(i: SettlementIntent): SettlementIntent {
        val payer = if (i.payer.kind == OwnerKind.HOUSEHOLDS || i.payer.kind == OwnerKind.SYSTEM) e.finance.households else i.payer
        val payee = if (i.payee.kind == OwnerKind.HOUSEHOLDS || i.payee.kind == OwnerKind.SYSTEM) e.finance.households else i.payee
        return if (payer == i.payer && payee == i.payee) i else i.copy(payer = payer, payee = payee)
    }

    // ---- the day -------------------------------------------------------------------------------

    /**
     * Takes the day's turn for every city: projects, petitions and concessions move on, and on the first day of a month the
     * computer mayors give their orders, the city is brought up to date from the world and its effects are handed back.
     * Runs before the day's settlement so that the month's concession payments join that batch.
     */
    fun advance(tick: Tick, summary: TickSummary) {
        pending.clear()
        if (!active) return
        // A plan founded today has just had its monthly update; it is not done twice.
        val monthStart = Calendar.isMonthStart(tick) && state.plans().none { it.lastMonthly.value >= tick.value }
        if (monthStart) mayors(tick)
        val step = service.step(tick, monthStart) { inputsFor(it) }
        pending += step.intents.map { toPool(it) }
        summary.urbanNotices = step.notices
        if (monthStart) feedBack(tick)
    }

    /** The day's urban payments for the settlement batch. */
    fun drain(): List<SettlementIntent> {
        if (pending.isEmpty()) return emptyList()
        val out = pending.toList()
        pending.clear()
        return out
    }

    /** Lets the computer mayor give the month's orders in every city that no player governs. */
    private fun mayors(tick: Tick) {
        val governed = governedCities()
        // Scores are rebuilt from the saved plans, so a game loaded mid-month decides exactly as one that was never saved.
        service.refreshScores()
        for (plan in state.plans()) {
            if (plan.city in governed) continue
            for ((_, outcome) in service.runAi(plan.city, tick)) {
                if (outcome is Outcome.Ok) pending += outcome.value.intents.map { toPool(it) }
            }
        }
    }

    // ---- what the plan gives back --------------------------------------------------------------

    /**
     * Hands each plan's monthly results to the world: the housing the city holds moves toward what its districts can house,
     * the rent index is nudged by how scarce housing is, and the commute trims the share of people who can work. Every step
     * is small and clamped, so a plan can lean on the world but never jolt it.
     */
    private fun feedBack(tick: Tick) {
        // The share of people who work that a city starts with; the world never changes it, so this is the level a short commute returns to.
        val baseParticipation = app.many.economytycoon.world.DemographicParams().participationPpm
        for (plan in state.plans()) {
            val city = e.world.cityOrNull(plan.city) ?: continue
            val out = service.outputs(plan.city, tick) ?: continue
            val pop = e.world.population(plan.city)

            val have = e.world.housingCapacity(plan.city)
            val want = out.housingCapacity / PEOPLE_PER_HOUSING_UNIT
            val room = mulDiv(have.coerceAtLeast(1L), HOUSING_STEP_PPM, 1_000_000L)
            e.world.setHousing(city.id, have + (want - have).coerceIn(-room, room))

            val gap = out.rentIndexPpm - 1_000_000L
            val nudge = mulDiv(pop.rentIndexPpm, gap / RENT_WEIGHT_DIVISOR, 1_000_000L)
            val cap = mulDiv(pop.rentIndexPpm, RENT_STEP_PPM, 1_000_000L)
            pop.rentIndexPpm = (pop.rentIndexPpm + nudge.coerceIn(-cap, cap)).coerceIn(MIN_RENT_PPM, MAX_RENT_PPM)

            val excess = (plan.traffic.averageCommuteSeconds - COMMUTE_FREE_SECONDS).coerceIn(0L, COMMUTE_SPAN_SECONDS)
            val target = baseParticipation - excess * COMMUTE_PENALTY_PPM_PER_SECOND
            pop.participation += (target - pop.participation) / PARTICIPATION_EASE
        }
    }

    /** What a parcel in [city] is worth against the same parcel in a city with middling access, ppm; one when there is no plan. */
    fun landFactorPpm(city: CityId?): Long {
        if (city == null || !active) return 1_000_000L
        val plan = state.plan(city) ?: return 1_000_000L
        val mean = plan.districts.sumOf { it.accessibilityPermille.toLong() } / plan.districts.size.coerceAtLeast(1)
        return LAND_FACTOR_FLOOR_PPM + mean.coerceIn(0L, 1_000L) * LAND_FACTOR_PPM_PER_PERMILLE
    }

    /** How well a company stands with the councils: its working facilities, two points each up to a ceiling. */
    private fun standing(company: CompanyId?): Int {
        if (company == null) return 0
        val working = e.production.facilitiesOf(company).count { it.isOperating() }
        return (working * STANDING_POINTS_PER_FACILITY).coerceAtMost(STANDING_CEILING)
    }

    // ---- leaving -------------------------------------------------------------------------------

    /** A company that has ceased to exist withdraws its petitions, drops its bids and hands back what it ran for the cities. */
    fun forget(company: CompanyId, tick: Tick) {
        if (!active) return
        for (plan in state.plans()) {
            for (p in plan.petitions.all()) {
                if (p.applicant == company && (p.status == PetitionStatus.FILED || p.status == PetitionStatus.HEARING)) {
                    service.execute(UrbanCommand.WithdrawPetition(plan.city, p.id, company), tick)
                }
            }
            for (c in plan.concessions.all()) {
                if (c.status == ConcessionStatus.TENDER) c.bidList.removeAll { it.company == company }
                else if (c.concessionaire == company && c.isLive) service.execute(UrbanCommand.TerminateConcession(plan.city, c.id), tick)
            }
        }
    }

    companion object {
        /** People that one housing unit of the world holds; the world counts a city's housing at half its people. */
        const val PEOPLE_PER_HOUSING_UNIT = 2L
        /** Most that a city's housing may move in a month, ppm of what it holds. */
        const val HOUSING_STEP_PPM = 10_000L
        /** Share of the rent gap (as 1/n) that is passed to the world's rent index each month. */
        const val RENT_WEIGHT_DIVISOR = 40L
        /** Most that the rent index may move by the plan in a month, ppm of itself. */
        const val RENT_STEP_PPM = 15_000L
        const val MIN_RENT_PPM = 500_000L
        const val MAX_RENT_PPM = 2_500_000L
        /** A commute up to this long costs no workers. */
        const val COMMUTE_FREE_SECONDS = 2_400L
        /** Beyond the free commute, the extra seconds that count. */
        const val COMMUTE_SPAN_SECONDS = 2_400L
        /** Participation lost per extra second of commute, ppm: at most about 2.4 points of participation. */
        const val COMMUTE_PENALTY_PPM_PER_SECOND = 10L
        /** The share of the gap to the commute-adjusted participation closed each month, as 1/n. */
        const val PARTICIPATION_EASE = 3L
        const val LAND_FACTOR_FLOOR_PPM = 900_000L
        const val LAND_FACTOR_PPM_PER_PERMILLE = 200L
        const val STANDING_POINTS_PER_FACILITY = 2
        const val STANDING_CEILING = 8

        private val RANK = mapOf(
            ZoneUse.COMMERCIAL to 0, ZoneUse.MIXED to 1, ZoneUse.CIVIC to 2, ZoneUse.RESIDENTIAL to 3, ZoneUse.GREEN to 4, ZoneUse.INDUSTRIAL to 5,
        )
    }
}
