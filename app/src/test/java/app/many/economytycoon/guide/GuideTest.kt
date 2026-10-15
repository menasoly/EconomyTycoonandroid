package app.many.economytycoon.guide

import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.production.Facility
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val region = e.world.regionsOf(country.id).first().id
    private val me: CompanyId = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Guide Works", country.id, null, Money.units(2_000_000))) as Outcome.Ok).value.created!!)

    private fun done() = Guide.statuses(e, me).filter { it.state == StepState.DONE }.map { it.step.id }
    private fun farm(): Facility = e.production.grantFacility(me, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)

    @Test fun stepIdsAreUniqueAndEveryStepSaysHowToDoIt() {
        assertEquals(Guide.steps.size, Guide.steps.map { it.id }.toSet().size)
        for (s in Guide.steps) {
            assertTrue(s.id, s.title.isNotBlank() && s.why.isNotBlank() && s.how.isNotBlank())
        }
    }

    @Test fun aNewCompanyHasDoneNothingAndIsToldWhereToStart() {
        val p = GuideProgress.of(e, me)
        assertEquals(0, p.doneCount)
        assertEquals("first-day", p.current!!.id)
        assertFalse(p.finished)
        assertEquals(0L, p.ppm)
    }

    @Test fun onlyOneStepIsCurrentAndTheRestWait() {
        val s = Guide.statuses(e, me)
        assertEquals(1, s.count { it.state == StepState.CURRENT })
        assertEquals(Guide.steps.size - 1, s.count { it.state == StepState.LATER })
    }

    @Test fun timePassingCompletesTheFirstStep() {
        sim.advance(1)
        assertTrue(done().contains("first-day"))
        assertEquals("build-first", GuideProgress.of(e, me).current!!.id)
    }

    @Test fun buildingAndGivingWork() {
        val f = farm()
        assertTrue(done().contains("build-first"))
        assertFalse(done().contains("set-recipe"))
        val r = sim.execute(Actor.Player(1), Command.SetRecipe(me, f.id, f.lines[0].id, RecipeId("grow_wheat")))
        assertTrue(r.toString(), r.isOk)
        assertTrue(done().contains("set-recipe"))
    }

    @Test fun hiringIsSeenOnceAnyoneIsEmployed() {
        val f = farm()
        sim.execute(Actor.Player(1), Command.SetStaffing(me, f.id, RoleId("farmhand"), 4))
        sim.advance(30)
        assertTrue(done().contains("hire-staff"))
    }

    @Test fun anAutomationRuleCountsAsAutomatedSelling() {
        val f = farm()
        val r = sim.execute(Actor.Player(1), Command.SetSellRule(me, SellRule(f.id, GoodId("wheat"), PriceStrategy.UNDERCUT, 50_000)))
        assertTrue(r.toString(), r.isOk)
        assertTrue(done().contains("sell-rule"))
    }

    @Test fun twoFacilitiesCompleteTheGrowthStep() {
        farm()
        assertFalse(done().contains("second-facility"))
        farm()
        assertTrue(done().contains("second-facility"))
    }

    @Test fun aClosedFacilityStillCountsAsBuilt() {
        val f = farm()
        e.production.shutdown(me, f.id)
        assertTrue(done().contains("build-first"))
    }

    @Test fun borrowingCompletesTheLoanStep() {
        e.finance.mint(e.tick, e.finance.bank, Money.units(5_000_000), "test:lender")
        val r = sim.execute(Actor.Player(1), Command.Borrow(me, Money.units(50_000), 90))
        assertTrue(r.toString(), r.isOk)
        assertTrue(done().contains("borrow"))
    }

    @Test fun researchCountsOnceAProjectIsRunningOrDone() {
        farm()
        val tech = e.research.available(me).firstOrNull()
        if (tech != null) {
            e.research.grant(me, tech.id, e.tick)
            assertTrue(done().contains("research"))
        }
    }

    @Test fun aCompanyThatDoesNotExistHasNothingDone() {
        farm()
        val ghost = CompanyId(9_999)
        assertTrue(Guide.statuses(e, ghost).none { it.state == StepState.DONE })
    }

    @Test fun progressIsAShareOfStepsDone() {
        sim.advance(1)
        farm()
        val p = GuideProgress.of(e, me)
        assertEquals(2, p.doneCount)
        assertEquals(2_000_000L / Guide.steps.size, p.ppm)
    }

    @Test fun unknownStepsAreNotFound() {
        assertNull(Guide.byId("nope"))
        assertNotNull(Guide.byId("borrow"))
    }
}
