package app.many.economytycoon.intel

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.simulation.Economy

/** Reads who makes what, and how much, from the production lines of every company. */
class SectorIntel(private val e: Economy) {
    private fun name(c: CompanyId) = e.companies.company(c)?.name ?: "Company ${c.value}"

    private fun priceRatio(good: GoodId): Long {
        val ref = e.content.good(good).referencePrice.perUnit.raw
        if (ref <= 0L) return 1_000_000L
        return mulDiv(e.markets.averagePrice(good).perUnit.raw, 1_000_000L, ref)
    }

    /** Every enabled line with a recipe, as the goods it makes and the goods it takes in. */
    fun flows(): List<LineFlow> {
        val out = ArrayList<LineFlow>()
        for (f in e.production.facilities()) {
            if (f.status != FacilityStatus.OPERATING) continue
            for (line in f.lines) {
                val id = line.recipe ?: continue
                if (!line.enabled) continue
                val r = e.content.recipe(id)
                val days = r.durationTicks.coerceAtLeast(1)
                // A line that has not had a chance to run yet is counted at full tilt, as the owner plans it.
                val util = if (line.batchesCompleted == 0L && line.utilizationPpm == 0L) 1_000_000L else line.utilizationPpm
                for (o in r.outputs) out += LineFlow(f.owner, f.id, o.good, o.quantity.raw / days, util, true)
                for (i in r.inputs) out += LineFlow(f.owner, f.id, i.good, i.quantity.raw / days, util, false)
            }
        }
        return out
    }

    fun sectors(): List<GoodSector> = SectorMath.build(flows(), ::name, ::priceRatio)

    fun sector(good: GoodId): GoodSector? = sectors().firstOrNull { it.good == good }

    /** Goods the company makes, with its place in each, biggest share first. */
    fun positionsOf(company: CompanyId): List<Pair<GoodSector, Long>> =
        sectors().filter { s -> s.producers.any { it.company == company } }.map { it to it.shareOf(company) }.sortedByDescending { it.second }

    /** Goods where a new producer would meet little competition and a good price, the best-priced first. */
    fun openings(): List<GoodSector> =
        sectors().filter { (it.producers.isEmpty() && it.industryUsePerDay > 0L) || it.isOpening() }
            .sortedByDescending { it.priceRatioPpm }
}
