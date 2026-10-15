package app.many.economytycoon.tutorial

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialEngineTest {
    private val engine = TutorialEngine()
    private val none = TutorialProgress()
    private val facts = TutorialFacts()

    @Test fun freshProgressStartsAtTheWelcome() = assertEquals("welcome", engine.current(none)?.id)

    @Test fun nothingCompletesWithoutFacts() = assertSame(none, engine.update(none, facts))

    @Test fun acknowledgingCompletesTheWelcome() {
        val p = engine.acknowledge(none, "welcome")
        assertTrue(p.isDone("welcome"))
        assertEquals("market", engine.current(p)?.id)
    }

    @Test fun acknowledgingAnUnknownStepChangesNothing() = assertSame(none, engine.acknowledge(none, "nope"))

    @Test fun visitingCompletesAVisitStep() {
        val p = engine.update(engine.acknowledge(none, "welcome"), facts.copy(visited = setOf("market")))
        assertTrue(p.isDone("market"))
        assertEquals("trade", engine.current(p)?.id)
    }

    @Test fun laterStepsCanCompleteBeforeEarlierOnes() {
        val p = engine.update(none, facts.copy(facilities = 1, employees = 3))
        assertTrue(p.isDone("build") && p.isDone("hire"))
        assertEquals("welcome", engine.current(p)?.id)
    }

    @Test fun completionIsPermanent() {
        val p = engine.update(none, facts.copy(facilities = 1))
        val later = engine.update(p, facts.copy(facilities = 0))
        assertTrue(later.isDone("build"))
    }

    @Test fun skippingMovesOn() {
        val p = engine.skipCurrent(none)
        assertTrue("welcome" in p.skipped)
        assertEquals("market", engine.current(p)?.id)
    }

    @Test fun doneStepsCannotBeSkipped() {
        val p = engine.acknowledge(none, "welcome")
        assertSame(p, engine.skip(p, "welcome"))
    }

    @Test fun skippedStepsStillCountAsDoneInTheView() {
        val v = engine.view(engine.skipCurrent(none), facts)
        assertEquals(1, v.doneCount)
        assertEquals(StepState.Status.SKIPPED, v.steps.first().status)
    }

    @Test fun skippingTheUnknownChangesNothing() = assertSame(none, engine.skip(none, "nope"))

    @Test fun dismissingStopsUpdates() {
        val p = engine.dismiss(none)
        assertTrue(p.dismissed)
        assertSame(p, engine.update(p, facts.copy(facilities = 5)))
    }

    @Test fun restartingClearsEverything() = assertEquals(TutorialProgress(), engine.restart())

    @Test fun viewMarksExactlyOneCurrentStep() {
        val v = engine.view(none, facts)
        assertEquals(1, v.steps.count { it.status == StepState.Status.CURRENT })
        assertEquals(TutorialScript.steps.size - 1, v.steps.count { it.status == StepState.Status.LOCKED })
        assertFalse(v.finished)
    }

    @Test fun viewReportsPartialProgressOnTheCurrentGoal() {
        val p = engine.update(engine.acknowledge(none, "welcome"), facts.copy(visited = setOf("market", "trade", "planner"), facilities = 1, employees = 1, runningFacilities = 1, monthlyRevenue = Money.units(5), day = 45))
        val survive = engine.view(p, facts.copy(day = 45)).steps.first { it.step.id == "survive" }
        assertEquals(500_000L, survive.progressPpm)
    }

    @Test fun everythingDoneFinishesTheGuide() {
        val all = TutorialFacts(day = 100, facilities = 1, runningFacilities = 1, employees = 5, loans = 1, activeContracts = 1, vehicles = 1, techsUnlocked = 1, listed = true,
            monthlyRevenue = Money.units(10), monthlyProfit = Money.units(10), visited = TutorialScript.steps.mapNotNull { (it.goal as? Goal.Visit)?.route }.toSet())
        val p = engine.update(engine.acknowledge(none, "welcome"), all)
        val v = engine.view(p, all)
        assertTrue(v.finished)
        assertNull(v.current)
        assertEquals(1_000_000L, v.progressPpm)
        assertEquals(v.total, v.doneCount)
    }

    @Test fun progressRisesAsStepsComplete() {
        val a = engine.view(none, facts).progressPpm
        val b = engine.view(engine.update(none, facts.copy(facilities = 1, employees = 1)), facts).progressPpm
        assertTrue(b > a)
    }

    @Test fun anEmptyScriptIsAlreadyFinished() {
        val v = TutorialEngine(emptyList()).view(none, facts)
        assertTrue(v.finished)
        assertEquals(1_000_000L, v.progressPpm)
    }

    @Test fun customScriptsWork() {
        val step = TutorialStep("only", Lesson.BASICS, "Only", "The only step in a custom script, used for testing.", Goal.Reach(Gauge.DAY, 3))
        val e = TutorialEngine(listOf(step))
        assertEquals("only", e.current(none)?.id)
        assertNull(e.current(e.update(none, facts.copy(day = 3))))
    }
}
