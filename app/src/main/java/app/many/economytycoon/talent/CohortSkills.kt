package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.workforce.CohortKey

/** The skill levels of the people in one workforce cohort: one distribution per skill the role asks for. */
class CohortSkillSet(val key: CohortKey) {
    internal val dists = java.util.TreeMap<SkillId, SkillDistribution>()
    /** Share of the cohort holding a certificate in a skill, ppm. */
    internal val certified = java.util.TreeMap<SkillId, Long>()

    fun dist(skill: SkillId): SkillDistribution? = dists[skill]
    fun skills(): List<SkillId> = dists.keys.toList()
    fun certifiedShare(skill: SkillId): Long = certified[skill] ?: 0L
    fun heads(primary: SkillId): Long = dists[primary]?.total ?: 0L

    fun hashInto(h: CanonicalHasher) {
        h.putLong(key.company.value).putLong(key.facility.value).putString(key.role.key).putLong(key.region.value)
        h.putInt(dists.size)
        for ((s, d) in dists) { h.putString(s.key); d.hashInto(h) }
        h.putInt(certified.size)
        for ((s, v) in certified) h.putString(s.key).putLong(v)
    }
}

/** How a cohort's head count changed since the last look. */
data class HeadcountChange(val joined: Long, val left: Long)

/**
 * Keeps a skill picture of every workforce cohort. Hires bring the skills of whoever the labour pool offered,
 * leavers take theirs back to the pool, and the group learns on the job each month. The workforce module stays the
 * source of head counts; this service follows it.
 */
class CohortSkillService(
    private val state: TalentState,
    private val taxonomy: SkillTaxonomy,
    private val markets: SkillMarketService,
    val dynamics: SkillDynamicsParams = SkillDynamicsParams(),
    /** Extra share of hires who are the best of the pool, by cohort (set by recruiting campaigns). */
    private val hireBoost: (CohortKey) -> Long = { 0L },
) {
    fun get(key: CohortKey): CohortSkillSet? = state.cohortSkills[key]
    fun all(): List<CohortSkillSet> = state.cohortSkills.values.toList()
    fun keysOf(company: app.many.economytycoon.core.CompanyId): List<CohortKey> = state.cohortSkills.keys.filter { it.company == company }

    private fun ensure(key: CohortKey, profile: RoleProfile): CohortSkillSet {
        val set = state.cohortSkills.getOrPut(key) { CohortSkillSet(key) }
        for (r in profile.requirements) set.dists.getOrPut(r.skill) { SkillDistribution() }
        return set
    }

    /** Brings the picture in line with the cohort's head count. Cohorts of unknown roles are ignored. */
    internal fun reconcile(fact: CohortFact, world: TalentWorld): HeadcountChange {
        val profile = taxonomy.profile(fact.key.role) ?: return HeadcountChange(0L, 0L)
        val set = ensure(fact.key, profile)
        val primary = set.dists.getValue(profile.primary)
        val delta = fact.headcount - primary.total
        val region = fact.key.region
        if (delta > 0L) {
            val bestShare = Ppm.clamp((fact.wageRatioPpm - 900_000L) * 2L + hireBoost(fact.key))
            val before = primary.total
            for (r in profile.requirements) {
                val group = if (r.skill == profile.primary) markets.hire(world, region, r.skill, delta, bestShare)
                else markets.genericGroup(world, region, r.skill, delta)
                set.dists.getValue(r.skill).addAll(group)
            }
            for (s in set.certified.keys.toList()) set.certified[s] = (set.certified[s] ?: 0L) * before / (before + delta)
            return HeadcountChange(delta, 0L)
        }
        if (delta < 0L) {
            val keep = fact.headcount
            for (r in profile.requirements) {
                val gone = set.dists.getValue(r.skill).shrinkTo(keep)
                if (r.skill == profile.primary) markets.release(region, r.skill, gone)
            }
            return HeadcountChange(0L, -delta)
        }
        return HeadcountChange(0L, 0L)
    }

    /** Learning on the job for a month: pace follows how engaged the people are. */
    internal fun learnMonthly(fact: CohortFact) {
        val profile = taxonomy.profile(fact.key.role) ?: return
        val set = state.cohortSkills[fact.key] ?: return
        val practice = (600_000L + Ppm.mul(fact.moralePpm, 400_000L)).coerceIn(0L, Ppm.ONE)
        for (r in profile.requirements) {
            val dist = set.dists[r.skill] ?: continue
            if (dist.isEmpty) continue
            SkillDynamics.learnByDoing(dist, taxonomy.skill(r.skill), r.requiredPpm, 30L, practice, dynamics)
        }
    }

    /** People who were not in any cohort the caller still reports go back to the pool. */
    internal fun retire(key: CohortKey, activeKeys: Set<CohortKey>) {
        if (key in activeKeys) return
        val set = state.cohortSkills.remove(key) ?: return
        val profile = taxonomy.profile(key.role) ?: return
        set.dists[profile.primary]?.let { markets.release(key.region, profile.primary, it) }
    }

    /** Moves [count] people (the best first when [policy] says so) from one cohort to another. Returns who moved, per skill. */
    internal fun move(world: TalentWorld, from: CohortKey, to: CohortKey, count: Long, policy: TakePolicy, minLevel: Long = 0L): Long {
        val src = state.cohortSkills[from] ?: return 0L
        val dst = state.cohortSkills[to]
        val srcProfile = taxonomy.profile(from.role) ?: return 0L
        val dstProfile = taxonomy.profile(to.role) ?: return 0L
        val take = minOf(count, src.heads(srcProfile.primary))
        if (take <= 0L) return 0L
        val moved = java.util.TreeMap<SkillId, SkillDistribution>()
        // the people picked are decided by their main skill, and the same head count leaves every other skill
        val picked = src.dists.getValue(srcProfile.primary).take(take, policy, minLevel)
        if (picked.total == 0L) return 0L
        moved[srcProfile.primary] = picked
        for (r in srcProfile.requirements) if (r.skill != srcProfile.primary) moved[r.skill] = src.dists.getValue(r.skill).take(picked.total, TakePolicy.PROPORTIONAL)
        val target = dst ?: ensure(to, dstProfile)
        for (r in dstProfile.requirements) {
            val group = moved[r.skill]
            val d = target.dists.getOrPut(r.skill) { SkillDistribution() }
            if (group != null) d.addAll(group)
            else d.addAll(markets.genericGroup(world, to.region, r.skill, picked.total))
        }
        return picked.total
    }

    /** Productivity of a cohort against its role, or null when nothing is known about it. */
    fun productivity(key: CohortKey): Productivity? {
        val profile = taxonomy.profile(key.role) ?: return null
        val set = state.cohortSkills[key] ?: return null
        return SkillMatch.cohort(profile) { set.dists[it] }
    }

    /** The plain skill number the workforce module understands for this cohort. */
    fun skillPpm(key: CohortKey): Long? = productivity(key)?.let { SkillMatch.equivalentSkillPpm(it.multiplierPpm) }

    fun meanLevel(key: CohortKey): Long? {
        val profile = taxonomy.profile(key.role) ?: return null
        return state.cohortSkills[key]?.dists?.get(profile.primary)?.meanOrNull()
    }
}
