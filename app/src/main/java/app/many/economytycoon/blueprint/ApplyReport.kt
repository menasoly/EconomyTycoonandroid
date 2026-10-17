package app.many.economytycoon.blueprint

import app.many.economytycoon.core.Outcome

enum class StepStatus(val label: String) {
    APPLIED("Done"), REJECTED("Refused"), BLOCKED("Blocked"), NOT_RUN("Not run"),
}

/** What the executor said about one step. A step missing from the results was never run. */
data class StepResult(val index: Int, val ok: Boolean, val rule: String? = null, val message: String? = null) {
    companion object {
        fun of(index: Int, outcome: Outcome<*>): StepResult = when (outcome) {
            is Outcome.Ok -> StepResult(index, true)
            is Outcome.Rejected -> StepResult(index, false, outcome.rejection.rule, outcome.rejection.message)
        }
    }
}

data class ReportRow(val step: PlannedStep, val status: StepStatus, val detail: String?)

/** Outcome of running (part of) a plan: which steps landed, which were refused, which were blocked by a refusal. */
data class ApplyReport(val rows: List<ReportRow>, val skipped: List<SkippedStep>) {
    fun count(status: StepStatus) = rows.count { it.status == status }
    val applied: Int get() = count(StepStatus.APPLIED)
    val rejected: Int get() = count(StepStatus.REJECTED)
    val blocked: Int get() = count(StepStatus.BLOCKED)
    val notRun: Int get() = count(StepStatus.NOT_RUN)

    /** Share of planned steps that landed, in ppm; a plan with no steps counts as fully applied. */
    val coveragePpm: Long get() = if (rows.isEmpty()) 1_000_000L else applied * 1_000_000L / rows.size

    /** True when every planned step landed and the plan itself left nothing out. */
    val isComplete: Boolean get() = applied == rows.size && skipped.none { it.reason != SkipReason.ALREADY_DONE }

    val headline: String
        get() = when {
            rows.isEmpty() && skipped.none { it.reason != SkipReason.ALREADY_DONE } -> "Nothing to change."
            isComplete -> "Applied all $applied steps."
            else -> buildString {
                append("Applied $applied of ${rows.size} steps")
                if (rejected > 0) append(", $rejected refused")
                if (blocked > 0) append(", $blocked blocked")
                if (notRun > 0) append(", $notRun not run")
                val left = skipped.count { it.reason != SkipReason.ALREADY_DONE }
                if (left > 0) append("; $left left out of the plan")
                append('.')
            }
        }

    fun rowsWith(status: StepStatus) = rows.filter { it.status == status }

    companion object {
        /**
         * Joins a plan with the executor's results. A step that depends, directly or through other steps, on a
         * refused one is reported as blocked even if the executor never saw it.
         */
        fun build(plan: ApplyPlan, results: List<StepResult>): ApplyReport {
            val byIndex = results.associateBy { it.index }
            val failed = HashMap<Int, PlannedStep>()
            val rows = ArrayList<ReportRow>()
            for (s in plan.steps) {
                val r = byIndex[s.index]
                val culprit = s.dependsOn.firstOrNull { it in failed }
                val row = when {
                    culprit != null -> {
                        failed[s.index] = failed.getValue(culprit)
                        ReportRow(s, StepStatus.BLOCKED, "Depends on \"${failed.getValue(culprit).summary}\", which did not happen.")
                    }
                    r == null -> ReportRow(s, StepStatus.NOT_RUN, null)
                    r.ok -> ReportRow(s, StepStatus.APPLIED, null)
                    else -> { failed[s.index] = s; ReportRow(s, StepStatus.REJECTED, r.message ?: r.rule) }
                }
                if (row.status == StepStatus.NOT_RUN) failed[s.index] = s
                rows += row
            }
            return ApplyReport(rows, plan.skipped)
        }
    }
}
