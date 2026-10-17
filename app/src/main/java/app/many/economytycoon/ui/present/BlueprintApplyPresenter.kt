package app.many.economytycoon.ui.present

import app.many.economytycoon.blueprint.ApplyPhase
import app.many.economytycoon.blueprint.ApplyPlan
import app.many.economytycoon.blueprint.ApplyReport
import app.many.economytycoon.blueprint.ApplyTarget
import app.many.economytycoon.blueprint.BlueprintDiff
import app.many.economytycoon.blueprint.DiffKind
import app.many.economytycoon.blueprint.SkipReason
import app.many.economytycoon.blueprint.StepStatus
import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting

data class ApplyStepRow(val number: Int, val text: String, val cost: String?, val after: String?)

data class ApplyPhaseGroup(val label: String, val rows: List<ApplyStepRow>)

data class ApplySkipRow(val text: String, val reason: String, val detail: String, val tone: Tone)

data class DiffRow(val section: String, val symbol: String, val text: String, val tone: Tone)

data class ApplyPreviewModel(
    val title: String,
    val blocked: String?,
    val tiles: List<Tile>,
    val groups: List<ApplyPhaseGroup>,
    val problems: List<ApplySkipRow>,
    val alreadyDone: Int,
    val changes: List<DiffRow>,
    val unapplied: List<DiffRow>,
    val canApply: Boolean,
    val button: String,
    val summary: String,
)

data class ReportRowModel(val text: String, val status: String, val tone: Tone, val detail: String?)

data class ApplyReportModel(val headline: String, val tone: Tone, val rows: List<ReportRowModel>, val problems: List<ApplySkipRow>)

/** The apply preview and its outcome: what will be built, installed and set, what is left out and why. */
object BlueprintApplyPresenter {
    fun skipTone(r: SkipReason): Tone = when (r) {
        SkipReason.ALREADY_DONE -> Tone.MUTED
        SkipReason.UNAFFORDABLE, SkipReason.LINE_BUSY, SkipReason.MACHINE_LIMIT, SkipReason.LINE_LIMIT -> Tone.WARN
        SkipReason.DEPENDENCY_SKIPPED -> Tone.INFO
        else -> Tone.BAD
    }

    fun diffTone(k: DiffKind): Tone = when (k) { DiffKind.ADDED -> Tone.GOOD; DiffKind.REMOVED -> Tone.BAD; DiffKind.CHANGED -> Tone.INFO }

    fun diffRows(d: BlueprintDiff): List<DiffRow> = d.entries.map { DiffRow(it.section, it.kind.symbol, it.text, diffTone(it.kind)) }

    fun model(plan: ApplyPlan, money: (Money) -> String = { Formatting.money(it) }): ApplyPreviewModel {
        val isNew = plan.target is ApplyTarget.NewFacility
        val title = (if (isNew) "Build " else "Apply ") + plan.blueprintName
        if (plan.blocked != null) {
            return ApplyPreviewModel(title, plan.blocked, emptyList(), emptyList(), emptyList(), 0, emptyList(), emptyList(), false, "Cannot apply", plan.blocked)
        }
        val groups = ApplyPhase.values().mapNotNull { phase ->
            val rows = plan.stepsIn(phase).map { s ->
                ApplyStepRow(s.index + 1, s.summary, s.cost.takeIf { it.raw > 0 }?.let(money), s.dependsOn.takeIf { it.isNotEmpty() }?.let { "after step " + it.joinToString(", ") { d -> (d + 1).toString() } })
            }
            if (rows.isEmpty()) null else ApplyPhaseGroup(phase.label, rows)
        }
        val problems = plan.problems.map { ApplySkipRow(it.summary, it.reason.label, it.detail, skipTone(it.reason)) }
        val tiles = listOf(
            Tile("Cost", money(plan.totalCost)),
            Tile("Steps", plan.steps.size.toString(), note = if (isNew) "starts with the build" else null),
            Tile("Left out", problems.size.toString(), if (problems.isEmpty()) Tone.GOOD else Tone.WARN),
        )
        val summary = when {
            plan.isNoOp -> "The facility already matches this blueprint."
            plan.isComplete -> "Everything in the blueprint can be applied."
            else -> "${plan.steps.size} of the steps can run; ${problems.size} cannot."
        }
        val button = when { plan.steps.isEmpty() -> "Nothing to do"; isNew -> "Build and configure"; else -> "Apply ${plan.steps.size} steps" }
        return ApplyPreviewModel(title, null, tiles, groups, problems, plan.noOps.size, diffRows(plan.diff), diffRows(plan.unapplied), plan.steps.isNotEmpty(), button, summary)
    }

    fun statusTone(s: StepStatus): Tone = when (s) {
        StepStatus.APPLIED -> Tone.GOOD
        StepStatus.REJECTED -> Tone.BAD
        StepStatus.BLOCKED -> Tone.WARN
        StepStatus.NOT_RUN -> Tone.MUTED
    }

    fun report(r: ApplyReport): ApplyReportModel {
        val tone = when {
            r.isComplete -> Tone.GOOD
            r.applied == 0 && r.rows.isNotEmpty() -> Tone.BAD
            else -> Tone.WARN
        }
        return ApplyReportModel(
            r.headline, tone,
            r.rows.map { ReportRowModel(it.step.summary, it.status.label, statusTone(it.status), it.detail) },
            r.skipped.filter { it.reason != SkipReason.ALREADY_DONE }.map { ApplySkipRow(it.summary, it.reason.label, it.detail, skipTone(it.reason)) },
        )
    }
}
