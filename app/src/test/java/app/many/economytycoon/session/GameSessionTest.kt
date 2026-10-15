package app.many.economytycoon.session

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.persistence.MemorySaveStore
import app.many.economytycoon.persistence.SaveManager
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {
    private fun start(name: String = "Test Industries", settings: GameSettings = GameSettings(autosave = false, pauseOnCritical = false), saves: SaveManager? = null): GameSession =
        (NewGame.start(NewGameOptions(name, Difficulty.RELAXED, seed = 5L), SimTestSupport.content, settings, saves) as Outcome.Ok).value

    @Test fun startingAGameFoundsTheFirstCompany() {
        val s = start()
        assertEquals(1, s.playerCompanies().size)
        assertEquals(s.playerCompanies().first(), s.selectedCompany)
        assertEquals("Test Industries", s.economy.companies.require(s.selectedCompany!!).name)
        assertEquals(Money.units(800_000), s.economy.finance.ledger.cash(s.economy.companies.require(s.selectedCompany!!).owner))
    }

    @Test fun badOptionsAreRejected() {
        val short = NewGame.start(NewGameOptions("ab"), SimTestSupport.content)
        assertEquals(Rules.INVALID_ARGUMENT, short.rejectionOrNull()?.rule)
        val noCountry = NewGame.start(NewGameOptions("Fine Name", countryIndex = 99), SimTestSupport.content)
        assertEquals(Rules.INVALID_ARGUMENT, noCountry.rejectionOrNull()?.rule)
    }

    @Test fun difficultyChangesRivalsAndCapital() {
        assertTrue(Difficulty.CUTTHROAT.aiCompanies > Difficulty.RELAXED.aiCompanies)
        assertTrue(Difficulty.CUTTHROAT.playerCapital < Difficulty.RELAXED.playerCapital)
        assertEquals(Difficulty.STANDARD.aiCompanies, NewGame.config(NewGameOptions("x")).aiCompanies)
    }

    @Test fun steppingAdvancesOneTickAndNotifies() {
        val s = start(); var ticks = 0
        s.addListener { _, change -> if (change == SessionChange.TICK) ticks++ }
        val before = s.tick.value
        s.step(); s.step()
        assertEquals(before + 2, s.tick.value)
        assertEquals(2, ticks)
        assertNotNull(s.lastTickSummary)
    }

    @Test fun elapsedTimeBecomesTicksAtTheCurrentSpeed() {
        val s = start()
        assertEquals(GameSpeed.NORMAL, s.speed)
        assertEquals(5, s.advanceBy(5_000)) // one tick per second at normal speed
        assertEquals(1, s.advanceBy(1_000))
        assertEquals(0, s.advanceBy(400))
        assertEquals(1, s.advanceBy(600))
        s.setSpeed(GameSpeed.FAST)
        assertEquals(3, s.advanceBy(1_000))
    }

    @Test fun pausedSessionsDoNotAdvance() {
        val s = start(); s.setSpeed(GameSpeed.PAUSED)
        assertEquals(0, s.advanceBy(60_000))
        assertEquals(0L, s.tick.value)
    }

    @Test fun longStallsAreCappedPerCall() {
        val s = start(); s.setSpeed(GameSpeed.FASTEST)
        assertEquals(12, s.advanceBy(600_000))
        assertEquals(12L, s.tick.value)
    }

    @Test fun commandsAreAppliedOnTheNextTickAndReported() {
        val s = start()
        val seq = s.issue(Command.Rename(s.selectedCompany!!, "Renamed Industries"))
        assertEquals(1, s.pendingCommands())
        assertNull(s.outcomeOf(seq))
        s.step()
        val outcome = s.outcomeOf(seq)!!
        assertTrue(outcome.accepted)
        assertEquals("Renamed Industries", s.economy.companies.require(s.selectedCompany!!).name)
    }

    @Test fun refusedCommandsCarryTheirRule() {
        val s = start()
        val seq = s.issue(Command.Rename(s.selectedCompany!!, "x"))
        s.step()
        val o = s.outcomeOf(seq)!!
        assertFalse(o.accepted)
        assertEquals(Rules.INVALID_ARGUMENT, o.rule)
        assertEquals(seq, s.recentOutcomes().first().sequence)
    }

    @Test fun otherPlayersCompaniesCannotBeSelected() {
        val s = start()
        val rival = s.economy.companies.active().first { it.id != s.selectedCompany }
        assertFalse(s.select(rival.id))
        assertEquals(s.playerCompanies().first(), s.selectedCompany)
    }

    @Test fun savingThroughTheSessionWritesASlot() {
        val manager = SaveManager(MemorySaveStore(), { 1L })
        val s = start(saves = manager)
        s.step()
        assertNotNull(s.save("slot1", "From session"))
        assertEquals(1L, manager.list().single().tick)
        assertNull(start().save("slot1", "no manager"))
    }

    @Test fun autosavesFollowTheSettingsInterval() {
        val manager = SaveManager(MemorySaveStore(), { 1L }, autosaveEveryTicks = 5)
        val s = start(settings = GameSettings(autosave = true, autosaveEveryTicks = 5, pauseOnCritical = false), saves = manager)
        repeat(12) { s.step() }
        assertTrue(manager.list().isNotEmpty())
        assertTrue(manager.list().all { it.kind == app.many.economytycoon.persistence.SlotKind.AUTO })
    }

    @Test fun resumeKeepsTheStateOfALoadedGame() {
        val manager = SaveManager(MemorySaveStore(), { 1L })
        val s = start(saves = manager)
        repeat(8) { s.step() }
        s.save("x", "keep")
        val loaded = manager.load("x", SimTestSupport.content)
        val resumed = GameSession.resume(loaded.simulation, 1L, GameSettings(), manager)
        assertEquals(s.simulation.hash(), resumed.simulation.hash())
        assertEquals(s.playerCompanies(), resumed.playerCompanies())
    }

    @Test fun criticalAlertsPauseTheGameWhenAsked() {
        val s = start(settings = GameSettings(autosave = false, pauseOnCritical = true))
        s.setSpeed(GameSpeed.NORMAL)
        val c = s.economy.companies.require(s.selectedCompany!!)
        val amount = Money.units(900_000)
        s.economy.finance.ledger.post(
            app.many.economytycoon.finance.TransactionDraft(
                "burn-all", s.tick, app.many.economytycoon.finance.ReasonCode.TRANSFER,
                listOf(
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(c.owner, app.many.economytycoon.finance.Chart.COGS), amount),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(c.owner, app.many.economytycoon.finance.Chart.CASH), -amount),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(s.economy.finance.households, app.many.economytycoon.finance.Chart.CASH), amount),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(s.economy.finance.households, app.many.economytycoon.finance.Chart.REVENUE_OTHER), -amount),
                ),
            ),
        ).getOrThrow()
        s.step()
        assertEquals(GameSpeed.PAUSED, s.speed)
    }
}

class GameSessionTrackingTest {
    private fun start() = (NewGame.start(NewGameOptions("Tracked Works", Difficulty.RELAXED, seed = 5L), SimTestSupport.content, GameSettings(autosave = false, pauseOnCritical = false)) as Outcome.Ok).value

    @Test fun stepsFeedTheStatsSeries() {
        val s = start()
        repeat(12) { s.step() }
        assertTrue(s.stats.of(s.selectedCompany!!).size >= 2)
    }

    @Test fun newsRecordsMonthlyCloses() {
        val s = start()
        repeat(31) { s.step() }
        assertTrue(s.news.items().any { it.kind == NewsKind.FINANCE })
    }

    @Test fun completedGoalsBecomeHeadlines() {
        val s = start()
        s.issueNow(Command.Rename(s.selectedCompany!!, "Tracked Works Ltd"))
        repeat(2) { s.step() }
        assertTrue(s.news.items().none { it.headline.isBlank() })
    }

    @Test fun changedSettingsApplyAtOnceAndAreAnnounced() {
        val s = start(); var seen = 0
        s.addListener { _, change -> if (change == SessionChange.SETTINGS) seen++ }
        s.applySettings(s.settings.copy(currency = CurrencyStyle.EURO, compactNumbers = false))
        assertEquals("€", s.settings.symbol)
        assertFalse(s.settings.compactNumbers)
        assertEquals(1, seen)
    }
}
