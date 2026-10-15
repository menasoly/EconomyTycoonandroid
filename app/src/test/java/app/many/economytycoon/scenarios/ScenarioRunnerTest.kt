package app.many.economytycoon.scenarios

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.NewGame
import app.many.economytycoon.session.NewGameOptions
import app.many.economytycoon.simulation.ScenarioRunner
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioRunnerTest {
    private fun start(id: String): GameSession {
        val def = ScenarioCatalog.require(id)
        return (NewGame.start(NewGame.forScenario(def, "Test Holdings"), SimTestSupport.content) as Outcome.Ok).value
    }

    private fun GameSession.playerCompany(): CompanyId = playerCompanies().first()

    private fun GameSession.windfall(units: Long) {
        val owner = economy.companies.require(playerCompany()).owner
        economy.finance.mint(economy.tick, owner, Money.units(units), "windfall")
    }

    private fun GameSession.drainTo(units: Long) {
        val c = economy.companies.require(playerCompany())
        val who = c.owner
        val extra = economy.finance.ledger.cash(who) - Money.units(units)
        val gov = LedgerOwner.government(c.country)
        economy.finance.ledger.post(
            TransactionDraft(
                "test:drain", Tick(0), ReasonCode.TRANSFER,
                listOf(
                    Entry(AccountRef(who, Chart.CASH), -extra),
                    Entry(AccountRef(who, Chart.OTHER_EXPENSE), extra),
                    Entry(AccountRef(gov, Chart.CASH), extra),
                    Entry(AccountRef(gov, Chart.REVENUE_OTHER), -extra),
                ),
                "drain",
            ),
        )
    }

    private fun GameSession.stepTo(tick: Long) { while (this.tick.value < tick) step() }

    @Test fun aNewScenarioGameIsActiveAndStartsAtTheFoundingTick() {
        val s = start("million-club")
        val run = s.economy.scenario.run!!
        assertEquals("million-club", run.id)
        assertTrue(run.isActive)
        assertEquals(s.tick, run.startedAt)
        assertEquals(1, s.economy.scenarioRunner.progress().size)
    }

    @Test fun aGameWithoutAScenarioHasNothingToCheck() {
        val s = app.many.economytycoon.ui.present.PresentSupport.session("Plain Works")
        assertNull(s.economy.scenario.run)
        assertTrue(s.economy.scenarioRunner.progress().isEmpty())
        assertTrue(s.economy.scenarioRunner.run(Tick(5)).isEmpty())
    }

    @Test fun reachingEveryTargetWinsWithAMedalForTheDaysTaken() {
        val s = start("million-club")
        s.windfall(1_000_000)
        val events = ArrayList<ScenarioEvent>()
        while (s.tick.value < 20 && events.none { it is ScenarioEvent.Won }) events += s.step().scenarioEvents
        val run = s.economy.scenario.run!!
        assertEquals(ScenarioStatus.WON, run.status)
        assertEquals(Medal.GOLD, run.medal)
        assertTrue(events.any { it is ScenarioEvent.GoalReached })
        val won = events.filterIsInstance<ScenarioEvent.Won>().single()
        assertEquals(run.finishedAt, won.tick)
        assertEquals(won.tick.value - run.startedAt.value, won.days)
        assertTrue(run.reached(0))
    }

    @Test fun aFinishedRunIsNotCheckedAgain() {
        val s = start("million-club")
        s.windfall(1_000_000)
        s.stepTo(10)
        val run = s.economy.scenario.run!!
        assertFalse(run.isActive)
        assertTrue(s.economy.scenarioRunner.run(Tick(15)).isEmpty())
        assertEquals(ScenarioStatus.WON, run.status)
    }

    @Test fun targetsAreOnlyCheckedEveryFifthDay() {
        val s = start("million-club")
        s.windfall(1_000_000)
        val r = ScenarioRunner(s.economy)
        assertTrue(r.run(Tick(3)).isEmpty())
        assertTrue(s.economy.scenario.run!!.isActive)
        assertTrue(r.run(Tick(5)).any { it is ScenarioEvent.Won })
    }

    @Test fun aGoalIsAnnouncedOnceEvenWhileTheGameContinues() {
        val s = start("first-harvest")
        val e = s.economy
        e.scenario.markReached(e.scenario.run!!, 1)
        val events = e.scenarioRunner.run(Tick(5)) + e.scenarioRunner.run(Tick(10))
        assertTrue(events.none { it is ScenarioEvent.GoalReached && it.index == 1 })
    }

    @Test fun runningOutOfTimeLoses() {
        val s = start("million-club")
        val def = ScenarioCatalog.require("million-club")
        val events = s.economy.scenarioRunner.run(Tick(def.deadlineDays.toLong()))
        val lost = events.filterIsInstance<ScenarioEvent.Lost>().single()
        assertEquals(LossReason.DEADLINE, lost.reason)
        val run = s.economy.scenario.run!!
        assertEquals(ScenarioStatus.LOST, run.status)
        assertEquals(LossReason.DEADLINE, run.reason)
    }

    @Test fun oneDayBeforeTheDeadlineIsStillPlayable() {
        val s = start("million-club")
        val def = ScenarioCatalog.require("million-club")
        val before = (def.deadlineDays - 5).toLong()
        assertTrue(s.economy.scenarioRunner.run(Tick(before)).isEmpty())
        assertTrue(s.economy.scenario.run!!.isActive)
    }

    @Test fun losingEveryCompanyEndsTheGame() {
        val s = start("million-club")
        s.economy.scenario.begin("million-club", 99L, s.tick)
        val events = s.economy.scenarioRunner.run(Tick(5))
        assertEquals(LossReason.NO_COMPANY, (events.single() as ScenarioEvent.Lost).reason)
    }

    @Test fun deepLossesRuinAScenarioThatSetsALimit() {
        val s = start("lean-times")
        s.drainTo(-250_000)
        val events = s.economy.scenarioRunner.run(Tick(5))
        assertEquals(LossReason.RUINED, events.filterIsInstance<ScenarioEvent.Lost>().single().reason)
    }

    @Test fun scenariosWithoutALimitAreNotRuinedByDebt() {
        val s = start("million-club")
        s.drainTo(-250_000)
        assertTrue(s.economy.scenarioRunner.run(Tick(5)).isEmpty())
        assertTrue(s.economy.scenario.run!!.isActive)
    }

    @Test fun silverIsEarnedByAWinInTheMiddleBand() {
        val def = ScenarioCatalog.require("million-club")
        assertEquals(Medal.SILVER, def.medalFor(def.goldDays + 1L))
        val s = start("million-club")
        s.windfall(1_000_000)
        val e = s.economy
        val late = Tick(def.goldDays + 10L)
        val events = e.scenarioRunner.run(Tick((late.value / 5) * 5 + 5))
        assertEquals(Medal.SILVER, events.filterIsInstance<ScenarioEvent.Won>().single().medal)
    }
}
