package app.many.economytycoon.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTest {
    @Test fun aShortRunKeepsEveryInvariant() {
        val sim = SimTestSupport.sim()
        sim.pipeline.checkInvariants = true
        val problems = ArrayList<String>()
        for (i in 1..40) {
            val s = sim.advance()
            problems += s.problems.map { "t${s.tick.value}: $it" }
        }
        assertEquals(emptyList<String>(), problems.take(10))
        assertEquals(40L, sim.tick.value)
    }

    @Test fun theEconomyActuallyTrades() {
        val sim = SimTestSupport.sim()
        val s = sim.advance(100)
        assertTrue("no trades in 100 ticks", s.sumOf { it.tradesExecuted } > 0)
        assertTrue(s.sumOf { it.consumerSpend.raw } > 0)
        assertTrue("nothing produced in 100 ticks", s.sumOf { it.batches } > 0)
    }

    @Test fun phasesRunInOrder() {
        val s = SimTestSupport.sim().advance()
        assertEquals(Phase.values().toList(), s.phasesRun)
    }
}
