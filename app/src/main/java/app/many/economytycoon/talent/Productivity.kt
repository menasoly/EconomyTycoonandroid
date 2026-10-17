package app.many.economytycoon.talent

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RoleId

/** How one skill of a role compares with what the role asks. */
data class SkillGapRow(
    val skill: SkillId,
    val requiredPpm: Long,
    val meanPpm: Long,
    val factorPpm: Long,
    val meetingPpm: Long,
) {
    val shortfallPpm: Long get() = (requiredPpm - meanPpm).coerceAtLeast(0L)
}

/**
 * Result of weighing a person or a cohort against a role. [multiplierPpm] is output per head relative to someone who
 * exactly meets the requirement (1,000,000): the integration feeds it into production in place of the plain skill
 * term. [matchPpm] is the weighted share of heads who meet every requirement's level.
 */
data class Productivity(
    val role: RoleId,
    val multiplierPpm: Long,
    val matchPpm: Long,
    val gaps: List<SkillGapRow>,
) {
    val weakest: SkillGapRow? get() = gaps.maxByOrNull { it.shortfallPpm }?.takeIf { it.shortfallPpm > 0L }
}

/** Pure functions that turn skill levels and a role's requirements into productivity. */
object SkillMatch {
    const val FLOOR_PPM = 600_000L
    const val SURPLUS_BONUS_PPM = 300_000L
    /** Share of the result taken by the weakest skill; the rest is the weighted average. */
    const val WEAKEST_LINK_PPM = 250_000L

    /** Output of someone at [actual] in a skill that asks [required], relative to meeting it exactly. */
    fun factor(actual: Long, required: Long): Long {
        if (required <= 0L) return Ppm.ONE
        val r = Ppm.ratio(actual.coerceAtLeast(0L), required).coerceAtMost(2 * Ppm.ONE)
        return if (r < Ppm.ONE) {
            FLOOR_PPM + Ppm.mul(Ppm.ONE - FLOOR_PPM, Ppm.mul(r, r))
        } else {
            val s = r - Ppm.ONE
            Ppm.ONE + Ppm.mul(SURPLUS_BONUS_PPM, 2 * s - Ppm.mul(s, s))
        }
    }

    /** Average [factor] over a group, band by band. An empty group returns the floor. */
    fun groupFactor(dist: SkillDistribution, required: Long): Long {
        val n = dist.total
        if (n == 0L) return FLOOR_PPM
        var sum = 0L
        for (b in 0 until SkillDistribution.BUCKETS) {
            val c = dist.count(b)
            if (c > 0L) sum += c * factor(SkillDistribution.midpoint(b), required)
        }
        return sum / n
    }

    private fun combine(profile: RoleProfile, factors: List<Long>, match: Long, gaps: List<SkillGapRow>): Productivity {
        var weighted = 0L
        for ((i, req) in profile.requirements.withIndex()) weighted += factors[i] * req.weightPpm
        val average = weighted / profile.totalWeight
        val weakest = factors.minOrNull() ?: Ppm.ONE
        val blended = Ppm.mul(average, Ppm.ONE - WEAKEST_LINK_PPM) + Ppm.mul(weakest, WEAKEST_LINK_PPM)
        return Productivity(profile.role, blended, match, gaps)
    }

    /** One person's levels against a role. */
    fun person(profile: RoleProfile, levelOf: (SkillId) -> Long): Productivity {
        val factors = ArrayList<Long>(); val gaps = ArrayList<SkillGapRow>()
        var matchW = 0L
        for (req in profile.requirements) {
            val lvl = levelOf(req.skill)
            val f = factor(lvl, req.requiredPpm)
            factors += f
            val meets = if (lvl >= req.requiredPpm) Ppm.ONE else 0L
            gaps += SkillGapRow(req.skill, req.requiredPpm, lvl, f, meets)
            matchW += meets * req.weightPpm
        }
        return combine(profile, factors, matchW / profile.totalWeight, gaps)
    }

    /** A cohort's distributions against a role; a missing distribution counts as nobody having the skill. */
    fun cohort(profile: RoleProfile, distOf: (SkillId) -> SkillDistribution?): Productivity {
        val factors = ArrayList<Long>(); val gaps = ArrayList<SkillGapRow>()
        var matchW = 0L
        for (req in profile.requirements) {
            val d = distOf(req.skill)
            val f = if (d == null) FLOOR_PPM else groupFactor(d, req.requiredPpm)
            val meeting = d?.shareAtLeast(req.requiredPpm) ?: 0L
            factors += f
            gaps += SkillGapRow(req.skill, req.requiredPpm, d?.mean() ?: 0L, f, meeting)
            matchW += meeting * req.weightPpm
        }
        return combine(profile, factors, matchW / profile.totalWeight, gaps)
    }

    /**
     * The plain skill number (0..1,000,000) that gives the same productivity through the workforce module's skill term
     * (0.6 to 1.4 of a reference worker). Lets the integration keep one productivity formula.
     */
    fun equivalentSkillPpm(multiplierPpm: Long): Long = Ppm.clamp(Ppm.ratio(multiplierPpm - 600_000L, 800_000L))

    /** Daily value of the productivity difference for [heads] people paid [wage] a day. */
    fun dailyValueOfGain(deltaMultiplierPpm: Long, wage: Money, heads: Long): Money =
        Money(Ppm.of(wage.raw, deltaMultiplierPpm)) * heads
}
