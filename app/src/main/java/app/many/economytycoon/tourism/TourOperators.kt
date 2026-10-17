package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * A tour operator sells packages that include rooms. It buys the rooms from properties under allotment contracts and
 * marks them up. [strengthPpm] is its weight against other operators in the destinations where it sells.
 */
class TourOperator(
    val id: OperatorId,
    val owner: CompanyId?,
    val name: String,
) {
    var strengthPpm: Long = 1_000_000L
        internal set
    var marginPpm: Long = 180_000L
        internal set
    var lifetimeRooms: Long = 0L
        internal set
    var lifetimeMargin: Money = Money.ZERO
        internal set
    var monthRooms: Long = 0L
        internal set
    var monthMargin: Money = Money.ZERO
        internal set

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(owner?.value ?: -1L).putString(name).putLong(strengthPpm).putLong(marginPpm)
        h.putLong(lifetimeRooms).putMoney(lifetimeMargin).putLong(monthRooms).putMoney(monthMargin)
    }
}

enum class AllotmentStatus(val label: String, val live: Boolean) {
    PROPOSED("Proposed", false), ACTIVE("Active", true), ENDED("Ended", false), CANCELLED("Cancelled", false), DECLINED("Declined", false)
}

/** Which side offered the contract; the other side has to accept. */
enum class ContractSide { OPERATOR, PROPERTY }

/**
 * Rooms a property sets aside for an operator at a net rate. Each night the operator sells what it can; rooms still
 * unsold [releaseDays] before the night go back to the property, except for a committed share that the operator pays for
 * whether or not it sells it.
 */
class Allotment(
    val id: AllotmentId,
    val operator: OperatorId,
    val property: PropertyId,
    val room: RoomClass,
    val roomsPerNight: Int,
    val netRate: Money,
    val startsAt: Tick,
    val endsAt: Tick,
    val releaseDays: Int,
    /** Share of the allotment the operator pays for regardless of sales, ppm. */
    val committedPpm: Long,
    val proposedBy: ContractSide,
    val proposedAt: Tick,
) {
    init {
        require(roomsPerNight > 0) { "an allotment needs rooms" }
        require(endsAt > startsAt) { "an allotment needs a period" }
        require(releaseDays in 0 until Seasonality.HORIZON) { "release days must be inside the booking horizon" }
        require(committedPpm in 0..1_000_000L) { "commitment is a share" }
        require(netRate.raw > 0L) { "the net rate must be positive" }
    }

    var status: AllotmentStatus = AllotmentStatus.PROPOSED
        internal set
    var endedAt: Long = -1L
        internal set
    internal val soldMap = java.util.TreeMap<Long, Int>()

    var monthSold: Long = 0L
        internal set
    var monthSoldValue: Money = Money.ZERO
        internal set
    var monthShortfallRooms: Long = 0L
        internal set
    var monthShortfallValue: Money = Money.ZERO
        internal set
    var monthReleased: Long = 0L
        internal set
    var lifetimeSold: Long = 0L
        internal set
    var lifetimeShortfall: Money = Money.ZERO
        internal set
    /** The side that ended the contract early and the fee it paid for doing so. */
    var terminatedBy: ContractSide? = null
        internal set
    var terminationFee: Money = Money.ZERO
        internal set

    val nights: Long get() = endsAt - startsAt
    val committedRooms: Int get() = ((roomsPerNight.toLong() * committedPpm + 999_999L) / 1_000_000L).toInt()

    fun soldOn(date: Long): Int = soldMap[date] ?: 0
    fun covers(date: Long): Boolean = status == AllotmentStatus.ACTIVE && date >= startsAt.value && date < endsAt.value
    val soldDates: List<Long> get() = soldMap.keys.toList()

    /** Rooms still held back for the operator on [date], seen [lead] days before it. Zero once released. */
    fun heldOn(date: Long, lead: Int): Int =
        if (!covers(date) || lead <= releaseDays) 0 else (roomsPerNight - soldOn(date)).coerceAtLeast(0)

    /** Rooms the operator can still sell on [date]: everything not sold yet, whether or not it is released. */
    fun openOn(date: Long): Int = if (covers(date)) (roomsPerNight - soldOn(date)).coerceAtLeast(0) else 0

    internal fun sell(date: Long, rooms: Int) { soldMap.merge(date, rooms, Int::plus) }

    internal fun forgetBefore(date: Long) { soldMap.headMap(date).clear() }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(operator.value).putLong(property.value).putInt(room.ordinal).putInt(roomsPerNight).putMoney(netRate)
        h.putLong(startsAt.value).putLong(endsAt.value).putInt(releaseDays).putLong(committedPpm).putInt(proposedBy.ordinal).putLong(proposedAt.value)
        h.putInt(status.ordinal).putLong(endedAt)
        h.putInt(soldMap.size); for ((k, v) in soldMap) h.putLong(k).putInt(v)
        h.putLong(monthSold).putMoney(monthSoldValue).putLong(monthShortfallRooms).putMoney(monthShortfallValue).putLong(monthReleased)
        h.putLong(lifetimeSold).putMoney(lifetimeShortfall).putInt(terminatedBy?.ordinal ?: -1).putMoney(terminationFee)
    }
}

/** Pure rules for allotment contracts. */
object AllotmentRules {
    /** Net rates outside this share of the rack rate are refused by an operator with no owner. */
    const val AUTO_ACCEPT_MIN_PPM = 450_000L
    const val AUTO_ACCEPT_MAX_PPM = 850_000L

    /** Early termination costs this share of the committed value of the nights that remain. */
    const val TERMINATION_SHARE_PPM = 250_000L

    fun netShareOfRack(a: Allotment, rack: Money): Long = if (rack.raw <= 0L) 0L else mulDiv(a.netRate.raw, 1_000_000L, rack.raw)

    /** Would an operator without an owner take this contract? */
    fun autoAccepts(netShare: Long): Boolean = netShare in AUTO_ACCEPT_MIN_PPM..AUTO_ACCEPT_MAX_PPM

    /** What the operator pays for the committed rooms of a night that it did not sell. */
    fun shortfallRooms(a: Allotment, sold: Int): Int = (a.committedRooms - sold).coerceAtLeast(0)

    /** Fee for ending a contract at [tick]: a quarter of the committed value of the nights still ahead. */
    fun terminationFee(a: Allotment, tick: Tick): Money {
        val remaining = (a.endsAt.value - maxOf(tick.value, a.startsAt.value)).coerceAtLeast(0L)
        val committed = a.netRate * (a.committedRooms.toLong() * remaining)
        return Money(mulDiv(committed.raw, TERMINATION_SHARE_PPM, 1_000_000L))
    }

    /** The price the operator charges its customers for a room-night. */
    fun packagePrice(netRate: Money, marginPpm: Long): Money = Money(mulDiv(netRate.raw, 1_000_000L + marginPpm, 1_000_000L))

    /** Splits [rooms] among operators by strength; the parts always add up to [rooms]. */
    fun splitByStrength(rooms: Long, strengths: List<Long>): List<Long> {
        if (strengths.isEmpty() || rooms <= 0L) return List(strengths.size) { 0L }
        if (strengths.sum() <= 0L) return List(strengths.size) { 0L }
        return app.many.economytycoon.core.splitLargestRemainder(rooms, strengths.toLongArray()).toList()
    }
}
