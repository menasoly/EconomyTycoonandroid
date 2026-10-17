package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.world.ClimateZone

/** The rules a grid runs by. They are set by the regulator and by the operator's policy, not by the weather. */
data class GridParams(
    val priceCap: Money = Money.units(500),
    val priceFloor: Money = Money.ZERO,
    /** Spare capacity under this share of load lifts the price towards the cap. */
    val scarcityThresholdPpm: Long = 100_000L,
    /** What a megawatt-hour not served is worth, for blackout reports. */
    val valueOfLostLoad: Money = Money.units(2000),
    /** Firm capacity wanted above the expected peak. */
    val targetReservePpm: Long = 150_000L,
    /** Expected growth of the peak over a year. */
    val growthPpm: Long = 30_000L,
    /** Bits of [Technology.ordinal] for the technologies the operator may build. */
    val buildableMask: Int = DEFAULT_BUILDABLE,
    /** Whether the operator builds and retires units on its own. */
    val operatorPlans: Boolean = true,
    /** Whether the government makes the retailer whole for a household price cap. */
    val compensateCap: Boolean = true,
) {
    init {
        require(priceCap > priceFloor) { "the cap must be above the floor" }
        require(scarcityThresholdPpm in 0L..500_000L) { "scarcity threshold out of range" }
        require(valueOfLostLoad.raw >= 0L) { "value of lost load cannot be negative" }
    }

    fun dispatch(): DispatchParams = DispatchParams(priceCap, priceFloor, scarcityThresholdPpm)

    fun buildable(): Set<Technology> = Technology.values().filter { (buildableMask shr it.ordinal) and 1 == 1 }.toSet()

    fun withBuildable(techs: Set<Technology>): GridParams = copy(buildableMask = techs.fold(0) { m, t -> m or (1 shl t.ordinal) })

    fun hashInto(h: CanonicalHasher) {
        h.putMoney(priceCap).putMoney(priceFloor).putLong(scarcityThresholdPpm).putMoney(valueOfLostLoad).putLong(targetReservePpm).putLong(growthPpm)
        h.putInt(buildableMask).putBoolean(operatorPlans).putBoolean(compensateCap)
    }

    companion object {
        /** Coal, gas, nuclear, wind and solar; hydro needs a site and storage is ordered on price spreads. */
        val DEFAULT_BUILDABLE: Int = listOf(Technology.COAL, Technology.GAS, Technology.NUCLEAR, Technology.WIND, Technology.SOLAR).fold(0) { m, t -> m or (1 shl t.ordinal) }
    }
}

/**
 * One regional electricity grid with its units, loads, tariffs, history and records. A grid's money moves through its
 * own system account, so the pool and the retailer settle against one balance that the wiring can watch.
 */
class Grid(
    val id: GridId,
    val region: RegionId,
    val country: CountryId,
    val name: String,
    val climate: ClimateZone,
    params: GridParams = GridParams(),
) {
    var params: GridParams = params
        internal set
    var tariff: TariffSchedule = TariffSchedule()
        internal set

    internal val unitMap = java.util.TreeMap<UnitId, GenUnit>()
    internal val loadMap = java.util.TreeMap<LoadId, GridLoad>()
    internal val historyList = ArrayList<DaySummary>()
    internal val blackoutList = ArrayList<BlackoutRecord>()
    internal val outageList = ArrayList<OutageEvent>()

    /** Yesterday's wind, carried into today's weather. */
    var windCarryPpm: Long = 300_000L
        internal set
    var lastDay: List<HourProfile> = emptyList()
        internal set
    var capacityRound: CapacityRound? = null
        internal set

    /** Capacity levy per megawatt-hour that the last auction implies. */
    var capacityLevy: Money = Money.ZERO
        internal set

    /** Money the retailer gave up because of the household cap and has not been compensated for. */
    var capShortfall: Money = Money.ZERO
        internal set
    var lastPlanTick: Long = -1L
        internal set

    /** Energy sold in the pool and billed since the last bill, for the month's settlement. */
    var monthDemandKwh: Long = 0L
        internal set
    var lifetimeUnservedKwh: Long = 0L
        internal set

    /** The account that holds the pool and the retailer's money. */
    val systemOwner: LedgerOwner get() = LedgerOwner(app.many.economytycoon.finance.OwnerKind.SYSTEM, id.value)
    val householdOwner: LedgerOwner get() = LedgerOwner.households(region.value)

    val units: List<GenUnit> get() = unitMap.values.toList()
    val loads: List<GridLoad> get() = loadMap.values.toList()
    val history: List<DaySummary> get() = historyList.toList()
    val blackouts: List<BlackoutRecord> get() = blackoutList.toList()
    val outages: List<OutageEvent> get() = outageList.toList()

    fun unit(id: UnitId): GenUnit? = unitMap[id]
    fun load(id: LoadId): GridLoad? = loadMap[id]
    fun liveUnits(): List<GenUnit> = unitMap.values.filter { it.isLive }
    fun unitsOf(tech: Technology): List<GenUnit> = unitMap.values.filter { it.tech == tech && it.isLive }
    fun openBlackout(): BlackoutRecord? = blackoutList.lastOrNull()?.takeIf { it.open }

    /** Nameplate capacity of units that are in service or temporarily out, in kilowatts. */
    fun installedKw(): Long = unitMap.values.filter { it.isLive && it.status != UnitStatus.MOTHBALLED }.sumOf { it.capacityKw }

    /** Firm capacity of the same units. Storage counts at its credit like any other. */
    fun firmKw(): Long = unitMap.values.filter { it.isLive && it.status != UnitStatus.MOTHBALLED }.sumOf { CapacityMarket.firmKw(it.tech, it.capacityKw) }

    fun firmUnderConstructionKw(): Long = unitMap.values.filter { it.status == UnitStatus.BUILDING }.sumOf { CapacityMarket.firmKw(it.tech, it.capacityKw) }

    /** Connected base draw of every load, in kilowatts. */
    fun connectedBaseKw(): Long = loadMap.values.filter { it.connected }.sumOf { it.baseKw }

    internal fun trimRecords() {
        if (historyList.size > MAX_HISTORY) historyList.subList(0, historyList.size - MAX_HISTORY).clear()
        if (blackoutList.size > MAX_BLACKOUTS) blackoutList.subList(0, blackoutList.size - MAX_BLACKOUTS).clear()
        if (outageList.size > MAX_OUTAGES) outageList.subList(0, outageList.size - MAX_OUTAGES).clear()
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(region.value).putLong(country.value).putString(name).putInt(climate.ordinal)
        params.hashInto(h)
        tariff.hashInto(h)
        h.putLong(windCarryPpm).putMoney(capacityLevy).putMoney(capShortfall).putLong(lastPlanTick).putLong(monthDemandKwh).putLong(lifetimeUnservedKwh)
        h.putInt(unitMap.size); for (u in unitMap.values) u.hashInto(h)
        h.putInt(loadMap.size); for (l in loadMap.values) l.hashInto(h)
        h.putInt(historyList.size); for (d in historyList) d.hashInto(h)
        h.putInt(lastDay.size); for (p in lastDay) p.hashInto(h)
        h.putInt(blackoutList.size); for (b in blackoutList) b.hashInto(h)
        h.putInt(outageList.size)
        for (o in outageList) h.putLong(o.unit.value).putInt(o.kind.ordinal).putLong(o.start).putLong(o.until)
        h.putBoolean(capacityRound != null); capacityRound?.hashInto(h)
    }

    companion object {
        const val MAX_HISTORY = 720
        const val MAX_BLACKOUTS = 100
        const val MAX_OUTAGES = 200
    }
}
