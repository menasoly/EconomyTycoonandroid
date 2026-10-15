package app.many.economytycoon.replay

import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayVerifierTest {
    private val verifier = ReplayVerifier(ReplayTestSupport.content)
    private val trace = ReplayTestSupport.record()

    @Test fun anUntouchedTraceReplaysIdentically() {
        val v = verifier.verify(trace)
        assertTrue(v.describe(), v.ok)
        assertEquals(Verdict.Identical(trace.checkpoints.size, 40L), v)
    }

    @Test fun aTraceReadBackFromBytesStillReplays() {
        val v = verifier.verify(TraceCodec.read(TraceCodec.write(trace)))
        assertTrue(v.describe(), v.ok)
    }

    @Test fun aWrongHashIsReportedAtItsTick() {
        val bad = trace.copy(checkpoints = trace.checkpoints.mapIndexed { i, c -> if (i == 2) c.copy(hash = "0".repeat(c.hash.length)) else c })
        val v = verifier.verify(bad) as Verdict.Diverged
        assertEquals(trace.checkpoints[2].tick, v.tick)
        assertEquals(trace.checkpoints[1].tick, v.lastGood)
        assertEquals(trace.checkpoints[2].hash, v.actual)
    }

    @Test fun aMissingCommandChangesTheOutcome() {
        val v = verifier.verify(trace.copy(log = emptyList()))
        assertTrue(v.describe(), v is Verdict.Diverged)
    }

    @Test fun differentContentIsRefused() {
        val v = verifier.verify(trace.copy(content = listOf("other:9.9")))
        assertTrue(v.describe(), v is Verdict.Refused)
        assertTrue(!v.ok)
    }

    @Test fun anEmptyTraceIsRefused() {
        assertTrue(verifier.verify(trace.copy(checkpoints = emptyList())) is Verdict.Refused)
    }

    @Test fun aTraceWithoutContentNamesStillReplays() {
        assertTrue(verifier.verify(trace.copy(content = emptyList())).ok)
    }

    @Test fun replayRebuildsTheFinishedGame() {
        val sim = verifier.replay(trace)
        assertEquals(trace.lastTick, sim.tick.value)
        assertEquals(trace.checkpoints.last().hash, sim.hash())
        assertTrue(sim.economy.companies.active().any { it.name == "Replay Works" })
    }

    @Test fun replayCanStopEarly() {
        val sim = verifier.replay(trace, 10L)
        assertEquals(10L, sim.tick.value)
        assertEquals(trace.checkpointAt(10L)!!.hash, sim.hash())
    }

    @Test fun twoIdenticalRunsNeverDiffer() {
        val a = Simulation(SimTestSupport.config(70, 3), ReplayTestSupport.content)
        val b = Simulation(SimTestSupport.config(70, 3), ReplayTestSupport.content)
        assertNull(verifier.firstDifference(a, b, 20))
    }

    @Test fun differentSeedsDifferAtOnce() {
        val a = Simulation(SimTestSupport.config(70, 3), ReplayTestSupport.content)
        val b = Simulation(SimTestSupport.config(71, 3), ReplayTestSupport.content)
        assertEquals(0L, verifier.firstDifference(a, b, 5))
    }

    @Test fun anInputOnlyOneRunHasShowsUpAtTheTickItIsApplied() {
        val a = Simulation(SimTestSupport.config(72, 3), ReplayTestSupport.content)
        val b = Simulation(SimTestSupport.config(72, 3), ReplayTestSupport.content)
        a.advance(6); b.advance(6)
        val country = b.economy.world.countries().first()
        b.execute(app.many.economytycoon.simulation.Actor.Player(1L), app.many.economytycoon.simulation.Command.FoundCompany("Extra Works", country.id, null, app.many.economytycoon.core.Money.units(300_000)))
        assertEquals(6L, verifier.firstDifference(a, b, 5))
    }

    @Test fun verdictsDescribeThemselves() {
        assertTrue(Verdict.Identical(3, 40).describe().contains("3 checkpoints"))
        assertTrue(Verdict.Diverged(20, "a", "b", 10).describe().contains("tick 20"))
        assertTrue(Verdict.Diverged(20, "a", "b", null).describe().contains("none"))
        assertTrue(Verdict.Refused("x").describe().contains("x"))
    }
}
