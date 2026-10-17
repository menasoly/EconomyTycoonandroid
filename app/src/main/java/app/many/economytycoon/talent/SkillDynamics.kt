package app.many.economytycoon.talent

/** Knobs for how fast people learn and forget. Rates are ppm of mastery. */
data class SkillDynamicsParams(
    /** How far above what the job asks a worker can still pick things up on the job. */
    val ceilingMarginPpm: Long = 150_000L,
    /** Extra pace when colleagues who already know the work are around, at most. */
    val mentorBoostPpm: Long = 500_000L,
    /** Colleagues count as mentors from this far under the ceiling. */
    val mentorBandPpm: Long = 100_000L,
    /** People out of work forget this many times faster than the skill's own pace suggests. */
    val idleForgetMultiplier: Long = 1_500_000L,
)

/** Learning by doing and forgetting, as pure shifts of a skill distribution. */
object SkillDynamics {

    /** The level a job can teach up to: what it asks plus a margin, never past mastery. */
    fun ceilingFor(requiredPpm: Long, p: SkillDynamicsParams): Long = (requiredPpm + p.ceilingMarginPpm).coerceIn(0L, Ppm.ONE)

    /**
     * Gain in a stretch of [days] days for someone at [level], where [practicePpm] is how much of the time the skill is
     * really used and [mentorPpm] the boost from experienced colleagues. Zero at or above the ceiling; the gain falls
     * with the square of the remaining headroom, so the last stretch to mastery is slow.
     */
    fun learnDelta(skill: Skill, level: Long, ceiling: Long, days: Long, practicePpm: Long, mentorPpm: Long): Long {
        if (ceiling <= 0L || level >= ceiling) return 0L
        val head = Ppm.ratio(ceiling - level, ceiling)
        val shape = Ppm.mul(head, head)
        val base = Ppm.perDays(skill.learnPerYearPpm, days)
        val paced = Ppm.mul(Ppm.mul(base, shape), Ppm.clamp(practicePpm))
        return Ppm.mul(paced, Ppm.ONE + mentorPpm.coerceAtLeast(0L))
    }

    /** Boost from colleagues who are near the ceiling already, scaled by how many there are. */
    fun mentorBoost(dist: SkillDistribution, ceiling: Long, p: SkillDynamicsParams): Long {
        val share = dist.shareAtLeast((ceiling - p.mentorBandPpm).coerceAtLeast(0L))
        return Ppm.mul(share, p.mentorBoostPpm)
    }

    /** The whole group learns on the job for [days] days. Returns the mean gain in ppm for reporting. */
    internal fun learnByDoing(
        dist: SkillDistribution, skill: Skill, requiredPpm: Long, days: Long, practicePpm: Long, p: SkillDynamicsParams,
    ): Long {
        val before = dist.mean()
        val ceiling = ceilingFor(requiredPpm, p)
        val mentor = mentorBoost(dist, ceiling, p)
        dist.shift { b -> learnDelta(skill, SkillDistribution.midpoint(b), ceiling, days, practicePpm, mentor) }
        return dist.mean() - before
    }

    /** What an unused skill loses over [days] days at [level]: the closer to the floor, the less there is left to lose. */
    fun forgetDelta(skill: Skill, level: Long, days: Long, multiplierPpm: Long = Ppm.ONE): Long {
        if (level <= skill.floorPpm) return 0L
        val above = Ppm.ratio(level - skill.floorPpm, Ppm.ONE - skill.floorPpm)
        val loss = Ppm.mul(Ppm.mul(Ppm.perDays(skill.decayPerYearPpm, days), above), multiplierPpm)
        return -minOf(loss, level - skill.floorPpm)
    }

    internal fun forget(dist: SkillDistribution, skill: Skill, days: Long, multiplierPpm: Long = Ppm.ONE) {
        dist.shift { b -> forgetDelta(skill, SkillDistribution.midpoint(b), days, multiplierPpm) }
    }

    /**
     * Pushes a trained subgroup up. [seatsShare] is the share of the group in training, [gainPpm] what a full
     * participant gains in this stretch and [capPpm] the level the course can bring a person to.
     */
    internal fun train(dist: SkillDistribution, gainPpm: Long, seatsShare: Long, capPpm: Long) {
        val perHead = Ppm.mul(gainPpm, Ppm.clamp(seatsShare))
        if (perHead <= 0L) return
        dist.shift { b ->
            val level = SkillDistribution.midpoint(b)
            if (level >= capPpm) 0L else minOf(perHead, capPpm - level)
        }
    }
}
