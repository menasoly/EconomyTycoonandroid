package app.many.economytycoon.ui.present

import app.many.economytycoon.blueprint.ApplyPlanner
import app.many.economytycoon.blueprint.ApplyTarget
import app.many.economytycoon.blueprint.Blueprint
import app.many.economytycoon.blueprint.BlueprintSpec
import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.blueprint.FacilityView
import app.many.economytycoon.blueprint.LineView
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintApplyRunnerTest {
    private val company = CompanyId(1)
    private val facility = FacilityId(7)
    private val planner = ApplyPlanner(T.content)

    /** A tiny stand-in for the game: AddLine adds a line, everything else just succeeds. */
    private class Fake(var view: FacilityView, val refuse: (Command) -> Boolean = { false }) {
        val seen = ArrayList<Command>()
        fun submit(c: Command): Outcome<*> {
            seen += c
            if (refuse(c)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "refused")
            if (c is Command.AddLine) view = view.copy(lines = view.lines + LineView(LineId(1000L + view.lines.size), view.lines.size, true, null, emptyList(), false))
            return Outcome.ok(Unit)
        }
    }

    private fun start(lines: Int) = T.view(BlueprintSpec(T.bakery, List(lines) { T.line(null, priority = it) }), id = 7)

    @Test fun runsEveryStepInOrderAndPicksUpNewLines() {
        val fake = Fake(start(1))
        val plan = planner.plan(T.sample(), ApplyTarget.Existing(fake.view))
        val run = BlueprintApplyRunner(company, { fake.view }, fake::submit).run(plan, facility)
        assertTrue(run.report.isComplete)
        assertEquals(plan.steps.size, fake.seen.size)
        val adds = fake.seen.indexOfLast { it is Command.AddLine }
        val firstInstallOnNew = fake.seen.indexOfFirst { it is Command.InstallMachine && it.line == LineId(1001) }
        assertTrue(adds < firstInstallOnNew)
        assertTrue(fake.seen.any { it is Command.SetRecipe && it.line == LineId(1002) && it.recipe == T.makeSourdough })
    }

    @Test fun aRefusedStepSkipsItsDependents() {
        val fake = Fake(start(3)) { it is Command.InstallMachine && it.type == T.oven }
        val plan = planner.plan(T.sample(), ApplyTarget.Existing(fake.view))
        val run = BlueprintApplyRunner(company, { fake.view }, fake::submit).run(plan, facility)
        assertTrue(run.report.rejected >= 1)
        assertTrue(run.report.blocked >= 1)
        assertFalse(fake.seen.any { it is Command.SetRecipe && it.recipe == T.makeBread })
        assertTrue(fake.seen.any { it is Command.SetRecipe && it.recipe == T.makeFlour })
        assertFalse(run.report.isComplete)
    }

    @Test fun unrelatedStepsStillRunAfterARefusal() {
        val fake = Fake(start(3)) { it is Command.SetStaffing }
        val plan = planner.plan(T.sample(), ApplyTarget.Existing(fake.view))
        val run = BlueprintApplyRunner(company, { fake.view }, fake::submit).run(plan, facility)
        assertTrue(run.report.rejected >= 1)
        assertTrue(fake.seen.any { it is Command.SetSellRule })
        assertTrue(run.report.applied > run.report.rejected)
    }

    @Test fun newFacilityPlansAreNotRunHere() {
        val fake = Fake(start(1))
        val plan = planner.plan(T.sample(), ApplyTarget.NewFacility(T.region, "N"))
        val run = BlueprintApplyRunner(company, { fake.view }, fake::submit).run(plan, facility)
        assertTrue(fake.seen.isEmpty())
        assertTrue(run.results.isEmpty())
        assertFalse(run.report.isComplete)
    }

    @Test fun missingFacilityViewLeavesLineStepsUnrun() {
        val fake = Fake(start(3))
        val plan = planner.plan(T.sample(), ApplyTarget.Existing(fake.view))
        val run = BlueprintApplyRunner(company, { null }, fake::submit).run(plan, facility)
        assertTrue(fake.seen.none { it is Command.SetRecipe })
        assertTrue(run.report.notRun > 0)
    }

    @Test fun emptyPlansRunNothing() {
        val fake = Fake(T.view())
        val plan = planner.plan(T.sample(), ApplyTarget.Existing(fake.view))
        val run = BlueprintApplyRunner(company, { fake.view }, fake::submit).run(plan, facility)
        assertTrue(fake.seen.isEmpty())
        assertEquals("Nothing to change.", run.report.headline)
        assertTrue(Blueprint.of(T.sampleSpec(), "x").name.isNotEmpty())
    }
}
