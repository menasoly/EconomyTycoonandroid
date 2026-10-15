package app.many.economytycoon.session

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectivesTest {
    private fun fresh() = (NewGame.start(NewGameOptions("Goal Getters", Difficulty.RELAXED, seed = 12L), SimTestSupport.content) as Outcome.Ok).value

    @Test fun idsAreUniqueAndEveryObjectiveHasText() {
        assertEquals(Objectives.all.size, Objectives.all.map { it.id }.toSet().size)
        assertTrue(Objectives.all.all { it.title.isNotBlank() && it.description.isNotBlank() && it.group.isNotBlank() })
        assertTrue(Objectives.groups().size >= 5)
    }

    @Test fun aNewCompanyHasCompletedNothingTrivialButStartsWithProgress() {
        val s = fresh()
        val progress = Objectives.evaluate(s.economy, s.selectedCompany!!)
        assertEquals(Objectives.all.size, progress.size)
        assertFalse(progress.first { it.objective.id == "first-facility" }.done)
        assertTrue(progress.all { it.ppm in 0..1_000_000 })
    }

    @Test fun wealthObjectivesFollowNetWorth() {
        val s = fresh()
        val p = Objectives.byId("net-worth-1m")!!.progress(s.economy, s.selectedCompany!!)
        assertEquals(800_000L, p.value)
        assertEquals(800_000L, p.ppm)
        assertFalse(p.done)
    }

    @Test fun anObjectiveIsReportedOnlyOnce() {
        val s = fresh(); val watcher = ObjectiveWatcher(s.economy)
        val c = s.selectedCompany!!
        val first = watcher.newlyCompleted(c)
        assertTrue(watcher.newlyCompleted(c).isEmpty())
        assertEquals(first.map { it.id }.toSet(), watcher.completed(c))
    }

    @Test fun completionAppearsAfterTheGoalIsMet() {
        val s = fresh(); val c = s.selectedCompany!!
        val watcher = ObjectiveWatcher(s.economy)
        watcher.newlyCompleted(c)
        val option = BuildPlanner(s.economy).options(c, s.economy.world.regions().first().id).first { it.canBuild && !it.needsDeposit }
        s.issueNow(BuildPlanner(s.economy).buildCommand(c, option, s.economy.world.regions().first().id)!!)
        repeat(option.buildTicks + 3) { s.step() }
        val fresh = watcher.newlyCompleted(c).map { it.id }
        assertTrue(fresh.toString(), "first-facility" in fresh || "first-facility" in watcher.completed(c))
    }

    @Test fun unknownCompaniesGiveNoNews() {
        val s = fresh()
        assertTrue(ObjectiveWatcher(s.economy).newlyCompleted(app.many.economytycoon.core.CompanyId(9_999)).isEmpty())
    }
}
