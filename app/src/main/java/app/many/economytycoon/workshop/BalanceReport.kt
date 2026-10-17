package app.many.economytycoon.workshop

import app.many.economytycoon.scenarios.GoalKind

enum class DifficultyBand(val label: String) {
    TRIVIAL("Trivial"), EASY("Easy"), MODERATE("Moderate"), HARD("Hard"), BRUTAL("Brutal"), IMPOSSIBLE("Impossible");

    /** The ratings (0 to 100) that fall in this band. */
    val scores: IntRange get() = when (this) {
        TRIVIAL -> 0..11
        EASY -> 12..34
        MODERATE -> 35..59
        HARD -> 60..84
        BRUTAL -> 85..99
        IMPOSSIBLE -> 100..100
    }

    /** The tag an author would use for this band, where one fits. */
    fun matches(tag: DifficultyTag): Boolean = when (tag) {
        DifficultyTag.GENTLE -> this <= MODERATE
        DifficultyTag.HARSH -> this >= HARD
        else -> true
    }

    companion object {
        fun forScore(score: Int): DifficultyBand = values().first { score in it.scores || it == IMPOSSIBLE }
    }
}

/** How hard the scenario looks from the runs. [score] is 0 (nothing to do) to 100 (cannot be done). */
data class DifficultyRating(val score: Int, val band: DifficultyBand, val bestPolicy: String?, val projectedDays: Long?, val ratioPpm: Long)

/** One policy over every seed it was played on. */
data class PolicyDigest(
    val policy: String,
    val runs: Int,
    val wins: Int,
    val losses: Int,
    val bankruptRuns: Int,
    val softlockRuns: Int,
    /** Progress on the required goals when each run ended, 0 to 1,000,000. */
    val progress: Summary,
    /** How far the required goals moved during the run, in ppm. */
    val gain: Summary,
    val netWorth: Summary,
    val cash: Summary,
    val daysToWin: Summary?,
    /** Median of the per-run projections of the day the goals would be met, or null when no run was getting there. */
    val projectedDays: Long?,
    val score: Long,
) {
    val winRatePpm: Long get() = WorkshopStats.share(wins.toLong(), runs.toLong())
}

/** One thing the analysis noticed. [policy] and [seed] say which run, when it is about one. */
data class BalanceFinding(val code: String, val severity: IssueSeverity, val message: String, val policy: String? = null, val seed: Long? = null) {
    override fun toString() = "${severity.name.padEnd(7)} $code: $message"
}

class BalanceReport(
    val draftId: String,
    val deadlineDays: Int,
    val horizonDays: Int,
    val seeds: List<Long>,
    val digests: List<PolicyDigest>,
    val rating: DifficultyRating,
    val findings: List<BalanceFinding>,
) {
    fun digest(policy: String): PolicyDigest? = digests.firstOrNull { it.policy == policy }
    fun has(code: String) = findings.any { it.code == code }
    fun withCode(code: String) = findings.filter { it.code == code }
    val problems: List<BalanceFinding> get() = findings.filter { it.severity == IssueSeverity.ERROR }
    val warnings: List<BalanceFinding> get() = findings.filter { it.severity == IssueSeverity.WARNING }
    val healthy: Boolean get() = problems.isEmpty()
}

/** What a goal kind does over time. A scenario is only won when all required goals hold at once, so goals that can fall matter. */
object GoalMonotony {
    private val rising = setOf(GoalKind.TECHNOLOGIES, GoalKind.INDUSTRIES)

    /** True when progress on the goal can only grow once made; the others can be lost again. */
    fun isMonotone(kind: GoalKind): Boolean = kind in rising

    fun fluctuating(kinds: List<GoalKind>): List<GoalKind> = kinds.filter { !isMonotone(it) }
}
