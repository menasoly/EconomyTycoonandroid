package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.scenarios.Medal
import app.many.economytycoon.scenarios.PlayerProfile
import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.NewGame
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenariosPresenterTest {
    private val ids = ScenarioCatalog.all.map { it.id }
    private fun start(id: String): GameSession =
        (NewGame.start(NewGame.forScenario(ScenarioCatalog.require(id), "Presented Co"), SimTestSupport.content) as Outcome.Ok).value

    @Test fun onlyTheFirstScenarioIsOpenOnAFreshProfile() {
        val m = ScenariosPresenter.list(PlayerProfile(), null)
        assertEquals(ids.size, m.items.size)
        assertFalse(m.items[0].locked); assertTrue(m.items.drop(1).all { it.locked })
        assertEquals("Win the one before it to unlock.", m.items[1].tagline)
        assertEquals("0 of ${ids.size * 3} stars", m.stars)
        assertNull(m.detail)
    }

    @Test fun aLockedSelectionShowsNoDetail() {
        assertNull(ScenariosPresenter.list(PlayerProfile(), ids[3]).detail)
        assertNotNull(ScenariosPresenter.list(PlayerProfile(), ids[0]).detail)
    }

    @Test fun recordsAndMedalsReadClearly() {
        val p = PlayerProfile()
        p.won(ids[0], Medal.GOLD, 400); p.lost(ids[0])
        val item = ScenariosPresenter.item(ScenarioCatalog.require(ids[0]), p)
        assertEquals(Medal.GOLD, item.medal); assertEquals(Tone.GOOD, item.tone)
        assertEquals("best 400 days · 1 win, 1 loss", item.record)
        p.lost(ids[1])
        assertEquals("not yet won · 0 wins, 1 loss", ScenariosPresenter.item(ScenarioCatalog.require(ids[1]), p).record)
        assertNull(ScenariosPresenter.item(ScenarioCatalog.require(ids[2]), p).record)
    }

    @Test fun medalTonesRiseWithTheMedal() {
        assertEquals(Tone.GOOD, ScenariosPresenter.medalTone(Medal.GOLD))
        assertEquals(Tone.INFO, ScenariosPresenter.medalTone(Medal.SILVER))
        assertEquals(Tone.WARN, ScenariosPresenter.medalTone(Medal.BRONZE))
        assertEquals(Tone.NEUTRAL, ScenariosPresenter.medalTone(Medal.NONE))
    }

    @Test fun detailListsFactsGoalsAndMedalDeadlines() {
        for (def in ScenarioCatalog.all) {
            val d = ScenariosPresenter.detail(def)
            assertEquals(4, d.facts.size)
            assertEquals(def.goals.size, d.goals.size)
            assertTrue(d.medals.contains("Gold within"))
            assertEquals(def.tips, d.tips)
        }
    }

    @Test fun starsScaleWithMedals() {
        val p = PlayerProfile(); p.won(ids[0], Medal.GOLD, 1); p.won(ids[1], Medal.SILVER, 1)
        assertEquals("5 of ${ids.size * 3} stars", ScenariosPresenter.list(p, null).stars)
    }

    @Test fun aGameWithoutAScenarioHasNoStatus() {
        assertNull(ScenariosPresenter.status(PresentSupport.session("Plain Co")))
    }

    @Test fun aRunningScenarioShowsGoalsAndTimeLeft() {
        val s = start("first-harvest")
        val m = ScenariosPresenter.status(s)!!
        assertEquals("First Harvest", m.title)
        assertEquals("In progress", m.statusLabel); assertEquals(Tone.INFO, m.tone)
        assertEquals(3, m.goals.size)
        assertTrue(m.goals.none { it.done })
        assertEquals("720 days left", m.timeLeft)
        assertEquals(0.0, m.timeRatio, 0.0001)
    }

    @Test fun timeRunningShortWarns() {
        val s = start("first-harvest")
        val def = ScenarioCatalog.require("first-harvest")
        while (s.tick.value < 1) s.step()
        // Begin the run earlier so thirty days remain, without simulating two years.
        s.economy.scenario.begin(def.id, 1L, Tick(s.tick.value - (def.deadlineDays - 30L)))
        val m = ScenariosPresenter.status(s)!!
        assertEquals("Running out of time", m.statusLabel); assertEquals(Tone.WARN, m.tone)
        assertEquals("30 days left", m.timeLeft)
    }

    @Test fun aWonRunShowsItsMedalAndAnEndNote() {
        val s = start("million-club")
        val owner = s.economy.companies.require(s.playerCompanies().first()).owner
        s.economy.finance.mint(s.economy.tick, owner, Money.units(1_000_000), "windfall")
        while (s.tick.value < 10) s.step()
        val m = ScenariosPresenter.status(s)!!
        assertEquals("Won with a gold medal", m.statusLabel); assertEquals(Tone.GOOD, m.tone)
        assertTrue(m.timeLeft.startsWith("Finished after"))
        assertEquals("You can keep playing as a sandbox.", m.medalNote)
        assertTrue(m.goals.single().done)
    }

    @Test fun aLostRunNamesTheReason() {
        val s = start("million-club")
        val def = ScenarioCatalog.require("million-club")
        s.economy.scenarioRunner.run(Tick(def.deadlineDays.toLong()))
        val m = ScenariosPresenter.status(s)!!
        assertEquals("Time ran out", m.statusLabel); assertEquals(Tone.BAD, m.tone)
    }

    @Test fun goalLinesFormatMoneyAndCountsAndYesNo() {
        val s = start("market-leader")
        val lines = s.economy.scenarioRunner.progress().map { ScenariosPresenter.goalLine(s, it) }
        assertEquals("Not yet", lines[0].progress)
        assertTrue(lines[1].progress.contains(" of "))
        val harvest = start("first-harvest")
        val h = harvest.economy.scenarioRunner.progress().map { ScenariosPresenter.goalLine(harvest, it) }
        assertTrue(h[1].progress.endsWith(" of 40"))
        assertTrue(h.all { it.ratio in 0.0..1.0 })
    }
}
