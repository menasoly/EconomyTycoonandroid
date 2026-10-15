package app.many.economytycoon.logistics

import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.ShipmentId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.VehicleId

enum class ShipmentStatus { IN_TRANSIT, AT_DESTINATION, DELIVERED, LOST, RETURNED }

/**
 * Goods on the move. The cargo leaves the source at carrying cost ([costValue]); [invoiceValue], when set,
 * is what the consignee's stock is valued at on arrival (a sale), otherwise the cost carries over.
 */
class Shipment(
    val id: ShipmentId,
    val shipper: CompanyId,
    val consignee: CompanyId,
    val from: StockRef,
    val to: StockRef,
    val fromCity: CityId,
    val toCity: CityId,
    val good: GoodId,
    val quantity: Quantity,
    val costValue: Money,
    val invoiceValue: Money?,
    val departed: Tick,
    val modes: List<TransportMode>,
    val distance: Int,
    val freight: Money,
    val tariff: Money,
    val vehicle: VehicleId?,
) {
    var arrival: Long = 0
        internal set
    var status: ShipmentStatus = ShipmentStatus.IN_TRANSIT
        internal set
    /** Value remaining after spoilage in transit. */
    var carriedValue: Money = costValue
        internal set
    var carriedQuantity: Quantity = quantity
        internal set
    var waitingTicks: Int = 0
        internal set
    internal var spoilCarry: Long = 0

    internal fun spoilCarryValue(): Long = spoilCarry

    val isActive: Boolean get() = status == ShipmentStatus.IN_TRANSIT || status == ShipmentStatus.AT_DESTINATION

    internal fun restore(arrival: Long, status: ShipmentStatus, carriedValue: Money, carriedQuantity: Quantity, waiting: Int, spoilCarry: Long) {
        this.arrival = arrival; this.status = status; this.carriedValue = carriedValue; this.carriedQuantity = carriedQuantity
        waitingTicks = waiting; this.spoilCarry = spoilCarry
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(shipper.value).putLong(consignee.value).putInt(from.kind.ordinal).putLong(from.id).putInt(to.kind.ordinal).putLong(to.id)
            .putString(good.key).putQuantity(quantity).putMoney(costValue).putMoney(invoiceValue ?: Money(-1)).putLong(departed.value).putLong(arrival)
            .putInt(status.ordinal).putMoney(carriedValue).putQuantity(carriedQuantity).putInt(waitingTicks).putLong(spoilCarry)
            .putMoney(freight).putMoney(tariff).putLong(vehicle?.value ?: -1L)
    }
}

/** An estimate of what moving goods would cost and how long it would take. */
class FreightQuote(
    val distance: Int,
    val ticks: Int,
    val freight: Money,
    val tariff: Money,
    val modes: List<TransportMode>,
    val crossesBorder: Boolean,
) {
    val total: Money get() = freight + tariff
}
