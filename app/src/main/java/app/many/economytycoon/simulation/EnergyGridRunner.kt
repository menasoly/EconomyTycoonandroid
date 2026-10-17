package app.many.economytycoon.simulation

import app.many.economytycoon.content.FacilityClass
import app.many.economytycoon.content.FacilityTypeDef
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.energygrid.CustomerClass
import app.many.economytycoon.energygrid.DayInputs
import app.many.economytycoon.energygrid.DayReport
import app.many.economytycoon.energygrid.FuelDemand
import app.many.economytycoon.energygrid.FuelPrices
import app.many.economytycoon.energygrid.Grid
import app.many.economytycoon.energygrid.GridCommand
import app.many.economytycoon.energygrid.GridId
import app.many.economytycoon.energygrid.GridUnits
import app.many.economytycoon.energygrid.InvestmentPlanner
import app.many.economytycoon.energygrid.PpaStatus
import app.many.economytycoon.energygrid.Technology
import app.many.economytycoon.energygrid.UnitId
import app.many.economytycoon.energygrid.UnitStatus
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority
import app.many.economytycoon.finance.SettlementStatus
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.FacilityStatus

/**
 * Connects the electricity grids to the economy. A world starts with the simple regional electricity pool; once
 * [establish] has run, every region has a grid of its own and this class takes over for it: it keeps the grid's loads
 * and power plants in step with the facilities, runs the day, turns the fuel the plants burned into purchases, and hands
 * the month's bills to the settlement engine. Regions without a grid, and all games that never set grids up, keep the
 * old pool exactly as it was. Money moves only as settlement intents.
 */
class EnergyGridRunner(private val e: Economy) {
    private val state get() = e.energyGrids
    private val service get() = e.energyGridService

    /** The day's payments (fuel, new capacity ordered by the grid operators), kept for the settlement phase of the same tick. */
    private var dayIntents: List<SettlementIntent> = emptyList()

    /** True once grids exist; before that nothing here does anything and nothing is saved or hashed. */
    val active: Boolean get() = !state.isEmpty

    fun covers(region: RegionId): Boolean = active && state.gridOfRegion(region) != null

    private fun fail(rule: String, message: String): Outcome<CommandResult> = Outcome.reject(rule, message)

    private fun cash(company: CompanyId): Money = e.finance.ledger.cash(LedgerOwner.company(company))

    // ---- setting up ----------------------------------------------------------------------------

    /**
     * Gives every region a grid: a public fleet sized to the people living there, a connection for its households, and a
     * link between each region and its nearest neighbour in the same country. Everything follows from the world, so the
     * same world always gets the same grids.
     */
    fun establish(tick: Tick): Outcome<CommandResult> {
        if (active) return fail(Rules.WRONG_STATE, "the electricity grids are already set up")
        val regions = e.world.regions().sortedBy { it.id }
        if (regions.isEmpty()) return fail(Rules.WRONG_STATE, "there are no regions to supply")
        val made = ArrayList<Grid>()
        for (r in regions) {
            val g = service.addGrid(r.id, r.country, "${r.name} grid", r.climate).getOrNull() ?: continue
            val houses = householdKw(r.id)
            service.setHouseholdKw(g.id, houses)
            seedFleet(g, houses, e.world.region(r.id).terrain.let { it == app.many.economytycoon.world.Terrain.HILLS || it == app.many.economytycoon.world.Terrain.MOUNTAINS }, tick)
            made += g
        }
        linkNeighbours(made)
        return Outcome.ok(CommandResult(made.size.toLong(), "${made.size} electricity grids set up"))
    }

    private fun people(region: RegionId): Long = e.world.citiesIn(region).sumOf { e.world.population(it.id).total }

    private fun householdKw(region: RegionId): Long = mulDiv(people(region), HOUSEHOLD_KW_PER_THOUSAND, 1_000L)

    /** Public plant of a mix that covers the households, an allowance for industry, and the reserve the operator aims for. */
    private fun seedFleet(g: Grid, householdKw: Long, hydroSite: Boolean, tick: Tick) {
        val installed = mulDiv(householdKw, INSTALLED_PER_HOUSEHOLD_PPM, 1_000_000L)
        val mix = if (hydroSite) HYDRO_MIX else PLAIN_MIX
        for ((tech, share) in mix) {
            val total = mulDiv(installed, share, 1_000_000L)
            if (total < MIN_SEED_KW) continue
            val count = ((total + MAX_SEED_UNIT_KW - 1L) / MAX_SEED_UNIT_KW).toInt().coerceIn(1, MAX_SEED_UNITS)
            val each = total / count
            repeat(count) { service.addUnit(g.id, tech, each, null, tick) }
        }
    }

    private fun linkNeighbours(grids: List<Grid>) {
        for (g in grids) {
            val here = e.world.region(g.region).center
            val near = grids.filter { it.id != g.id && it.country == g.country }.minWithOrNull(
                compareBy<Grid> {
                    val c = e.world.region(it.region).center
                    val dx = (c.x - here.x).toLong(); val dy = (c.y - here.y).toLong()
                    dx * dx + dy * dy
                }.thenBy { it.id },
            ) ?: continue
            val smaller = minOf(householdKwOf(g), householdKwOf(near))
            service.addLink(g.id, near.id, (smaller / LINK_SHARE).coerceAtLeast(MIN_LINK_KW), LINK_LOSS_PPM, Money.units(2))
        }
    }

    private fun householdKwOf(g: Grid): Long = g.loads.firstOrNull { it.customer == CustomerClass.HOUSEHOLD }?.baseKw ?: 0L

    // ---- commands ------------------------------------------------------------------------------

    /** Carries out one of the grid commands for [cmd]'s company, paying any capital cost at once. */
    fun execute(cmd: Command, tick: Tick): Outcome<CommandResult> {
        if (!active) return fail(Rules.WRONG_STATE, "this game has no electricity grids")
        val grid = GridCommandMap.of(cmd) ?: return fail(Rules.INVALID_ARGUMENT, "that choice does not exist")
        precheck(grid)?.let { return Outcome.Rejected(it) }
        val result = e.energyGridExecutor.execute(grid, tick)
        result.rejection?.let { return Outcome.Rejected(it) }
        pay(result.intents, tick)
        return Outcome.ok(CommandResult(message = result.message))
    }

    private fun precheck(cmd: GridCommand): Rejection? = when (cmd) {
        is GridCommand.ConnectFacility -> {
            val f = e.production.facility(cmd.facility)
            val g = state.grid(cmd.grid)
            when {
                f == null || f.owner != cmd.company || f.status == FacilityStatus.DEMOLISHED -> Rejection(Rules.NOT_OWNER, "not your facility")
                g == null -> Rejection(Rules.UNKNOWN_ENTITY, "unknown grid")
                g.region != f.region -> Rejection(Rules.INVALID_ARGUMENT, "that facility is in another region")
                cmd.baseKw > MAX_KW -> Rejection(Rules.LIMIT_EXCEEDED, "that draw is out of range")
                else -> null
            }
        }
        is GridCommand.SetDraw -> if (cmd.baseKw > MAX_KW) Rejection(Rules.LIMIT_EXCEEDED, "that draw is out of range") else null
        is GridCommand.OrderUnit -> {
            val cost = Money(cmd.tech.profile.capexPerKw.raw * cmd.capacityKw.coerceIn(0L, MAX_KW))
            when {
                cmd.capacityKw <= 0L -> Rejection(Rules.INVALID_QUANTITY, "a unit needs capacity")
                cmd.capacityKw > MAX_KW -> Rejection(Rules.LIMIT_EXCEEDED, "that unit is larger than any grid takes")
                cash(cmd.company) < cost -> Rejection(Rules.INSUFFICIENT_FUNDS, "the unit costs $cost")
                else -> null
            }
        }
        is GridCommand.OfferPpa -> {
            val buyer = e.companies.company(cmd.buyer)
            if (buyer == null || !buyer.isActive) Rejection(Rules.UNKNOWN_ENTITY, "unknown buyer") else null
        }
        else -> null
    }

    /** Settles intents of a command at once; one that cannot be paid in cash is booked as a payable, never lost. */
    private fun pay(intents: List<SettlementIntent>, tick: Tick) {
        if (intents.isEmpty()) return
        e.finance.settlement.settle(tick, intents)
    }

    // ---- the day -------------------------------------------------------------------------------

    /**
     * Brings the grids in step with the facilities, runs the day, applies blackout damage to the facilities that lost power,
     * and works out the fuel the plants burned. The payments wait for the settlement phase.
     */
    fun advance(tick: Tick, summary: TickSummary) {
        dayIntents = emptyList()
        if (!active || tick.value <= state.lastTick) return
        reconcile(tick)
        val burned = state.grids.flatMap { g -> g.units.filter { it.tech.isThermal } }.associate { it.id to it.lifetimeKwh }
        val prices = fuelPrices()
        val weather = state.grids.associate { it.id to e.world.weather.activeIn(it.region, tick) }
        val report = service.advanceDay(tick, DayInputs(prices, weather), e.random)
        summary.gridReport = report
        for (impact in report.impacts) e.production.applyBlackout(impact.facility, impact.shedHours, impact.conditionLossPpm)
        dayIntents = report.capexIntents + fuelIntents(tick, burned, prices)
    }

    /** Prices per megawatt-hour of heat, from what the markets currently pay for coal, gas and nuclear fuel. */
    fun currentFuelPrices(): FuelPrices = fuelPrices()

    private fun fuelPrices(): FuelPrices {
        val base = FuelPrices()
        fun perHeat(good: String, heatMilliMwh: Long, fallback: Money): Money {
            val id = GoodId(good)
            if (e.content.goodOrNull(id) == null) return fallback
            return Money(mulDiv(e.markets.averagePrice(id).perUnit.raw, 1_000L, heatMilliMwh).coerceAtLeast(1L))
        }
        return FuelPrices(
            coal = perHeat("coal", COAL_HEAT_MILLI_MWH, base.coal),
            gas = perHeat("natural_gas", GAS_HEAT_MILLI_MWH, base.gas),
            uranium = perHeat("enriched_uranium", URANIUM_HEAT_MILLI_MWH, base.uranium),
        )
    }

    /** Each owner pays for the fuel its plants burned today, in proportion to what they produced, to the country's import desk. */
    private fun fuelIntents(tick: Tick, before: Map<UnitId, Long>, prices: FuelPrices): List<SettlementIntent> {
        val out = ArrayList<SettlementIntent>()
        for (g in state.grids) {
            val owed = java.util.TreeMap<Long, Long>()
            for (u in g.units) {
                if (!u.tech.isThermal) continue
                val kwh = u.lifetimeKwh - (before[u.id] ?: u.lifetimeKwh)
                if (kwh <= 0L) continue
                val heat = FuelDemand.heatKwh(u.tech, kwh, u.efficiencyPpm)
                val cost = GridUnits.cost(prices.of(u.tech.profile.fuel), heat)
                owed.merge(u.owner?.value ?: PUBLIC, cost.raw, Long::plus)
            }
            val payee = e.tradeBoard.boardOf(g.country)?.let { LedgerOwner.company(it) } ?: LedgerOwner.government(g.country)
            for ((who, raw) in owed) {
                if (raw <= 0L) continue
                val payer = if (who == PUBLIC) LedgerOwner.government(g.country) else LedgerOwner.company(CompanyId(who))
                if (payer == payee) continue
                out += SettlementIntent.simple(
                    "energy:fuel:${g.id.value}:${tick.value}:$who", payer, payee, Money(raw), SettlementPriority.SUPPLIERS, ReasonCode.PURCHASE,
                    Chart.COGS, Chart.REVENUE_SALES, deferrable = true, memo = "fuel for ${g.name}",
                )
            }
        }
        return out
    }

    // ---- keeping the grids in step with the facilities -------------------------------------------

    private fun reconcile(tick: Tick) {
        if (tick.value % Calendar.DAYS_PER_MONTH == 0L) {
            for (g in state.grids) service.setHouseholdKw(g.id, householdKw(g.region))
        }
        val weekly = tick.value % Calendar.DAYS_PER_WEEK == 0L
        for (f in e.production.facilities()) {
            val grid = state.gridOfRegion(f.region) ?: continue
            val type = e.content.facilityType(f.type)
            if (type.facilityClass == FacilityClass.POWER_PLANT) plant(f, type, grid, tick) else load(f, type, grid, weekly)
        }
    }

    private fun load(f: Facility, type: FacilityTypeDef, grid: Grid, weekly: Boolean) {
        val connected = service.loadOfFacility(f.id)
        if (f.status == FacilityStatus.DEMOLISHED) {
            if (connected != null) service.disconnectFacility(f.id)
            return
        }
        val wanted = loadKw(f, type)
        if (connected == null) {
            if (f.isOperating() && wanted > 0L) service.connectFacility(grid.id, customerOf(type), f.owner, f.id, wanted)
            return
        }
        // A draw the player has trimmed stays; a plant that has outgrown its draw, or fallen far below it, is metered again.
        if (weekly || connected.baseKw == 0L) {
            val draw = if (f.isOperating()) wanted else 0L
            if (draw > connected.baseKw || draw < connected.baseKw / 2L) service.setLoadKw(connected.id, draw)
        }
    }

    /** Average draw in kilowatts of a facility's working lines: base need of each line plus the recipe's energy spread over its batch. */
    private fun loadKw(f: Facility, type: FacilityTypeDef): Long {
        var perDay = 0L
        for (line in f.lines) {
            if (!line.enabled) continue
            val recipe = line.recipe?.let { e.content.recipeOrNull(it) } ?: continue
            perDay += type.baseEnergyPerLine.raw + recipe.energyPerBatch.raw / recipe.durationTicks
        }
        return perDay / GridUnits.HOURS_PER_DAY
    }

    private fun customerOf(type: FacilityTypeDef): CustomerClass = when (type.facilityClass) {
        FacilityClass.WAREHOUSE -> CustomerClass.COMMERCIAL
        else -> CustomerClass.INDUSTRIAL
    }

    /** A power plant facility stands on its grid as a unit of the matching technology; its own burn recipes are switched off. */
    private fun plant(f: Facility, type: FacilityTypeDef, grid: Grid, tick: Tick) {
        val tech = technologyOf(type.id.key) ?: return
        var unit = state.plantLinks[f.id]?.let { state.unit(it) }
        if (unit != null && (f.status == FacilityStatus.DEMOLISHED || unit.owner != f.owner || unit.status == UnitStatus.RETIRED)) {
            service.retireUnit(unit.id)
            service.unlinkPlant(f.id)
            unit = null
        }
        if (f.status == FacilityStatus.DEMOLISHED) return
        if (unit == null) {
            if (!f.isOperating()) return
            val kw = plantKw(f, tech)
            if (kw <= 0L) return
            val made = service.addUnit(grid.id, tech, kw, f.owner, tick, f.name.ifBlank { type.name }).getOrNull() ?: return
            service.linkPlant(f.id, made.id)
        } else if (!f.isOperating()) {
            if (unit.status == UnitStatus.AVAILABLE || unit.status == UnitStatus.MAINTENANCE) service.mothball(unit.id)
        } else if (unit.status == UnitStatus.MOTHBALLED) {
            service.restart(unit.id, tick)
        }
        for (line in f.lines) {
            if (!line.enabled) continue
            val makesPower = line.recipe?.let { e.content.recipeOrNull(it) }?.outputs?.any { it.good == e.electricity } == true
            if (makesPower) e.production.setLineEnabled(f.owner, f.id, line.id, false)
        }
    }

    /** Nameplate kilowatts of a plant: what its power lines would make, scaled up for sources that seldom run at full power. */
    private fun plantKw(f: Facility, tech: Technology): Long {
        var perDay = 0L
        for (line in f.lines) {
            val recipe = line.recipe?.let { e.content.recipeOrNull(it) } ?: continue
            val out = recipe.outputs.firstOrNull { it.good == e.electricity } ?: continue
            perDay += out.quantity.raw / recipe.durationTicks
        }
        val average = perDay / GridUnits.HOURS_PER_DAY
        return when (tech) {
            Technology.WIND, Technology.SOLAR, Technology.HYDRO -> mulDiv(average, 1_000_000L, InvestmentPlanner.expectedLoadFactorPpm(tech))
            else -> average
        }
    }

    // ---- settlement ----------------------------------------------------------------------------

    /**
     * The day's fuel and capacity payments, plus on the last day of each month the bills, pool payments, capacity payments
     * and agreement settlements of the month. Households pay from the households' pool, the one the economy spends from.
     */
    fun settlementIntents(tick: Tick): List<SettlementIntent> {
        if (!active) return emptyList()
        val out = ArrayList<SettlementIntent>(dayIntents)
        dayIntents = emptyList()
        val month = tick.value / Calendar.DAYS_PER_MONTH
        if (tick.value % Calendar.DAYS_PER_MONTH == Calendar.DAYS_PER_MONTH - 1L && month > state.lastBilledMonth) {
            out += service.settleMonth(tick).intents.map { toPool(it) }
        }
        return out
    }

    private fun toPool(i: SettlementIntent): SettlementIntent {
        val payer = if (i.payer.kind == OwnerKind.HOUSEHOLDS) e.finance.households else i.payer
        val payee = if (i.payee.kind == OwnerKind.HOUSEHOLDS) e.finance.households else i.payee
        return if (payer == i.payer && payee == i.payee) i else i.copy(payer = payer, payee = payee)
    }

    /** Settles the payments a decision made at once, for computer-run utilities. */
    fun payNow(intents: List<SettlementIntent>, tick: Tick): Boolean {
        if (intents.isEmpty()) return true
        val results = e.finance.settlement.settle(tick, intents)
        return intents.all { k -> results.any { it.intent.key == k.key && (it.status == SettlementStatus.PAID || it.status == SettlementStatus.DEFERRED) } }
    }

    // ---- leaving -------------------------------------------------------------------------------

    /** A company that has ceased to exist takes its plant off the grids and ends its agreements. */
    fun forget(company: CompanyId, tick: Tick) {
        if (!active) return
        for (g in state.grids) {
            for (u in g.units) if (u.owner == company && u.status != UnitStatus.RETIRED) service.retireUnit(u.id)
            for (l in g.loads) if (l.company == company) l.facility?.let { service.disconnectFacility(it) }
        }
        for (p in state.ppasOfCompany(company)) {
            when {
                p.status == PpaStatus.OFFERED -> service.declinePpa(p.id, company, tick)
                p.isActive -> service.terminatePpa(p.id, company, tick)
            }
        }
    }

    companion object {
        /** Households draw this many kilowatts for every thousand people, on average over the day. */
        const val HOUSEHOLD_KW_PER_THOUSAND = 250L

        /** Installed public plant for each kilowatt of household draw, in ppm: room for industry, the evening peak and the reserve. */
        const val INSTALLED_PER_HOUSEHOLD_PPM = 4_500_000L

        private const val PUBLIC = -1L
        private const val MAX_KW = 5_000_000L
        private const val MIN_SEED_KW = 1_000L
        private const val MAX_SEED_UNIT_KW = 250_000L
        private const val MAX_SEED_UNITS = 6
        private const val LINK_SHARE = 4L
        private const val MIN_LINK_KW = 10_000L
        private const val LINK_LOSS_PPM = 30_000L

        /** Megawatt-hours of heat in one unit of each fuel, times a thousand, worked out from the burn recipes of the energy pack. */
        private const val COAL_HEAT_MILLI_MWH = 5_846L
        private const val GAS_HEAT_MILLI_MWH = 2_923L
        private const val URANIUM_HEAT_MILLI_MWH = 2_865_700L

        private val PLAIN_MIX: List<Pair<Technology, Long>> = listOf(
            Technology.COAL to 250_000L, Technology.GAS to 350_000L, Technology.NUCLEAR to 100_000L, Technology.WIND to 190_000L, Technology.SOLAR to 110_000L,
        )
        private val HYDRO_MIX: List<Pair<Technology, Long>> = listOf(
            Technology.COAL to 150_000L, Technology.GAS to 300_000L, Technology.NUCLEAR to 100_000L, Technology.HYDRO to 150_000L,
            Technology.WIND to 190_000L, Technology.SOLAR to 110_000L,
        )

        /** The technology a power plant facility type stands for; types with no counterpart are treated as the closest one. */
        fun technologyOf(typeKey: String): Technology? = when (typeKey) {
            "coal_power_plant", "biomass_plant" -> Technology.COAL
            "gas_power_plant", "oil_power_plant" -> Technology.GAS
            "nuclear_plant" -> Technology.NUCLEAR
            "wind_farm" -> Technology.WIND
            "solar_farm" -> Technology.SOLAR
            "hydro_plant", "geothermal_plant" -> Technology.HYDRO
            else -> null
        }
    }
}
