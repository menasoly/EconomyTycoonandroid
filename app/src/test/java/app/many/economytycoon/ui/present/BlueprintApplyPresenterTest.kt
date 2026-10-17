package app.many.economytycoon.ui.present

import app.many.economytycoon.blueprint.ApplyOptions
import app.many.economytycoon.blueprint.ApplyPlanner
import app.many.economytycoon.blueprint.ApplyReport
import app.many.economytycoon.blueprint.ApplyTarget
import app.many.economytycoon.blueprint.Blueprint
import app.many.economytycoon.blueprint.BlueprintSpec
import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.blueprint.DiffKind
import app.many.economytycoon.blueprint.SkipReason
import app.many.economytycoon.blueprint.StepResult
import app.many.economytycoon.blueprint.StepStatus
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintApplyPresenterTest {
    private val planner = ApplyPlanner(T.content)
    private val newTarget = ApplyTarget.NewFacility(T.region, "North")
    private fun existing(lines: Int = 1) = ApplyTarget.Existing(T.view(BlueprintSpec(T.bakery, List(lines) { T.line(null, priority = it) })))

    @Test fun newFacilityPreviewIsGroupedByPhase() {
        val m = BlueprintApplyPresenter.model(planner.plan(T.sample(), newTarget))
        assertEquals("Build Corner bakery", m.title)
        assertNull(m.blocked)
        assertEquals("Build", m.groups.first().label)
        assertEquals(listOf("Build", "Add lines and storage", "Install machines", "Configure lines", "Staffing", "Trading rules"), m.groups.map { it.label })
        assertEquals("Build and configure", m.button)
        assertTrue(m.canApply)
        assertEquals("Everything in the blueprint can be applied.", m.summary)
    }

    @Test fun stepsAreNumberedFromOneAndShowDependencies() {
        val m = BlueprintApplyPresenter.model(planner.plan(T.sample(), newTarget))
        val rows = m.groups.flatMap { it.rows }
        assertEquals((1..rows.size).toList(), rows.map { it.number }.sorted())
        assertNull(rows.first { it.number == 1 }.after)
        assertTrue(rows.first { it.text.startsWith("Install Mixer") }.after!!.startsWith("after step"))
        assertEquals("Build Bakery \"North\"", rows.first().text)
        assertTrue(rows.first().cost != null)
        assertNull(rows.first { it.text.startsWith("Switch") || it.text.startsWith("Schedule") }.cost)
    }

    @Test fun tilesSummariseCostStepsAndLeftOut() {
        val plan = planner.plan(T.sample(), existing(), options = ApplyOptions(budget = Money.units(12_000)))
        val m = BlueprintApplyPresenter.model(plan)
        assertEquals(plan.steps.size.toString(), m.tiles[1].value)
        assertEquals(plan.problems.size.toString(), m.tiles[2].value)
        assertEquals(Tone.WARN, m.tiles[2].tone)
        assertTrue(m.problems.any { it.reason == "Not enough cash" && it.tone == Tone.WARN })
        assertTrue(m.problems.any { it.reason == "Needs a step that was skipped" && it.tone == Tone.INFO })
        assertTrue(m.summary.contains("cannot"))
    }

    @Test fun blockedPlansShowOnlyTheReason() {
        val plan = planner.plan(T.sample(), newTarget, options = ApplyOptions(budget = Money.units(1)))
        val m = BlueprintApplyPresenter.model(plan)
        assertTrue(m.blocked!!.contains("afford"))
        assertFalse(m.canApply)
        assertEquals("Cannot apply", m.button)
        assertTrue(m.groups.isEmpty())
    }

    @Test fun noOpPlansSaySo() {
        val m = BlueprintApplyPresenter.model(planner.plan(T.sample(), ApplyTarget.Existing(T.view())))
        assertEquals("The facility already matches this blueprint.", m.summary)
        assertEquals("Nothing to do", m.button)
        assertFalse(m.canApply)
        assertTrue(m.alreadyDone > 0)
        assertTrue(m.changes.isEmpty())
    }

    @Test fun changesShowTheDryRunDiffWithSymbols() {
        val bp = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(T.makeFlour), T.line(T.makeBread, listOf(T.oven)))), "Two")
        val m = BlueprintApplyPresenter.model(planner.plan(bp, existing(1)))
        assertTrue(m.changes.any { it.symbol == "+" && it.section == "Lines" })
        assertTrue(m.unapplied.isEmpty())
        assertEquals(Tone.GOOD, BlueprintApplyPresenter.diffTone(DiffKind.ADDED))
        assertEquals(Tone.BAD, BlueprintApplyPresenter.diffTone(DiffKind.REMOVED))
        assertEquals(Tone.INFO, BlueprintApplyPresenter.diffTone(DiffKind.CHANGED))
    }

    @Test fun unappliedDifferencesAreShownSeparately() {
        val bp = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(T.makeBreadPro, listOf(T.proOven)))), "Pro")
        val m = BlueprintApplyPresenter.model(planner.plan(bp, existing(1), T.known()))
        assertTrue(m.unapplied.isNotEmpty())
        assertTrue(m.problems.single { it.text.contains("Pro oven") }.reason == "Technology not researched")
    }

    @Test fun skipTonesAreStable() {
        assertEquals(Tone.MUTED, BlueprintApplyPresenter.skipTone(SkipReason.ALREADY_DONE))
        assertEquals(Tone.BAD, BlueprintApplyPresenter.skipTone(SkipReason.TECH_NOT_RESEARCHED))
        assertEquals(Tone.WARN, BlueprintApplyPresenter.skipTone(SkipReason.LINE_LIMIT))
    }

    @Test fun reportRowsMirrorTheExecutorsAnswers() {
        val plan = planner.plan(T.sample(), newTarget)
        val results = plan.steps.map { if (it.index == 1) StepResult(1, false, Rules.LIMIT_EXCEEDED, "no more lines") else StepResult(it.index, true) }
        val r = BlueprintApplyPresenter.report(ApplyReport.build(plan, results))
        assertEquals(Tone.WARN, r.tone)
        assertEquals(StepStatus.APPLIED.label, r.rows[0].status)
        assertEquals("Refused", r.rows[1].status)
        assertEquals(Tone.BAD, r.rows[1].tone)
        assertEquals("no more lines", r.rows[1].detail)
        assertTrue(r.rows.any { it.status == "Blocked" && it.tone == Tone.WARN })
    }

    @Test fun reportToneIsGoodWhenCompleteAndBadWhenNothingLanded() {
        val plan = planner.plan(T.sample(), newTarget)
        assertEquals(Tone.GOOD, BlueprintApplyPresenter.report(ApplyReport.build(plan, plan.steps.map { StepResult(it.index, true) })).tone)
        assertEquals(Tone.BAD, BlueprintApplyPresenter.report(ApplyReport.build(plan, listOf(StepResult(0, false, "x", "no")))).tone)
    }
}
