package app.many.economytycoon.session

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.WarehouseId
import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.logistics.FreightQuote
import app.many.economytycoon.logistics.ShipmentStatus
import app.many.economytycoon.logistics.StockRef
import app.many.economytycoon.logistics.VehicleStatus
import app.many.economytycoon.simulation.Economy

data class ShipmentRow(
    val id: Long,
    val good: String,
    val quantity: Quantity,
    val from: String,
    val to: String,
    val status: ShipmentStatus,
    val arrivesIn: Long,
    val freight: Money,
    val outbound: Boolean,
    val modes: List<TransportMode>,
)

data class VehicleRow(
    val id: Long,
    val typeName: String,
    val mode: TransportMode,
    val capacity: Quantity,
    val status: VehicleStatus,
    val location: String?,
    val conditionPpm: Long,
    val trips: Long,
    val distance: Long,
)

data class WarehouseRow(
    val id: WarehouseId,
    val city: String,
    val capacity: Quantity,
    val used: Quantity,
    val stockValue: Money,
    val rentPerTick: Money,
    val unpaidTicks: Int,
)

data class VehicleOption(val type: app.many.economytycoon.core.VehicleTypeId, val name: String, val mode: TransportMode, val capacity: Quantity, val speed: Int, val cost: Money, val locked: Boolean, val techName: String?)

/** Freight, fleet and warehouse views. */
class LogisticsReports(private val e: Economy) {

    fun shipments(company: CompanyId, includeFinished: Boolean = false): List<ShipmentRow> =
        e.logistics.shipmentsOf(company).filter { includeFinished || it.isActive }.sortedByDescending { it.id.value }.map { s ->
            ShipmentRow(
                id = s.id.value, good = e.content.good(s.good).name, quantity = s.carriedQuantity,
                from = e.world.city(s.fromCity).name, to = e.world.city(s.toCity).name, status = s.status,
                arrivesIn = (s.arrival - e.tick.value).coerceAtLeast(0L), freight = s.freight + s.tariff,
                outbound = s.shipper == company, modes = s.modes,
            )
        }

    fun fleet(company: CompanyId): List<VehicleRow> =
        e.logistics.vehiclesOf(company).sortedBy { it.id }.map { v ->
            val def = e.content.vehicleType(v.type)
            VehicleRow(v.id.value, def.name, def.mode, def.capacity, v.status, v.location?.let { e.world.city(it).name }, v.conditionPpm, v.tripsMade, v.distanceTravelled)
        }

    fun warehouses(company: CompanyId): List<WarehouseRow> =
        e.logistics.warehousesOf(company).sortedBy { it.id }.map { w ->
            WarehouseRow(w.id, e.world.city(w.city).name, w.inventory.capacity, w.inventory.used(), w.inventory.totalValue(), w.rentPerTick, w.unpaidTicks)
        }

    fun vehicleOptions(company: CompanyId): List<VehicleOption> =
        e.content.allVehicleTypes.map { d ->
            val locked = d.requiredTech != null && !e.research.isUnlocked(company, d.requiredTech!!)
            VehicleOption(d.id, d.name, d.mode, d.capacity, d.speed, d.cost, locked, d.requiredTech?.let { e.content.technologyOrNull(it)?.name })
        }.sortedWith(compareBy({ it.locked }, { it.cost.raw }))

    /** Cost and time to move [quantity] of a good between two cities, or null when no route exists. */
    fun quote(from: CityId, to: CityId, good: GoodId, quantity: Quantity): FreightQuote? =
        e.logistics.quote(from, to, good, quantity, e.tick)

    /** Where stock can be shipped from or to: the company's own facilities and warehouses. */
    fun stockPoints(company: CompanyId): List<StockPoint> {
        val facilities = e.production.facilitiesOf(company).map { f ->
            StockPoint(StockRef.facility(f.id), f.name.ifBlank { e.content.facilityTypeOrNull(f.type)?.name ?: "Facility ${f.id.value}" }, f.city ?: e.world.citiesIn(f.region).firstOrNull()?.id)
        }
        val stores = e.logistics.warehousesOf(company).map { w -> StockPoint(StockRef.warehouse(w.id), "Warehouse in ${e.world.city(w.city).name}", w.city) }
        return (facilities + stores).sortedBy { it.ref }
    }
}

data class StockPoint(val ref: StockRef, val name: String, val city: CityId?)
