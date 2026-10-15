package app.many.economytycoon.logistics

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.ShipmentId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.VehicleId
import app.many.economytycoon.core.VehicleTypeId
import app.many.economytycoon.core.WarehouseId
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.production.Inventory
import app.many.economytycoon.production.Payer
import app.many.economytycoon.production.ProductionService
import app.many.economytycoon.world.WorldState

data class LogisticsParams(
    /** Chance per tick in transit that a shipment is lost, by mode, ppm. */
    val lossPpmRoad: Long = 400,
    val lossPpmRail: Long = 200,
    val lossPpmSea: Long = 900,
    val lossPpmAir: Long = 100,
    val maxWaitAtDestination: Int = 5,
    val customsTicks: Int = 1,
    /** Warehouse rent per unit of capacity per tick, scaled by the city's rent index. */
    val warehouseRentPerUnit: Money = Money(8),
    val maxUnpaidRentTicks: Int = 5,
    val vehicleWearPerDistancePpm: Long = 120,
    val vehicleRepairFraction: Long = 400_000,
    val vehicleResalePpm: Long = 600_000,
    val maxWarehouseCapacity: Quantity = Quantity.units(500_000),
)

object LogisticsMemo {
    const val FREIGHT = "freight"
    const val TARIFF = "tariff"
    const val RENT = "warehouse rent"
    const val VEHICLE = "vehicle"
    const val REPAIR = "vehicle repair"
    const val FLEET_RUNNING = "fleet running cost"
}

class ShipRequest(
    val shipper: CompanyId,
    val consignee: CompanyId,
    val from: StockRef,
    val to: StockRef,
    val good: GoodId,
    val quantity: Quantity,
    val invoiceValue: Money? = null,
    val modes: Set<TransportMode> = TransportMode.values().toSet(),
    val useFleet: Boolean = false,
    /** Who pays freight and tariffs; defaults to the shipper. */
    val payer: CompanyId = shipper,
)

class Delivery(val shipment: Shipment, val stored: Quantity, val value: Money)

class LogisticsReport(val tick: Tick) {
    val deliveries = ArrayList<Delivery>()
    val lost = ArrayList<Shipment>()
    val returned = ArrayList<Shipment>()
    val evicted = ArrayList<Warehouse>()
    var spoiledValue: Money = Money.ZERO
    var lostValue: Money = Money.ZERO
    var evictedValue: Money = Money.ZERO
    var rentCharged: Money = Money.ZERO
    var breakdowns: Int = 0

    /** Value that left inventories for good and should be written off. */
    val writeOff: Money get() = spoiledValue + lostValue + evictedValue
}

/**
 * Moves goods between stock locations, owns fleets and leased warehouses, and charges for it.
 * Freight is priced from the world's transport network; own vehicles trade capital for lower running cost.
 */
class LogisticsService(
    private val ids: IdAllocators,
    private val content: ContentRepository,
    private val world: WorldState,
    private val production: ProductionService,
    val params: LogisticsParams = LogisticsParams(),
    var payer: Payer = app.many.economytycoon.production.FreePayer,
) {
    private val shipments = java.util.TreeMap<ShipmentId, Shipment>()
    private val vehicles = java.util.TreeMap<VehicleId, Vehicle>()
    private val warehouses = java.util.TreeMap<WarehouseId, Warehouse>()

    // ---- queries -------------------------------------------------------------------------------

    fun shipment(id: ShipmentId): Shipment? = shipments[id]
    fun shipments(): List<Shipment> = shipments.values.toList()
    fun activeShipments(): List<Shipment> = shipments.values.filter { it.isActive }
    fun shipmentsOf(company: CompanyId): List<Shipment> = shipments.values.filter { it.shipper == company || it.consignee == company }
    fun vehicle(id: VehicleId): Vehicle? = vehicles[id]
    fun vehicles(): List<Vehicle> = vehicles.values.toList()
    fun vehiclesOf(company: CompanyId): List<Vehicle> = vehicles.values.filter { it.owner == company && it.status != VehicleStatus.RETIRED }
    fun warehouse(id: WarehouseId): Warehouse? = warehouses[id]
    fun warehouses(): List<Warehouse> = warehouses.values.toList()
    fun warehousesOf(company: CompanyId): List<Warehouse> = warehouses.values.filter { it.owner == company && it.status == WarehouseStatus.ACTIVE }

    fun inventory(ref: StockRef): Inventory? = when (ref.kind) {
        StockKind.FACILITY -> production.facility(FacilityId(ref.id))?.inventory
        StockKind.WAREHOUSE -> warehouses[WarehouseId(ref.id)]?.takeIf { it.status == WarehouseStatus.ACTIVE }?.inventory
    }

    fun ownerOf(ref: StockRef): CompanyId? = when (ref.kind) {
        StockKind.FACILITY -> production.facility(FacilityId(ref.id))?.owner
        StockKind.WAREHOUSE -> warehouses[WarehouseId(ref.id)]?.owner
    }

    fun cityOf(ref: StockRef): CityId? = when (ref.kind) {
        StockKind.WAREHOUSE -> warehouses[WarehouseId(ref.id)]?.city
        StockKind.FACILITY -> production.facility(FacilityId(ref.id))?.let { f -> f.city ?: world.citiesIn(f.region).minByOrNull { it.id.value }?.id }
    }

    /** Value of goods currently on the road for a company, at carrying cost. */
    fun inTransitValue(company: CompanyId): Money {
        var t = 0L
        for (s in shipments.values) if (s.isActive && s.shipper == company) t += s.carriedValue.raw
        return Money(t)
    }

    fun warehouseValue(company: CompanyId): Money {
        var t = 0L
        for (w in warehouses.values) if (w.owner == company && w.status == WarehouseStatus.ACTIVE) t += w.inventory.totalValue().raw
        return Money(t)
    }

    private fun country(city: CityId): CountryId = world.countryOfCity(city).id

    // ---- pricing -------------------------------------------------------------------------------

    fun quote(fromCity: CityId, toCity: CityId, good: GoodId, quantity: Quantity, tick: Tick, modes: Set<TransportMode> = TransportMode.values().toSet(), goodsValue: Money = Money.ZERO): FreightQuote? {
        val route = world.network.route(fromCity, toCity, tick.value, modes) ?: return null
        val def = content.goodOrNull(good)
        val weight = def?.weight ?: 1_000L
        val freight = Money(mulDiv(mulDiv(route.costPerUnit.raw, quantity.raw, Quantity.SCALE), weight, 1_000L))
        val cross = country(fromCity) != country(toCity)
        val tariff = if (cross) goodsValue.times(Rate(world.country(country(toCity)).tariffPpm)) else Money.ZERO
        val customs = if (cross) params.customsTicks else 0
        return FreightQuote(route.distance, route.ticks + customs, freight, tariff, route.modes.distinct(), cross)
    }

    private fun weatherMultiplier(from: CityId, to: CityId, tick: Tick): Long {
        val a = world.weather.transportMultiplier(world.regionOfCity(from), tick)
        val b = world.weather.transportMultiplier(world.regionOfCity(to), tick)
        return maxOf(a, b, 1_000_000L)
    }

    private fun lossPpm(mode: TransportMode) = when (mode) {
        TransportMode.ROAD -> params.lossPpmRoad; TransportMode.RAIL -> params.lossPpmRail
        TransportMode.SEA -> params.lossPpmSea; TransportMode.AIR -> params.lossPpmAir
    }

    // ---- shipping ------------------------------------------------------------------------------

    fun ship(req: ShipRequest, tick: Tick): Outcome<Shipment> {
        if (req.quantity.raw <= 0) return Outcome.reject(Rules.INVALID_QUANTITY, "quantity must be positive")
        if (req.from == req.to) return Outcome.reject(Rules.INVALID_ARGUMENT, "source and destination are the same")
        val src = inventory(req.from) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown source stock")
        if (inventory(req.to) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown destination stock")
        if (ownerOf(req.from) != req.shipper) return Outcome.reject(Rules.NOT_OWNER, "source belongs to another company")
        if (ownerOf(req.to) != req.consignee) return Outcome.reject(Rules.NOT_OWNER, "destination does not belong to the consignee")
        if (content.goodOrNull(req.good) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown good ${req.good.key}")
        if (src.available(req.good) < req.quantity) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "not enough ${req.good.key} at the source")
        val fromCity = cityOf(req.from) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "source has no city")
        val toCity = cityOf(req.to) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "destination has no city")

        val route = world.network.route(fromCity, toCity, tick.value, req.modes) ?: return Outcome.reject(Rules.NO_ROUTE, "no route between the cities")
        val unitCost = src.unitCost(req.good)
        val goodsValue = req.invoiceValue ?: Money(mulDiv(unitCost.raw, req.quantity.raw, Quantity.SCALE))
        val quote = quote(fromCity, toCity, req.good, req.quantity, tick, req.modes, goodsValue) ?: return Outcome.reject(Rules.NO_ROUTE, "no route between the cities")

        var vehicle: Vehicle? = null
        var freight = quote.freight
        var ticks = quote.ticks
        if (req.useFleet) {
            if (route.modes.distinct().size != 1) return Outcome.reject(Rules.WRONG_STATE, "own vehicles can only run single-mode routes")
            val mode = route.modes.first()
            vehicle = vehicles.values.firstOrNull { v ->
                v.owner == req.shipper && v.location == fromCity && v.isAvailable(tick) &&
                    content.vehicleType(v.type).let { it.mode == mode && it.capacity >= req.quantity }
            } ?: return Outcome.reject(Rules.WRONG_STATE, "no idle vehicle that can carry this load at the source")
            val vt = content.vehicleType(vehicle.type)
            freight = Money(vt.operatingCostPerDistance.raw * route.distance)
            ticks = maxOf(1, (route.distance + vt.speed - 1) / vt.speed) + (if (quote.crossesBorder) params.customsTicks else 0)
        }

        // Congestion: whatever does not fit on a segment this tick queues, stretching the trip.
        var shortfallPpm = 0L
        for (sid in route.segments) {
            val seg = world.network.segment(sid) ?: continue
            val fit = seg.reserve(req.quantity)
            if (fit < req.quantity) shortfallPpm = maxOf(shortfallPpm, mulDiv((req.quantity - fit).raw, 1_000_000L, req.quantity.raw))
        }
        val weather = weatherMultiplier(fromCity, toCity, tick)
        var total = (ticks.toLong() * weather + 999_999L) / 1_000_000L
        total += (ticks.toLong() * shortfallPpm + 999_999L) / 1_000_000L
        val arrival = tick.value + total.coerceAtLeast(1L)

        val charge = freight
        if (charge.raw > 0 && !payer.charge(req.payer, charge, LogisticsMemo.FREIGHT)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay freight")
        val tariffDue = quote.tariff
        if (tariffDue.raw > 0 && !payer.charge(req.payer, tariffDue, "${LogisticsMemo.TARIFF}:${country(toCity).value}")) {
            return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay import tariff")
        }
        val removed = src.remove(req.good, req.quantity).getOrThrow()
        val s = Shipment(
            ids.shipment(), req.shipper, req.consignee, req.from, req.to, fromCity, toCity, req.good, req.quantity, removed,
            req.invoiceValue, tick, route.modes.distinct(), route.distance, freight, tariffDue, vehicle?.id,
        )
        s.arrival = arrival
        shipments[s.id] = s
        if (vehicle != null) {
            vehicle.status = VehicleStatus.ON_TRIP
            vehicle.freeAt = arrival + (arrival - tick.value)
            vehicle.tripsMade++
            vehicle.distanceTravelled += route.distance
            vehicle.conditionPpm = (vehicle.conditionPpm - route.distance * params.vehicleWearPerDistancePpm).coerceAtLeast(0L)
        }
        return Outcome.ok(s)
    }

    /** Moves goods between two stocks in the same city for free and at once. */
    fun moveLocal(owner: CompanyId, from: StockRef, to: StockRef, good: GoodId, quantity: Quantity): Outcome<Quantity> {
        val src = inventory(from) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown source stock")
        val dst = inventory(to) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown destination stock")
        if (ownerOf(from) != owner || ownerOf(to) != owner) return Outcome.reject(Rules.NOT_OWNER, "both stocks must be yours")
        if (cityOf(from) != cityOf(to)) return Outcome.reject(Rules.INVALID_ARGUMENT, "stocks are in different cities")
        if (quantity.raw <= 0) return Outcome.reject(Rules.INVALID_QUANTITY, "quantity must be positive")
        val move = Quantity.min(quantity, dst.room(good))
        if (move.raw <= 0) return Outcome.reject(Rules.INSUFFICIENT_CAPACITY, "destination is full")
        val value = src.remove(good, move).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        dst.add(good, move, value)
        return Outcome.ok(move)
    }

    // ---- fleet ---------------------------------------------------------------------------------

    fun buyVehicle(owner: CompanyId, type: VehicleTypeId, city: CityId, tick: Tick): Outcome<Vehicle> {
        val def = content.vehicleTypeOrNull(type) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown vehicle ${type.key}")
        if (world.cityOrNull(city) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown city")
        if (def.mode !in world.network.modesAt(city) && !(def.mode == TransportMode.ROAD)) return Outcome.reject(Rules.INVALID_ARGUMENT, "${def.mode.name.lowercase()} is not available in this city")
        if (!payer.charge(owner, def.cost, "${LogisticsMemo.VEHICLE} ${def.name}")) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot afford ${def.name}")
        val v = Vehicle(ids.vehicle(), type, owner, tick)
        v.restore(city, VehicleStatus.IDLE, 1_000_000L, tick.value, 0, 0)
        vehicles[v.id] = v
        return Outcome.ok(v)
    }

    /** Sells an idle vehicle back; returns the money for the caller to credit. */
    fun sellVehicle(owner: CompanyId, id: VehicleId): Outcome<Money> {
        val v = vehicles[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown vehicle")
        if (v.owner != owner) return Outcome.reject(Rules.NOT_OWNER, "not your vehicle")
        if (v.status == VehicleStatus.ON_TRIP) return Outcome.reject(Rules.WRONG_STATE, "vehicle is on a trip")
        if (v.status == VehicleStatus.RETIRED) return Outcome.reject(Rules.WRONG_STATE, "vehicle already sold")
        val def = content.vehicleType(v.type)
        val value = mulDiv(mulDiv(def.cost.raw, params.vehicleResalePpm, 1_000_000L), v.conditionPpm, 1_000_000L)
        v.restore(v.location, VehicleStatus.RETIRED, v.conditionPpm, v.freeAt, v.tripsMade, v.distanceTravelled)
        return Outcome.ok(Money(value))
    }

    fun repairVehicle(owner: CompanyId, id: VehicleId): Outcome<Money> {
        val v = vehicles[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown vehicle")
        if (v.owner != owner) return Outcome.reject(Rules.NOT_OWNER, "not your vehicle")
        if (v.status == VehicleStatus.ON_TRIP || v.status == VehicleStatus.RETIRED) return Outcome.reject(Rules.WRONG_STATE, "vehicle cannot be repaired now")
        val def = content.vehicleType(v.type)
        val cost = Money(mulDiv(mulDiv(def.cost.raw, params.vehicleRepairFraction, 1_000_000L), 1_000_000L - v.conditionPpm, 1_000_000L))
        if (cost.raw > 0 && !payer.charge(owner, cost, LogisticsMemo.REPAIR)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot afford repairs")
        v.restore(v.location, VehicleStatus.IDLE, 1_000_000L, v.freeAt, v.tripsMade, v.distanceTravelled)
        return Outcome.ok(cost)
    }

    // ---- warehouses ----------------------------------------------------------------------------

    fun leaseWarehouse(owner: CompanyId, city: CityId, capacity: Quantity, tick: Tick): Outcome<Warehouse> {
        val c = world.cityOrNull(city) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown city")
        if (capacity.raw <= 0 || capacity > params.maxWarehouseCapacity) return Outcome.reject(Rules.INVALID_QUANTITY, "capacity out of range")
        val rentIndex = world.population(city).rentIndexPpm
        val rent = Money(mulDiv(mulDiv(params.warehouseRentPerUnit.raw, capacity.raw, Quantity.SCALE), rentIndex, 1_000_000L))
        if (!payer.charge(owner, rent, LogisticsMemo.RENT)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay the first rent")
        val id = ids.warehouse()
        val w = Warehouse(id, owner, city, c.region, Inventory(capacity) { g -> content.goodOrNull(g)?.volume ?: 1_000L }, tick)
        w.restore(rent, 0, WarehouseStatus.ACTIVE)
        warehouses[id] = w
        return Outcome.ok(w)
    }

    /** Ends a lease; stock must have been moved out first. */
    fun closeWarehouse(owner: CompanyId, id: WarehouseId): Outcome<Unit> {
        val w = warehouses[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown warehouse")
        if (w.owner != owner) return Outcome.reject(Rules.NOT_OWNER, "not your warehouse")
        if (w.status != WarehouseStatus.ACTIVE) return Outcome.reject(Rules.WRONG_STATE, "warehouse is not active")
        if (w.inventory.goods().isNotEmpty()) return Outcome.reject(Rules.WRONG_STATE, "empty the warehouse first")
        if (activeShipments().any { it.from == StockRef.warehouse(id) || it.to == StockRef.warehouse(id) }) return Outcome.reject(Rules.WRONG_STATE, "shipments are still pending")
        w.restore(w.rentPerTick, w.unpaidTicks, WarehouseStatus.CLOSED)
        return Outcome.ok(Unit)
    }

    // ---- tick ----------------------------------------------------------------------------------

    fun step(tick: Tick, random: RandomProvider): LogisticsReport {
        val report = LogisticsReport(tick)
        val rnd = random.stream("logistics", 0)
        for (s in shipments.values.toList()) {
            if (s.status == ShipmentStatus.IN_TRANSIT) transit(s, tick, rnd, report)
            if (s.status == ShipmentStatus.IN_TRANSIT && tick.value >= s.arrival) arrive(s, report)
            else if (s.status == ShipmentStatus.AT_DESTINATION) arrive(s, report)
        }
        for (v in vehicles.values) stepVehicle(v, tick, rnd, report)
        for (w in warehouses.values.toList()) chargeRent(w, report)
        // Finished shipments stay in the table for a while for the UI, then are dropped.
        shipments.values.removeAll { !it.isActive && tick.value - it.arrival > 30 }
        return report
    }

    private fun transit(s: Shipment, tick: Tick, rnd: app.many.economytycoon.core.DeterministicRandom, report: LogisticsReport) {
        val def = content.goodOrNull(s.good)
        if (def != null && def.spoilagePpm > 0 && s.carriedQuantity.raw > 0) {
            val num = s.carriedQuantity.raw * def.spoilagePpm + s.spoilCarry
            s.spoilCarry = num % 1_000_000L
            val lost = (num / 1_000_000L).coerceAtMost(s.carriedQuantity.raw)
            if (lost > 0) {
                val lostValue = Money(mulDiv(s.carriedValue.raw, lost, s.carriedQuantity.raw))
                s.carriedQuantity = Quantity(s.carriedQuantity.raw - lost)
                s.carriedValue = s.carriedValue - lostValue
                report.spoiledValue += lostValue
            }
        }
        val risk = s.modes.maxOf { lossPpm(it) }
        if (rnd.chance(Rate(risk))) {
            s.status = ShipmentStatus.LOST
            report.lost += s
            report.lostValue += s.carriedValue
            s.carriedValue = Money.ZERO; s.carriedQuantity = Quantity.ZERO
            freeVehicle(s, tick)
        }
    }

    private fun freeVehicle(s: Shipment, tick: Tick) {
        val v = s.vehicle?.let { vehicles[it] } ?: return
        if (s.status == ShipmentStatus.LOST && v.status == VehicleStatus.ON_TRIP) v.freeAt = tick.value + 1
    }

    private fun arrive(s: Shipment, report: LogisticsReport) {
        val dst = inventory(s.to)
        if (dst == null) { sendBack(s, report); return }
        if (s.carriedQuantity.raw <= 0) { s.status = ShipmentStatus.DELIVERED; return }
        val originalQty = s.quantity.raw
        val value = if (s.invoiceValue != null) Money(mulDiv(s.invoiceValue.raw, s.carriedQuantity.raw, originalQty)) else s.carriedValue
        val stored = dst.add(s.good, s.carriedQuantity, value)
        if (stored.raw > 0) {
            val storedValue = if (stored == s.carriedQuantity) value else Money(mulDiv(value.raw, stored.raw, s.carriedQuantity.raw))
            val costPart = if (stored == s.carriedQuantity) s.carriedValue else Money(mulDiv(s.carriedValue.raw, stored.raw, s.carriedQuantity.raw))
            report.deliveries += Delivery(s, stored, storedValue)
            s.carriedQuantity = s.carriedQuantity - stored
            s.carriedValue = s.carriedValue - costPart
        }
        if (s.carriedQuantity.raw <= 0) { s.status = ShipmentStatus.DELIVERED; s.waitingTicks = 0; return }
        s.status = ShipmentStatus.AT_DESTINATION
        s.waitingTicks++
        if (s.waitingTicks > params.maxWaitAtDestination) sendBack(s, report)
    }

    private fun sendBack(s: Shipment, report: LogisticsReport) {
        val src = inventory(s.from)
        if (src != null && s.carriedQuantity.raw > 0) {
            src.add(s.good, s.carriedQuantity, s.carriedValue, allowOverflow = true)
            s.status = ShipmentStatus.RETURNED
            report.returned += s
        } else {
            report.lostValue += s.carriedValue
            s.status = ShipmentStatus.LOST
            report.lost += s
        }
        s.carriedQuantity = Quantity.ZERO; s.carriedValue = Money.ZERO
    }

    private fun stepVehicle(v: Vehicle, tick: Tick, rnd: app.many.economytycoon.core.DeterministicRandom, report: LogisticsReport) {
        when (v.status) {
            VehicleStatus.ON_TRIP -> if (tick.value >= v.freeAt) {
                v.restore(v.location, VehicleStatus.IDLE, v.conditionPpm, v.freeAt, v.tripsMade, v.distanceTravelled)
            }
            VehicleStatus.BROKEN -> if (tick.value >= v.freeAt) {
                v.restore(v.location, VehicleStatus.IDLE, 400_000L, v.freeAt, v.tripsMade, v.distanceTravelled)
            }
            VehicleStatus.IDLE -> if (v.conditionPpm < 400_000L) {
                val chance = (400_000L - v.conditionPpm) / 40L
                if (rnd.chance(Rate(chance))) {
                    v.restore(v.location, VehicleStatus.BROKEN, v.conditionPpm, tick.value + 3 + rnd.nextInt(5), v.tripsMade, v.distanceTravelled)
                    report.breakdowns++
                }
            }
            VehicleStatus.RETIRED -> Unit
        }
    }

    private fun chargeRent(w: Warehouse, report: LogisticsReport) {
        if (w.status != WarehouseStatus.ACTIVE) return
        if (payer.charge(w.owner, w.rentPerTick, LogisticsMemo.RENT)) {
            report.rentCharged += w.rentPerTick
            w.restore(w.rentPerTick, 0, WarehouseStatus.ACTIVE)
        } else {
            val unpaid = w.unpaidTicks + 1
            if (unpaid >= params.maxUnpaidRentTicks) {
                report.evictedValue += w.inventory.totalValue()
                for (g in w.inventory.goods()) w.inventory.clear(g)
                w.restore(w.rentPerTick, unpaid, WarehouseStatus.EVICTED)
                report.evicted += w
            } else w.restore(w.rentPerTick, unpaid, WarehouseStatus.ACTIVE)
        }
    }

    // ---- persistence support -------------------------------------------------------------------

    internal fun clearAll() { shipments.clear(); vehicles.clear(); warehouses.clear() }
    internal fun restoreShipment(s: Shipment) { shipments[s.id] = s }
    internal fun restoreVehicle(v: Vehicle) { vehicles[v.id] = v }
    internal fun restoreWarehouse(w: Warehouse) { warehouses[w.id] = w }
    internal fun newInventory(capacity: Quantity) = Inventory(capacity) { g -> content.goodOrNull(g)?.volume ?: 1_000L }

    fun hashInto(h: CanonicalHasher) {
        h.section("logistics")
        h.putInt(shipments.size); for (s in shipments.values) s.hashInto(h)
        h.putInt(vehicles.size); for (v in vehicles.values) v.hashInto(h)
        h.putInt(warehouses.size); for (w in warehouses.values) w.hashInto(h)
    }
}
