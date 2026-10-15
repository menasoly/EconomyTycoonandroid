package app.many.economytycoon.ui

import app.many.economytycoon.core.Tick
import app.many.economytycoon.persistence.MemorySaveStore
import app.many.economytycoon.persistence.ProfileStore
import app.many.economytycoon.persistence.SaveManager
import app.many.economytycoon.scenarios.LossReason
import app.many.economytycoon.scenarios.Medal
import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.scenarios.ScenarioEvent
import app.many.economytycoon.session.SettingsStore
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.ui.app.AppController
import app.many.economytycoon.ui.app.AppEnvironment
import app.many.economytycoon.ui.app.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppControllerScenarioTest {
    private var clock = 100L
    private val store = MemorySaveStore()
    private val profileStore = ProfileStore(MemorySaveStore())
    private fun controller() = AppController(
        AppEnvironment(SimTestSupport.content, SaveManager(store, { clock++ }), SettingsStore(store), profile = profileStore),
    ) { 7L }

    private val first = ScenarioCatalog.all[0].id
    private val second = ScenarioCatalog.all[1].id

    @Test fun theScenarioScreenOpensOnTheLatestUnlockedOne() {
        val c = controller()
        c.openScenarios()
        assertEquals(AppScreen.Scenarios(first), c.screen)
    }

    @Test fun aLockedScenarioCannotBeChosen() {
        val c = controller()
        c.openScenarios()
        c.selectScenario(second)
        assertEquals(first, (c.screen as AppScreen.Scenarios).selected)
        c.updateScenarioName("Locked Out Ltd")
        assertEquals("Locked Out Ltd", (c.screen as AppScreen.Scenarios).companyName)
    }

    @Test fun selectionAndNameOnlyApplyOnTheScenarioScreen() {
        val c = controller()
        c.selectScenario(first); c.updateScenarioName("Nowhere Co")
        assertTrue(c.screen is AppScreen.Menu)
        assertEquals(listOf("Not on the scenario screen."), c.startScenario())
    }

    @Test fun aShortNameIsRefused() {
        val c = controller()
        c.openScenarios(); c.updateScenarioName("ab")
        assertNotNull(c.startScenario())
        assertTrue(c.screen is AppScreen.Scenarios)
        c.updateScenarioName("x".repeat(41))
        assertNotNull(c.startScenario())
    }

    @Test fun startingBeginsThePlayerOnTheScenario() {
        val c = controller()
        c.openScenarios(); c.updateScenarioName("  Harvest Moon  ")
        assertNull(c.startScenario())
        val session = (c.screen as AppScreen.Playing).session
        assertEquals(first, session.economy.scenario.run!!.id)
        assertEquals("Harvest Moon", session.economy.companies.require(session.selectedCompany!!).name)
        assertNotNull(session.scenarioSink)
    }

    @Test fun aWinIsRecordedInTheSavedProfileAndOpensTheNextScenario() {
        val c = controller()
        c.openScenarios(); c.updateScenarioName("Winning Co")
        assertNull(c.startScenario())
        val session = (c.screen as AppScreen.Playing).session
        session.scenarioSink!!.invoke(ScenarioEvent.Won(first, 400, Medal.GOLD, Tick(400)))
        assertEquals(Medal.GOLD, c.profile.medal(first))
        assertEquals(Medal.GOLD, profileStore.load().medal(first))
        assertTrue(c.profile.isUnlocked(second))
        assertEquals(400L, c.profile.record(first)!!.bestDays)
    }

    @Test fun aLossIsRecordedToo() {
        val c = controller()
        c.openScenarios(); c.updateScenarioName("Losing Co")
        assertNull(c.startScenario())
        val session = (c.screen as AppScreen.Playing).session
        session.scenarioSink!!.invoke(ScenarioEvent.Lost(first, LossReason.DEADLINE, Tick(720)))
        assertEquals(1, c.profile.record(first)!!.losses)
        assertEquals(1, profileStore.load().record(first)!!.losses)
        assertFalse(c.profile.isUnlocked(second))
    }

    @Test fun reachingAGoalDoesNotTouchTheProfile() {
        val c = controller()
        c.openScenarios(); c.updateScenarioName("Goal Co")
        assertNull(c.startScenario())
        val session = (c.screen as AppScreen.Playing).session
        session.scenarioSink!!.invoke(ScenarioEvent.GoalReached(first, 0, Tick(5)))
        assertNull(c.profile.record(first))
        assertTrue(profileStore.load().all().isEmpty())
    }

    @Test fun loadingASavedScenarioGameReattachesTheProfile() {
        val c = controller()
        c.openScenarios(); c.updateScenarioName("Saved Co")
        assertNull(c.startScenario())
        val session = (c.screen as AppScreen.Playing).session
        session.save("s1", "Scenario save")
        c.quitToMenu(save = false)
        assertTrue(c.load("s1"))
        val loaded = (c.screen as AppScreen.Playing).session
        assertNotNull(loaded.scenarioSink)
        assertEquals(first, loaded.economy.scenario.run!!.id)
    }

    @Test fun everyNewGameCountsTowardLifetimeTotalsAndIsSaved() {
        val c = controller()
        c.openScenarios(); c.updateScenarioName("Counted Co")
        assertNull(c.startScenario())
        assertEquals(1L, c.profile.stats[app.many.economytycoon.achievements.Stat.GAMES_STARTED])
        assertEquals(1L, profileStore.load().stats[app.many.economytycoon.achievements.Stat.GAMES_STARTED])
        assertTrue(c.profile.achievements.isUnlocked("six-figures"))
    }

    @Test fun leavingAGameWritesTheProfile() {
        val c = controller()
        c.openScenarios(); c.updateScenarioName("Leaving Co")
        assertNull(c.startScenario())
        val session = (c.screen as AppScreen.Playing).session
        repeat(7) { session.step() }
        c.quitToMenu(save = false)
        assertEquals(7L, profileStore.load().stats[app.many.economytycoon.achievements.Stat.DAYS_PLAYED])
    }

    @Test fun loadedGamesKeepTrackingTheSameProfile() {
        val c = controller()
        c.openScenarios(); c.updateScenarioName("Resumed Co")
        assertNull(c.startScenario())
        (c.screen as AppScreen.Playing).session.save("r1", "Resume")
        c.quitToMenu(save = false)
        assertTrue(c.load("r1"))
        val session = (c.screen as AppScreen.Playing).session
        assertTrue(session.achievements.profile === c.profile)
        assertNotNull(session.profileSink)
    }

    @Test fun theAchievementsScreenOpensFromTheMenu() {
        val c = controller()
        c.openAchievements()
        assertEquals(AppScreen.Achievements, c.screen)
        c.showMenu()
        assertEquals(AppScreen.Menu, c.screen)
    }
}
