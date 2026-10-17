package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyReportTest {
    private val planner = ApplyPlanner(T.content)
    private val plan = planner.plan(T.sample(), ApplyTarget.NewFacility(T.region, "North"))

    private fun allOk() = plan.steps.map { StepResult(it.index, true) }

    @Test fun everythingAppliedIsComplete() {
        val r = ApplyReport.build(plan, allOk())
        assertTrue(r.isComplete)
        assertEquals(plan.steps.size, r.applied)
        assertEquals(1_000_000L, r.coveragePpm)
        assertEquals("Applied all ${plan.steps.size} steps.", r.headline)
    }

    @Test fun aRefusalBlocksItsDependentsTransitively() {
        val install = plan.steps.first { it.action is StepAction.InstallMachine }
        val results = plan.steps.map { if (it.index == install.index) StepResult(it.index, false, Rules.INSUFFICIENT_FUNDS, "cannot afford Oven") else StepResult(it.index, true) }
        val r = ApplyReport.build(plan, results)
        assertEquals(StepStatus.REJECTED, r.rows[install.index].status)
        assertEquals("cannot afford Oven", r.rows[install.index].detail)
        val dependents = plan.steps.filter { install.index in it.dependsOn }
        assertTrue(dependents.isNotEmpty())
        assertTrue(dependents.all { r.rows[it.index].status == StepStatus.BLOCKED })
        assertFalse(r.isComplete)
        assertTrue(r.coveragePpm < 1_000_000L)
        assertTrue(r.headline.contains("refused"))
    }

    @Test fun failedBuildBlocksEverythingThatFollows() {
        val results = listOf(StepResult(0, false, Rules.INSUFFICIENT_FUNDS, "no money"))
        val r = ApplyReport.build(plan, results)
        assertEquals(1, r.rejected)
        assertEquals(plan.steps.size - 1, r.blocked)
        assertEquals(0, r.applied)
    }

    @Test fun stepsNeverRunAreReported() {
        val some = allOk().take(3)
        val r = ApplyReport.build(plan, some)
        assertEquals(3, r.applied)
        assertTrue(r.notRun > 0)
        assertEquals(plan.steps.size, r.rows.size)
    }

    @Test fun outcomesConvertToResults() {
        assertTrue(StepResult.of(1, Outcome.ok(Unit)).ok)
        val bad = StepResult.of(2, Outcome.reject(Rules.WRONG_STATE, "busy"))
        assertEquals(Rules.WRONG_STATE, bad.rule)
        assertEquals("busy", bad.message)
        assertFalse(bad.ok)
    }

    @Test fun planLeftOvers_makeTheReportIncompleteEvenWhenAllStepsLand() {
        val limited = planner.plan(T.sample(), ApplyTarget.Existing(T.view(BlueprintSpec(T.bakery, listOf(T.line(null))))), T.known(), ApplyOptions(budget = Money.units(1_000)))
        val r = ApplyReport.build(limited, limited.steps.map { StepResult(it.index, true) })
        assertFalse(r.isComplete)
        assertTrue(r.headline.contains("left out of the plan"))
    }

    @Test fun emptyPlanIsNothingToDo() {
        val noop = planner.plan(T.sample(), ApplyTarget.Existing(T.view()))
        val r = ApplyReport.build(noop, emptyList())
        assertEquals("Nothing to change.", r.headline)
        assertEquals(1_000_000L, r.coveragePpm)
    }
}
