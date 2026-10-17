package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority
import app.many.economytycoon.world.ClimateZone

/** The order for a new unit: what it costs and the payment to make for it. */
data class UnitOrder(val unit: GenUnit, val cost: Money, val payment: SettlementIntent)

/** Limits on what one company can have in play. */
data class GridLimits(
    val maxPpasPerCompany: Int = 8,
    val minPpaDays: Int = 30,
    val maxPpaDays: Int = 1_800,
    val offerLapseDays: Int = 30,
    /** Share of the strike value of the remaining energy paid to leave a PPA early. */
    val ppaExitPpm: Long = 100_000L,
    val maxUnitsPerGrid: Int = 60,
    val maxLoadsPerGrid: Int = 400,
    /** A new unit's maintenance clock starts staggered across this share of its interval. */
    val staggerSharePpm: Long = 500_000L,
)

/**
 * The owner of the energy grid state. It builds and connects things, runs the daily dispatch through [GridDayRunner],
 * and produces the ledger intents for bills and agreements. It never posts to the ledger: money leaves this module
 * only as [SettlementIntent]s for the settlement engine to turn into entries.
 */
class EnergyGridService(val state: EnergyGridState = EnergyGridState(), val limits: GridLimits = GridLimits()) {

    // ----- grids -----

    fun addGrid(region: RegionId, country: CountryId, name: String, climate: ClimateZone, params: GridParams = GridParams()): Outcome<Grid> {
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a grid needs a name")
        if (state.gridOfRegion(region) != null) return Outcome.reject(Rules.DUPLICATE, "that region already has a grid")
        val id = state.counters.grid()
        val g = Grid(id, region, country, name.trim(), climate, params)
        state.gridMap[id] = g
        return Outcome.ok(g)
    }

    fun setParams(grid: GridId, params: GridParams): Outcome<Grid> {
        val g = state.grid(grid) ?: return unknownGrid()
        g.params = params
        return Outcome.ok(g)
    }

    fun setTariff(grid: GridId, schedule: TariffSchedule): Outcome<Grid> {
        val g = state.grid(grid) ?: return unknownGrid()
        g.tariff = schedule
        return Outcome.ok(g)
    }

    /** Caps what households pay all in per megawatt-hour, or lifts the cap with null. */
    fun setHouseholdCap(grid: GridId, cap: Money?): Outcome<Grid> {
        val g = state.grid(grid) ?: return unknownGrid()
        if (cap != null && cap.raw <= 0L) return Outcome.reject(Rules.INVALID_PRICE, "a price cap must be positive")
        g.tariff = g.tariff.copy(householdCapPerMwh = cap)
        return Outcome.ok(g)
    }

    // ----- units -----

    private fun nameFor(tech: Technology, id: UnitId) = "${tech.label} plant ${id.value}"

    /** Adds a unit that is already built, as at the start of a game. */
    fun addUnit(grid: GridId, tech: Technology, capacityKw: Long, owner: CompanyId?, tick: Tick, name: String? = null): Outcome<GenUnit> {
        val g = state.grid(grid) ?: return unknownGrid()
        if (capacityKw <= 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "a unit needs capacity")
        if (g.unitMap.values.count { it.status != UnitStatus.RETIRED } >= limits.maxUnitsPerGrid) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many units on this grid")
        val id = state.counters.unit()
        val u = GenUnit(id, grid, tech, name?.takeIf { it.isNotBlank() } ?: nameFor(tech, id), capacityKw, owner)
        u.commissioned = tick.value
        u.lastMaintenance = tick.value - stagger(tech, id)
        g.unitMap[id] = u
        return Outcome.ok(u)
    }

    private fun stagger(tech: Technology, id: UnitId): Long {
        val span = tech.profile.maintenanceEveryTicks * limits.staggerSharePpm / 1_000_000L
        return if (span <= 0L) 0L else (id.value * 37L) % span
    }

    /** Orders a new unit. It comes into service after the technology's build time, and the cost is returned as a payment to make. */
    fun orderUnit(grid: GridId, tech: Technology, capacityKw: Long, owner: CompanyId?, tick: Tick, name: String? = null): Outcome<UnitOrder> {
        val g = state.grid(grid) ?: return unknownGrid()
        if (capacityKw <= 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "a unit needs capacity")
        if (g.unitMap.values.count { it.status != UnitStatus.RETIRED } >= limits.maxUnitsPerGrid) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many units on this grid")
        val id = state.counters.unit()
        val u = GenUnit(id, grid, tech, name?.takeIf { it.isNotBlank() } ?: nameFor(tech, id), capacityKw, owner)
        u.status = UnitStatus.BUILDING
        u.statusUntil = tick.value + tech.profile.buildTicks
        u.commissioned = u.statusUntil
        g.unitMap[id] = u
        val cost = Money(tech.profile.capexPerKw.raw * capacityKw)
        val payer = if (owner != null) LedgerOwner.company(owner) else LedgerOwner.government(g.country)
        val intent = SettlementIntent.simple(
            "energy:build:${id.value}", payer, g.systemOwner, cost, SettlementPriority.SUPPLIERS, ReasonCode.PURCHASE,
            Chart.FIXED_ASSETS, Chart.REVENUE_SERVICES, deferrable = true, memo = "${tech.label} construction",
        )
        return Outcome.ok(UnitOrder(u, cost, intent))
    }

    fun retireUnit(unit: UnitId): Outcome<GenUnit> {
        val u = state.unit(unit) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown unit")
        if (u.status == UnitStatus.RETIRED) return Outcome.reject(Rules.WRONG_STATE, "that unit is already retired")
        u.status = UnitStatus.RETIRED
        u.statusUntil = 0L
        u.outputKw = 0L
        return Outcome.ok(u)
    }

    fun mothball(unit: UnitId): Outcome<GenUnit> {
        val u = state.unit(unit) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown unit")
        if (u.status != UnitStatus.AVAILABLE && u.status != UnitStatus.MAINTENANCE) return Outcome.reject(Rules.WRONG_STATE, "only a working unit can be mothballed")
        u.status = UnitStatus.MOTHBALLED
        u.statusUntil = 0L
        u.outputKw = 0L
        return Outcome.ok(u)
    }

    /** Brings a mothballed unit back; it spends two weeks being made ready. */
    fun restart(unit: UnitId, tick: Tick): Outcome<GenUnit> {
        val u = state.unit(unit) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown unit")
        if (u.status != UnitStatus.MOTHBALLED) return Outcome.reject(Rules.WRONG_STATE, "that unit is not mothballed")
        u.status = UnitStatus.MAINTENANCE
        u.statusUntil = tick.value + RESTART_DAYS
        return Outcome.ok(u)
    }

    // ----- loads -----

    fun connectFacility(grid: GridId, customer: CustomerClass, company: CompanyId, facility: FacilityId, baseKw: Long): Outcome<GridLoad> {
        val g = state.grid(grid) ?: return unknownGrid()
        if (customer == CustomerClass.HOUSEHOLD) return Outcome.reject(Rules.INVALID_ARGUMENT, "facilities are not households")
        if (baseKw < 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "draw cannot be negative")
        if (state.gridMap.values.any { x -> x.loadMap.values.any { it.facility == facility } }) return Outcome.reject(Rules.DUPLICATE, "that facility is already connected")
        if (g.loadMap.size >= limits.maxLoadsPerGrid) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many connections on this grid")
        val l = GridLoad(state.counters.load(), grid, LoadKind.FACILITY, customer, company, facility)
        l.baseKw = baseKw
        g.loadMap[l.id] = l
        return Outcome.ok(l)
    }

    /** Sets the draw of all households on a grid, creating their connection on first use. */
    fun setHouseholdKw(grid: GridId, baseKw: Long): Outcome<GridLoad> {
        val g = state.grid(grid) ?: return unknownGrid()
        if (baseKw < 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "draw cannot be negative")
        val existing = g.loadMap.values.firstOrNull { it.kind == LoadKind.HOUSEHOLDS }
        val l = existing ?: GridLoad(state.counters.load(), grid, LoadKind.HOUSEHOLDS, CustomerClass.HOUSEHOLD, null, null).also { g.loadMap[it.id] = it }
        l.baseKw = baseKw
        return Outcome.ok(l)
    }

    fun setLoadKw(load: LoadId, baseKw: Long): Outcome<GridLoad> {
        val l = findLoad(load) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown connection")
        if (baseKw < 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "draw cannot be negative")
        l.baseKw = baseKw
        return Outcome.ok(l)
    }

    /** Changes the contract class of a facility, for example to an interruptible tariff. */
    fun reclassify(load: LoadId, customer: CustomerClass): Outcome<GridLoad> {
        val g = state.gridMap.values.firstOrNull { it.loadMap.containsKey(load) } ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown connection")
        val old = g.loadMap.getValue(load)
        if (old.kind != LoadKind.FACILITY || customer == CustomerClass.HOUSEHOLD) return Outcome.reject(Rules.INVALID_ARGUMENT, "only facilities can change class")
        val replacement = GridLoad(old.id, old.grid, old.kind, customer, old.company, old.facility)
        replacement.restore(old.baseKw, old.connected, old.monthServedKwh, old.monthShedKwh, old.lifetimeShedKwh, old.shedHoursToday)
        g.loadMap[load] = replacement
        return Outcome.ok(replacement)
    }

    fun setConnected(load: LoadId, connected: Boolean): Outcome<GridLoad> {
        val l = findLoad(load) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown connection")
        l.connected = connected
        return Outcome.ok(l)
    }

    /** Removes a facility's connection, for example when it closes. */
    fun disconnectFacility(facility: FacilityId): Outcome<Unit> {
        for (g in state.gridMap.values) {
            val l = g.loadMap.values.firstOrNull { it.facility == facility } ?: continue
            g.loadMap.remove(l.id)
            return Outcome.ok(Unit)
        }
        return Outcome.reject(Rules.UNKNOWN_ENTITY, "that facility is not connected")
    }

    fun loadOfFacility(facility: FacilityId): GridLoad? = state.gridMap.values.firstNotNullOfOrNull { g -> g.loadMap.values.firstOrNull { it.facility == facility } }

    private fun findLoad(id: LoadId): GridLoad? = state.gridMap.values.firstNotNullOfOrNull { it.loadMap[id] }

    // ----- interconnectors -----

    fun addLink(a: GridId, b: GridId, capacityKw: Long, lossPpm: Long, wheelingPerMwh: Money): Outcome<Interconnector> {
        if (a == b) return Outcome.reject(Rules.INVALID_ARGUMENT, "a link joins two different grids")
        if (state.grid(a) == null || state.grid(b) == null) return unknownGrid()
        if (capacityKw <= 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "a link needs capacity")
        if (lossPpm !in 0L..500_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "losses must be between 0 and 50%")
        if (wheelingPerMwh.raw < 0L) return Outcome.reject(Rules.INVALID_PRICE, "a charge cannot be negative")
        if (state.linkMap.values.any { (it.a == a && it.b == b) || (it.a == b && it.b == a) }) return Outcome.reject(Rules.DUPLICATE, "those grids are already linked")
        val l = Interconnector(state.counters.link(), a, b, capacityKw, lossPpm, wheelingPerMwh)
        state.linkMap[l.id] = l
        return Outcome.ok(l)
    }

    fun upgradeLink(link: LinkId, extraKw: Long): Outcome<Interconnector> {
        val l = state.link(link) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown link")
        if (extraKw <= 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "an upgrade adds capacity")
        l.capacityKw += extraKw
        return Outcome.ok(l)
    }

    fun setLinkInService(link: LinkId, inService: Boolean): Outcome<Interconnector> {
        val l = state.link(link) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown link")
        l.inService = inService
        return Outcome.ok(l)
    }

    // ----- power purchase agreements -----

    fun offerPpa(
        grid: GridId, seller: CompanyId, buyer: CompanyId, profile: PpaProfile, volumeKw: Long, sharePpm: Long, unit: UnitId?,
        strikePerMwh: Money, startsAt: Tick, durationDays: Int, penaltyPpm: Long, tick: Tick,
    ): Outcome<Ppa> {
        val g = state.grid(grid) ?: return unknownGrid()
        if (seller == buyer) return Outcome.reject(Rules.INVALID_ARGUMENT, "a company cannot contract with itself")
        if (strikePerMwh.raw <= 0L) return Outcome.reject(Rules.INVALID_PRICE, "the strike price must be positive")
        if (durationDays !in limits.minPpaDays..limits.maxPpaDays) return Outcome.reject(Rules.INVALID_ARGUMENT, "an agreement runs between ${limits.minPpaDays} and ${limits.maxPpaDays} days")
        if (startsAt < tick) return Outcome.reject(Rules.INVALID_ARGUMENT, "an agreement cannot start in the past")
        if (penaltyPpm !in 0L..2_000_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "penalty out of range")
        val owned = g.unitMap.values.filter { it.owner == seller && it.isLive }
        if (owned.isEmpty()) return Outcome.reject(Rules.NOT_OWNER, "the seller has no generation on this grid")
        if (g.loadMap.values.none { it.company == buyer && it.connected }) return Outcome.reject(Rules.PREREQUISITE_MISSING, "the buyer has no load on this grid")
        when (profile) {
            PpaProfile.BASELOAD -> {
                if (volumeKw <= 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "a baseload agreement needs a volume")
                if (volumeKw > owned.sumOf { it.capacityKw }) return Outcome.reject(Rules.INSUFFICIENT_CAPACITY, "the seller cannot promise more than it has built")
            }
            PpaProfile.AS_PRODUCED -> {
                val u = unit?.let { g.unit(it) } ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "name the unit the agreement follows")
                if (u.owner != seller || !u.isLive) return Outcome.reject(Rules.NOT_OWNER, "the seller does not own that unit")
                if (sharePpm !in 1L..1_000_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "the share must be between 0 and 100%")
            }
        }
        if (state.ppaMap.values.count { (it.seller == seller || it.buyer == seller) && (it.status == PpaStatus.OFFERED || it.isActive) } >= limits.maxPpasPerCompany ||
            state.ppaMap.values.count { (it.seller == buyer || it.buyer == buyer) && (it.status == PpaStatus.OFFERED || it.isActive) } >= limits.maxPpasPerCompany
        ) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many agreements in play")
        val p = Ppa(
            state.counters.ppa(), grid, seller, buyer, profile, if (profile == PpaProfile.BASELOAD) volumeKw else 0L,
            if (profile == PpaProfile.AS_PRODUCED) sharePpm else 0L, if (profile == PpaProfile.AS_PRODUCED) unit else null,
            strikePerMwh, startsAt.value, startsAt.value + durationDays, penaltyPpm, tick.value,
        )
        state.ppaMap[p.id] = p
        return Outcome.ok(p)
    }

    fun acceptPpa(id: PpaId, by: CompanyId, tick: Tick): Outcome<Ppa> {
        val p = state.ppa(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown agreement")
        if (p.buyer != by) return Outcome.reject(Rules.NOT_OWNER, "only the buyer can accept")
        if (p.status != PpaStatus.OFFERED) return Outcome.reject(Rules.WRONG_STATE, "that agreement is not on offer")
        if (tick.value > p.startsAt || tick.value - p.offeredAt > limits.offerLapseDays) {
            p.status = PpaStatus.DECLINED; p.closedAt = tick.value
            return Outcome.reject(Rules.WRONG_STATE, "that offer has lapsed")
        }
        p.status = PpaStatus.ACTIVE
        return Outcome.ok(p)
    }

    /** The buyer declines, or the seller withdraws, an offer that has not been accepted. */
    fun declinePpa(id: PpaId, by: CompanyId, tick: Tick): Outcome<Ppa> {
        val p = state.ppa(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown agreement")
        if (p.buyer != by && p.seller != by) return Outcome.reject(Rules.NOT_OWNER, "you are not a party to that agreement")
        if (p.status != PpaStatus.OFFERED) return Outcome.reject(Rules.WRONG_STATE, "that agreement is not on offer")
        p.status = PpaStatus.DECLINED
        p.closedAt = tick.value
        return Outcome.ok(p)
    }

    /** Ends an active agreement early. The party that leaves pays the exit fee to the other; the intent for it is returned. */
    fun terminatePpa(id: PpaId, by: CompanyId, tick: Tick): Outcome<SettlementIntent?> {
        val p = state.ppa(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown agreement")
        if (p.buyer != by && p.seller != by) return Outcome.reject(Rules.NOT_OWNER, "you are not a party to that agreement")
        if (!p.isActive) return Outcome.reject(Rules.WRONG_STATE, "that agreement is not active")
        val fee = p.exitFee(tick.value, limits.ppaExitPpm)
        p.status = PpaStatus.TERMINATED
        p.closedAt = tick.value
        if (fee.raw <= 0L) return Outcome.ok(null)
        val other = if (by == p.seller) p.buyer else p.seller
        val intent = SettlementIntent.simple(
            "energy:ppa:exit:${p.id.value}", LedgerOwner.company(by), LedgerOwner.company(other), fee, SettlementPriority.OTHER, ReasonCode.FEE,
            Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "early exit from power purchase agreement ${p.id.value}",
        )
        return Outcome.ok(intent)
    }

    // ----- running -----

    /**
     * Runs one day on every grid: weather, outages and maintenance, demand, storage planning, the hourly merit-order
     * dispatch with trade over the links, shedding and blackouts, agreements, the capacity auction and the operator's
     * investment round. [tick] must be later than the last day run.
     */
    fun advanceDay(tick: Tick, inputs: DayInputs, random: RandomProvider): DayReport {
        require(tick.value > state.lastTick) { "day ${tick.value} has already been run" }
        val report = GridDayRunner(this, state, limits).run(tick, inputs, random)
        state.lastTick = tick.value
        return report
    }

    /**
     * Closes the month: builds the bills, pool payments, capacity payments and agreement settlements as intents, then
     * resets the monthly counters. Call it once at each month start, after that day has been simulated.
     */
    fun settleMonth(tick: Tick): BillingRun {
        val month = tick.value / Calendar.DAYS_PER_MONTH
        require(month > state.lastBilledMonth) { "month $month has already been billed" }
        val run = GridBilling.run(state, tick)
        state.lastBilledMonth = month
        return run
    }

    fun peakDemandKw(grid: GridId, days: Int = 90): Long {
        val g = state.grid(grid) ?: return 0L
        val recent = g.historyList.takeLast(days)
        return if (recent.isEmpty()) g.connectedBaseKw() * 3L / 2L else recent.maxOf { it.peakDemandKw }
    }

    private fun <T> unknownGrid(): Outcome<T> = Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown grid")

    companion object {
        const val RESTART_DAYS = 14L
    }
}
