package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome

/** Something that can say how hard a draft is. The real one plays it; tests give one that computes. */
fun interface DifficultyProbe {
    fun rate(draft: ScenarioDraft): Outcome<DifficultyRating>
}

/** Rates a draft by running a balance study. */
class StudyProbe(private val study: BalanceStudy, private val spec: StudySpec = StudySpec()) : DifficultyProbe {
    override fun rate(draft: ScenarioDraft): Outcome<DifficultyRating> = study.run(draft, spec).map { it.report.rating }
}

/** Which number to move, between which values, and how hard the scenario should end up. */
data class TuneSpec(val param: SweepParam, val low: Long, val high: Long, val target: DifficultyBand, val maxProbes: Int = 8) {
    init {
        require(low < high) { "the range to search must have a low end below its high end" }
        require(maxProbes >= 3) { "tuning needs at least three probes: both ends and one in between" }
        require(target != DifficultyBand.IMPOSSIBLE) { "an impossible scenario is not a target" }
    }
}

data class TuneStep(val value: Long, val rating: DifficultyRating)

enum class TuneOutcome(val label: String) {
    ALREADY_THERE("already at the target"),
    FOUND("found a value in the target band"),
    NEAREST("no tried value hit the band; this is the nearest"),
    UNREACHABLE("the number cannot reach the target within the range"),
    FAILED("a draft in the range could not be played"),
}

class TuneResult(val outcome: TuneOutcome, val value: Long?, val steps: List<TuneStep>, val message: String, val tuned: ScenarioDraft?) {
    val probes: Int get() = steps.size
    fun lines(): List<String> = listOf(message) + steps.map { "tried ${it.value}: ${it.rating.band.label} (${it.rating.score})" }
}

/**
 * Searches one number of a draft for the value that makes the scenario as hard as the author wants. Difficulty is
 * assumed to move one way as the number grows (the ends of the range show which way); a number that does not is
 * reported as such rather than guessed at. Each value is rated at most once, and the search stops at [TuneSpec.maxProbes].
 */
class AutoTuner(private val probe: DifficultyProbe) {
    fun tune(draft: ScenarioDraft, spec: TuneSpec): TuneResult {
        val rated = LinkedHashMap<Long, DifficultyRating>()
        val range = spec.target.scores

        fun rate(v: Long): String? {
            if (v in rated) return null
            return when (val r = probe.rate(spec.param.apply(draft, v))) {
                is Outcome.Ok -> { rated[v] = r.value; null }
                is Outcome.Rejected -> "the draft cannot be played with ${spec.param.label.lowercase()} at $v: ${r.rejection.message}"
            }
        }
        fun steps() = rated.map { TuneStep(it.key, it.value) }
        fun failed(why: String) = TuneResult(TuneOutcome.FAILED, null, steps(), why, null)
        fun done(outcome: TuneOutcome, v: Long) = TuneResult(outcome, v, steps(), "${outcome.label}: ${spec.param.label.lowercase()} $v", spec.param.apply(draft, v))
        fun hit(v: Long) = rated[v]?.score?.let { it in range } == true

        val current = spec.param.current(draft)
        rate(current)?.let { return failed(it) }
        if (hit(current)) return done(TuneOutcome.ALREADY_THERE, current)

        rate(spec.low)?.let { return failed(it) }
        rate(spec.high)?.let { return failed(it) }
        val sLow = rated.getValue(spec.low).score; val sHigh = rated.getValue(spec.high).score
        if (hit(spec.low)) return done(TuneOutcome.FOUND, spec.low)
        if (hit(spec.high)) return done(TuneOutcome.FOUND, spec.high)
        val reachable = range.first <= maxOf(sLow, sHigh) && range.last >= minOf(sLow, sHigh)
        if (!reachable || sLow == sHigh) {
            val best = nearest(rated, range)
            return TuneResult(TuneOutcome.UNREACHABLE, best, steps(), "${TuneOutcome.UNREACHABLE.label}: ${spec.param.label.lowercase()} from ${spec.low} to ${spec.high} rates between $sLow and $sHigh", null)
        }

        val rising = sHigh > sLow
        var lo = spec.low; var hi = spec.high
        while (rated.size < spec.maxProbes && hi - lo > 1) {
            val mid = lo + (hi - lo) / 2
            rate(mid)?.let { return failed(it) }
            if (hit(mid)) return done(TuneOutcome.FOUND, mid)
            val tooEasy = rated.getValue(mid).score < range.first
            if (tooEasy == rising) lo = mid else hi = mid
        }
        return done(TuneOutcome.NEAREST, nearest(rated, range))
    }

    private fun nearest(rated: Map<Long, DifficultyRating>, range: IntRange): Long =
        rated.entries.minWithOrNull(compareBy<Map.Entry<Long, DifficultyRating>> { distance(it.value.score, range) }.thenBy { it.key })?.key ?: 0L

    private fun distance(score: Int, range: IntRange): Int = when {
        score in range -> 0
        score < range.first -> range.first - score
        else -> score - range.last
    }
}
