package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.splitLargestRemainder

data class MigrationParams(
    /** Days between looks at who moves. */
    val reviewEvery: Int = 30,
    /** Share of a pool that leaves in a year when the pull abroad is at its strongest, before mobility. */
    val baseEmigrationAnnualPpm: Long = 150_000L,
    /** How much better abroad must look before anyone goes: the cost and worry of moving. */
    val frictionPpm: Long = 150_000L,
    /** The gap at which the pull stops growing. */
    val saturationPpm: Long = 800_000L,
    /** How readily each tier of skill moves, novice to expert: scarce skills are mobile. */
    val mobilityPpm: List<Long> = listOf(400_000L, 600_000L, 900_000L, 1_400_000L, 2_000_000L),
    /** Share of those who leave who are the best of the pool; the rest are a cross-section. */
    val selectivityPpm: Long = 600_000L,
    /** Qualifications are not fully recognised abroad: arrivals start this far lower. */
    val recognitionLossPpm: Long = 40_000L,
    /** Returning migrants come home with this much more skill. */
    val returnGainPpm: Long = 30_000L,
    val returnAnnualPpm: Long = 150_000L,
    /** Home counts as competitive again when it is within this much of the host country. */
    val returnWithinPpm: Long = 50_000L,
    /** Share of the flow that is drawn from people already in work. */
    val employedShareMax: Long = 500_000L,
    val defaultQuotaPpm: Long = 8_000L,
    /** Waves of at least this many people are announced. */
    val waveThreshold: Long = 40L,
    /** Weight of living conditions (stability and happiness) against pay in how attractive a country is. */
    val lifeWeightPpm: Long = 300_000L,
    val skilledFloorPpm: Long = 550_000L,
)

/** A country's door policy. */
class CountryPolicy(val country: CountryId) {
    /** 0 shuts the door, 1,000,000 welcomes everyone. */
    var opennessPpm: Long = Ppm.ONE
        internal set
    /** Most newcomers a year, as a share of the working-age population. */
    var quotaPpm: Long = 8_000L
        internal set
    /** When set, only people at skilled level can come in. */
    var skilledOnly: Boolean = false
        internal set

    internal fun restore(openness: Long, quota: Long, skilledOnly: Boolean) { opennessPpm = openness; quotaPpm = quota; this.skilledOnly = skilledOnly }
    fun hashInto(h: CanonicalHasher) { h.putLong(country.value).putLong(opennessPpm).putLong(quotaPpm).putBoolean(skilledOnly) }
}

data class DiasporaKey(val from: CountryId, val to: CountryId, val skill: SkillId) : Comparable<DiasporaKey> {
    override fun compareTo(other: DiasporaKey): Int {
        var c = from.compareTo(other.from); if (c != 0) return c
        c = to.compareTo(other.to); if (c != 0) return c
        return skill.compareTo(other.skill)
    }
}

/** A country's migration over the last year, in people. */
class MigrationLedger {
    var out: Long = 0L
        internal set
    var skilledOut: Long = 0L
        internal set
    var arrived: Long = 0L
        internal set
    var returned: Long = 0L
        internal set
    var visasUsed: Long = 0L
        internal set

    /** Positive when the country is losing people. */
    val net: Long get() = out - arrived - returned
    val skilledShareOfOutflow: Long get() = Ppm.ratio(skilledOut, out)

    internal fun fade() { out = out * 2 / 3; skilledOut = skilledOut * 2 / 3; arrived = arrived * 2 / 3; returned = returned * 2 / 3 }
    internal fun restore(out: Long, skilledOut: Long, arrived: Long, returned: Long, visas: Long) {
        this.out = out; this.skilledOut = skilledOut; this.arrived = arrived; this.returned = returned; visasUsed = visas
    }
    fun hashInto(h: CanonicalHasher) { h.putLong(out).putLong(skilledOut).putLong(arrived).putLong(returned).putLong(visasUsed) }
}

/** What a migration review decided for people already in work. */
data class MigrationOutcome(val effects: List<WorkforceEffect>, val events: List<TalentEvent>) {
    companion object { val NONE = MigrationOutcome(emptyList(), emptyList()) }
}

/**
 * Talented people move to where life is better. Pay and living conditions set how attractive a country is; a gap
 * beyond the cost of moving makes some of the pool and some of the employed leave, the mobile and the skilled most
 * of all, and the best of them first. They arrive a little lower in level (credentials are not fully recognised),
 * count against the destination's quota, and may come home with more skill if home catches up.
 */
class MigrationService(
    private val state: TalentState,
    private val taxonomy: SkillTaxonomy,
    private val markets: SkillMarketService,
    private val skills: CohortSkillService,
    val params: MigrationParams = MigrationParams(),
) {
    /** A country's door policy; a country nobody has set one for has the default. Reading never adds anything to the state. */
    fun policy(country: CountryId): CountryPolicy = state.policies[country] ?: CountryPolicy(country).also { it.quotaPpm = params.defaultQuotaPpm }
    fun ledger(country: CountryId): MigrationLedger = state.ledgers[country] ?: MigrationLedger()
    private fun policyFor(country: CountryId): CountryPolicy = state.policies.getOrPut(country) { CountryPolicy(country).also { it.quotaPpm = params.defaultQuotaPpm } }
    private fun ledgerFor(country: CountryId): MigrationLedger = state.ledgers.getOrPut(country) { MigrationLedger() }
    fun diaspora(from: CountryId, to: CountryId, skill: SkillId): SkillDistribution = state.diaspora[DiasporaKey(from, to, skill)]?.copy() ?: SkillDistribution()
    fun diasporaOf(from: CountryId): Long = state.diaspora.entries.filter { it.key.from == from }.sumOf { it.value.total }
    fun diasporaIn(to: CountryId): Long = state.diaspora.entries.filter { it.key.to == to }.sumOf { it.value.total }

    fun setPolicy(country: CountryId, opennessPpm: Long, quotaPpm: Long, skilledOnly: Boolean) {
        val p = policyFor(country)
        p.opennessPpm = Ppm.clamp(opennessPpm); p.quotaPpm = quotaPpm.coerceIn(0L, Ppm.ONE); p.skilledOnly = skilledOnly
    }

    /** How attractive a country is to live and work in: pay, lifted or dulled by living conditions. */
    fun attractiveness(world: TalentWorld, country: CountryId): Long {
        val regions = world.regionsOf(country)
        var heads = 0L; var wage = 0L; var happy = 0L
        for (r in regions) {
            val w = world.workingAge(r).coerceAtLeast(1L)
            heads += w; wage += world.averageWage(r).raw * w; happy += world.happinessPpm(r) * w
        }
        if (heads == 0L) return 0L
        val pay = wage / heads
        val life = (happy / heads + world.stabilityPpm(country)) / 2
        // living conditions lift or dull pay: an average country (life 0.5) is worth its pay exactly
        return Ppm.of(pay, Ppm.ONE - params.lifeWeightPpm + 2L * Ppm.mul(life, params.lifeWeightPpm))
    }

    private fun mobility(levelPpm: Long): Long = params.mobilityPpm[SkillTier.of(levelPpm).ordinal.coerceAtMost(params.mobilityPpm.size - 1)]

    private fun pullAnnual(origin: Long, dest: Long, mobilityPpm: Long): Long {
        if (origin <= 0L || dest <= origin) return 0L
        val gap = Ppm.ratio(dest - origin, origin) - params.frictionPpm
        if (gap <= 0L) return 0L
        val strength = Ppm.clamp(Ppm.ratio(gap, params.saturationPpm))
        return Ppm.mul(Ppm.mul(params.baseEmigrationAnnualPpm, strength), mobilityPpm)
    }

    private fun quotaLeft(world: TalentWorld, dest: CountryId): Long {
        val working = world.regionsOf(dest).sumOf { world.workingAge(it) }
        val annual = Ppm.of(working, policy(dest).quotaPpm)
        return (annual - ledger(dest).visasUsed).coerceAtLeast(0L)
    }

    private fun admits(dest: CountryId, skill: SkillId, level: Long): Boolean {
        val p = policy(dest)
        if (p.opennessPpm <= 0L) return false
        return !p.skilledOnly || level >= params.skilledFloorPpm
    }

    private fun regionWeights(world: TalentWorld, regions: List<RegionId>): LongArray =
        LongArray(regions.size) { (world.averageWage(regions[it]).raw / 100L).coerceAtLeast(1L) }

    private fun arrive(world: TalentWorld, from: CountryId, to: CountryId, skill: SkillId, group: SkillDistribution) {
        if (group.isEmpty) return
        group.shiftAll(-params.recognitionLossPpm)
        val regions = world.regionsOf(to)
        if (regions.isEmpty()) return
        state.diaspora.getOrPut(DiasporaKey(from, to, skill)) { SkillDistribution() }.addAll(group)
        val parts = group.copy().splitInto(regionWeights(world, regions))
        for ((i, r) in regions.withIndex()) markets.deposit(r, skill, parts[i])
        val l = ledgerFor(to); l.arrived += group.total; l.visasUsed += group.total
    }

    /** Looks at who moves. Runs on the review day; other days return nothing. */
    fun review(tick: Tick, world: TalentWorld, inputs: TalentInputs): MigrationOutcome {
        if (tick.value <= 0L || tick.value % params.reviewEvery != 0L) return MigrationOutcome.NONE
        val countries = world.countries()
        if (countries.size < 2) return MigrationOutcome.NONE
        val attr = java.util.TreeMap<CountryId, Long>()
        for (c in countries) attr[c] = attractiveness(world, c)
        val events = ArrayList<TalentEvent>(); val effects = ArrayList<WorkforceEffect>()
        poolFlows(tick, world, countries, attr, events)
        employedFlows(tick, world, inputs, attr, effects, events)
        returns(world, countries, attr)
        return MigrationOutcome(effects, events)
    }

    private fun bestDestination(origin: CountryId, countries: List<CountryId>, attr: Map<CountryId, Long>): List<CountryId> =
        countries.filter { it != origin && (attr[it] ?: 0L) > (attr[origin] ?: 0L) }.sortedWith(compareByDescending<CountryId> { attr[it] ?: 0L }.thenBy { it })

    private fun poolFlows(tick: Tick, world: TalentWorld, countries: List<CountryId>, attr: Map<CountryId, Long>, events: MutableList<TalentEvent>) {
        for (origin in countries) {
            val regions = world.regionsOf(origin)
            val dests = bestDestination(origin, countries, attr)
            if (dests.isEmpty()) continue
            val skillIds = state.pools.keys.filter { it.region in regions }.map { it.skill }.distinct().sorted()
            for (skill in skillIds) {
                val pools = regions.map { state.pools[PoolKey(it, skill)] }
                val sizes = LongArray(regions.size) { pools[it]?.total ?: 0L }
                val total = sizes.sum()
                if (total == 0L) continue
                val mean = pools.filterNotNull().let { ps -> ps.sumOf { it.mean() * it.total } / total }
                var remaining = total
                for (dest in dests) {
                    if (!admits(dest, skill, mean) || remaining <= 0L) continue
                    val annual = pullAnnual(attr.getValue(origin), attr.getValue(dest), mobility(mean))
                    if (annual <= 0L) continue
                    var n = Ppm.of(Ppm.of(total, Ppm.perDays(annual, params.reviewEvery.toLong())), policy(dest).opennessPpm)
                    n = minOf(n, quotaLeft(world, dest), remaining)
                    if (n <= 0L) continue
                    val take = splitLargestRemainder(n, sizes)
                    val group = SkillDistribution()
                    for ((i, pool) in pools.withIndex()) {
                        if (pool == null || take[i] == 0L) continue
                        val best = Ppm.of(take[i], params.selectivityPpm)
                        group.addAll(pool.take(best, TakePolicy.BEST_FIRST))
                        group.addAll(pool.take(take[i] - best, TakePolicy.PROPORTIONAL))
                    }
                    if (group.isEmpty) continue
                    remaining -= group.total
                    val l = ledgerFor(origin)
                    l.out += group.total
                    if (group.mean() >= params.skilledFloorPpm) l.skilledOut += group.total
                    val moved = group.total
                    arrive(world, origin, dest, skill, group)
                    if (moved >= params.waveThreshold) events += TalentEvent.MigrationWave(origin, dest, skill, moved, tick)
                }
            }
        }
    }

    private fun employedFlows(tick: Tick, world: TalentWorld, inputs: TalentInputs, attr: Map<CountryId, Long>, effects: MutableList<WorkforceEffect>, events: MutableList<TalentEvent>) {
        for (fact in inputs.cohorts) {
            val profile = taxonomy.profile(fact.key.role) ?: continue
            val set = skills.get(fact.key) ?: continue
            val primary = set.dist(profile.primary) ?: continue
            if (primary.total == 0L) continue
            val origin = world.countryOf(fact.key.region)
            val countries = world.countries()
            val dest = bestDestination(origin, countries, attr).firstOrNull { admits(it, profile.primary, primary.mean()) } ?: continue
            val annual = pullAnnual(attr.getValue(origin), attr.getValue(dest), mobility(primary.mean()))
            if (annual <= 0L) continue
            val unhappy = Ppm.ONE + 2L * (Ppm.ONE - fact.wageRatioPpm).coerceAtLeast(0L)
            val share = minOf(Ppm.mul(Ppm.perDays(annual, params.reviewEvery.toLong()), unhappy), params.employedShareMax)
            var n = Ppm.of(fact.headcount, Ppm.mul(share, policy(dest).opennessPpm))
            n = minOf(n, quotaLeft(world, dest), primary.total)
            if (n <= 0L) continue
            val best = Ppm.of(n, params.selectivityPpm)
            val group = SkillDistribution()
            group.addAll(primary.take(best, TakePolicy.BEST_FIRST))
            group.addAll(primary.take(n - best, TakePolicy.PROPORTIONAL))
            for (r in profile.requirements) if (r.skill != profile.primary) set.dist(r.skill)?.take(group.total, TakePolicy.PROPORTIONAL)
            val l = ledgerFor(origin)
            l.out += group.total
            if (group.mean() >= params.skilledFloorPpm) l.skilledOut += group.total
            val moved = group.total
            effects += WorkforceEffect.Emigrate(fact.key, moved)
            arrive(world, origin, dest, profile.primary, group)
            if (moved >= params.waveThreshold) events += TalentEvent.MigrationWave(origin, dest, profile.primary, moved, tick)
        }
    }

    private fun returns(world: TalentWorld, countries: List<CountryId>, attr: Map<CountryId, Long>) {
        for ((key, dist) in state.diaspora) {
            if (dist.isEmpty) continue
            val home = attr[key.from] ?: continue
            val host = attr[key.to] ?: continue
            if (home * (Ppm.ONE) < host * (Ppm.ONE - params.returnWithinPpm)) continue
            val want = Ppm.of(dist.total, Ppm.perDays(params.returnAnnualPpm, params.reviewEvery.toLong()))
            if (want <= 0L) continue
            // returnees come from the people still looking for work in the host country
            val hostRegions = world.regionsOf(key.to)
            val hostPools = hostRegions.mapNotNull { state.pools[PoolKey(it, key.skill)] }
            val available = hostPools.sumOf { it.total }
            val n = minOf(want, available, dist.total)
            if (n <= 0L) continue
            val back = SkillDistribution()
            var left = n
            for (p in hostPools) { if (left <= 0L) break; val got = p.take(left, TakePolicy.PROPORTIONAL); left -= got.total; back.addAll(got) }
            dist.take(back.total, TakePolicy.PROPORTIONAL)
            back.shiftAll(params.returnGainPpm)
            val homeRegions = world.regionsOf(key.from)
            if (homeRegions.isEmpty()) continue
            val parts = back.copy().splitInto(regionWeights(world, homeRegions))
            for ((i, r) in homeRegions.withIndex()) markets.deposit(r, key.skill, parts[i])
            ledgerFor(key.from).returned += back.total
        }
        state.diaspora.values.removeAll { it.isEmpty }
    }

    /** Resets the yearly visa counts and fades the flow figures. */
    internal fun rollYear() {
        for (l in state.ledgers.values) { l.fade(); l.visasUsed = 0L }
    }

    /** People lost to emigration over the last year as a share of the country's working-age population. */
    fun brainDrainPpm(world: TalentWorld, country: CountryId): Long =
        Ppm.ratio(ledger(country).skilledOut, world.regionsOf(country).sumOf { world.workingAge(it) })

    fun wageGapPpm(world: TalentWorld, a: CountryId, b: CountryId): Long {
        val x = attractiveness(world, a); val y = attractiveness(world, b)
        return if (x <= 0L) 0L else Ppm.ratio(y - x, x)
    }
}
