package app.many.economytycoon.net

import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.DeterministicRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.zip.CRC32

class MessageCodecTest {
    private fun ok(bytes: ByteArray): Packet = (MessageCodec.decode(bytes) as Decoded.Ok).packet
    private fun bad(bytes: ByteArray): DecodeError = (MessageCodec.decode(bytes) as Decoded.Bad).error

    /** Rebuilds a frame with a valid checksum around an arbitrary body, to reach the field-level checks. */
    private fun frame(kind: MessageKind, body: ByteArray, seq: Long = 1, protocol: Int = 1, tag: Int = kind.tag): ByteArray {
        val w = ByteWriter()
        w.writeByte(0xE7); w.writeByte(0x7C); w.writeVarInt(protocol); w.writeByte(tag); w.writeVarLong(seq); w.writeBytes(body)
        val head = w.toByteArray()
        val out = ByteWriter(); out.writeBytesRaw(head); out.writeFixedLong(CRC32().also { it.update(head) }.value)
        return out.toByteArray()
    }

    @Test fun everyKindHasASample() {
        assertEquals(MessageKind.values().toSet(), NetFixtures.samples().map { it.kind }.toSet())
    }

    @Test fun everySampleRoundTrips() {
        for ((i, m) in NetFixtures.samples().withIndex()) {
            val bytes = MessageCodec.encode(i.toLong(), m)
            val p = ok(bytes)
            assertEquals(i.toLong(), p.seq)
            assertEquals("kind ${m.kind}", m, p.message)
            assertArrayEquals(bytes, MessageCodec.encode(p.seq, p.message))
        }
    }

    @Test fun encodingIsDeterministic() {
        val a = NetFixtures.samples().map { MessageCodec.encode(3, it).toList() }
        val b = NetFixtures.samples().map { MessageCodec.encode(3, it).toList() }
        assertEquals(a, b)
    }

    @Test fun emptyAndShortInputIsRefused() {
        assertEquals(DecodeError.EMPTY, bad(ByteArray(0)))
        assertEquals(DecodeError.TOO_SHORT, bad(ByteArray(5)))
    }

    @Test fun oversizedInputIsRefusedBeforeParsing() {
        assertEquals(DecodeError.TOO_LARGE, bad(ByteArray(NetLimits.MAX_FRAME_BYTES + 1)))
    }

    @Test fun wrongMagicIsRefused() {
        val bytes = MessageCodec.encode(1, NetMessage.Ping(1, 1))
        bytes[0] = 0
        assertEquals(DecodeError.BAD_MAGIC, bad(bytes))
    }

    @Test fun anyDamagedByteIsCaughtByTheChecksum() {
        val bytes = MessageCodec.encode(1, NetMessage.Ack(5, 6))
        for (i in 2 until bytes.size) {
            val copy = bytes.copyOf(); copy[i] = (copy[i].toInt() xor 0x5A).toByte()
            val r = MessageCodec.decode(copy)
            assertTrue("byte $i", r is Decoded.Bad)
        }
    }

    @Test fun everyTruncationIsRefused() {
        for (m in NetFixtures.samples()) {
            val bytes = MessageCodec.encode(2, m)
            for (n in 0 until bytes.size) assertTrue("${m.kind} cut to $n", MessageCodec.decode(bytes.copyOf(n)) is Decoded.Bad)
        }
    }

    @Test fun unsupportedProtocolIsReported() {
        assertEquals(DecodeError.BAD_VERSION, bad(frame(MessageKind.PING, ByteArray(0), protocol = 9)))
        assertEquals(DecodeError.BAD_VERSION, bad(frame(MessageKind.PING, ByteArray(0), protocol = 0)))
    }

    @Test fun unknownKindIsRefused() {
        assertEquals(DecodeError.UNKNOWN_KIND, bad(frame(MessageKind.PING, ByteArray(0), tag = 99)))
    }

    @Test fun trailingBytesInTheBodyAreRefused() {
        val w = ByteWriter(); w.writeVarLong(1); w.writeVarLong(2); w.writeByte(0)
        assertEquals(DecodeError.TRAILING_BYTES, bad(frame(MessageKind.PING, w.toByteArray())))
    }

    @Test fun overlongVarintIsNotCanonical() {
        // 1 encoded as two bytes instead of one: zig-zag 2 as 0x82 0x00
        val body = byteArrayOf(0x82.toByte(), 0x00, 0x02)
        assertEquals(DecodeError.NOT_CANONICAL, bad(frame(MessageKind.PING, body)))
    }

    @Test fun negativeValuesAreRefusedWhereNotAllowed() {
        val w = ByteWriter(); w.writeVarLong(-1); w.writeVarLong(3)
        assertEquals(DecodeError.BAD_VALUE, bad(frame(MessageKind.ACK, w.toByteArray())))
    }

    @Test fun badEnumTagsAreRefused() {
        val w = ByteWriter(); w.writeByte(200)
        assertEquals(DecodeError.BAD_VALUE, bad(frame(MessageKind.BYE, w.toByteArray())))
        val e = ByteWriter(); e.writeVarInt(9999); e.writeString("x")
        assertEquals(DecodeError.BAD_VALUE, bad(frame(MessageKind.ERROR, e.toByteArray())))
    }

    @Test fun controlCharactersInTextAreRefused() {
        val w = ByteWriter(); w.writeByte(0); w.writeVarInt(0); w.writeString("line\nbreak")
        assertEquals(DecodeError.BAD_TEXT, bad(frame(MessageKind.CHAT, w.toByteArray())))
    }

    @Test fun invalidUtf8IsRefused() {
        val w = ByteWriter(); w.writeByte(0); w.writeVarInt(0); w.writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
        assertEquals(DecodeError.BAD_TEXT, bad(frame(MessageKind.CHAT, w.toByteArray())))
    }

    @Test fun overlongTextIsRefused() {
        val w = ByteWriter(); w.writeByte(0); w.writeVarInt(0); w.writeString("x".repeat(NetLimits.MAX_CHAT_CHARS + 1))
        assertEquals(DecodeError.TOO_LARGE, bad(frame(MessageKind.CHAT, w.toByteArray())))
        val ok = ByteWriter(); ok.writeByte(0); ok.writeVarInt(0); ok.writeString("x".repeat(NetLimits.MAX_CHAT_CHARS))
        assertTrue(MessageCodec.decode(frame(MessageKind.CHAT, ok.toByteArray())) is Decoded.Ok)
    }

    @Test fun oversizedCommandPayloadIsRefused() {
        val w = ByteWriter(); w.writeVarLong(1); w.writeVarLong(2); w.writeBytes(ByteArray(NetLimits.MAX_COMMAND_BYTES + 1)); w.writeBytesRaw(ByteArray(16))
        assertEquals(DecodeError.TOO_LARGE, bad(frame(MessageKind.SUBMIT, w.toByteArray())))
    }

    @Test fun hugeListCountsAreRefusedWithoutAllocating() {
        val w = ByteWriter(); w.writeVarLong(1); w.writeVarInt(Int.MAX_VALUE)
        assertEquals(DecodeError.TOO_MANY_ITEMS, bad(frame(MessageKind.COMMIT, w.toByteArray())))
        val w2 = ByteWriter(); w2.writeVarLong(1); w2.writeVarInt(NetLimits.MAX_COMMIT_ENTRIES)
        assertEquals(DecodeError.TRUNCATED, bad(frame(MessageKind.COMMIT, w2.toByteArray())))
    }

    @Test fun snapshotHeaderMustBeConsistent() {
        val w = ByteWriter()
        w.writeVarLong(1); w.writeVarLong(10); w.writeVarInt(1000); w.writeVarInt(100); w.writeVarInt(7)
        w.writeBytesRaw(ByteArray(32)); w.writeBytesRaw(ByteArray(32))
        assertEquals(DecodeError.BAD_VALUE, bad(frame(MessageKind.SNAPSHOT_HEADER, w.toByteArray())))
    }

    @Test fun tradeAssetsMustMakeSense() {
        fun request(kind: Int, key: String, amount: Long): ByteArray {
            val w = ByteWriter()
            w.writeByte(0); w.writeVarLong(1); w.writeVarLong(2)
            w.writeVarInt(1); w.writeByte(kind); w.writeString(key); w.writeVarLong(amount)
            w.writeVarInt(0); w.writeVarInt(5)
            return frame(MessageKind.TRADE_REQUEST, w.toByteArray())
        }
        assertTrue(MessageCodec.decode(request(0, "", 10)) is Decoded.Ok)
        assertEquals(DecodeError.BAD_VALUE, bad(request(0, "", 0)))
        assertEquals(DecodeError.BAD_VALUE, bad(request(0, "wheat", 10)))
        assertEquals(DecodeError.BAD_VALUE, bad(request(1, "", 10)))
        assertEquals(DecodeError.BAD_VALUE, bad(request(7, "", 10)))
    }

    @Test fun randomGarbageNeverThrows() {
        for (g in NetFixtures.garbage(1, 4000)) {
            val r = MessageCodec.decode(g)
            assertTrue(r is Decoded.Bad)
        }
    }

    @Test fun garbageWithAValidHeaderAndChecksumNeverThrows() {
        val rnd = DeterministicRandom(99)
        var accepted = 0
        repeat(3000) {
            val kind = MessageKind.values()[rnd.nextInt(MessageKind.values().size)]
            val body = ByteArray(rnd.nextInt(80)) { rnd.nextInt(256).toByte() }
            val r = MessageCodec.decode(frame(kind, body, seq = rnd.nextInt(5).toLong()))
            if (r is Decoded.Ok) {
                accepted++
                // anything accepted must be canonical
                assertArrayEquals(frame(kind, body, seq = r.packet.seq), MessageCodec.encode(r.packet.seq, r.packet.message))
            }
        }
        assertTrue(accepted >= 0)
    }

    @Test fun mutatedValidFramesNeverThrowAndAcceptedOnesAreCanonical() {
        val rnd = DeterministicRandom(1234)
        val samples = NetFixtures.samples().map { MessageCodec.encode(7, it) }
        repeat(6000) {
            val src = samples[rnd.nextInt(samples.size)].copyOf()
            repeat(1 + rnd.nextInt(3)) { src[rnd.nextInt(src.size)] = rnd.nextInt(256).toByte() }
            try {
                val r = MessageCodec.decode(src)
                if (r is Decoded.Ok) assertArrayEquals(src, MessageCodec.encode(r.packet.seq, r.packet.message))
            } catch (e: Throwable) {
                fail("decoder threw ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    @Test fun mutatedBodiesBehindAValidChecksumNeverThrow() {
        val rnd = DeterministicRandom(555)
        var refusals = 0
        for (m in NetFixtures.samples()) {
            val full = MessageCodec.encode(1, m)
            val bodyStart = 2 + 1 + 1 + 1
            repeat(200) {
                val head = full.copyOf(full.size - 8)
                val i = bodyStart + rnd.nextInt(head.size - bodyStart)
                head[i] = rnd.nextInt(256).toByte()
                val out = ByteWriter(); out.writeBytesRaw(head); out.writeFixedLong(CRC32().also { it.update(head) }.value)
                try {
                    if (MessageCodec.decode(out.toByteArray()) is Decoded.Bad) refusals++
                } catch (e: Throwable) {
                    fail("${m.kind}: decoder threw ${e::class.simpleName}: ${e.message}")
                }
            }
        }
        assertTrue(refusals > 1000)
    }
}
