package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

enum class ShortageKind(val label: String) {
    NONE("No shortage"), QUANTITY("Too few people"), QUALITY("People lack the skill"), BOTH("Too few, and under-skilled")
}

/** One skill in one region. */
data class PoolKey(val region: RegionId, val skill: SkillId) : Comparable<PoolKey> {
    override fun compareTo(other: PoolKey): Int {
        val c = region.compareTo(other.region)
        return if (c != 0) c else skill.compareTo(other.skill)
    }
}

data class MarketParams(
    /** How fast measured tightness moves the smoothed figure each month. */
    val tightnessSmoothPpm: Long = 250_000L,
    /** Wage premium per unit of tightness above or below balance. */
    val premiumPerTightnessPpm: Long = 350_000L,
    val premiumStepPpm: Long = 40_000L,
    val minPremiumPpm: Long = 750_000L,
    val maxPremiumPpm: Long = 2_200_000L,
    val shortageTightnessPpm: Long = 1_250_000L,
    val easedTightnessPpm: Long = 1_100_000L,
    /** The pool counts as under-skilled when its mean is this far below what the work asks. */
    val qualityGapPpm: Long = 120_000L,
    /** Share of people in the pool who find other work or leave the labour force each year. */
    val poolChurnAnnualPpm: Long = 250_000L,
    /** Share of employed people who change jobs or leave each year; sets the baseline demand for replacements. */
    val replacementAnnualPpm: Long = 150_000L,
    /** Only people at this share of the level a job asks count as ready to hire. */
    val hireReadyPpm: Long = 800_000L,
    val genericSpreadPpm: Long = 120_000L,
    /** Wage change per point of skill above or below what the role asks. */
    val wagePerSkillPpm: Long = 600_000L,
    val minSkillWagePpm: Long = 700_000L,
    val maxSkillWagePpm: Long = 1_500_000L,
)

/** The state of one skill's market in one region. */
class SkillMarket {
    var supply: Long = 0L
        internal set
    var demandFlow: Long = 0L
        internal set
    var employed: Long = 0L
        internal set
    var tightnessPpm: Long = Ppm.ONE
        internal set
    var premiumPpm: Long = Ppm.ONE
        internal set
    var poolMeanPpm: Long = 0L
        internal set
    var requiredPpm: Long = 0L
        internal set
    var shortage: ShortageKind = ShortageKind.NONE
        internal set
    var shortageSince: Long = -1L
        internal set
    /** Graduates and immigrants who joined the pool in the last 360 days. */
    var inflowYear: Long = 0L
        internal set

    val qualityGapPpm: Long get() = (requiredPpm - poolMeanPpm).coerceAtLeast(0L)

    internal fun restore(
        supply: Long, demand: Long, employed: Long, tight: Long, premium: Long, mean: Long, required: Long,
        shortage: ShortageKind, since: Long, inflow: Long,
    ) {
        this.supply = supply; demandFlow = demand; this.employed = employed; tightnessPpm = tight; premiumPpm = premium
        poolMeanPpm = mean; requiredPpm = required; this.shortage = shortage; shortageSince = since; inflowYear = inflow
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(supply).putLong(demandFlow).putLong(employed).putLong(tightnessPpm).putLong(premiumPpm).putLong(poolMeanPpm)
            .putLong(requiredPpm).putInt(shortage.ordinal).putLong(shortageSince).putLong(inflowYear)
    }
}

/** One line of the shortage report. */
data class ShortageRow(
    val region: RegionId,
    val skill: SkillId,
    val kind: ShortageKind,
    val supply: Long,
    val demandFlow: Long,
    val tightnessPpm: Long,
    val premiumPpm: Long,
    val qualityGapPpm: Long,
    val since: Long,
) {
    /** Larger is worse. */
    val severity: Long get() = (tightnessPpm - Ppm.ONE).coerceAtLeast(0L) + qualityGapPpm
}

/** Who looks for work, and what skill is worth. */
class SkillMarketService(
    private val state: TalentState,
    private val taxonomy: SkillTaxonomy,
    val params: MarketParams = MarketParams(),
    private val dynamics: SkillDynamicsParams = SkillDynamicsParams(),
) {
    fun pool(region: RegionId, skill: SkillId): SkillDistribution = state.pools[PoolKey(region, skill)]?.copy() ?: SkillDistribution()
    fun poolSize(region: RegionId, skill: SkillId): Long = state.pools[PoolKey(region, skill)]?.total ?: 0L
    fun poolSize(region: RegionId): Long = state.pools.entries.filter { it.key.region == region }.sumOf { it.value.total }
    fun market(region: RegionId, skill: SkillId): SkillMarket? = state.markets[PoolKey(region, skill)]
    fun premium(region: RegionId, skill: SkillId): Long = state.markets[PoolKey(region, skill)]?.premiumPpm ?: Ppm.ONE
    fun markets(): Map<PoolKey, SkillMarket> = state.markets

    /** The level a job's main skill asks for, taken as the strictest of the roles that rely on it. */
    fun requiredLevel(skill: SkillId): Long =
        taxonomy.rolesWithPrimary(skill).maxOfOrNull { it.requirements.first().requiredPpm } ?: 0L

    // ----- the pool ---------------------------------------------------------------------------

    internal fun deposit(region: RegionId, skill: SkillId, group: SkillDistribution) {
        if (group.isEmpty) return
        state.pools.getOrPut(PoolKey(region, skill)) { SkillDistribution() }.addAll(group)
        state.markets.getOrPut(PoolKey(region, skill)) { SkillMarket() }.inflowYear += group.total
    }

    /** Takes [n] ordinary job seekers out of the pool (people who sign up for a course). */
    internal fun takeFromPool(region: RegionId, skill: SkillId, n: Long): SkillDistribution =
        state.pools[PoolKey(region, skill)]?.take(n, TakePolicy.PROPORTIONAL) ?: SkillDistribution()

    /** Returns people to the pool without counting them as new arrivals (quits, layoffs). */
    internal fun release(region: RegionId, skill: SkillId, group: SkillDistribution) {
        if (group.isEmpty) return
        state.pools.getOrPut(PoolKey(region, skill)) { SkillDistribution() }.addAll(group)
    }

    /** What an average person in the region has of a skill when nothing better is known. */
    fun genericLevel(world: TalentWorld, region: RegionId, skill: SkillId): Long {
        val edu = world.educationPpm(region)
        val level = when (taxonomy.skillOrNull(skill)?.pathway ?: Pathway.EXPERIENCE) {
            Pathway.SCHOOL -> 250_000L + (edu - 400_000L) / 2
            Pathway.VOCATIONAL -> 300_000L + Ppm.mul(edu, 350_000L)
            Pathway.UNIVERSITY -> 350_000L + Ppm.mul(edu, 350_000L)
            Pathway.EXPERIENCE -> 300_000L
        }
        return level.coerceIn(100_000L, 800_000L)
    }

    fun genericGroup(world: TalentWorld, region: RegionId, skill: SkillId, n: Long): SkillDistribution =
        SkillDistribution.around(genericLevel(world, region, skill), params.genericSpreadPpm, n)

    /**
     * Takes [n] hires for a trade. A share of them, set by [bestShare], are the best the pool has (employers who pay
     * well pick first); the rest are a cross-section. When the pool runs dry the rest are ordinary local workers.
     */
    internal fun hire(world: TalentWorld, region: RegionId, skill: SkillId, n: Long, bestShare: Long): SkillDistribution {
        val out = SkillDistribution()
        if (n <= 0L) return out
        val pool = state.pools[PoolKey(region, skill)]
        if (pool != null && !pool.isEmpty) {
            val best = Ppm.of(n, Ppm.clamp(bestShare))
            out.addAll(pool.take(best, TakePolicy.BEST_FIRST))
            out.addAll(pool.take(n - out.total, TakePolicy.PROPORTIONAL))
        }
        if (out.total < n) out.addAll(genericGroup(world, region, skill, n - out.total))
        return out
    }

    // ----- monthly update -----------------------------------------------------------------------

    /**
     * Forgetting and churn in the pools, then each market's tightness, premium and shortage flag. [openings] and
     * [employed] are counted by the caller per trade.
     */
    internal fun monthly(tick: Tick, openings: Map<PoolKey, Long>, employed: Map<PoolKey, Long>, events: MutableList<TalentEvent>) {
        for ((key, dist) in state.pools) {
            val skill = taxonomy.skillOrNull(key.skill) ?: continue
            SkillDynamics.forget(dist, skill, 30L, dynamics.idleForgetMultiplier)
            val leaving = Ppm.of(dist.total, Ppm.perDays(params.poolChurnAnnualPpm, 30L))
            if (leaving > 0L) dist.take(leaving, TakePolicy.PROPORTIONAL)
        }
        val keys = java.util.TreeSet<PoolKey>()
        keys.addAll(state.pools.keys); keys.addAll(openings.keys); keys.addAll(employed.keys); keys.addAll(state.markets.keys)
        for (key in keys) {
            val required = requiredLevel(key.skill)
            if (required == 0L) continue
            val m = state.markets.getOrPut(key) { SkillMarket() }
            val pool = state.pools[key]
            val ready = pool?.countAtLeast(Ppm.mul(required, params.hireReadyPpm)) ?: 0L
            m.supply = ready
            m.poolMeanPpm = pool?.mean() ?: 0L
            m.requiredPpm = required
            m.employed = employed[key] ?: 0L
            m.demandFlow = (openings[key] ?: 0L) + Ppm.of(m.employed, params.replacementAnnualPpm)
            val supplyFlow = ready + m.inflowYear
            val measured = if (m.demandFlow == 0L) 0L else Ppm.ratio(m.demandFlow, supplyFlow + 1L).coerceAtMost(4 * Ppm.ONE)
            m.tightnessPpm = Ppm.approach(m.tightnessPpm, measured, params.tightnessSmoothPpm)
            val targetPremium = (Ppm.ONE + Ppm.mul(m.tightnessPpm - Ppm.ONE, params.premiumPerTightnessPpm)).coerceIn(params.minPremiumPpm, params.maxPremiumPpm)
            m.premiumPpm = Ppm.approach(m.premiumPpm, targetPremium, params.premiumStepPpm).coerceIn(params.minPremiumPpm, params.maxPremiumPpm)
            val before = m.shortage
            m.shortage = classify(m, ready)
            if (before == ShortageKind.NONE && m.shortage != ShortageKind.NONE) {
                m.shortageSince = tick.value
                events += TalentEvent.ShortageRaised(key.region, key.skill, m.shortage, tick)
            } else if (before != ShortageKind.NONE && m.shortage == ShortageKind.NONE) {
                m.shortageSince = -1L
                events += TalentEvent.ShortageEased(key.region, key.skill, tick)
            }
        }
    }

    private fun classify(m: SkillMarket, ready: Long): ShortageKind {
        val busy = m.demandFlow > 0L
        val qty = busy && (m.tightnessPpm >= params.shortageTightnessPpm || (m.shortage != ShortageKind.NONE && m.shortage != ShortageKind.QUALITY && m.tightnessPpm >= params.easedTightnessPpm))
        val qual = busy && m.poolMeanPpm > 0L && m.qualityGapPpm >= params.qualityGapPpm && ready < m.demandFlow * 2
        return when {
            qty && qual -> ShortageKind.BOTH
            qty -> ShortageKind.QUANTITY
            qual -> ShortageKind.QUALITY
            else -> ShortageKind.NONE
        }
    }

    /** Called once a year so the arrival counters cover a rolling year rather than all time. */
    internal fun rollYear() {
        for (m in state.markets.values) m.inflowYear = m.inflowYear * 2 / 3
    }

    fun shortages(region: RegionId? = null): List<ShortageRow> =
        state.markets.entries.filter { it.value.shortage != ShortageKind.NONE && (region == null || it.key.region == region) }
            .map { (k, m) -> ShortageRow(k.region, k.skill, m.shortage, m.supply, m.demandFlow, m.tightnessPpm, m.premiumPpm, m.qualityGapPpm, m.shortageSince) }
            .sortedWith(compareByDescending<ShortageRow> { it.severity }.thenBy { it.region }.thenBy { it.skill })

    // ----- wages ------------------------------------------------------------------------------------

    /** Wage index of a person (or group) at [meanLevel] in a role asking [requiredLevel]: skill above the ask is paid. */
    fun skillWageIndex(meanLevel: Long, requiredLevel: Long): Long =
        (Ppm.ONE + Ppm.mul(meanLevel - requiredLevel, params.wagePerSkillPpm)).coerceIn(params.minSkillWagePpm, params.maxSkillWagePpm)

    /** What a cohort should be paid compared with the role's base wage: its skill level times the local scarcity premium. */
    fun wageIndexFor(profile: RoleProfile, region: RegionId, meanLevel: Long): Long =
        Ppm.mul(skillWageIndex(meanLevel, profile.requirements.first().requiredPpm), premium(region, profile.primary))

    /** How much more a graduate-level skill earns than an entry-level one in the region: the return that pulls students in. */
    fun returnToSkill(region: RegionId, skill: SkillId): Long = premium(region, skill)
}
