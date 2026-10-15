package app.many.economytycoon.tutorial

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.persistence.MemorySaveStore
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialTrackerTest {
    private val facts = TutorialFacts()

    @Test fun visitsAreRemembered() {
        val t = TutorialTracker()
        t.noteVisit("market")
        assertTrue("market" in t.visitedScreens)
        t.update(facts)
        assertTrue(t.progress.isDone("market"))
    }

    @Test fun acknowledgeSkipDismissAndRestartWork() {
        val t = TutorialTracker()
        t.acknowledge("welcome")
        assertTrue(t.progress.isDone("welcome"))
        t.skipCurrent()
        assertTrue("market" in t.progress.skipped)
        t.dismiss()
        assertTrue(t.progress.dismissed)
        t.restart()
        assertEquals(TutorialProgress(), t.progress)
    }

    @Test fun progressSurvivesInAStore() {
        val store = MemorySaveStore()
        TutorialTracker(store = store).apply { acknowledge("welcome"); update(facts.copy(facilities = 1)) }
        val again = TutorialTracker(store = store)
        assertTrue(again.progress.isDone("welcome"))
        assertTrue(again.progress.isDone("build"))
    }

    @Test fun separateKeysKeepSeparateProgress() {
        val store = MemorySaveStore()
        TutorialTracker(store = store, key = "tutorial.a").acknowledge("welcome")
        assertFalse(TutorialTracker(store = store, key = "tutorial.b").progress.isDone("welcome"))
    }

    @Test fun viewIncludesVisitedScreens() {
        val t = TutorialTracker()
        t.noteVisit("trade")
        val step = t.view(facts).steps.first { it.step.id == "trade" }
        assertEquals(1_000_000L, step.progressPpm)
    }

    @Test fun factsReaderSeesTheLiveCompany() {
        val sim = SimTestSupport.sim(5L, 3).also { it.advance(10) }
        val e = sim.economy
        val ai = e.companies.active().first()
        val f = TutorialFactsReader.read(e, ai.id, setOf("market"))
        assertEquals(e.tick.value, f.day)
        assertEquals(e.production.facilitiesOf(ai.id).size, f.facilities)
        assertEquals(e.workforce.employedBy(ai.id), f.employees)
        assertTrue("market" in f.visited)
    }

    @Test fun factsReaderToleratesUnknownCompanies() {
        val sim = SimTestSupport.sim(5L, 1)
        val f = TutorialFactsReader.read(sim.economy, CompanyId(9_999), emptySet())
        assertEquals(0, f.facilities)
        assertEquals(0L, f.employees)
    }
}
