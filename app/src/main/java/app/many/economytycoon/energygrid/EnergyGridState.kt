package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Hashable
import app.many.economytycoon.core.StateHash

/**
 * Everything the energy grid module saves: the grids with their units and loads, the interconnectors, the power
 * purchase agreements and the id counters. Only [EnergyGridService] changes it; everything else reads.
 */
class EnergyGridState : Hashable {
    internal val gridMap = java.util.TreeMap<GridId, Grid>()
    internal val linkMap = java.util.TreeMap<LinkId, Interconnector>()
    internal val ppaMap = java.util.TreeMap<PpaId, Ppa>()
    val counters = GridCounters()

    /** Last day advanced, or -1 before the first. */
    var lastTick: Long = -1L
        internal set

    /** First day of the month last billed. */
    var lastBilledMonth: Long = -1L
        internal set

    val grids: List<Grid> get() = gridMap.values.toList()
    val links: List<Interconnector> get() = linkMap.values.toList()
    val ppas: List<Ppa> get() = ppaMap.values.toList()

    fun grid(id: GridId): Grid? = gridMap[id]
    fun link(id: LinkId): Interconnector? = linkMap[id]
    fun ppa(id: PpaId): Ppa? = ppaMap[id]
    fun gridOfRegion(region: app.many.economytycoon.core.RegionId): Grid? = gridMap.values.firstOrNull { it.region == region }
    fun linksOf(grid: GridId): List<Interconnector> = linkMap.values.filter { it.touches(grid) }
    fun ppasOf(grid: GridId): List<Ppa> = ppaMap.values.filter { it.grid == grid }
    fun ppasOfCompany(company: app.many.economytycoon.core.CompanyId): List<Ppa> = ppaMap.values.filter { it.seller == company || it.buyer == company }

    fun unit(id: UnitId): GenUnit? = gridMap.values.firstNotNullOfOrNull { it.unit(id) }

    val isEmpty: Boolean get() = gridMap.isEmpty()

    internal fun clear() {
        gridMap.clear(); linkMap.clear(); ppaMap.clear()
        counters.nextGrid = 1L; counters.nextUnit = 1L; counters.nextLoad = 1L; counters.nextPpa = 1L; counters.nextLink = 1L; counters.nextBlackout = 1L
        lastTick = -1L; lastBilledMonth = -1L
    }

    override fun hashInto(hasher: CanonicalHasher) {
        hasher.section("energygrid")
        hasher.putLong(lastTick).putLong(lastBilledMonth)
        hasher.putLong(counters.nextGrid).putLong(counters.nextUnit).putLong(counters.nextLoad).putLong(counters.nextPpa).putLong(counters.nextLink).putLong(counters.nextBlackout)
        hasher.putInt(gridMap.size); for (g in gridMap.values) g.hashInto(hasher)
        hasher.putInt(linkMap.size); for (l in linkMap.values) l.hashInto(hasher)
        hasher.putInt(ppaMap.size); for (p in ppaMap.values) p.hashInto(hasher)
    }

    /** The canonical hash of this section on its own. */
    fun stateHash(): StateHash = CanonicalHasher().also { hashInto(it) }.finish()
}
