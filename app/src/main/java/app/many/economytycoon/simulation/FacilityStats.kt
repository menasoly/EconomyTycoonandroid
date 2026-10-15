package app.many.economytycoon.simulation

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.production.FacilityReport

/**
 * Rolling profit per facility: what it made in goods, less what it consumed and cost to keep running,
 * smoothed over about a month. AI companies read this to decide which sites to keep.
 */
class FacilityStats(private val windowTicks: Long = 30L) {
    private val profit = java.util.TreeMap<FacilityId, Long>()

    fun profitOf(f: FacilityId): Money = Money(profit[f] ?: 0L)

    fun record(report: FacilityReport) {
        val delta = report.inventoryValueDelta.raw - report.upkeep.raw - report.machineMaintenance.raw
        val old = profit[report.facility] ?: 0L
        profit[report.facility] = old - old / windowTicks + delta
    }

    fun forget(f: FacilityId) { profit.remove(f) }
    fun forgetAll(facilities: Collection<FacilityId>) { for (f in facilities) profit.remove(f) }

    internal fun entries(): List<Pair<FacilityId, Long>> = profit.map { it.key to it.value }
    internal fun restore(list: List<Pair<FacilityId, Long>>) { profit.clear(); for ((f, v) in list) profit[f] = v }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(profit.size)
        for ((f, v) in profit) h.putLong(f.value).putLong(v)
    }
}
