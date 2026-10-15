package app.many.economytycoon.markets

import app.many.economytycoon.content.DemandProfileDef
import app.many.economytycoon.content.IncomeBand
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Season
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.world.WorldState

/** Read-only view of who lives where; keeps demand independent from the full world model. */
interface PopulationView {
    fun regions(): List<RegionId>
    fun consumers(region: RegionId, band: IncomeBand): Long

    /** Purchasing power relative to the world average, ppm. */
    fun incomeIndexPpm(region: RegionId): Long = 1_000_000L
}

class WorldPopulationView(private val world: WorldState) : PopulationView {
    override fun regions(): List<RegionId> = world.regions().map { it.id }

    override fun consumers(region: RegionId, band: IncomeBand): Long {
        var t = 0L
        for (c in world.citiesIn(region)) t += world.consumers(c.id, band)
        return t
    }

    override fun incomeIndexPpm(region: RegionId): Long {
        var wageSum = 0L; var people = 0L
        for (p in world.populations()) { wageSum += p.averageWage.raw * p.total; people += p.total }
        if (people == 0L) return 1_000_000L
        val avg = wageSum / people
        var rw = 0L; var rp = 0L
        for (c in world.citiesIn(region)) { val p = world.population(c.id); rw += p.averageWage.raw * p.total; rp += p.total }
        if (rp == 0L || avg == 0L) return 1_000_000L
        return mulDiv(rw / rp, 1_000_000L, avg).coerceIn(300_000L, 3_000_000L)
    }
}

/** A price level at which households will buy, and how much more they buy at that level than one level up. */
class DemandStep(val limit: Long, val quantity: Long)

/**
 * Builds the household demand curve for one good in one region as a ladder of bids. Demand is linear in
 * price around the reference price: demand(m) = base * (1 - elasticity * (m - 1)), clamped to [0, 3 * base].
 */
object ConsumerDemand {
    private val NODES = longArrayOf(400_000, 600_000, 800_000, 1_000_000, 1_250_000, 1_600_000, 2_000_000, 3_000_000, 5_000_000)

    /** Quantity households want per tick at the reference price, raw units. */
    fun baseQuantity(profile: DemandProfileDef, population: PopulationView, region: RegionId, season: Season): Long {
        var total = 0L
        for (band in IncomeBand.values()) {
            val milli = profile.perCapitaMilli[band] ?: 0L
            total += population.consumers(region, band) * milli
        }
        val seasonal = profile.seasonality[season.name] ?: 1_000_000L
        val buyers = 1_000_000L - profile.nonConsumersPpm
        total = mulDiv(total, seasonal, 1_000_000L)
        total = mulDiv(total, buyers, 1_000_000L)
        val incomeIdx = population.incomeIndexPpm(region)
        val incomeFactor = 1_000_000L + mulDiv(incomeIdx - 1_000_000L, profile.incomeElasticityPpm, 1_000_000L)
        return mulDiv(total, incomeFactor.coerceAtLeast(100_000L), 1_000_000L)
    }

    fun demandAt(base: Long, elasticityPpm: Long, multiplierPpm: Long): Long {
        val change = mulDiv(elasticityPpm, multiplierPpm - 1_000_000L, 1_000_000L)
        val factor = (1_000_000L - change).coerceIn(0L, 3_000_000L)
        return mulDiv(base, factor, 1_000_000L)
    }

    fun ladder(profile: DemandProfileDef, base: Long, referencePrice: Long, shiftPpm: Long): List<DemandStep> {
        val shifted = mulDiv(base, shiftPpm, 1_000_000L)
        if (shifted <= 0L || referencePrice <= 0L) return emptyList()
        val steps = ArrayList<DemandStep>()
        for (k in NODES.indices) {
            val here = demandAt(shifted, profile.elasticityPpm, NODES[k])
            val above = if (k + 1 < NODES.size) demandAt(shifted, profile.elasticityPpm, NODES[k + 1]) else 0L
            val q = here - above
            if (q > 0) steps += DemandStep(mulDiv(referencePrice, NODES[k], 1_000_000L), q)
        }
        return steps
    }

    /** Expected spending at a price for budget truncation, raw money. */
    fun spendAt(steps: List<DemandStep>, price: Long): Long {
        var q = 0L
        for (s in steps) if (s.limit >= price) q += s.quantity
        return mulDiv(q, price, 1_000L)
    }
}
