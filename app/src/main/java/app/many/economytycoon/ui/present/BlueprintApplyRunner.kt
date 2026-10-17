package app.many.economytycoon.ui.present

import app.many.economytycoon.blueprint.ApplyPlan
import app.many.economytycoon.blueprint.ApplyReport
import app.many.economytycoon.blueprint.FacilityView
import app.many.economytycoon.blueprint.StepAction
import app.many.economytycoon.blueprint.StepResult
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.simulation.Command

data class RunResult(val results: List<StepResult>, val report: ApplyReport)

/**
 * Walks an [ApplyPlan] for an existing facility and hands each command to [submit], which is the session's way of
 * giving a command (it validates and applies it). Lines added along the way are picked up from [view] so later steps
 * can name them. A step is not tried when a step it depends on failed or was not run.
 */
class BlueprintApplyRunner(
    private val company: CompanyId,
    private val view: (FacilityId) -> FacilityView?,
    private val submit: (Command) -> Outcome<*>,
) {
    fun run(plan: ApplyPlan, facility: FacilityId): RunResult {
        if (plan.target !is app.many.economytycoon.blueprint.ApplyTarget.Existing) return RunResult(emptyList(), ApplyReport.build(plan, emptyList()))
        val results = ArrayList<StepResult>()
        val done = HashSet<Int>()
        var lineIds = lines(facility)
        for (step in plan.steps) {
            if (step.dependsOn.any { it !in done }) continue
            var command = step.action.toCommand(company, facility, lineIds)
            if (command == null) {
                lineIds = lines(facility)
                command = step.action.toCommand(company, facility, lineIds)
            }
            if (command == null) continue
            val r = StepResult.of(step.index, submit(command))
            results += r
            if (r.ok) {
                done += step.index
                if (step.action is StepAction.AddLine) lineIds = lines(facility)
            }
        }
        return RunResult(results, ApplyReport.build(plan, results))
    }

    private fun lines(facility: FacilityId): List<LineId> =
        view(facility)?.lines?.sortedWith(compareBy({ it.priority }, { it.id }))?.map { it.id }.orEmpty()
}

/** Applies a plan to an existing facility through the player's own commands, one at a time. */
fun GameSession.applyBlueprint(plan: ApplyPlan, facility: FacilityId): RunResult {
    val company = selectedCompany ?: return RunResult(emptyList(), ApplyReport.build(plan, emptyList()))
    return BlueprintApplyRunner(company, { blueprints.host()?.view(it) }, { issueNow(it) }).run(plan, facility)
}
