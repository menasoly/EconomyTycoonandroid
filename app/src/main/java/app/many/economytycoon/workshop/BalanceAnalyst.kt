package app.many.economytycoon.workshop

/** Limits the analysis judges by. */
data class AnalysisTuning(
    /** The best policy must score this many times the next to count as dominant, in ppm of the second. */
    val dominantFactorPpm: Long = 2_000_000L,
    /** And lead by at least this much score. */
    val dominantMarginPpm: Long = 150_000L,
    /** Idle scoring this share of the best policy is a finding. */
    val idleViablePpm: Long = 800_000L,
    /** Below this score the best policy is not getting anywhere. */
    val noProgressScore: Long = 100_000L,
    /** Variation of final net worth across seeds above this is a finding, in ppm. */
    val varianceHighPpm: Long = 400_000L,
    /** A projection this many times the deadline or more is called impossible, in ppm. */
    val impossibleRatioPpm: Long = 3_000_000L,
    /** Cap on a projection when a run made no headway, as a multiple of the deadline. */
    val projectionCapFactor: Int = 10,
)

/**
 * Reads the runs of a draft the way a designer would: how hard is it, is there one right way to play, can the player
 * get stuck, can a goal be lost again after it was met, and does luck decide it. Pure functions of the runs, so it can
 * be tested without a simulation.
 */
object BalanceAnalyst {
    fun analyse(draft: ScenarioDraft, runs: List<RunMetrics>, tuning: AnalysisTuning = AnalysisTuning()): BalanceReport {
        val d = draft.normalised()
        val seeds = runs.map { it.seed }.distinct().sorted()
        val names = runs.map { it.policy }.distinct()
        val digests = names.map { digest(d, it, runs.filter { r -> r.policy == it }, tuning) }
        val rating = rate(d, digests, tuning)
        val findings = ArrayList<BalanceFinding>()
        dominance(digests, runs, d, tuning, findings)
        softlocks(runs, findings)
        goals(d, runs, findings)
        variance(digests, findings, tuning)
        invariants(runs, findings)
        tagMismatch(d, rating, findings)
        horizon(d, runs, findings)
        return BalanceReport(
            d.id, d.deadlineDays, runs.maxOfOrNull { it.days } ?: 0, seeds, digests, rating,
            findings.sortedWith(compareBy<BalanceFinding> { it.severity.ordinal }.thenBy { it.code }.thenBy { it.policy ?: "" }.thenBy { it.seed ?: 0L }),
        )
    }

    /** How far the run moved the required goals plus a bonus for winning and a penalty for losing; the unit of comparison between policies. */
    fun runScore(draft: ScenarioDraft, m: RunMetrics): Long {
        val gain = gain(draft, m)
        return gain + (if (m.won) 1_000_000L else 0L) - (if (m.end == RunEnd.LOST) 300_000L else 0L)
    }

    private fun gain(draft: ScenarioDraft, m: RunMetrics): Long =
        WeightedProgress.requiredPpm(draft, m.last.goalPpm) - WeightedProgress.requiredPpm(draft, m.samples.first().goalPpm)

    /** The day the run was heading to meet the goals on, extrapolating the pace of the run; null when it was not getting there. */
    fun projectedDays(draft: ScenarioDraft, m: RunMetrics): Long? {
        if (m.won) return m.allDoneDay?.toLong()
        if (m.end == RunEnd.LOST) return null
        val start = WeightedProgress.requiredPpm(draft, m.samples.first().goalPpm)
        val gain = gain(draft, m)
        if (gain <= 0L || m.days <= 0) return null
        return m.days.toLong() * (1_000_000L - start) / gain
    }

    private fun digest(d: ScenarioDraft, policy: String, runs: List<RunMetrics>, t: AnalysisTuning): PolicyDigest {
        val cap = d.deadlineDays.toLong() * t.projectionCapFactor
        val projections = runs.map { projectedDays(d, it) ?: cap }.map { it.coerceAtMost(cap) }.sorted()
        val anyHeadway = runs.any { projectedDays(d, it) != null }
        val wins = runs.filter { it.won }
        return PolicyDigest(
            policy = policy, runs = runs.size, wins = wins.size, losses = runs.count { it.end == RunEnd.LOST },
            bankruptRuns = runs.count { it.bankruptDay != null }, softlockRuns = runs.count { it.softlockDay != null },
            progress = WorkshopStats.summarize(runs.map { WeightedProgress.requiredPpm(d, it.last.goalPpm) }),
            gain = WorkshopStats.summarize(runs.map { gain(d, it) }),
            netWorth = WorkshopStats.summarize(runs.map { it.last.netWorth }),
            cash = WorkshopStats.summarize(runs.map { it.last.cash }),
            daysToWin = if (wins.isEmpty()) null else WorkshopStats.summarize(wins.mapNotNull { it.allDoneDay?.toLong() }),
            projectedDays = if (anyHeadway) WorkshopStats.median(projections) else null,
            score = if (runs.isEmpty()) 0L else runs.sumOf { runScore(d, it) } / runs.size,
        )
    }

    private fun rate(d: ScenarioDraft, digests: List<PolicyDigest>, t: AnalysisTuning): DifficultyRating {
        val best = digests.filter { it.projectedDays != null }.minByOrNull { it.projectedDays ?: Long.MAX_VALUE }
        val deadline = d.deadlineDays.toLong().coerceAtLeast(1L)
        if (best == null) return DifficultyRating(100, DifficultyBand.IMPOSSIBLE, null, null, Long.MAX_VALUE)
        val projected = best.projectedDays ?: 0L
        val ratio = projected * 1_000_000L / deadline
        val lossShare = WorkshopStats.share(digests.sumOf { it.losses }.toLong(), digests.sumOf { it.runs }.toLong())
        val raw = (ratio * 100 / 1_200_000L + lossShare * 10 / 1_000_000L).coerceIn(0L, 100L).toInt()
        val band = when {
            ratio >= t.impossibleRatioPpm -> DifficultyBand.IMPOSSIBLE
            raw < 12 -> DifficultyBand.TRIVIAL
            raw < 35 -> DifficultyBand.EASY
            raw < 60 -> DifficultyBand.MODERATE
            raw < 85 -> DifficultyBand.HARD
            else -> DifficultyBand.BRUTAL
        }
        return DifficultyRating(if (band == DifficultyBand.IMPOSSIBLE) 100 else raw, band, best.policy, projected, ratio)
    }

    private fun dominance(digests: List<PolicyDigest>, runs: List<RunMetrics>, d: ScenarioDraft, t: AnalysisTuning, out: MutableList<BalanceFinding>) {
        if (digests.isEmpty()) return
        val sorted = digests.sortedByDescending { it.score }
        val top = sorted.first()
        val idle = digests.firstOrNull { it.policy == PolicyKind.IDLE.key }
        if (idle != null) {
            val idleWins = runs.filter { it.policy == idle.policy && it.won }
            for (w in idleWins) out += BalanceFinding("idle.wins", IssueSeverity.ERROR, "doing nothing wins on day ${w.allDoneDay} (seed ${w.seed}); the goals ask for nothing", idle.policy, w.seed)
            if (idleWins.isEmpty() && digests.size > 1) {
                if (top.policy == idle.policy && top.score > 0L) out += BalanceFinding("idle.best", IssueSeverity.ERROR, "doing nothing scores higher than every active approach", idle.policy)
                else if (top.score > 0L && idle.score * 1_000_000L >= top.score * t.idleViablePpm) out += BalanceFinding("idle.viable", IssueSeverity.WARNING, "doing nothing reaches ${WorkshopStats.share(idle.score, top.score) / 10_000}% of the best approach's score", idle.policy)
            }
        }
        if (sorted.size >= 2) {
            val second = sorted[1]
            val lead = top.score - second.score
            val factor = if (second.score <= 0L) true else top.score * 1_000_000L >= second.score * t.dominantFactorPpm
            if (factor && lead >= t.dominantMarginPpm && everySeed(d, runs, top.policy, second.policy)) {
                out += BalanceFinding("dominant.strategy", IssueSeverity.WARNING, "${top.policy} beats ${second.policy} and every other approach on every seed; there is one right way to play", top.policy)
            }
        }
        if (top.score < t.noProgressScore) out += BalanceFinding("none.progress", IssueSeverity.WARNING, "no approach made headway towards the goals in ${runs.maxOfOrNull { it.days } ?: 0} days", top.policy)
    }

    private fun everySeed(d: ScenarioDraft, runs: List<RunMetrics>, a: String, b: String): Boolean {
        val bySeed = runs.groupBy { it.seed }
        if (bySeed.isEmpty()) return false
        for ((_, rs) in bySeed) {
            val ra = rs.firstOrNull { it.policy == a } ?: return false
            val rb = rs.firstOrNull { it.policy == b } ?: return false
            if (runScore(d, ra) < runScore(d, rb)) return false
        }
        return true
    }

    private fun softlocks(runs: List<RunMetrics>, out: MutableList<BalanceFinding>) {
        val stuck = runs.filter { it.softlockDay != null && it.policy != PolicyKind.IDLE.key }
        for (r in stuck.take(MAX_PER_CODE)) {
            out += BalanceFinding("softlock", IssueSeverity.ERROR, "stuck from day ${r.softlockDay}: ${r.softlockNote}", r.policy, r.seed)
        }
        if (stuck.size > MAX_PER_CODE) out += BalanceFinding("softlock.more", IssueSeverity.NOTE, "${stuck.size - MAX_PER_CODE} more runs got stuck")
    }

    private fun goals(d: ScenarioDraft, runs: List<RunMetrics>, out: MutableList<BalanceFinding>) {
        val required = d.goals.withIndex().filter { !it.value.bonus }
        for ((i, g) in required) {
            val n = runs.count { it.goalRegressed.getOrNull(i) == true }
            if (n > 0) out += BalanceFinding("goal.regressed", IssueSeverity.WARNING, "${g.kind.noun} was reached and then lost again in $n of ${runs.size} runs; the game needs every goal at the same moment")
        }
        val together = required.map { it.value.kind }
        val fluct = GoalMonotony.fluctuating(together)
        if (required.size >= 2 && fluct.size >= 2) out += BalanceFinding("goals.simultaneous", IssueSeverity.NOTE, "${fluct.joinToString { it.noun }} can each fall after being met, and all must hold on the same day")
        for (r in runs) {
            val each = required.all { r.goalFirstDay.getOrNull(it.index) != null }
            if (each && r.allDoneDay == null && required.size >= 2) {
                out += BalanceFinding("goal.never_together", IssueSeverity.WARNING, "every required goal was met at some point but never all at once", r.policy, r.seed)
            }
        }
    }

    private fun variance(digests: List<PolicyDigest>, out: MutableList<BalanceFinding>, t: AnalysisTuning) {
        for (dg in digests) {
            if (dg.policy == PolicyKind.IDLE.key || dg.runs < 2) continue
            if (dg.netWorth.variationPpm > t.varianceHighPpm) {
                out += BalanceFinding("variance.high", IssueSeverity.WARNING, "final net worth varies by ${dg.netWorth.variationPpm / 10_000}% across seeds (${dg.netWorth.min} to ${dg.netWorth.max}); luck decides this scenario", dg.policy)
            }
            if (dg.wins in 1 until dg.runs) out += BalanceFinding("variance.split", IssueSeverity.WARNING, "${dg.wins} of ${dg.runs} seeds are won; the seed decides whether it can be won", dg.policy)
        }
    }

    private fun invariants(runs: List<RunMetrics>, out: MutableList<BalanceFinding>) {
        for (r in runs) if (r.invariantProblems.isNotEmpty()) out += BalanceFinding("invariant", IssueSeverity.ERROR, "the books broke: ${r.invariantProblems.first()}", r.policy, r.seed)
    }

    private fun tagMismatch(d: ScenarioDraft, rating: DifficultyRating, out: MutableList<BalanceFinding>) {
        for (tag in d.tags) if (!rating.band.matches(tag)) {
            out += BalanceFinding("rating.tag", IssueSeverity.WARNING, "tagged ${tag.label.lowercase()} but the runs rate it ${rating.band.label.lowercase()}")
        }
    }

    private fun horizon(d: ScenarioDraft, runs: List<RunMetrics>, out: MutableList<BalanceFinding>) {
        val longest = runs.maxOfOrNull { it.days } ?: return
        if (longest * 4 < d.deadlineDays) out += BalanceFinding("horizon.short", IssueSeverity.NOTE, "the runs cover $longest of ${d.deadlineDays} days, so the rating extrapolates the early pace")
    }

    private const val MAX_PER_CODE = 5
}
