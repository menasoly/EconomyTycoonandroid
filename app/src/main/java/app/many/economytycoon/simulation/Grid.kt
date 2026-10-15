package app.many.economytycoon.simulation

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.core.splitLargestRemainder
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.production.EnergySupply
import app.many.economytycoon.production.Facility

data class GridParams(
    /** Public supply per tick for every thousand inhabitants, in energy units. */
    val baselinePerThousand: Long = 600,
    /** Price of energy relative to the good's reference price at idle, ppm. */
    val basePricePpm: Long = 1_000_000,
    /** Extra price at full load, ppm. */
    val loadSurchargePpm: Long = 1_500_000,
)

/**
 * Regional electricity pools. Each region has a public baseline supply plus whatever the power plants
 * there produced last tick. Facilities draw from the pool; what they draw is billed at a price that rises
 * with load and shared out between the plant owners and the public utility.
 */
class Grid(
    private val electricity: GoodId,
    private val referencePrice: Money,
    val params: GridParams = GridParams(),
) : EnergySupply {
    private class Pool {
        var capacity = 0L
        var drawn = 0L
    }

    private val pools = java.util.TreeMap<RegionId, Pool>()
    private val producers = java.util.TreeMap<RegionId, java.util.TreeMap<CompanyId, Long>>()
    private val bills = java.util.TreeMap<CompanyId, Long>()
    private val owed = java.util.TreeMap<RegionId, Long>()
    private val billedRegion = java.util.TreeMap<Pair<RegionId, CompanyId>, Long>(compareBy({ it.first.value }, { it.second.value }))
    private val nextProducers = java.util.TreeMap<RegionId, java.util.TreeMap<CompanyId, Long>>()
    private val baseline = java.util.TreeMap<RegionId, Long>()

    fun setBaseline(region: RegionId, populationHere: Long) {
        baseline[region] = mulDiv(populationHere, params.baselinePerThousand, 1_000L)
    }

    /** Opens the tick: pools hold the baseline plus last tick's plant output. */
    fun beginTick() {
        producers.clear()
        for ((r, m) in nextProducers) producers[r] = java.util.TreeMap(m)
        nextProducers.clear()
        for ((region, base) in baseline) {
            val p = pools.getOrPut(region) { Pool() }
            p.capacity = base + (producers[region]?.values?.sum() ?: 0L)
            p.drawn = 0
        }
    }

    fun price(region: RegionId): Money {
        val p = pools[region] ?: return referencePrice
        val load = if (p.capacity <= 0L) 1_000_000L else mulDiv(p.drawn, 1_000_000L, p.capacity).coerceAtMost(1_000_000L)
        val ppm = params.basePricePpm + mulDiv(params.loadSurchargePpm, load, 1_000_000L)
        return Money(mulDiv(referencePrice.raw, ppm, 1_000_000L))
    }

    override fun draw(facility: Facility, wanted: Quantity): Quantity {
        val pool = pools[facility.region] ?: return Quantity.ZERO
        val left = (pool.capacity - pool.drawn).coerceAtLeast(0L)
        val got = minOf(wanted.raw, left)
        if (got <= 0L) return Quantity.ZERO
        val cost = mulDiv(price(facility.region).raw, got, 1_000L)
        pool.drawn += got
        bills.merge(facility.owner, cost, Long::plus)
        owed.merge(facility.region, cost, Long::plus)
        billedRegion.merge(facility.region to facility.owner, cost, Long::plus)
        return Quantity(got)
    }

    /** Records plant output of this tick so that it can supply the next one. */
    fun recordProduction(region: RegionId, owner: CompanyId, units: Quantity) {
        if (units.raw <= 0L) return
        nextProducers.getOrPut(region) { java.util.TreeMap() }.merge(owner, units.raw, Long::plus)
    }

    /** What each company owes the grid for the tick. Cleared by [clearBills]. */
    fun bills(): Map<CompanyId, Money> = bills.mapValues { Money(it.value) }

    /**
     * How the money collected in a region is shared: plant owners by their share of the pool's capacity,
     * the public utility (null) by the baseline's share.
     */
    fun payees(region: RegionId, amount: Money): List<Pair<CompanyId?, Money>> {
        if (amount.raw <= 0L) return emptyList()
        val plants = producers[region].orEmpty()
        val base = baseline[region] ?: 0L
        val keys = ArrayList<CompanyId?>(); val weights = ArrayList<Long>()
        keys += null; weights += base
        for ((c, u) in plants) { keys += c; weights += u }
        if (weights.all { it == 0L }) return listOf(null to amount)
        val parts = splitLargestRemainder(amount.raw, weights.toLongArray())
        return keys.indices.filter { parts[it] > 0L }.map { keys[it] to Money(parts[it]) }
    }

    fun billsByRegion(): Map<Pair<RegionId, CompanyId>, Money> = billedRegion.mapValues { Money(it.value) }

    fun clearBills() { bills.clear(); owed.clear(); billedRegion.clear() }

    fun capacity(region: RegionId): Long = pools[region]?.capacity ?: 0L
    fun drawn(region: RegionId): Long = pools[region]?.drawn ?: 0L
    fun loadPpm(region: RegionId): Long {
        val p = pools[region] ?: return 0L
        return if (p.capacity <= 0L) 1_000_000L else mulDiv(p.drawn, 1_000_000L, p.capacity)
    }

    internal fun baselines(): List<Pair<RegionId, Long>> = baseline.map { it.key to it.value }
    internal fun restoreBaselines(list: List<Pair<RegionId, Long>>) { baseline.clear(); for ((r, v) in list) baseline[r] = v }

    internal fun pendingProduction() = nextProducers.flatMap { (r, m) -> m.map { Triple(r, it.key, it.value) } }
    internal fun restoreProduction(list: List<Triple<RegionId, CompanyId, Long>>) {
        nextProducers.clear()
        for ((r, c, u) in list) nextProducers.getOrPut(r) { java.util.TreeMap() }[c] = u
    }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(baseline.size)
        for ((r, v) in baseline) h.putLong(r.value).putLong(v)
        h.putInt(nextProducers.size)
        for ((r, m) in nextProducers) { h.putLong(r.value); for ((c, u) in m) h.putLong(c.value).putLong(u) }
    }
}
