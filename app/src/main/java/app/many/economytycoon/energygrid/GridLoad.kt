package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId

/**
 * Who is drawing power. The rank says who is cut off first when there is not enough: customers on an interruptible
 * contract go before shops, shops before factories, and homes last.
 */
enum class CustomerClass(val label: String, val shedRank: Int, val thermalSharePpm: Long, val damagePerHourPpm: Long) {
    INTERRUPTIBLE("Interruptible industry", 0, 60_000L, 12_000L),
    COMMERCIAL("Commercial", 1, 250_000L, 6_000L),
    INDUSTRIAL("Industrial", 2, 60_000L, 20_000L),
    HOUSEHOLD("Households", 3, 350_000L, 0L),
}

enum class LoadKind { FACILITY, HOUSEHOLDS }

/** One metered connection: a facility, or all the households of the region. */
class GridLoad(
    val id: LoadId,
    val grid: GridId,
    val kind: LoadKind,
    val customer: CustomerClass,
    val company: CompanyId?,
    val facility: FacilityId?,
) {
    init {
        require((kind == LoadKind.FACILITY) == (facility != null && company != null)) { "a facility load names its facility and company" }
        require(kind == LoadKind.FACILITY || customer == CustomerClass.HOUSEHOLD) { "household loads belong to the household class" }
    }

    /** Average draw in kilowatts before the daily and seasonal shape. */
    var baseKw: Long = 0L
        internal set
    var connected: Boolean = true
        internal set

    /** Energy delivered and cut off since the last bill. */
    var monthServedKwh: Long = 0L
        internal set
    var monthShedKwh: Long = 0L
        internal set
    var lifetimeShedKwh: Long = 0L
        internal set

    /** Hours without full supply today, for damage and reports. */
    var shedHoursToday: Int = 0
        internal set

    internal fun restore(baseKw: Long, connected: Boolean, monthServed: Long, monthShed: Long, lifetimeShed: Long, shedHoursToday: Int) {
        this.baseKw = baseKw; this.connected = connected; monthServedKwh = monthServed; monthShedKwh = monthShed
        lifetimeShedKwh = lifetimeShed; this.shedHoursToday = shedHoursToday
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(grid.value).putInt(kind.ordinal).putInt(customer.ordinal).putLong(company?.value ?: -1L).putLong(facility?.value ?: -1L)
        h.putLong(baseKw).putBoolean(connected).putLong(monthServedKwh).putLong(monthShedKwh).putLong(lifetimeShedKwh).putInt(shedHoursToday)
    }
}
