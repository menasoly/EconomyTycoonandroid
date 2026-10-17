package app.many.economytycoon.tourism

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * The only thing tourism needs from the labour side: how many of [wanted] staff a business can actually field today.
 * The workforce module implements it from its hiring pool; tourism never imports it.
 */
fun interface StaffSupply {
    fun available(company: CompanyId?, destination: DestinationId, wanted: Int, tick: Tick): Int
}

/** Everyone asked for turns up. */
object FullStaffSupply : StaffSupply {
    override fun available(company: CompanyId?, destination: DestinationId, wanted: Int, tick: Tick): Int = wanted
}

/** A fixed share of the staff wanted turns up, for scenarios and tests. */
class ShareStaffSupply(private val sharePpm: Long) : StaffSupply {
    override fun available(company: CompanyId?, destination: DestinationId, wanted: Int, tick: Tick): Int =
        (wanted.toLong() * sharePpm.coerceIn(0L, 1_000_000L) / 1_000_000L).toInt()
}

/** A week of expected staffing, for the seasonal plan. */
data class StaffingWeek(val weekStart: Long, val expectedOccupancyPpm: Long, val staffNeeded: Int)

/** Staff the property should hold over the coming weeks and how much of that is seasonal. */
data class StaffingPlan(val weeks: List<StaffingWeek>) {
    val peak: Int get() = weeks.maxOfOrNull { it.staffNeeded } ?: 0
    val trough: Int get() = weeks.minOfOrNull { it.staffNeeded } ?: 0
    val average: Int get() = if (weeks.isEmpty()) 0 else weeks.sumOf { it.staffNeeded } / weeks.size

    /** Permanent staff to keep: enough for the quiet weeks, the rest is hired for the season. */
    val permanent: Int get() = trough
    val seasonal: Int get() = peak - trough
}

object Staffing {
    /** Staff that keep the building running whatever the occupancy. */
    fun baseStaff(kind: PropertyKind, rooms: Int): Int = ((rooms.toLong() * kind.baseStaffPerHundredRooms + 99L) / 100L).toInt()

    /** Staff for the guests: [PropertyKind.staffPerTenOccupied] per ten occupied rooms at three stars, scaling with stars. */
    fun guestStaff(kind: PropertyKind, stars: Int, occupied: Int): Int {
        if (occupied <= 0) return 0
        val perTen = mulDiv(kind.staffPerTenOccupied.toLong(), AccommodationRules.starFactorPpm(stars), 1_000_000L)
        return ((occupied.toLong() * perTen + 9L) / 10L).toInt()
    }

    fun needed(kind: PropertyKind, stars: Int, rooms: Int, occupied: Int): Int = baseStaff(kind, rooms) + guestStaff(kind, stars, occupied)

    /** Staff on duty as a share of those needed, capped at one; with nobody needed the service is perfect. */
    fun serviceLevelPpm(needed: Int, had: Int): Long =
        if (needed <= 0) 1_000_000L else mulDiv(had.toLong().coerceAtMost(needed.toLong()), 1_000_000L, needed.toLong())

    /** A day's pay for [staff] people. */
    fun wageCost(staff: Int, wagePerDay: Money): Money = wagePerDay * staff.toLong()

    /** The wage used when the world supplies none: a day's pay a little above the local minimum. */
    fun defaultWage(minimumWagePerTick: Long): Money = Money.units(minimumWagePerTick).scale(160, 100)

    /** Staffing by week for forecast occupancy values, one per week. */
    fun plan(p: Property, weekStarts: List<Long>, occupancyPpm: List<Long>): StaffingPlan {
        require(weekStarts.size == occupancyPpm.size) { "one occupancy per week" }
        val rooms = p.sellableTotal()
        val weeks = weekStarts.indices.map { i ->
            val occ = occupancyPpm[i].coerceIn(0L, 1_000_000L)
            val occupied = (rooms.toLong() * occ / 1_000_000L).toInt()
            StaffingWeek(weekStarts[i], occ, needed(p.kind, p.stars, p.totalRooms, occupied))
        }
        return StaffingPlan(weeks)
    }
}
