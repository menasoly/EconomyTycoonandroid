package app.many.economytycoon.logistics

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.WarehouseId
import app.many.economytycoon.production.Inventory

enum class StockKind { FACILITY, WAREHOUSE }

/** A place goods can be loaded from or delivered to. */
data class StockRef(val kind: StockKind, val id: Long) : Comparable<StockRef> {
    override fun compareTo(other: StockRef): Int = compareValuesBy(this, other, { it.kind.ordinal }, { it.id })

    companion object {
        fun facility(id: app.many.economytycoon.core.FacilityId) = StockRef(StockKind.FACILITY, id.value)
        fun warehouse(id: WarehouseId) = StockRef(StockKind.WAREHOUSE, id.value)
    }
}

enum class WarehouseStatus { ACTIVE, EVICTED, CLOSED }

/** Leased public storage in a city. Rent is charged every tick; unpaid rent eventually costs the tenant its goods. */
class Warehouse(
    val id: WarehouseId,
    val owner: CompanyId,
    val city: CityId,
    val region: RegionId,
    val inventory: Inventory,
    val leased: Tick,
) {
    var rentPerTick: app.many.economytycoon.core.Money = app.many.economytycoon.core.Money.ZERO
        internal set
    var unpaidTicks: Int = 0
        internal set
    var status: WarehouseStatus = WarehouseStatus.ACTIVE
        internal set

    internal fun restore(rent: app.many.economytycoon.core.Money, unpaid: Int, status: WarehouseStatus) {
        rentPerTick = rent; unpaidTicks = unpaid; this.status = status
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(owner.value).putLong(city.value).putMoney(rentPerTick).putInt(unpaidTicks).putInt(status.ordinal)
        inventory.hashInto(h)
    }
}
