package app.many.economytycoon.replay

import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TraceRecorderTest {
    private val content = ReplayTestSupport.content

    @Test fun checkpointsAreTakenAtTheStartAndEveryInterval() {
        val t = ReplayTestSupport.record(ticks = 40, every = 10)
        assertEquals(listOf(0L, 10L, 20L, 30L, 40L), t.checkpoints.map { it.tick })
    }

    @Test fun anUnevenEndStillGetsAFinalCheckpoint() {
        val t = ReplayTestSupport.record(ticks = 25, every = 10)
        assertEquals(listOf(0L, 10L, 20L, 25L), t.checkpoints.map { it.tick })
        assertEquals(25L, t.lastTick)
    }

    @Test fun askingForTheTraceTwiceDoesNotAddCheckpoints() {
        val sim = Simulation(SimTestSupport.config(5, 2), content)
        val rec = TraceRecorder(sim, content, 10)
        rec.advance(10)
        assertEquals(rec.trace().checkpoints, rec.trace().checkpoints)
    }

    @Test fun theHashesDifferFromOneCheckpointToTheNext() {
        val t = ReplayTestSupport.record()
        assertEquals(t.checkpoints.size, t.checkpoints.map { it.hash }.toSet().size)
    }

    @Test fun theTraceNamesItsContent() {
        val t = ReplayTestSupport.record()
        assertTrue(t.content.isNotEmpty())
        assertEquals(t.content.sorted(), t.content)
        assertEquals(TraceRecorder.packs(content), t.content)
    }

    @Test fun checkpointLookupFindsByTick() {
        val t = ReplayTestSupport.record()
        assertEquals(t.checkpoints[2], t.checkpointAt(t.checkpoints[2].tick))
        assertEquals(null, t.checkpointAt(7L))
    }

    @Test fun anIntervalOfZeroIsRefused() {
        val sim = Simulation(SimTestSupport.config(5, 2), content)
        try { TraceRecorder(sim, content, 0); fail("zero interval accepted") } catch (e: IllegalArgumentException) { /* expected */ }
    }
}
