package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome

/** A number in a draft a sweep can vary. */
enum class SweepParam(val label: String) {
    CAPITAL("Starting capital"),
    RIVALS("Rivals"),
    SPENDING("Household spending (ppm)"),
    HOUSEHOLD_CASH("Cash per inhabitant"),
    AI_CAPITAL("Rival starting capital"),
    GOAL_SCALE("Goal targets (percent of written)"),
    YEARS("Years allowed");

    fun current(d: ScenarioDraft): Long = when (this) {
        CAPITAL -> d.company.capital
        RIVALS -> d.world.rivals.toLong()
        SPENDING -> d.rules.valueOf(RuleKey.SPENDING_PROPENSITY)
        HOUSEHOLD_CASH -> d.rules.valueOf(RuleKey.HOUSEHOLD_CASH)
        AI_CAPITAL -> d.rules.valueOf(RuleKey.AI_CAPITAL)
        GOAL_SCALE -> 100L
        YEARS -> d.timing.years.toLong()
    }

    /** The draft with this number set to [value]. */
    fun apply(d: ScenarioDraft, value: Long): ScenarioDraft = when (this) {
        CAPITAL -> d.copy(company = d.company.copy(capital = value))
        RIVALS -> d.copy(world = d.world.copy(rivals = value.toInt()))
        SPENDING -> d.copy(rules = d.rules.with(RuleKey.SPENDING_PROPENSITY, value))
        HOUSEHOLD_CASH -> d.copy(rules = d.rules.with(RuleKey.HOUSEHOLD_CASH, value))
        AI_CAPITAL -> d.copy(rules = d.rules.with(RuleKey.AI_CAPITAL, value))
        GOAL_SCALE -> d.copy(goals = d.goals.map { it.copy(target = if (it.target == 1L) 1L else (it.target * value / 100L).coerceAtLeast(1L)) })
        YEARS -> d.copy(timing = d.timing.copy(years = value.toInt()))
    }
}

data class SweepSpec(
    val param: SweepParam,
    val values: List<Long>,
    val policy: PolicyKind = PolicyKind.GREEDY,
    val seeds: List<Long> = listOf(1L),
    val settings: HarnessSettings = HarnessSettings(),
) {
    init {
        require(values.isNotEmpty()) { "a sweep needs at least one value" }
        require(seeds.isNotEmpty()) { "a sweep needs at least one seed" }
    }
}

/** What happened at one value of the swept number. [error] is set when the draft could not be played at that value. */
data class SweepRow(
    val value: Long,
    val runs: List<RunMetrics>,
    val error: String?,
    val netWorth: Summary,
    val cash: Summary,
    val progress: Summary,
    val winRatePpm: Long,
    val lossRatePpm: Long,
    val bankruptRatePpm: Long,
)

class SweepReport(val spec: SweepSpec, val rows: List<SweepRow>) {
    private val played: List<SweepRow> get() = rows.filter { it.error == null }

    /** How median final net worth moves as the number grows. */
    val netWorthTrend: Trend get() = WorkshopStats.trend(played.map { it.netWorth.median })
    val progressTrend: Trend get() = WorkshopStats.trend(played.map { it.progress.median })

    /** The value with the best median progress; the lowest value wins a tie. */
    val bestValue: Long? get() = played.maxWithOrNull(compareBy<SweepRow> { it.progress.median }.thenByDescending { it.value })?.value

    /** Whether making the number larger never makes the outcome worse, or never better; a mixed answer means a tuning cliff or noise. */
    val monotone: Boolean get() = progressTrend != Trend.MIXED

    fun row(value: Long): SweepRow? = rows.firstOrNull { it.value == value }

    fun lines(): List<String> = rows.map { r ->
        if (r.error != null) "${spec.param.label} ${r.value}: cannot play (${r.error})"
        else "${spec.param.label} ${r.value}: net worth ${r.netWorth.median}, progress ${r.progress.median / 10_000}%, won ${r.winRatePpm / 10_000}%, lost ${r.lossRatePpm / 10_000}%"
    }
}

class SweepRunner(private val harness: WorkshopHarness) {
    fun run(draft: ScenarioDraft, spec: SweepSpec): SweepReport {
        val rows = spec.values.map { v ->
            val variant = spec.param.apply(draft, v)
            val runs = ArrayList<RunMetrics>()
            var error: String? = null
            for (seed in spec.seeds) {
                when (val r = harness.run(variant, spec.policy, spec.settings, seed)) {
                    is Outcome.Ok -> runs += r.value
                    is Outcome.Rejected -> { error = r.rejection.message; break }
                }
            }
            if (error != null) emptyRow(v, error) else row(variant, v, runs)
        }
        return SweepReport(spec, rows)
    }

    private fun emptyRow(v: Long, error: String) =
        SweepRow(v, emptyList(), error, Summary.EMPTY, Summary.EMPTY, Summary.EMPTY, 0L, 0L, 0L)

    private fun row(d: ScenarioDraft, v: Long, runs: List<RunMetrics>): SweepRow = SweepRow(
        value = v, runs = runs, error = null,
        netWorth = WorkshopStats.summarize(runs.map { it.last.netWorth }),
        cash = WorkshopStats.summarize(runs.map { it.last.cash }),
        progress = WorkshopStats.summarize(runs.map { WeightedProgress.requiredPpm(d.normalised(), it.last.goalPpm) }),
        winRatePpm = WorkshopStats.share(runs.count { it.won }.toLong(), runs.size.toLong()),
        lossRatePpm = WorkshopStats.share(runs.count { it.end == RunEnd.LOST }.toLong(), runs.size.toLong()),
        bankruptRatePpm = WorkshopStats.share(runs.count { it.bankruptDay != null }.toLong(), runs.size.toLong()),
    )

    companion object {
        /** [steps] evenly spaced values from [from] to [to] inclusive, rounded down, without repeats. */
        fun range(from: Long, to: Long, steps: Int): List<Long> {
            require(steps >= 1) { "a range needs at least one step" }
            if (steps == 1) return listOf(from)
            return (0 until steps).map { from + (to - from) * it / (steps - 1) }.distinct()
        }
    }
}
