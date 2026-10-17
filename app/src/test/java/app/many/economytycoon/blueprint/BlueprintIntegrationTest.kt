package app.many.economytycoon.blueprint

import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.persistence.MemorySaveStore
import app.many.economytycoon.production.SchedulePolicy
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.ui.present.BlueprintApplyRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the blueprint round trip against the real command executor and the shipped content. */
class BlueprintIntegrationTest {
    private val bakery = FacilityTypeId("bakery")
    private val breadRecipe = RecipeId("bake_bread")

    private class Rig {
        val sim = Simulation(SimTestSupport.config(11L, 0), SimTestSupport.content)
        val e = sim.economy
        val me = Actor.Player(1)
        val country = e.world.countries().first()
        val company: CompanyId = CompanyId((sim.execute(me, Command.FoundCompany("Blueprint Works", country.id, country.capital, Money.units(5_000_000))) as Outcome.Ok).value.created!!)
        val region = e.world.regionsOf(country.id).first().id
        fun ok(c: Command) { val o = sim.execute(me, c); assertTrue("$c -> ${o.rejectionOrNull()}", o.isOk) }
        fun facility(type: FacilityTypeId, name: String): FacilityId {
            val def = e.content.facilityType(type)
            def.requiredTech?.let { e.research.grant(company, it, Tick(0)) }
            for (r in e.content.recipesForFacility(type)) r.requiredTech?.let { e.research.grant(company, it, Tick(0)) }
            return e.production.grantFacility(company, type, region, null, null, null, Tick(0), name).id
        }
    }

    private fun configuredSource(r: Rig): FacilityId {
        val id = r.facility(bakery, "Source bakery")
        r.ok(Command.AddLine(r.company, id)); r.ok(Command.AddLine(r.company, id))
        val lines = r.e.production.facility(id)!!.lines.map { it.id }
        r.ok(Command.SetRecipe(r.company, id, lines[0], breadRecipe))
        r.ok(Command.SetRecipe(r.company, id, lines[1], breadRecipe))
        r.ok(Command.SetLineEnabled(r.company, id, lines[1], false))
        r.ok(Command.SetSchedulePolicy(r.company, id, SchedulePolicy.AUTO_MARGIN))
        r.ok(Command.SetQuality(r.company, id, 400_000L))
        r.ok(Command.SetStaffing(r.company, id, RoleId("cook"), 6, null))
        r.ok(Command.SetTraining(r.company, id, RoleId("cook"), 100_000L))
        r.ok(Command.SetSellRule(r.company, SellRule(id, GoodId("bread"), PriceStrategy.COST_PLUS, 150_000L, Quantity.units(40), Quantity.units(500), 50_000L)))
        r.ok(Command.SetBuyRule(r.company, BuyRule(id, GoodId("flour"), Quantity.units(300), 1_200_000L)))
        return id
    }

    @Test fun capturedFacilityReappliesToAnotherAndMatchesExactly() {
        val r = Rig()
        val host = EconomyBlueprintHost(r.e, r.company)
        val source = configuredSource(r)
        val bp = BlueprintCapture.capture(host.view(source)!!, contentTag = "t", day = host.day).getOrThrow()
        assertEquals(3, bp.spec.lines.size)
        assertEquals(listOf(breadRecipe, breadRecipe, null), bp.spec.lines.map { it.recipe })

        val target = r.facility(bakery, "Target bakery")
        val view = host.view(target)!!
        val plan = ApplyPlanner(r.e.content).plan(bp, ApplyTarget.Existing(view), host.research())
        assertTrue(plan.isComplete)
        val run = BlueprintApplyRunner(r.company, host::view) { c -> r.sim.execute(r.me, c) }.run(plan, target)
        assertTrue(run.report.headline, run.report.isComplete)
        assertEquals(plan.steps.size, run.report.applied)

        val copy = BlueprintCapture.capture(host.view(target)!!).getOrThrow()
        assertEquals(bp.hash, copy.hash)
        assertTrue(BlueprintDiffer(r.e.content).diff(bp.spec, copy.spec).isEmpty)
    }

    @Test fun applyingTwiceChangesNothing() {
        val r = Rig()
        val host = EconomyBlueprintHost(r.e, r.company)
        val bp = BlueprintCapture.capture(host.view(configuredSource(r))!!).getOrThrow()
        val target = r.facility(bakery, "Target")
        val runner = BlueprintApplyRunner(r.company, host::view) { c -> r.sim.execute(r.me, c) }
        val first = ApplyPlanner(r.e.content).plan(bp, ApplyTarget.Existing(host.view(target)!!), host.research())
        runner.run(first, target)
        val hash = r.sim.hash()
        val second = ApplyPlanner(r.e.content).plan(bp, ApplyTarget.Existing(host.view(target)!!), host.research())
        assertTrue(second.isNoOp)
        runner.run(second, target)
        assertEquals(hash, r.sim.hash())
    }

    @Test fun planningAndCapturingNeverChangeTheSimulation() {
        val r = Rig()
        val host = EconomyBlueprintHost(r.e, r.company)
        val source = configuredSource(r)
        val target = r.facility(bakery, "Target")
        val before = r.sim.hash()
        val bp = BlueprintCapture.capture(host.view(source)!!).getOrThrow()
        ApplyPlanner(r.e.content).plan(bp, ApplyTarget.Existing(host.view(target)!!), host.research()).commands(r.company, target, host.view(target)!!.lines.map { it.id })
        ApplyPlanner(r.e.content).plan(bp, ApplyTarget.NewFacility(r.region, "x"), host.research())
        BlueprintAnalyzer(r.e.content).analyze(bp, host.research())
        assertEquals(before, r.sim.hash())
    }

    @Test fun newFacilityFlowBuildsThenConfigures() {
        val r = Rig()
        val host = EconomyBlueprintHost(r.e, r.company)
        val ctl = BlueprintController.open(MemorySaveStore()) { host }
        val source = configuredSource(r)
        val id = ctl.capture(source, name = "Master bakery").getOrThrow().id
        val began = ctl.beginNew(id, r.region, "Second bakery").getOrThrow()
        val build = began.plan.commands(r.company, null, emptyList()).single()
        assertTrue(build is Command.Build)
        r.ok(build)
        assertTrue(ctl.readyPending().isEmpty())
        val ticks = r.e.content.facilityType(bakery).buildTicks + 5
        repeat(ticks) { r.sim.advance() }
        val ready = ctl.readyPending().single()
        val plan = ctl.finishPending(ready).getOrThrow()
        val run = BlueprintApplyRunner(r.company, host::view) { c -> r.sim.execute(r.me, c) }.run(plan, ready.facility)
        assertTrue(run.report.headline, run.report.coveragePpm > 800_000L)
        val built = BlueprintCapture.capture(host.view(ready.facility)!!).getOrThrow()
        assertEquals(3, built.spec.lines.size)
        assertEquals(0, ctl.pending.size)
    }

    @Test fun anotherCompanysFacilityIsInvisibleToTheHost() {
        val r = Rig()
        val host = EconomyBlueprintHost(r.e, r.company)
        val other = r.e.companies.active().firstOrNull { it.id != r.company }
        assertEquals(null, other?.let { c -> r.e.production.facilitiesOf(c.id).firstOrNull()?.let { host.view(it.id) } })
        assertEquals(emptyList<FacilityChoice>(), host.facilities())
        assertTrue(host.regions().isNotEmpty())
        assertEquals(r.e.tick.value, host.day)
    }

    @Test fun missingResearchBlocksApplyingToTheCompany() {
        val r = Rig()
        val host = EconomyBlueprintHost(r.e, r.company)
        val tech = r.e.content.allTechnologies.first { it.unlocksFacilities.isNotEmpty() }.id
        assertTrue(!host.research().hasTech(tech))
        r.e.research.grant(r.company, tech, Tick(0))
        assertTrue(host.research().hasTech(tech))
    }
}
