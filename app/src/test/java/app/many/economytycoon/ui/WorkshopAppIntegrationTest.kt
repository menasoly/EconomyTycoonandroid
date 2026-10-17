package app.many.economytycoon.ui

import app.many.economytycoon.core.Tick
import app.many.economytycoon.persistence.MemorySaveStore
import app.many.economytycoon.persistence.ProfileStore
import app.many.economytycoon.persistence.SaveManager
import app.many.economytycoon.scenarios.Medal
import app.many.economytycoon.scenarios.ScenarioEvent
import app.many.economytycoon.session.SettingsStore
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.ui.app.AppController
import app.many.economytycoon.ui.app.AppEnvironment
import app.many.economytycoon.ui.app.AppScreen
import app.many.economytycoon.ui.nav.NavState
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.nav.Tab
import app.many.economytycoon.ui.present.Glossary
import app.many.economytycoon.ui.present.HelpContent
import app.many.economytycoon.ui.present.Tips
import app.many.economytycoon.ui.present.WorkshopPage
import app.many.economytycoon.ui.present.WorkshopPlayPresenter
import app.many.economytycoon.workshop.DraftEdit
import app.many.economytycoon.workshop.DraftPrinter
import app.many.economytycoon.workshop.ImportResult
import app.many.economytycoon.workshop.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopAppIntegrationTest {
    private var clock = 100L
    private val saveStore = MemorySaveStore()
    private val workshopStore = MemorySaveStore()
    private val profileStore = ProfileStore(MemorySaveStore())

    private fun controller(workshop: MemorySaveStore = workshopStore) = AppController(
        AppEnvironment(
            SimTestSupport.content, SaveManager(saveStore, { clock++ }), SettingsStore(MemorySaveStore()),
            profile = profileStore, workshopSaves = workshop,
        ),
    ) { 7L }

    private fun AppController.writeDraft() {
        val r = workshop.importText(DraftPrinter.print(Samples.cheap("app-play")))
        assertTrue(r.toString(), r is ImportResult.Written)
    }

    @Test fun theWorkshopOpensFromTheMenuAndMovesBetweenItsPages() {
        val c = controller()
        c.openWorkshop()
        assertEquals(AppScreen.Workshop(WorkshopPage.EDITOR), c.screen)
        c.openWorkshop(WorkshopPage.REPORT)
        assertEquals(AppScreen.Workshop(WorkshopPage.REPORT), c.screen)
        c.showMenu()
        assertEquals(AppScreen.Menu, c.screen)
    }

    @Test fun theLibraryLivesInTheEnvironmentsStoreAndSurvivesARestart() {
        val c = controller()
        c.writeDraft()
        assertTrue(c.workshop.save() is app.many.economytycoon.core.Outcome.Ok)
        assertTrue(workshopStore.keys().contains("workshop.library"))
        assertEquals(1, controller().workshop.library.size)
    }

    @Test fun playingTheDraftStartsAGameWithTheDraftsScenario() {
        val c = controller()
        c.writeDraft()
        assertNull(c.playWorkshopDraft())
        val session = (c.screen as AppScreen.Playing).session
        assertEquals("app-play", session.economy.scenario.run?.id)
        assertNotNull(session.economy.scenario.run?.custom)
        assertEquals("Cheap Co", session.economy.companies.require(session.playerCompanies().first()).name)
        assertEquals(1, profileStore.load().stats.get(app.many.economytycoon.achievements.Stat.GAMES_STARTED).toInt())
    }

    @Test fun aDraftWithErrorsDoesNotStartAndLeavesTheScreenAlone() {
        val c = controller()
        c.openWorkshop()
        c.workshop.editor.apply(DraftEdit.SetTitle("x"))
        val problems = c.playWorkshopDraft()
        assertNotNull(problems)
        assertTrue(problems!!.isNotEmpty())
        assertEquals(AppScreen.Workshop(WorkshopPage.EDITOR), c.screen)
    }

    @Test fun playingFromInsideAGameSavesThatGameFirst() {
        val c = controller()
        c.openSetup(); c.updateDraft(app.many.economytycoon.ui.app.NewGameDraft(companyName = "Old Game Ltd"))
        assertNull(c.begin())
        val old = (c.screen as AppScreen.Playing).session
        c.writeDraft()
        assertNull(c.playWorkshopDraft())
        assertTrue(c.slots().any { it.id == AppController.QUIT_SLOT })
        assertFalse((c.screen as AppScreen.Playing).session === old)
    }

    @Test fun aWorkshopGameNeverTouchesTheMedalsOrTheCounts() {
        val c = controller()
        c.writeDraft()
        c.playWorkshopDraft()
        val session = (c.screen as AppScreen.Playing).session
        val before = profileStore.load().stats.get(app.many.economytycoon.achievements.Stat.SCENARIOS_WON)
        session.scenarioSink?.invoke(ScenarioEvent.Won("app-play", 10, Medal.GOLD, Tick(10)))
        session.scenarioSink?.invoke(ScenarioEvent.Lost("app-play", app.many.economytycoon.scenarios.LossReason.DEADLINE, Tick(11)))
        assertNull(c.profile.record("app-play"))
        assertEquals(before, c.profile.stats.get(app.many.economytycoon.achievements.Stat.SCENARIOS_WON))
        assertEquals(0, c.profile.medalCount(Medal.GOLD))
    }

    @Test fun aCatalogGameStillEarnsItsMedal() {
        val c = controller()
        c.openScenarios(); c.updateScenarioName("Medal Co")
        assertNull(c.startScenario())
        val session = (c.screen as AppScreen.Playing).session
        val id = session.economy.scenario.run!!.id
        session.scenarioSink?.invoke(ScenarioEvent.Won(id, 100, Medal.SILVER, Tick(100)))
        assertEquals(Medal.SILVER, c.profile.medal(id))
    }

    @Test fun aWorkshopGameSurvivesLeavingAndContinuing() {
        val c = controller()
        c.writeDraft()
        assertNull(c.playWorkshopDraft())
        val session = (c.screen as AppScreen.Playing).session
        repeat(12) { session.step() }
        val hash = session.simulation.hash()
        c.quitToMenu(true)
        assertTrue(c.continueGame())
        val back = (c.screen as AppScreen.Playing).session
        assertEquals(hash, back.simulation.hash())
        assertEquals(session.economy.scenario.def(), back.economy.scenario.def())
        assertEquals("app-play", back.economy.scenario.run?.id)
    }

    @Test fun theScenarioScreenReadsAWorkshopRun() {
        val c = controller()
        c.writeDraft()
        c.playWorkshopDraft()
        val session = (c.screen as AppScreen.Playing).session
        val model = app.many.economytycoon.ui.present.ScenariosPresenter.status(session)
        assertNotNull(model)
        assertEquals("Cheap Run", model?.title)
        assertEquals("Made in the workshop, so it earns no medal.", model?.medalNote)
    }

    @Test fun thePlayPromptNamesWhatIsLeftOut() {
        val c = controller()
        c.writeDraft()
        val prompt = WorkshopPlayPresenter.prompt(c.workshop)
        assertTrue(prompt.canPlay)
        assertTrue(prompt.lines.first().contains("Cheap Run"))
        assertTrue(prompt.lines.any { it.contains("bonus goal") || it.contains("weights") || it.contains("tags") })
        c.workshop.editor.apply(DraftEdit.SetTitle("x"))
        assertFalse(WorkshopPlayPresenter.prompt(c.workshop).canPlay)
    }

    @Test fun theRoutesParseAndSitInTheMoreTab() {
        for (r in listOf(Route.Workshop, Route.WorkshopReport, Route.WorkshopLibrary)) {
            assertEquals(r, Route.parse(r.key))
            assertEquals(Tab.MORE, r.tab)
        }
        val nav = NavState().select(Tab.MORE).open(Route.Workshop).open(Route.WorkshopReport)
        assertEquals(nav, NavState.decode(nav.encode()))
        assertEquals(Route.Workshop, nav.back()?.current)
    }

    @Test fun theReferenceTextsAreWired() {
        for (id in listOf("workshop-seed", "workshop-baseline", "workshop-softlock", "workshop-share-code")) assertNotNull(id, Glossary.byId(id))
        assertEquals(listOf("Seed"), Glossary.search("seed").filter { it.id == "workshop-seed" }.map { it.term })
        assertNotNull(HelpContent.byId("workshop"))
        assertEquals(Route.Workshop, Tips.byId("workshop-test")?.route)
    }
}
