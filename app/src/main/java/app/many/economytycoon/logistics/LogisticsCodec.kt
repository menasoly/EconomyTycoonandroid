package app.many.economytycoon.logistics

import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.ShipmentId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.VehicleId
import app.many.economytycoon.core.VehicleTypeId
import app.many.economytycoon.core.WarehouseId

/** Binary save format for shipments, fleets and leased warehouses. */
object LogisticsCodec {
    const val VERSION = 1

    fun write(l: LogisticsService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val shipments = l.shipments()
        out.writeVarInt(shipments.size)
        for (s in shipments) {
            out.writeVarLong(s.id.value); out.writeVarLong(s.shipper.value); out.writeVarLong(s.consignee.value)
            out.writeVarInt(s.from.kind.ordinal); out.writeVarLong(s.from.id); out.writeVarInt(s.to.kind.ordinal); out.writeVarLong(s.to.id)
            out.writeVarLong(s.fromCity.value); out.writeVarLong(s.toCity.value)
            out.writeString(s.good.key); out.writeQuantity(s.quantity); out.writeMoney(s.costValue)
            out.writeBoolean(s.invoiceValue != null); s.invoiceValue?.let { out.writeMoney(it) }
            out.writeVarLong(s.departed.value)
            out.writeVarInt(s.modes.size); for (m in s.modes) out.writeVarInt(m.ordinal)
            out.writeVarInt(s.distance); out.writeMoney(s.freight); out.writeMoney(s.tariff)
            out.writeBoolean(s.vehicle != null); s.vehicle?.let { out.writeVarLong(it.value) }
            out.writeVarLong(s.arrival); out.writeVarInt(s.status.ordinal); out.writeMoney(s.carriedValue); out.writeQuantity(s.carriedQuantity)
            out.writeVarInt(s.waitingTicks)
            out.writeVarLong(s.spoilCarryValue())
        }
        val vehicles = l.vehicles()
        out.writeVarInt(vehicles.size)
        for (v in vehicles) {
            out.writeVarLong(v.id.value); out.writeString(v.type.key); out.writeVarLong(v.owner.value); out.writeVarLong(v.bought.value)
            out.writeBoolean(v.location != null); v.location?.let { out.writeVarLong(it.value) }
            out.writeVarInt(v.status.ordinal); out.writeVarLong(v.conditionPpm); out.writeVarLong(v.freeAt)
            out.writeVarLong(v.tripsMade); out.writeVarLong(v.distanceTravelled)
        }
        val warehouses = l.warehouses()
        out.writeVarInt(warehouses.size)
        for (w in warehouses) {
            out.writeVarLong(w.id.value); out.writeVarLong(w.owner.value); out.writeVarLong(w.city.value); out.writeVarLong(w.region.value)
            out.writeVarLong(w.leased.value); out.writeMoney(w.rentPerTick); out.writeVarInt(w.unpaidTicks); out.writeVarInt(w.status.ordinal)
            out.writeQuantity(w.inventory.capacity)
            val stock = w.inventory.entries().filter { it.second.quantity.raw != 0L || it.second.value.raw != 0L }
            out.writeVarInt(stock.size)
            for ((g, slot) in stock) {
                out.writeString(g.key); out.writeQuantity(slot.quantity); out.writeQuantity(slot.reserved); out.writeMoney(slot.value); out.writeVarLong(slot.spoilCarry)
            }
        }
    }

    fun read(r: ByteReader, target: LogisticsService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported logistics save version $version")
        target.clearAll()
        repeat(r.readVarInt()) {
            val id = ShipmentId(r.readVarLong()); val shipper = CompanyId(r.readVarLong()); val consignee = CompanyId(r.readVarLong())
            val from = StockRef(kind(r.readVarInt()), r.readVarLong()); val to = StockRef(kind(r.readVarInt()), r.readVarLong())
            val fromCity = CityId(r.readVarLong()); val toCity = CityId(r.readVarLong())
            val good = GoodId(r.readString()); val qty = r.readQuantity(); val cost = r.readMoney()
            val invoice = if (r.readBoolean()) r.readMoney() else null
            val departed = Tick(r.readVarLong())
            val modes = ArrayList<TransportMode>()
            repeat(r.readVarInt()) { modes += TransportMode.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad transport mode") } }
            val distance = r.readVarInt(); val freight = r.readMoney(); val tariff = r.readMoney()
            val vehicle = if (r.readBoolean()) VehicleId(r.readVarLong()) else null
            val s = Shipment(id, shipper, consignee, from, to, fromCity, toCity, good, qty, cost, invoice, departed, modes, distance, freight, tariff, vehicle)
            val arrival = r.readVarLong()
            val status = ShipmentStatus.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad shipment status") }
            s.restore(arrival, status, r.readMoney(), r.readQuantity(), r.readVarInt(), r.readVarLong())
            target.restoreShipment(s)
        }
        repeat(r.readVarInt()) {
            val v = Vehicle(VehicleId(r.readVarLong()), VehicleTypeId(r.readString()), CompanyId(r.readVarLong()), Tick(r.readVarLong()))
            val loc = if (r.readBoolean()) CityId(r.readVarLong()) else null
            val status = VehicleStatus.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad vehicle status") }
            v.restore(loc, status, r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong())
            target.restoreVehicle(v)
        }
        repeat(r.readVarInt()) {
            val id = WarehouseId(r.readVarLong()); val owner = CompanyId(r.readVarLong()); val city = CityId(r.readVarLong()); val region = RegionId(r.readVarLong())
            val leased = Tick(r.readVarLong()); val rent = r.readMoney(); val unpaid = r.readVarInt()
            val status = WarehouseStatus.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad warehouse status") }
            val inv = target.newInventory(r.readQuantity())
            repeat(r.readVarInt()) { inv.restoreSlot(GoodId(r.readString()), r.readQuantity(), r.readQuantity(), r.readMoney(), r.readVarLong()) }
            val w = Warehouse(id, owner, city, region, inv, leased)
            w.restore(rent, unpaid, status)
            target.restoreWarehouse(w)
        }
    }

    private fun kind(ordinal: Int) = StockKind.values().getOrElse(ordinal) { throw ByteReader.CorruptDataException("bad stock kind") }
}
