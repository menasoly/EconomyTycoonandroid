package app.many.economytycoon.session

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsRecorderTest {
    private val sim = SimTestSupport.sim(3L, 3)
    private val ids = sim.economy.companies.active().map { it.id }

    @Test fun samplesAreSpacedByTheInterval() {
        val r = StatsRecorder(CompanyReports(sim.economy), everyTicks = 5)
        repeat(21) { sim.advance(); r.sample(sim.tick.value, ids) }
        assertEquals(listOf(1L, 6L, 11L, 16L, 21L), r.of(ids.first()).map { it.tick })
    }

    @Test fun oldSamplesFallOffTheEnd() {
        val r = StatsRecorder(CompanyReports(sim.economy), everyTicks = 1, capacity = 8)
        repeat(30) { sim.advance(); r.sample(sim.tick.value, ids) }
        val points = r.of(ids.first())
        assertEquals(8, points.size)
        assertEquals(sim.tick.value, points.last().tick)
    }

    @Test fun netWorthChangeNeedsTwoSamples() {
        val r = StatsRecorder(CompanyReports(sim.economy), everyTicks = 1)
        assertNull(r.netWorthChange(ids.first()))
        sim.advance(); r.sample(sim.tick.value, ids)
        assertNull(r.netWorthChange(ids.first()))
        repeat(10) { sim.advance(); r.sample(sim.tick.value, ids) }
        assertNotNull(r.netWorthChange(ids.first()))
        assertEquals(r.latest(ids.first()), r.of(ids.first()).last())
    }

    @Test fun unknownCompaniesAreSkippedAndClearEmpties() {
        val r = StatsRecorder(CompanyReports(sim.economy), everyTicks = 1)
        sim.advance(); r.sample(sim.tick.value, listOf(CompanyId(9_999)) + ids)
        assertTrue(r.of(CompanyId(9_999)).isEmpty())
        r.clear()
        assertTrue(r.of(ids.first()).isEmpty())
    }
}
