package app.many.economytycoon.session

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.scenarios.LossReason
import app.many.economytycoon.scenarios.Medal
import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.scenarios.ScenarioEvent
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioNewsTest {
    private fun start(id: String): GameSession =
        (NewGame.start(NewGame.forScenario(ScenarioCatalog.require(id), "Newsworthy Co"), SimTestSupport.content) as Outcome.Ok).value

    @Test fun headlinesNameTheScenarioAndWhatHappened() {
        val s = start("million-club")
        val e = s.economy
        assertEquals("The Million Club: Reach a net worth of 1 million - done", ScenarioNews.headline(e, ScenarioEvent.GoalReached("million-club", 0, Tick(5))))
        assertEquals("The Million Club won in 40 days (Gold medal)", ScenarioNews.headline(e, ScenarioEvent.Won("million-club", 40, Medal.GOLD, Tick(40))))
        assertEquals("The Million Club won in 1 day (Bronze medal)", ScenarioNews.headline(e, ScenarioEvent.Won("million-club", 1, Medal.BRONZE, Tick(1))))
        assertEquals("The Million Club lost: time ran out", ScenarioNews.headline(e, ScenarioEvent.Lost("million-club", LossReason.DEADLINE, Tick(2_160))))
    }

    @Test fun anUnknownScenarioStillReadsSensibly() {
        val s = start("million-club")
        assertTrue(ScenarioNews.headline(s.economy, ScenarioEvent.Lost("gone", LossReason.RUINED, Tick(1))).startsWith("Scenario lost"))
    }

    @Test fun stepsPostScenarioNewsAndTellTheSink() {
        val s = start("million-club")
        val seen = ArrayList<ScenarioEvent>()
        s.scenarioSink = { seen += it }
        val owner = s.economy.companies.require(s.playerCompanies().first()).owner
        s.economy.finance.mint(s.economy.tick, owner, Money.units(1_000_000), "windfall")
        while (s.tick.value < 10) s.step()
        assertTrue(seen.any { it is ScenarioEvent.GoalReached })
        assertTrue(seen.any { it is ScenarioEvent.Won })
        val headlines = s.news.items().map { it.headline }
        assertTrue(headlines.any { it.startsWith("The Million Club won in") })
        assertTrue(headlines.any { it.endsWith("- done") })
    }

    @Test fun aPlainGameSendsNothingToTheSink() {
        val s = app.many.economytycoon.ui.present.PresentSupport.session("Quiet Co")
        var calls = 0
        s.scenarioSink = { calls++ }
        repeat(20) { s.step() }
        assertEquals(0, calls)
    }
}
