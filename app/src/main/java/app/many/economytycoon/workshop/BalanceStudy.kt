package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome

/** Which policies to play, on which seeds, and how. */
data class StudySpec(
    val policies: List<PolicyKind> = PolicyKind.values().toList(),
    val seeds: List<Long> = listOf(1L, 2L),
    val settings: HarnessSettings = HarnessSettings(),
    val tuning: AnalysisTuning = AnalysisTuning(),
) {
    init {
        require(policies.isNotEmpty()) { "a study needs at least one policy" }
        require(seeds.isNotEmpty()) { "a study needs at least one seed" }
        require(seeds.distinct().size == seeds.size) { "seeds must differ" }
    }
}

class StudyResult(val runs: List<RunMetrics>, val report: BalanceReport) {
    fun runsOf(policy: PolicyKind): List<RunMetrics> = runs.filter { it.policy == policy.key }
    fun run(policy: PolicyKind, seed: Long): RunMetrics? = runs.firstOrNull { it.policy == policy.key && it.seed == seed }
}

/** Plays every policy on every seed and analyses the lot. Order is fixed (policy, then seed) so repeated studies match. */
class BalanceStudy(private val harness: WorkshopHarness) {
    fun run(draft: ScenarioDraft, spec: StudySpec = StudySpec()): Outcome<StudyResult> {
        val runs = ArrayList<RunMetrics>()
        for (p in spec.policies) for (s in spec.seeds) {
            when (val r = harness.run(draft, p, spec.settings, s)) {
                is Outcome.Ok -> runs += r.value
                is Outcome.Rejected -> return Outcome.Rejected(r.rejection)
            }
        }
        return Outcome.ok(StudyResult(runs, BalanceAnalyst.analyse(draft, runs, spec.tuning)))
    }
}
