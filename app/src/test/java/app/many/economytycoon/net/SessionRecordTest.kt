package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SessionRecordTest {
    /** Plays a short two-player game on the toy simulation and records it the way the server does. */
    private fun record(income: Boolean = true, ticks: Int = 40): SessionRecord {
        val sim = ToySimulation(income = income)
        val seats = listOf(SeatRecord(PlayerId(1), "Alpha Works"), SeatRecord(PlayerId(2), "Beta Works"))
        for (s in seats) sim.seat(s.player, s.name)
        val log = CommitLog()
        val checkpoints = java.util.TreeMap<Long, ByteArray>()
        checkpoints[0L] = sim.stateHash()
        for (t in 0 until ticks) {
            val entries = if (t % 4 == 0) listOf(CommitEntry(PlayerId(1), t / 4L + 1, CommandPayload.encode(ToySimulation.budget(1, t * 10L)))) else emptyList()
            log.append(sim.tick, entries, emptyList())
            for (e in entries) (CommandPayload.parse(e.payload) as ParsedCommand.Ok).let { sim.enqueue(e.player, it.command) }
            sim.advance()
            if (sim.tick % 10 == 0L) checkpoints[sim.tick] = sim.stateHash()
        }
        return SessionRecord(NetFixtures.session, sim.fingerprint, seats, log, checkpoints)
    }

    @Test fun aRecordSurvivesTheCodec() {
        val r = record()
        val back = SessionRecordCodec.read(SessionRecordCodec.write(r))
        assertEquals(r.session, back.session)
        assertEquals(r.fingerprint, back.fingerprint)
        assertEquals(r.seats, back.seats)
        assertEquals(r.log.all(), back.log.all())
        assertEquals(r.checkpoints.keys, back.checkpoints.keys)
        assertEquals(r.ticks, back.ticks)
    }

    @Test fun theEncodingIsStable() {
        assertEquals(Hex.encode(SessionRecordCodec.write(record())), Hex.encode(SessionRecordCodec.write(record())))
    }

    @Test fun anyDamageToTheBytesIsDetected() {
        val bytes = SessionRecordCodec.write(record())
        var caught = 0
        for (i in bytes.indices step 7) {
            val copy = bytes.copyOf()
            copy[i] = (copy[i].toInt() xor 0x55).toByte()
            try { SessionRecordCodec.read(copy) } catch (e: ByteReader.CorruptDataException) { caught++ }
        }
        assertEquals((bytes.size + 6) / 7, caught)
    }

    @Test fun truncatedAndForeignDataIsRefusedWithoutOtherExceptions() {
        val bytes = SessionRecordCodec.write(record())
        for (n in listOf(0, 3, 12, bytes.size / 2, bytes.size - 1)) {
            try { SessionRecordCodec.read(bytes.copyOf(n)); fail("accepted $n bytes") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message?.isNotEmpty() == true) }
        }
    }

    @Test fun replayReproducesEveryCheckpoint() {
        val verdict = SessionReplay { ToySimulation() }.verify(record())
        assertTrue(verdict.toString(), verdict.ok)
        assertEquals(5, (verdict as ReplayVerdict.Identical).checkpoints)
        assertEquals(40L, verdict.ticks)
    }

    @Test fun replayFindsTheFirstTickWhereTheGameDiffers() {
        val verdict = SessionReplay { ToySimulation(income = false) }.verify(record())
        assertTrue(verdict is ReplayVerdict.Diverged)
        assertEquals(10L, (verdict as ReplayVerdict.Diverged).tick)
    }

    @Test fun replayRefusesDifferentContent() {
        val verdict = SessionReplay { ToySimulation(fingerprint = "toy:2") }.verify(record())
        assertTrue(verdict is ReplayVerdict.Refused)
    }

    @Test fun replayRefusesARecordWithoutCheckpoints() {
        val r = record()
        val bare = SessionRecord(r.session, r.fingerprint, r.seats, r.log, emptyMap())
        assertTrue(SessionReplay { ToySimulation() }.verify(bare) is ReplayVerdict.Refused)
    }

    @Test fun aReplayRunCanBeSteppedAndStopsAtTheEnd() {
        val run = (SessionReplay { ToySimulation() }.start(record()) as app.many.economytycoon.core.Outcome.Ok).value
        run.runTo(15)
        assertEquals(15L, run.applied)
        assertEquals(15L, run.port.tick)
        run.runTo(1_000)
        assertTrue(run.finished)
        assertEquals(false, run.step())
    }
}
