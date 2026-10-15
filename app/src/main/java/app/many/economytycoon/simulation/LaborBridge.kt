package app.many.economytycoon.simulation

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.LaborRequirement
import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.LaborSupply
import app.many.economytycoon.workforce.CohortKey
import app.many.economytycoon.workforce.LaborMarkets
import app.many.economytycoon.workforce.MarketKey
import app.many.economytycoon.workforce.RoleCatalog
import app.many.economytycoon.workforce.SkillTier
import app.many.economytycoon.workforce.WorkRole
import app.many.economytycoon.workforce.WorkforceService
import app.many.economytycoon.world.WorldState

/** Builds the workforce role catalog from content definitions. */
object RoleCatalogs {
    fun tierOf(band: SkillBand): SkillTier = when (band) {
        SkillBand.UNSKILLED -> SkillTier.BASIC
        SkillBand.SKILLED -> SkillTier.TRAINED
        SkillBand.SPECIALIST -> SkillTier.SPECIALIST
        SkillBand.EXPERT -> SkillTier.EXPERT
    }

    fun from(content: ContentRepository): RoleCatalog = RoleCatalog(
        content.allRoles.map { WorkRole(it.id, it.name, tierOf(it.skillBand), it.baseWage, it.trainingTicks) },
    )
}

/** Tells the production engine how well a facility is staffed. */
class WorkforceLabor(private val workforce: WorkforceService) : LaborSupply {
    override fun staffingPpm(facility: Facility, required: List<LaborRequirement>, tick: Tick): Long {
        if (required.isEmpty()) return 1_000_000L
        var weighted = 0L
        var heads = 0L
        for (r in required) {
            val key = CohortKey(facility.owner, facility.id, r.role, facility.region)
            weighted += workforce.staffingPpm(key, r.headcount.toLong(), tick) * r.headcount
            heads += r.headcount
        }
        return if (heads == 0L) 1_000_000L else weighted / heads
    }

    override fun onStrike(facility: Facility, tick: Tick): Boolean =
        workforce.cohortsAt(facility.id).any { it.onStrike(tick) }
}

/**
 * Keeps the labor markets in step with the world's population: each region's jobseekers are split over
 * the roles by skill tier and local education, less those already employed in the role.
 */
class LaborSupplyUpdater(
    private val world: WorldState,
    private val content: ContentRepository,
    private val workforce: WorkforceService,
    private val markets: LaborMarkets,
) {
    private val roles = content.allRoles
    private val byTier: Map<SkillTier, List<app.many.economytycoon.content.RoleDef>> =
        roles.groupBy { RoleCatalogs.tierOf(it.skillBand) }

    /** Share of the labor force that fits each tier, ppm, for a region's education level. */
    fun tierShares(educationPpm: Long): Map<SkillTier, Long> {
        val expert = 20_000L + educationPpm * 60_000L / 1_000_000L
        val specialist = 80_000L + educationPpm * 200_000L / 1_000_000L
        val trained = 300_000L + educationPpm * 100_000L / 1_000_000L
        val basic = 1_000_000L - expert - specialist - trained
        return mapOf(SkillTier.BASIC to basic, SkillTier.TRAINED to trained, SkillTier.SPECIALIST to specialist, SkillTier.EXPERT to expert)
    }

    fun update() {
        for (region in world.regions()) {
            val cities = world.citiesIn(region.id)
            if (cities.isEmpty()) continue
            val labor = cities.sumOf { world.population(it.id).laborForce }
            val education = cities.sumOf { world.population(it.id).educationPpm * world.population(it.id).total } /
                cities.sumOf { world.population(it.id).total }.coerceAtLeast(1L)
            val shares = tierShares(education)
            val employed = java.util.HashMap<app.many.economytycoon.core.RoleId, Long>()
            for (c in workforce.all()) if (c.key.region == region.id) employed.merge(c.key.role, c.headcount + c.pendingCount, Long::plus)
            for ((tier, defs) in byTier) {
                val tierLabor = mulDiv(labor, shares[tier] ?: 0L, 1_000_000L)
                val perRole = tierLabor / defs.size.coerceAtLeast(1)
                for (d in defs) {
                    val free = perRole - (employed[d.id] ?: 0L)
                    markets.setSupply(MarketKey(region.id, d.id), free)
                }
            }
        }
    }

    /** Pushes regional employment and wages back into the population model, spread over each region's cities. */
    fun recordEmployment() {
        for (region in world.regions()) {
            val cities = world.citiesIn(region.id)
            if (cities.isEmpty()) continue
            val employed = workforce.employedInRegion(region.id)
            val wage = workforce.averageWageInRegion(region.id)
            val totalLabor = cities.sumOf { world.population(it.id).laborForce }.coerceAtLeast(1L)
            var left = employed
            for ((i, c) in cities.withIndex()) {
                val share = if (i == cities.lastIndex) left else mulDiv(employed, world.population(c.id).laborForce, totalLabor).coerceAtMost(left)
                left -= share
                world.recordLabor(c.id, share, if (wage.isPositive) wage else Money.ZERO)
            }
        }
    }
}
