package app.many.economytycoon.blueprint

import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.persistence.MemorySaveStore
import app.many.economytycoon.persistence.SaveManager
import app.many.economytycoon.session.Difficulty
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.SettingsStore
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.ui.app.AppController
import app.many.economytycoon.ui.app.AppEnvironment
import app.many.economytycoon.ui.app.AppScreen
import app.many.economytycoon.ui.app.NewGameDraft
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.applyBlueprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The blueprint feature as the game wires it: through the app controller, the session clock and the app's save store. */
class BlueprintSessionIntegrationTest {
    private val bakery = FacilityTypeId("bakery")
    private val blueprintStore = MemorySaveStore()
    private val saves = MemorySaveStore()

    private fun controller() = AppController(
        AppEnvironment(SimTestSupport.content, SaveManager(saves, { 100L }), SettingsStore(saves), blueprints = blueprintStore),
    ) { 77L }

    private fun playing(c: AppController): GameSession {
        c.openSetup()
        c.updateDraft(NewGameDraft(companyName = "Blueprint Co", difficulty = Difficulty.RELAXED))
        assertEquals(null, c.begin())
        return (c.screen as AppScreen.Playing).session
    }

    private fun grant(session: GameSession, name: String): FacilityId {
        val e = session.economy
        val company = session.selectedCompany!!
        e.content.facilityType(bakery).requiredTech?.let { e.research.grant(company, it, Tick(0)) }
        for (r in e.content.recipesForFacility(bakery)) r.requiredTech?.let { e.research.grant(company, it, Tick(0)) }
        val region = e.world.regions().sortedBy { it.id }.first().id
        return e.production.grantFacility(company, bakery, region, null, null, null, Tick(0), name).id
    }

    @Test fun routeKeyRoundTrips() {
        assertEquals(Route.Blueprints, Route.parse(Route.Blueprints.key))
    }

    @Test fun libraryIsKeptInTheAppStoreAcrossGames() {
        val first = playing(controller())
        val facility = grant(first, "Source bakery")
        first.issueNow(Command.AddLine(first.selectedCompany!!, facility))
        val saved = first.blueprints.capture(facility, name = "Master").getOrThrow()
        assertEquals(1, first.blueprints.library.size)

        val second = playing(controller())
        assertEquals(1, second.blueprints.library.size)
        assertEquals(saved.name, second.blueprints.library.all().single().name)
    }

    @Test fun capturingAndPlanningLeaveTheSimulationHashAlone() {
        val session = playing(controller())
        val source = grant(session, "Source bakery")
        val target = grant(session, "Target bakery")
        val before = session.simulation.hash()
        val id = session.blueprints.capture(source).getOrThrow().id
        session.blueprints.planExisting(id, target).getOrThrow()
        assertEquals(before, session.simulation.hash())
    }

    @Test fun applyingThroughTheSessionConfiguresTheFacility() {
        val session = playing(controller())
        val company = session.selectedCompany!!
        val source = grant(session, "Source bakery")
        session.issueNow(Command.AddLine(company, source))
        val line = session.economy.production.facility(source)!!.lines.first().id
        val recipe = session.economy.content.recipesForFacility(bakery).first().id
        assertTrue(session.issueNow(Command.SetRecipe(company, source, line, recipe)) is Outcome.Ok)
        val id = session.blueprints.capture(source).getOrThrow().id
        val target = grant(session, "Target bakery")
        val plan = session.blueprints.planExisting(id, target).getOrThrow()
        val run = session.applyBlueprint(plan, target)
        assertTrue(run.report.headline, run.report.applied > 0)
        assertEquals(recipe, session.economy.production.facility(target)!!.lines.first().recipe)
    }

    @Test fun buildFromABlueprintIsAnnouncedOnceAndConfiguredWhenFinished() {
        val session = playing(controller())
        val company = session.selectedCompany!!
        val source = grant(session, "Source bakery")
        session.issueNow(Command.AddLine(company, source))
        val id = session.blueprints.capture(source, name = "Master").getOrThrow().id
        val region = session.economy.world.regions().sortedBy { it.id }.first().id
        val began = session.blueprints.beginNew(id, region, "Second bakery").getOrThrow()
        val build = began.plan.steps.first().action.toCommand(company, null, emptyList())!!
        assertTrue(session.issueNow(build) is Outcome.Ok)
        assertEquals(1, session.blueprints.pending.size)

        repeat(session.economy.content.facilityType(bakery).buildTicks + 5) { session.step() }
        val ready = session.blueprintsReady().single()
        val announced = { session.news.items().count { it.headline.contains("Second bakery is built") } }
        assertEquals(1, announced())
        repeat(3) { session.step() }
        assertEquals(1, announced())

        val plan = session.blueprints.finishPending(ready).getOrThrow()
        session.applyBlueprint(plan, ready.facility)
        assertEquals(0, session.blueprints.pending.size)
        assertEquals(session.economy.production.facility(source)!!.lines.size, session.economy.production.facility(ready.facility)!!.lines.size)
    }

    @Test fun pendingBuildsSurviveRestartingTheApp() {
        val session = playing(controller())
        val source = grant(session, "Source bakery")
        val id = session.blueprints.capture(source).getOrThrow().id
        val region = session.economy.world.regions().sortedBy { it.id }.first().id
        session.blueprints.beginNew(id, region, "Later bakery").getOrThrow()
        val again = playing(controller())
        assertEquals(1, again.blueprints.pending.size)
        assertNotEquals(0, again.blueprints.library.size)
    }
}
