package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.production.SchedulePolicy
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.world.ClimateZone
import app.many.economytycoon.world.Point
import app.many.economytycoon.world.Region
import app.many.economytycoon.world.Terrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyPlannerTest {
    private val planner = ApplyPlanner(T.content)
    private val company = CompanyId(1)
    private val newBakery = ApplyTarget.NewFacility(T.region, "North bakery")

    private fun empty(lines: Int = 1) = T.view(BlueprintSpec(T.bakery, List(lines) { T.line(null, priority = it) }))

    @Test fun newFacilityPlanStartsWithTheBuild() {
        val plan = planner.plan(T.sample(), newBakery)
        assertFalse(plan.isBlocked)
        assertTrue(plan.steps.first().action is StepAction.Build)
        assertEquals("Build Bakery \"North bakery\"", plan.steps.first().summary)
        assertTrue(plan.isComplete)
    }

    @Test fun stepsRunInPhaseOrderAndDependOnEarlierSteps() {
        val plan = planner.plan(T.sample(), newBakery)
        assertEquals(plan.steps.map { it.phase }.sorted(), plan.steps.map { it.phase })
        for (s in plan.steps) {
            assertEquals(s.index, plan.steps.indexOf(s))
            assertTrue(s.dependsOn.all { it < s.index })
        }
        val recipe = plan.steps.first { it.action is StepAction.SetRecipe && (it.action as StepAction.SetRecipe).recipe == T.makeSourdough }
        val installs = plan.steps.filter { it.action is StepAction.InstallMachine && (it.action as StepAction.InstallMachine).slot == 2 }
        assertTrue(recipe.dependsOn.containsAll(installs.map { it.index }))
    }

    @Test fun costOfANewPlanMatchesTheEstimateWithoutMaterials() {
        val bp = T.sample()
        val plan = planner.plan(bp, newBakery)
        val est = CostEstimator(T.content).estimate(bp.spec)!!
        assertEquals(est.capital - est.buildMaterials, plan.totalCost)
    }

    @Test fun planResultEqualsTheBlueprintWhenNothingIsSkipped() {
        val bp = T.sample()
        val plan = planner.plan(bp, newBakery)
        assertEquals(bp.spec, plan.after)
        assertTrue(plan.unapplied.isEmpty)
        assertEquals(bp.hash, plan.after.canonicalHash())
    }

    @Test fun bindingNeedsAFacilityBeforeAnythingButTheBuild() {
        val plan = planner.plan(T.sample(), newBakery)
        val unbuilt = plan.bind(company, null, emptyList())
        assertTrue(unbuilt.first().command is Command.Build)
        assertTrue(unbuilt.drop(1).all { it.command == null && it.pending == "waiting for the facility to be built" })
        val build = unbuilt.first().command as Command.Build
        assertEquals(T.bakery, build.type)
        assertEquals("North bakery", build.name)
    }

    @Test fun bindingWithAFacilityGivesCommandsForKnownLinesOnly() {
        val plan = planner.plan(T.sample(), newBakery)
        val f = FacilityId(9)
        val first = plan.commands(company, f, listOf(LineId(1)))
        assertTrue(first.any { it is Command.AddLine })
        assertTrue(first.any { it is Command.SetRecipe && it.line == LineId(1) && it.recipe == T.makeFlour })
        assertTrue(first.none { it is Command.SetRecipe && it.recipe == T.makeBread })
        val all = plan.commands(company, f, listOf(LineId(1), LineId(2), LineId(3)))
        assertTrue(all.size > first.size)
        assertTrue(all.any { it is Command.InstallMachine && it.line == LineId(3) && it.type == T.mixer })
        assertTrue(all.any { it is Command.SetSellRule && it.rule.facility == f })
        assertTrue(all.any { it is Command.SetStaffing && it.role == T.baker && it.target == 6L })
        assertTrue(all.any { it is Command.SetSchedulePolicy && it.policy == SchedulePolicy.AUTO_MARGIN })
        assertTrue(all.any { it is Command.SetQuality && it.levelPpm == 300_000L })
        assertTrue(all.any { it is Command.ExpandStorage })
    }

    @Test fun identicalFacilityNeedsNothing() {
        val plan = planner.plan(T.sample(), ApplyTarget.Existing(T.view()))
        assertTrue(plan.isNoOp)
        assertTrue(plan.steps.isEmpty())
        assertTrue(plan.diff.isEmpty)
        assertTrue(plan.noOps.isNotEmpty())
        assertTrue(plan.problems.isEmpty())
    }

    @Test fun partlyConfiguredFacilityGetsOnlyTheMissingSteps() {
        val partial = T.view(BlueprintSpec(T.bakery, listOf(T.line(T.makeFlour), T.line(null, priority = 1))))
        val plan = planner.plan(T.sample(), ApplyTarget.Existing(partial))
        val kinds = plan.steps.map { it.action::class.simpleName }
        assertTrue("AddLine" in kinds)
        assertFalse("Build" in kinds)
        assertTrue(plan.steps.none { it.action is StepAction.SetRecipe && (it.action as StepAction.SetRecipe).recipe == T.makeFlour })
        assertEquals(1, plan.steps.count { it.action is StepAction.AddLine })
        assertEquals(T.sampleSpec(), plan.after)
        assertTrue(plan.diff.entries.any { it.section == "Machines" })
    }

    @Test fun existingLinesAreCommandedByTheirOwnIds() {
        val view = empty(3)
        val plan = planner.plan(T.sample(), ApplyTarget.Existing(view))
        val cmds = plan.commands(company, view.id, view.lines.map { it.id })
        assertTrue(cmds.none { it is Command.AddLine })
        val installs = cmds.filterIsInstance<Command.InstallMachine>()
        assertEquals(3, installs.size)
        assertEquals(view.lines[1].id, installs.first { it.type == T.oven && it.line == view.lines[1].id }.line)
    }

    @Test fun unresearchedMachineSkipsItsRecipeToo() {
        val spec = BlueprintSpec(T.bakery, listOf(T.line(T.makeBreadPro, listOf(T.proOven))))
        val plan = planner.plan(Blueprint.of(spec, "Pro"), ApplyTarget.Existing(empty()), T.known("mixing"))
        val reasons = plan.problems.map { it.reason }
        assertTrue(SkipReason.TECH_NOT_RESEARCHED in reasons)
        assertTrue(plan.problems.any { it.reason == SkipReason.TECH_NOT_RESEARCHED && "Automation" in it.detail })
        assertTrue(plan.steps.isEmpty())
        assertFalse(plan.isComplete)
        assertFalse(plan.unapplied.isEmpty)
    }

    @Test fun recipeWhoseMachineWasSkippedReportsDependency() {
        val spec = BlueprintSpec(T.bakery, listOf(T.line(T.makeBread, listOf(T.oven))))
        val plan = planner.plan(Blueprint.of(spec, "Bread"), ApplyTarget.Existing(empty()), options = ApplyOptions(budget = Money.units(5_000)))
        assertEquals(SkipReason.UNAFFORDABLE, plan.problems.first { it.summary.contains("Oven") }.reason)
        val rec = plan.problems.first { it.action is StepAction.SetRecipe }
        assertEquals(SkipReason.DEPENDENCY_SKIPPED, rec.reason)
        assertTrue("Oven" in rec.detail)
    }

    @Test fun recipeTechIsCheckedSeparatelyFromMachines() {
        val spec = BlueprintSpec(T.bakery, listOf(T.line(T.makeSourdough, listOf(T.oven, T.mixer))))
        val plan = planner.plan(Blueprint.of(spec, "Sour"), ApplyTarget.Existing(empty()), T.known())
        assertTrue(plan.steps.any { it.action is StepAction.InstallMachine && (it.action as StepAction.InstallMachine).machine == T.oven })
        assertEquals(SkipReason.TECH_NOT_RESEARCHED, plan.problems.first { it.summary.contains("Mixer") }.reason)
        assertEquals(SkipReason.TECH_NOT_RESEARCHED, plan.problems.first { it.action is StepAction.SetRecipe }.reason)
    }

    @Test fun lineLimitSkipsExtraLinesAndEverythingOnThem() {
        val lines = List(5) { T.line(T.makeFlour, priority = it) }
        val plan = planner.plan(Blueprint.of(BlueprintSpec(T.bakery, lines), "Wide"), ApplyTarget.Existing(empty()))
        val limited = plan.problems.filter { it.reason == SkipReason.LINE_LIMIT }
        assertEquals(2, limited.size)
        assertEquals(2, plan.problems.count { it.reason == SkipReason.DEPENDENCY_SKIPPED })
        assertEquals(3, plan.after.lines.size)
    }

    @Test fun runningLinesKeepTheirRecipe() {
        val view = T.view(BlueprintSpec(T.bakery, listOf(T.line(T.makeFlour))), running = setOf(0))
        val bp = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(T.makeBread, listOf(T.oven)))), "Switch")
        val plan = planner.plan(bp, ApplyTarget.Existing(view))
        assertEquals(SkipReason.LINE_BUSY, plan.problems.single().reason)
        assertTrue(plan.steps.any { it.action is StepAction.InstallMachine })
        assertEquals(T.makeFlour, plan.after.lines[0].recipe)
    }

    @Test fun budgetDropsWhatCannotBePaidAndItsDependents() {
        val plan = planner.plan(T.sample(), ApplyTarget.Existing(empty(1)), options = ApplyOptions(budget = Money.units(12_000)))
        assertTrue(plan.totalCost <= Money.units(12_000))
        assertTrue(plan.problems.any { it.reason == SkipReason.UNAFFORDABLE })
        assertTrue(plan.problems.any { it.reason == SkipReason.DEPENDENCY_SKIPPED })
        assertFalse(plan.isComplete)
    }

    @Test fun cannotAffordTheBuildBlocksThePlan() {
        val plan = planner.plan(T.sample(), newBakery, options = ApplyOptions(budget = Money.units(100)))
        assertTrue(plan.isBlocked)
        assertTrue(plan.steps.isEmpty())
        assertTrue("afford" in plan.blocked!!)
    }

    @Test fun wrongFacilityKindBlocks() {
        val plan = planner.plan(T.sample(), ApplyTarget.Existing(T.view(BlueprintSpec(FacilityTypeId("farm"), listOf(T.line(null))))))
        assertTrue(plan.isBlocked)
        assertTrue("Farm" in plan.blocked!!)
    }

    @Test fun shutDownFacilityBlocks() {
        val plan = planner.plan(T.sample(), ApplyTarget.Existing(empty().copy(operating = false)))
        assertEquals("The facility is not operating. Reopen it before applying a blueprint.", plan.blocked)
    }

    @Test fun removedFacilityTypeBlocks() {
        val plan = planner.plan(Blueprint.of(BlueprintSpec(FacilityTypeId("gone"), emptyList()), "Old"), newBakery)
        assertTrue(plan.isBlocked)
    }

    @Test fun siteRulesBlockANewBuild() {
        val mine = Blueprint.of(BlueprintSpec(FacilityTypeId("mine"), listOf(T.line(RecipeId("dig_ore")))), "Mine")
        val region = Region(T.region, CountryId(1), "Vale", Point(1, 1), Terrain.PLAINS, ClimateZone.TEMPERATE, 100, 500_000L, false)
        val blocked = planner.plan(mine, ApplyTarget.NewFacility(T.region, "M", deposit = DepositId(1), site = SiteInfo(region)))
        assertTrue(blocked.isBlocked)
        val ok = planner.plan(mine, ApplyTarget.NewFacility(T.region, "M", deposit = DepositId(1), site = SiteInfo(region, setOf(GoodId("ore")))))
        assertFalse(ok.isBlocked)
        assertEquals(DepositId(1), (ok.steps.first().action as StepAction.Build).deposit)
        val noDeposit = planner.plan(mine, ApplyTarget.NewFacility(T.region, "M"))
        assertTrue("deposit" in noDeposit.blocked!!)
    }

    @Test fun unresearchedFacilityBlocks() {
        val lab = Blueprint.of(BlueprintSpec(FacilityTypeId("lab_bakery"), listOf(T.line(null))), "Lab")
        assertTrue(planner.plan(lab, newBakery, T.known()).isBlocked)
        assertFalse(planner.plan(lab, newBakery, T.known("mixing", "automation")).isBlocked)
    }

    @Test fun extraMachinesStayUnlessAsked() {
        val view = T.view(BlueprintSpec(T.bakery, listOf(T.line(null, listOf(T.oven, T.mixer)))))
        val bp = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(null, listOf(T.oven)))), "Lean")
        val keep = planner.plan(bp, ApplyTarget.Existing(view))
        assertTrue(keep.steps.isEmpty())
        assertTrue(keep.unapplied.isEmpty)
        val strip = planner.plan(bp, ApplyTarget.Existing(view), options = ApplyOptions(removeExtraMachines = true))
        val rm = strip.steps.single().action as StepAction.RemoveMachine
        assertEquals(T.mixer, rm.machine)
        assertEquals(listOf(T.oven), strip.after.lines[0].machines)
        assertTrue(strip.commands(company, view.id, view.lines.map { it.id }).single() is Command.RemoveMachine)
    }

    @Test fun clearRecipesIsOptIn() {
        val view = T.view(BlueprintSpec(T.bakery, listOf(T.line(T.makeFlour))))
        val bp = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(null))), "Blank")
        assertTrue(planner.plan(bp, ApplyTarget.Existing(view)).steps.isEmpty())
        val clear = planner.plan(bp, ApplyTarget.Existing(view), options = ApplyOptions(clearRecipes = true))
        assertNull((clear.steps.single().action as StepAction.SetRecipe).recipe)
    }

    @Test fun optionsLeaveOutWholeSections() {
        val plan = planner.plan(T.sample(), newBakery, options = ApplyOptions(staffing = false, trading = false, storage = false, schedule = false))
        val kinds = plan.steps.map { it.phase }.toSet()
        assertFalse(ApplyPhase.STAFF in kinds)
        assertFalse(ApplyPhase.TRADE in kinds)
        assertTrue(plan.steps.none { it.action is StepAction.ExpandStorage || it.action is StepAction.SetPolicy || it.action is StepAction.SetQuality })
        assertTrue(plan.unapplied.isEmpty)
    }

    @Test fun extraRulesAreRemovedOnRequest() {
        val s = T.sampleSpec()
        val view = T.view(s.copy(sells = s.sells + s.sells[0].copy(good = GoodId("flour"))))
        val keep = planner.plan(T.sample(), ApplyTarget.Existing(view))
        assertTrue(keep.steps.isEmpty())
        val drop = planner.plan(T.sample(), ApplyTarget.Existing(view), options = ApplyOptions(removeExtraRules = true))
        assertEquals(GoodId("flour"), (drop.steps.single().action as StepAction.RemoveSell).good)
        assertTrue(drop.commands(company, view.id, view.lines.map { it.id }).single() is Command.RemoveSellRule)
    }

    @Test fun removedContentInRulesIsSkippedNotFatal() {
        val s = T.sampleSpec()
        val spec = s.copy(sells = s.sells + s.sells[0].copy(good = GoodId("gone")), staff = s.staff + StaffSpec(app.many.economytycoon.core.RoleId("gone"), 1))
        val plan = planner.plan(Blueprint.of(spec, "Old"), newBakery)
        assertEquals(2, plan.problems.count { it.reason == SkipReason.UNKNOWN_CONTENT })
        assertNotNull(plan.steps.firstOrNull { it.action is StepAction.SetSell })
    }

    @Test fun planningIsDeterministic() {
        assertEquals(planner.plan(T.sample(), newBakery), planner.plan(T.sample(), newBakery))
        val a = planner.plan(T.sample(), ApplyTarget.Existing(empty(2)), T.known("mixing"))
        val b = planner.plan(T.sample(), ApplyTarget.Existing(empty(2)), T.known("mixing"))
        assertEquals(a, b)
    }
}
