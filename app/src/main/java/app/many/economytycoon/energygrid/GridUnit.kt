package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money

/** Where a unit stands. Only [AVAILABLE] units can produce. */
enum class UnitStatus(val label: String, val canProduce: Boolean) {
    BUILDING("Under construction", false),
    AVAILABLE("Available", true),
    MAINTENANCE("In maintenance", false),
    FORCED_OUTAGE("Forced outage", false),
    MOTHBALLED("Mothballed", false),
    RETIRED("Retired", false),
}

/**
 * One generating or storage unit. The owner is a company, or null for a unit the grid operator owns on behalf of the
 * public. Everything that changes lives here and is only changed by [EnergyGridService].
 */
class GenUnit(
    val id: UnitId,
    val grid: GridId,
    val tech: Technology,
    val name: String,
    capacityKw: Long,
    val owner: CompanyId?,
) {
    init { require(capacityKw > 0L) { "a unit needs capacity" } }

    var capacityKw: Long = capacityKw
        internal set
    var status: UnitStatus = UnitStatus.AVAILABLE
        internal set

    /** Tick at which the current non-available status ends; construction finishes then. Zero when it has no end. */
    var statusUntil: Long = 0L
        internal set

    /** Tick the unit entered service, or will. */
    var commissioned: Long = 0L
        internal set
    var lastMaintenance: Long = 0L
        internal set

    /** How many times in a row maintenance has been postponed because the grid was tight. */
    var deferrals: Int = 0
        internal set

    /** Output of the last dispatched hour, which the next day's ramp limits start from. */
    var outputKw: Long = 0L
        internal set
    var efficiencyPpm: Long = tech.profile.efficiencyPpm
        internal set

    /** Energy held, for storage. */
    var socKwh: Long = 0L
        internal set

    /** Energy sold since the last settlement, and what the pool owes for it. */
    var soldKwh: Long = 0L
        internal set
    var poolRevenue: Money = Money.ZERO
        internal set
    var lifetimeKwh: Long = 0L
        internal set
    var outageCount: Int = 0
        internal set

    val storageCapacityKwh: Long get() = capacityKw * tech.profile.storageHours
    val isBuilt: Boolean get() = status != UnitStatus.BUILDING
    val isLive: Boolean get() = status != UnitStatus.RETIRED && status != UnitStatus.BUILDING

    fun ageAt(tick: Long): Long = if (status == UnitStatus.BUILDING) 0L else (tick - commissioned).coerceAtLeast(0L)

    /** Running cost per megawatt-hour at these fuel prices. */
    fun marginalCost(prices: FuelPrices): Money = tech.marginalCost(prices, efficiencyPpm)

    internal fun restore(
        status: UnitStatus, statusUntil: Long, commissioned: Long, lastMaintenance: Long, deferrals: Int, outputKw: Long,
        efficiencyPpm: Long, socKwh: Long, soldKwh: Long, poolRevenue: Money, lifetimeKwh: Long, outageCount: Int, capacityKw: Long,
    ) {
        this.status = status; this.statusUntil = statusUntil; this.commissioned = commissioned
        this.lastMaintenance = lastMaintenance; this.deferrals = deferrals; this.outputKw = outputKw
        this.efficiencyPpm = efficiencyPpm; this.socKwh = socKwh; this.soldKwh = soldKwh; this.poolRevenue = poolRevenue
        this.lifetimeKwh = lifetimeKwh; this.outageCount = outageCount; this.capacityKw = capacityKw
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(grid.value).putInt(tech.ordinal).putString(name).putLong(capacityKw).putLong(owner?.value ?: -1L)
        h.putInt(status.ordinal).putLong(statusUntil).putLong(commissioned).putLong(lastMaintenance).putInt(deferrals)
        h.putLong(outputKw).putLong(efficiencyPpm).putLong(socKwh).putLong(soldKwh).putMoney(poolRevenue).putLong(lifetimeKwh).putInt(outageCount)
    }
}
