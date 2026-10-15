package app.many.economytycoon.persistence

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.scenarios.Medal
import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.scenarios.ScenarioStatus
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.NewGame
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioSnapshotTest {
    private fun start(id: String): GameSession =
        (NewGame.start(NewGame.forScenario(ScenarioCatalog.require(id), "Saved Holdings"), SimTestSupport.content) as Outcome.Ok).value

    private fun copy(sim: Simulation) = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)

    @Test fun anActiveScenarioSurvivesASave() {
        val s = start("million-club")
        repeat(12) { s.step() }
        val loaded = copy(s.simulation)
        assertEquals(s.simulation.hash(), loaded.hash())
        val a = s.economy.scenario.run!!; val b = loaded.economy.scenario.run!!
        assertEquals(a.id, b.id); assertEquals(a.playerId, b.playerId); assertEquals(a.startedAt, b.startedAt)
        assertEquals(ScenarioStatus.ACTIVE, b.status)
    }

    @Test fun aGameWithoutAScenarioStillLoadsWithoutOne() {
        val plain = app.many.economytycoon.ui.present.PresentSupport.session("Plain Saves")
        val loaded = copy(plain.simulation)
        assertNull(loaded.economy.scenario.run)
        assertEquals(plain.simulation.hash(), loaded.hash())
    }

    @Test fun aWonRunKeepsItsMedalAndReachedGoals() {
        val s = start("million-club")
        val owner = s.economy.companies.require(s.playerCompanies().first()).owner
        s.economy.finance.mint(s.economy.tick, owner, Money.units(1_000_000), "windfall")
        while (s.tick.value < 12) s.step()
        val run = s.economy.scenario.run!!
        assertEquals(ScenarioStatus.WON, run.status)
        val b = copy(s.simulation).economy.scenario.run!!
        assertEquals(ScenarioStatus.WON, b.status)
        assertEquals(run.medal, b.medal); assertEquals(Medal.GOLD, b.medal)
        assertEquals(run.finishedAt, b.finishedAt); assertEquals(run.reachedMask, b.reachedMask)
    }

    @Test fun aLoadedGameKeepsPlayingInLockstep() {
        val s = start("first-harvest")
        repeat(20) { s.step() }
        val loaded = copy(s.simulation)
        repeat(30) { s.simulation.advance(); loaded.advance() }
        assertEquals(s.simulation.hash(), loaded.hash())
        assertTrue(loaded.economy.scenario.run!!.isActive)
    }
}
