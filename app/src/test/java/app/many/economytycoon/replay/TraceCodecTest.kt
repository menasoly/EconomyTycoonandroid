package app.many.economytycoon.replay

import app.many.economytycoon.core.ByteReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TraceCodecTest {
    private val trace = ReplayTestSupport.record()

    @Test fun aTraceSurvivesTheRoundTrip() {
        val back = TraceCodec.read(TraceCodec.write(trace))
        assertEquals(trace.config, back.config)
        assertEquals(trace.checkpoints, back.checkpoints)
        assertEquals(trace.log, back.log)
        assertEquals(trace.content, back.content)
    }

    @Test fun writingTwiceGivesTheSameBytes() {
        assertArrayEquals(TraceCodec.write(trace), TraceCodec.write(trace))
    }

    private fun rejects(bytes: ByteArray, fragment: String) {
        try { TraceCodec.read(bytes); fail("expected a failure mentioning '$fragment'") }
        catch (e: ByteReader.CorruptDataException) { assertTrue(e.message, e.message!!.contains(fragment)) }
    }

    @Test fun otherFilesAreNotTraces() {
        rejects(ByteArray(40) { 7 }, "not a trace")
        rejects(ByteArray(3), "too short")
    }

    @Test fun aFlippedByteFailsTheChecksum() {
        val bytes = TraceCodec.write(trace)
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x55).toByte()
        try { TraceCodec.read(bytes); fail("damage went unnoticed") } catch (e: ByteReader.CorruptDataException) { /* expected */ }
    }

    @Test fun aTruncatedFileIsRejected() {
        val bytes = TraceCodec.write(trace)
        try { TraceCodec.read(bytes.copyOf(bytes.size - 20)); fail("truncation went unnoticed") } catch (e: ByteReader.CorruptDataException) { /* expected */ }
    }

    @Test fun theLogIsKept() {
        assertTrue(trace.log.isNotEmpty())
        assertEquals(trace.log.size, TraceCodec.read(TraceCodec.write(trace)).log.size)
    }
}
