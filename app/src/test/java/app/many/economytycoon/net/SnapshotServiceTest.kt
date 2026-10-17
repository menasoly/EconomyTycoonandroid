package app.many.economytycoon.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotServiceTest {
    private fun setup(): Triple<ToySimulation, CommitLog, SnapshotService> {
        val sim = ToySimulation()
        sim.seat(PlayerId(1), "Alpha Works")
        val log = CommitLog()
        return Triple(sim, log, SnapshotService(sim, log, keep = 2))
    }

    private fun step(sim: ToySimulation, log: CommitLog) { log.append(sim.tick, emptyList(), emptyList()); sim.advance() }

    @Test fun theSameTickSharesOneBundle() {
        val (_, _, service) = setup()
        val a = service.current()
        assertNotNull(a)
        assertSame(a, service.current())
        assertEquals(1, service.taken)
    }

    @Test fun eachTickGetsItsOwnBundleWithTheRightChain() {
        val (sim, log, service) = setup()
        step(sim, log); step(sim, log)
        val b = service.current() ?: error("no bundle")
        assertEquals(2L, b.tick)
        assertEquals(log.head, b.chain)
        assertTrue(sim.stateHash().contentEquals(b.stateHash))
        step(sim, log)
        assertNotEquals(b.id, service.current()?.id)
    }

    @Test fun olderBundlesAreForgottenButRecentOnesCanBeResumed() {
        val (sim, log, service) = setup()
        val ids = ArrayList<Long>()
        repeat(3) { ids += service.current()?.id ?: error("no bundle"); step(sim, log) }
        assertNull(service.byId(ids[0]))
        assertNotNull(service.byId(ids[1]))
        assertNotNull(service.byId(ids[2]))
    }

    @Test fun anUnknownIdIsNotFound() {
        assertNull(setup().third.byId(12345))
    }

    @Test fun aSnapshotRestoresIntoAnotherSimulation() {
        val (sim, log, service) = setup()
        repeat(5) { step(sim, log) }
        val b = service.current() ?: error("no bundle")
        val other = ToySimulation()
        assertTrue(other.restore(b.bytes) is app.many.economytycoon.core.Outcome.Ok)
        assertTrue(other.stateHash().contentEquals(b.stateHash))
    }
}
