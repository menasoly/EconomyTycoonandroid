package app.many.economytycoon.logistics

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.VehicleId
import app.many.economytycoon.core.VehicleTypeId

enum class VehicleStatus { IDLE, ON_TRIP, BROKEN, RETIRED }

/** A truck, train, ship or plane owned by a company. A vehicle serves one shipment at a time and then returns home. */
class Vehicle(
    val id: VehicleId,
    val type: VehicleTypeId,
    val owner: CompanyId,
    val bought: Tick,
) {
    var location: CityId? = null
        internal set
    var status: VehicleStatus = VehicleStatus.IDLE
        internal set
    var conditionPpm: Long = 1_000_000L
        internal set
    var freeAt: Long = 0
        internal set
    var tripsMade: Long = 0
        internal set
    var distanceTravelled: Long = 0
        internal set

    internal fun restore(location: CityId?, status: VehicleStatus, condition: Long, freeAt: Long, trips: Long, distance: Long) {
        this.location = location; this.status = status; conditionPpm = condition; this.freeAt = freeAt; tripsMade = trips; distanceTravelled = distance
    }

    fun isAvailable(tick: Tick) = status == VehicleStatus.IDLE && tick.value >= freeAt

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putString(type.key).putLong(owner.value).putLong(location?.value ?: -1L).putInt(status.ordinal)
            .putLong(conditionPpm).putLong(freeAt).putLong(tripsMade).putLong(distanceTravelled)
    }
}
