package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money

/**
 * A transmission line between two grids. [capacityKw] limits the power sent in either direction, [lossPpm] is the
 * share lost on the way, and [wheelingPerMwh] is what the line's operator charges for each megawatt-hour sent.
 */
class Interconnector(
    val id: LinkId,
    val a: GridId,
    val b: GridId,
    capacityKw: Long,
    val lossPpm: Long,
    val wheelingPerMwh: Money,
) {
    init {
        require(a != b) { "an interconnector joins two different grids" }
        require(capacityKw > 0L) { "an interconnector needs capacity" }
        require(lossPpm in 0L..500_000L) { "losses must be between 0 and 50%" }
    }

    var capacityKw: Long = capacityKw
        internal set
    var inService: Boolean = true
        internal set

    /** Energy sent from [a] to [b] and from [b] to [a] since the start, before losses. */
    var sentAbKwh: Long = 0L
        internal set
    var sentBaKwh: Long = 0L
        internal set
    var lostKwh: Long = 0L
        internal set
    var congestedHours: Int = 0
        internal set

    fun touches(grid: GridId) = grid == a || grid == b
    fun other(grid: GridId): GridId = if (grid == a) b else a

    fun view(): LinkView = LinkView(id, a, b, if (inService) capacityKw else 0L, lossPpm, wheelingPerMwh)

    internal fun restore(capacityKw: Long, inService: Boolean, sentAb: Long, sentBa: Long, lost: Long, congested: Int) {
        this.capacityKw = capacityKw; this.inService = inService; sentAbKwh = sentAb; sentBaKwh = sentBa; lostKwh = lost; congestedHours = congested
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(a.value).putLong(b.value).putLong(capacityKw).putLong(lossPpm).putMoney(wheelingPerMwh)
        h.putBoolean(inService).putLong(sentAbKwh).putLong(sentBaKwh).putLong(lostKwh).putInt(congestedHours)
    }
}

/** A link as the trade step sees it. */
data class LinkView(val id: LinkId, val a: GridId, val b: GridId, val capacityKw: Long, val lossPpm: Long, val wheelingPerMwh: Money)

/** Power sent over a link in one hour: [sentKw] leaves [from], [receivedKw] reaches [to] after losses. */
data class Transfer(val link: LinkId, val from: GridId, val to: GridId, val sentKw: Long, val receivedKw: Long)
