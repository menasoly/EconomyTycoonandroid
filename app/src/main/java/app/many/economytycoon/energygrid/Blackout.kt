package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.splitLargestRemainder

/** A draw on the grid in one hour. */
data class LoadDraw(val load: LoadId, val customer: CustomerClass, val kw: Long)

/** How much of a load's draw is cut off in one hour. */
data class ShedAllocation(val load: LoadId, val kw: Long)

/**
 * Chooses who goes dark when the grid is short. Customers are cut in the order of [CustomerClass.shedRank]; inside a
 * class the shortfall is shared in proportion to what each one draws, with the odd kilowatts going by largest
 * remainder so the cuts add up exactly.
 */
object LoadShedder {
    fun shed(draws: List<LoadDraw>, unservedKw: Long): List<ShedAllocation> {
        if (unservedKw <= 0L) return emptyList()
        val out = ArrayList<ShedAllocation>()
        var left = unservedKw
        for (rank in CustomerClass.values().sortedBy { it.shedRank }) {
            if (left <= 0L) break
            val tier = draws.filter { it.customer == rank && it.kw > 0L }.sortedBy { it.load }
            if (tier.isEmpty()) continue
            val tierKw = tier.sumOf { it.kw }
            if (left >= tierKw) {
                for (d in tier) out += ShedAllocation(d.load, d.kw)
                left -= tierKw
            } else {
                val parts = splitLargestRemainder(left, LongArray(tier.size) { tier[it].kw })
                for ((i, d) in tier.withIndex()) if (parts[i] > 0L) out += ShedAllocation(d.load, parts[i].coerceAtMost(d.kw))
                left = 0L
            }
        }
        return out
    }
}

/** A stretch of consecutive hours in which a grid could not serve all its load. */
class BlackoutRecord(val id: BlackoutId, val grid: GridId, val startTick: Long, val startHour: Int) {
    var hours: Int = 0
        internal set
    var peakShedKw: Long = 0L
        internal set
    var shedKwh: Long = 0L
        internal set
    var open: Boolean = true
        internal set
    var endTick: Long = -1L
        internal set
    var damage: Money = Money.ZERO
        internal set
    internal val affectedLoads = java.util.TreeSet<LoadId>()

    val loadsAffected: Int get() = affectedLoads.size

    /** The hour count from the first dark hour, a number the reports use for ordering. */
    val startedAtHour: Long get() = startTick * GridUnits.HOURS_PER_DAY + startHour

    internal fun addHour(shedKw: Long, loads: Collection<LoadId>, value: Money) {
        hours++
        shedKwh += shedKw
        if (shedKw > peakShedKw) peakShedKw = shedKw
        affectedLoads.addAll(loads)
        damage = damage + value
    }

    internal fun close(tick: Long) { open = false; endTick = tick }

    internal fun restore(hours: Int, peak: Long, shed: Long, open: Boolean, endTick: Long, damage: Money, affected: List<LoadId>) {
        this.hours = hours; peakShedKw = peak; shedKwh = shed; this.open = open; this.endTick = endTick; this.damage = damage
        affectedLoads.clear(); affectedLoads.addAll(affected)
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(grid.value).putLong(startTick).putInt(startHour).putInt(hours).putLong(peakShedKw).putLong(shedKwh)
        h.putBoolean(open).putLong(endTick).putMoney(damage).putInt(affectedLoads.size)
        for (l in affectedLoads) h.putLong(l.value)
    }
}

/**
 * What a day without full power did to one connected facility: hours cut off, energy missed, and the share of its
 * condition lost to hard stops. The wiring turns this into downtime and wear on the facility.
 */
data class FacilityImpact(
    val grid: GridId,
    val load: LoadId,
    val facility: FacilityId,
    val company: CompanyId,
    val shedHours: Int,
    val shedKwh: Long,
    val conditionLossPpm: Long,
)

object BlackoutDamage {
    const val MAX_LOSS_PPM = 300_000L

    /** Share of condition lost by a facility of this class after [hours] hours without full supply in one day. */
    fun conditionLossPpm(customer: CustomerClass, hours: Int): Long =
        (customer.damagePerHourPpm * hours).coerceAtMost(MAX_LOSS_PPM)
}
