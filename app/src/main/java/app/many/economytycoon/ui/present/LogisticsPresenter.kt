package app.many.economytycoon.ui.present

import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.logistics.ShipmentStatus
import app.many.economytycoon.logistics.VehicleStatus
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class ShipmentItem(val id: Long, val title: String, val route: String, val status: String, val tone: Tone, val eta: String, val cost: String)
data class VehicleItem(val id: Long, val title: String, val status: String, val tone: Tone, val condition: Double, val conditionTone: Tone, val detail: String)
data class WarehouseItem(val id: Long, val title: String, val fill: Double, val fillText: String, val rent: String, val behindOnRent: Boolean)
data class VehicleChoice(val typeKey: String, val name: String, val mode: String, val detail: String, val price: String, val locked: Boolean, val lockNote: String?)

data class LogisticsModel(
    val tiles: List<Tile>,
    val shipments: List<ShipmentItem>,
    val fleet: List<VehicleItem>,
    val warehouses: List<WarehouseItem>,
    val choices: List<VehicleChoice>,
)

object LogisticsPresenter {
    fun modeLabel(m: TransportMode): String = when (m) {
        TransportMode.ROAD -> "Road"
        TransportMode.RAIL -> "Rail"
        TransportMode.SEA -> "Sea"
        TransportMode.AIR -> "Air"
    }

    fun shipmentStatus(s: ShipmentStatus): Pair<String, Tone> = when (s) {
        ShipmentStatus.IN_TRANSIT -> "In transit" to Tone.INFO
        ShipmentStatus.AT_DESTINATION -> "Waiting to unload" to Tone.WARN
        ShipmentStatus.DELIVERED -> "Delivered" to Tone.GOOD
        ShipmentStatus.LOST -> "Lost" to Tone.BAD
        ShipmentStatus.RETURNED -> "Returned" to Tone.WARN
    }

    fun vehicleStatus(s: VehicleStatus): Pair<String, Tone> = when (s) {
        VehicleStatus.IDLE -> "Idle" to Tone.NEUTRAL
        VehicleStatus.ON_TRIP -> "On a trip" to Tone.INFO
        VehicleStatus.BROKEN -> "Broken down" to Tone.BAD
        VehicleStatus.RETIRED -> "Retired" to Tone.MUTED
    }

    fun build(session: GameSession, includeFinished: Boolean = false): LogisticsModel? {
        val company = session.selectedCompany ?: return null
        val r = session.reports.logistics
        val money = session.settings::money
        val rows = r.shipments(company, includeFinished)
        val shipments = rows.map { s ->
            val (label, tone) = shipmentStatus(s.status)
            ShipmentItem(
                s.id, "${Formatting.quantity(s.quantity)} ${s.good}", "${s.from} → ${s.to} · ${s.modes.joinToString("/") { modeLabel(it) }}", label, tone,
                if (s.status == ShipmentStatus.IN_TRANSIT) "arrives in " + Formatting.duration(s.arrivesIn) else "", money(s.freight),
            )
        }
        val fleet = r.fleet(company).map { v ->
            val (label, tone) = vehicleStatus(v.status)
            VehicleItem(
                v.id, "${v.typeName} #${v.id}", label, tone, v.conditionPpm / 1_000_000.0, Tones.health(v.conditionPpm),
                "${modeLabel(v.mode)} · carries ${Formatting.quantity(v.capacity)} · ${Formatting.plural(v.trips, "trip")}" + (v.location?.let { " · at $it" } ?: ""),
            )
        }
        val stores = r.warehouses(company).map { w ->
            val fill = if (w.capacity.raw <= 0L) 0.0 else (w.used.raw.toDouble() / w.capacity.raw).coerceIn(0.0, 1.0)
            WarehouseItem(w.id.value, "Warehouse in ${w.city}", fill, "${Formatting.quantity(w.used)} of ${Formatting.quantity(w.capacity)} used", money(w.rentPerTick) + " a day", w.unpaidTicks > 0)
        }
        val choices = r.vehicleOptions(company).map { o ->
            VehicleChoice(o.type.key, o.name, modeLabel(o.mode), "Carries ${Formatting.quantity(o.capacity)} · speed ${o.speed}", money(o.cost), o.locked, o.techName?.takeIf { o.locked }?.let { "Needs $it" })
        }
        val tiles = listOf(
            Tile("Vehicles", fleet.size.toString(), note = "${fleet.count { it.status == "Idle" }} idle"),
            Tile("Shipments", shipments.count { it.status != "Delivered" }.toString(), note = "under way"),
            Tile("Warehouses", stores.size.toString(), if (stores.any { it.behindOnRent }) Tone.WARN else Tone.NEUTRAL),
            Tile("Freight bill", money(app.many.economytycoon.core.Money(rows.sumOf { it.freight.raw })), note = "on listed shipments"),
        )
        return LogisticsModel(tiles, shipments, fleet, stores, choices)
    }
}
