package app.many.economytycoon.workshop

import app.many.economytycoon.persistence.MemorySaveStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopRunnerTest {
    private val quick = HarnessSettings(days = 10, sampleEvery = 10, checkEvery = 5, stallDays = 5)

    /** Holds jobs until the test lets them go, so "busy" can be seen. */
    private class Manual : Executor {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.addLast(command) }
        fun runAll() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }

    @Test fun aStudyRunsThroughTheExecutorAndFilesItsResult() {
        val manual = Manual()
        val s = WorkshopSession(Samples.content, MemorySaveStore(), manual)
        s.startFrom("sprint", "Runner Test")
        assertTrue(s.runner.startStudy(StudySpec(settings = quick)))
        assertNotNull(s.runner.busy)
        assertNull(s.lastStudy)
        manual.runAll()
        assertNull(s.runner.busy)
        assertEquals(1, s.runner.completed)
        assertNotNull(s.lastStudy)
        assertNull(s.runner.message)
    }

    @Test fun onlyOneJobRunsAtATime() {
        val manual = Manual()
        val s = WorkshopSession(Samples.content, MemorySaveStore(), manual)
        assertTrue(s.runner.startStudy(StudySpec(settings = quick)))
        assertFalse(s.runner.startStudy(StudySpec(settings = quick)))
        assertFalse(s.runner.startSweep(SweepSpec(SweepParam.CAPITAL, listOf(200_000L, 300_000L), settings = quick)))
        manual.runAll()
        assertTrue(s.runner.startSweep(SweepSpec(SweepParam.CAPITAL, listOf(200_000L, 300_000L), settings = quick)))
        manual.runAll()
        assertNotNull(s.lastSweep)
        assertEquals(2, s.runner.completed)
    }

    @Test fun aTestFinishingAfterTheDraftWasReplacedIsNotFiledUnderTheNewOne() {
        val manual = Manual()
        val s = WorkshopSession(Samples.content, MemorySaveStore(), manual)
        s.startFrom("sprint", "First One")
        s.runner.startStudy(StudySpec(settings = quick))
        s.startFrom("sprint", "Second One")
        manual.runAll()
        assertNull(s.lastStudy)
    }

    @Test fun aFailingJobReportsAndFreesTheRunner() {
        val s = WorkshopSession(Samples.content, MemorySaveStore()) { throw IllegalStateException("no threads") }
        assertFalse(s.runner.startStudy(StudySpec(settings = quick)))
        assertNull(s.runner.busy)
        assertTrue(s.runner.message!!.contains("no threads"))
    }

    @Test fun anInvalidDraftGivesAMessageNotACrash() {
        val manual = Manual()
        val s = WorkshopSession(Samples.content, MemorySaveStore(), manual)
        s.editor.apply(DraftEdit.SetTitle("x"))
        s.runner.startStudy(StudySpec(settings = quick))
        manual.runAll()
        assertNull(s.runner.busy)
        assertNotNull(s.runner.message)
    }

    @Test fun theDefaultExecutorRunsOffTheCallingThread() {
        val s = WorkshopSession(Samples.content, MemorySaveStore())
        s.startFrom("sprint", "Threaded")
        assertTrue(s.runner.startStudy(StudySpec(settings = quick)))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120)
        while (s.runner.busy != null && System.nanoTime() < deadline) Thread.sleep(50)
        assertNull(s.runner.busy)
        assertNotNull(s.lastStudy)
    }
}
