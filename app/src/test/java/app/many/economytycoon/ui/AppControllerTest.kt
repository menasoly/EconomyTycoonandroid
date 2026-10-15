package app.many.economytycoon.ui

import app.many.economytycoon.persistence.MemorySaveStore
import app.many.economytycoon.persistence.SaveManager
import app.many.economytycoon.session.Difficulty
import app.many.economytycoon.session.GameSettings
import app.many.economytycoon.session.SettingsStore
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.ui.app.AppController
import app.many.economytycoon.ui.app.AppEnvironment
import app.many.economytycoon.ui.app.AppScreen
import app.many.economytycoon.ui.app.LoadFailure
import app.many.economytycoon.ui.app.NewGameDraft
import app.many.economytycoon.world.WorldSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppControllerTest {
    private var clock = 100L
    private val store = MemorySaveStore()
    private fun controller(entropy: Long = 77L) = AppController(
        AppEnvironment(SimTestSupport.content, SaveManager(store, { clock++ }), SettingsStore(store)),
    ) { entropy }

    private fun playing(c: AppController): AppScreen.Playing {
        c.openSetup()
        c.updateDraft(NewGameDraft(companyName = "Menu Tested Co", difficulty = Difficulty.RELAXED))
        assertNull(c.begin())
        return c.screen as AppScreen.Playing
    }

    @Test fun theAppStartsOnTheMenu() {
        assertTrue(controller().screen is AppScreen.Menu)
    }

    @Test fun setupKeepsTheDraftAndStartsAGame() {
        val c = controller()
        c.openSetup()
        c.updateDraft(NewGameDraft(companyName = "  Alpine Works  ", difficulty = Difficulty.CUTTHROAT, seedText = "42"))
        assertEquals("  Alpine Works  ", (c.screen as AppScreen.Setup).draft.companyName)
        assertNull(c.begin())
        val session = (c.screen as AppScreen.Playing).session
        assertEquals("Alpine Works", session.economy.companies.require(session.selectedCompany!!).name)
        assertEquals(42L, session.economy.config.seed)
    }

    @Test fun aBlankSeedUsesTheEntropySource() {
        val c = controller(entropy = 9_001L)
        val s = playing(c).session
        assertEquals(9_001L, s.economy.config.seed)
    }

    @Test fun invalidDraftsExplainThemselvesAndDoNotStart() {
        val c = controller()
        c.openSetup()
        c.updateDraft(NewGameDraft(companyName = "ab", seedText = "abc", countryIndex = 5))
        val problems = c.begin()!!
        assertEquals(3, problems.size)
        assertTrue(c.screen is AppScreen.Setup)
    }

    @Test fun beginOutsideSetupDoesNothing() {
        assertNotNull(controller().begin())
    }

    @Test fun draftChecksNamesSeedsAndCountries() {
        assertTrue(NewGameDraft(companyName = "Good Name").isValid())
        assertFalse(NewGameDraft(companyName = "x".repeat(41)).isValid())
        assertFalse(NewGameDraft(companyName = "Good Name", seedText = "1.5").isValid())
        assertEquals(12L, NewGameDraft(seedText = " 12 ").seedOrNull)
        assertNull(NewGameDraft(seedText = "").seedOrNull)
        assertFalse(NewGameDraft(companyName = "Good Name", worldSize = WorldSize.SMALL, countryIndex = 1).isValid())
        assertTrue(NewGameDraft(companyName = "Good Name", worldSize = WorldSize.MEDIUM, countryIndex = 2).isValid())
    }

    @Test fun quittingSavesAndContinueFindsIt() {
        val c = controller()
        val s = playing(c).session
        repeat(5) { s.step() }
        c.quitToMenu()
        assertTrue(c.screen is AppScreen.Menu)
        val slot = c.continueSlot()!!
        assertEquals(AppController.QUIT_SLOT, slot.id)
        assertEquals(5L, slot.tick)
        assertTrue(c.continueGame())
        assertEquals(5L, (c.screen as AppScreen.Playing).session.tick.value)
    }

    @Test fun quittingWithoutSavingLeavesNoSlot() {
        val c = controller()
        playing(c)
        c.quitToMenu(save = false)
        assertTrue(c.slots().isEmpty())
        assertNull(c.continueSlot())
        assertFalse(c.continueGame())
    }

    @Test fun missingSavesReportAClearFailure() {
        val c = controller()
        assertFalse(c.load("nope"))
        assertTrue(c.lastFailure is LoadFailure.Missing)
        assertTrue(c.lastFailure!!.message.contains("nope"))
    }

    @Test fun damagedSavesReportAClearFailureAndKeepTheMenu() {
        val c = controller()
        val s = playing(c).session
        s.save("one", "One")
        c.showMenu()
        store.put("one.etsave", ByteArray(64))
        assertFalse(c.load("one"))
        assertTrue(c.lastFailure is LoadFailure.Damaged)
        assertTrue(c.screen is AppScreen.Menu)
    }

    @Test fun backupsAreUsedAndReported() {
        val c = controller()
        val s = playing(c).session
        s.save("two", "Two"); s.step(); s.save("two", "Two again")
        c.showMenu()
        store.put("two.etsave", ByteArray(64))
        assertTrue(c.load("two"))
        assertTrue(c.recoveredFromBackup)
    }

    @Test fun settingsPersistThroughTheStore() {
        val c = controller()
        c.updateSettings(GameSettings(autosave = false))
        assertFalse(controller().settings.autosave)
    }

    @Test fun listAndDeleteAndRename() {
        val c = controller()
        val s = playing(c).session
        s.save("a", "Alpha"); s.save("b", "Beta")
        assertEquals(2, c.slots().size)
        c.renameSlot("a", "Renamed")
        assertEquals("Renamed", c.slots().first { it.id == "a" }.label)
        c.deleteSlot("b")
        assertEquals(listOf("a"), c.slots().map { it.id })
    }

    @Test fun leavingTheSetupScreenDiscardsDraftUpdates() {
        val c = controller()
        c.updateDraft(NewGameDraft(companyName = "Ignored"))
        assertTrue(c.screen is AppScreen.Menu)
    }
}
