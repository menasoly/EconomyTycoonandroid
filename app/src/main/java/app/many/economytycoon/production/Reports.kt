package app.many.economytycoon.production

import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity

/** What one facility did in a tick. The simulation turns this into ledger postings and market stock updates. */
class FacilityReport(val facility: FacilityId) {
    var batches: Long = 0
    var defects: Long = 0
    val produced = java.util.TreeMap<GoodId, Quantity>()
    val consumed = java.util.TreeMap<GoodId, Quantity>()
    val wasteProduced = java.util.TreeMap<GoodId, Quantity>()
    val spoiled = java.util.TreeMap<GoodId, Quantity>()
    var producedValue: Money = Money.ZERO
    var consumedValue: Money = Money.ZERO
    var spoiledValue: Money = Money.ZERO
    var energyUsed: Quantity = Quantity.ZERO
    var upkeep: Money = Money.ZERO
    var machineMaintenance: Money = Money.ZERO
    var breakdowns: Int = 0
    var pollutionViolation: Boolean = false
    val blocked = java.util.TreeMap<LineId, Blocked>()

    /** Net change in inventory value caused by production this tick (what the ledger should book to INVENTORY). */
    val inventoryValueDelta: Money get() = producedValue - consumedValue - spoiledValue

    internal fun add(map: java.util.TreeMap<GoodId, Quantity>, good: GoodId, q: Quantity) {
        map[good] = (map[good] ?: Quantity.ZERO) + q
    }
}

class ProductionReport(val tick: app.many.economytycoon.core.Tick) {
    val facilities = ArrayList<FacilityReport>()
    val completedProjects = ArrayList<app.many.economytycoon.core.ProjectId>()
    val stalledProjects = ArrayList<app.many.economytycoon.core.ProjectId>()

    val inventoryValueDelta: Money get() = Money(facilities.sumOf { it.inventoryValueDelta.raw })
    val totalBatches: Long get() = facilities.sumOf { it.batches }
    val totalUpkeep: Money get() = Money(facilities.sumOf { it.upkeep.raw + it.machineMaintenance.raw })
}
