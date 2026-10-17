package app.many.economytycoon.net

import app.many.economytycoon.core.DeterministicRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotTransferTest {
    private fun bundle(size: Int = 50_000, chunk: Int = 4_096, id: Long = 3) =
        SnapshotBundle(id, 120, NetFixtures.bytes(size, 4), NetFixtures.hash(2), 0xABCL, chunk)

    /** Runs a transfer over a network profile and returns the received snapshot, or null if it did not finish. */
    private fun transfer(b: SnapshotBundle, profile: LinkProfile, seed: Long, maxMs: Long = 60_000, receiver: SnapshotReceiver = SnapshotReceiver(), sender: SnapshotSender = SnapshotSender(b)): ReceivedSnapshot? {
        val net = MemoryNetwork(seed, profile)
        val s = net.endpoint(EndpointId(1)); val r = net.endpoint(EndpointId(2))
        var done: ReceivedSnapshot? = null
        var t = 0L
        while (t < maxMs && done == null) {
            for (m in sender.pump(t)) s.send(EndpointId(2), MessageCodec.encode(0, m))
            net.advanceTo(t)
            for (d in r.receive()) {
                val p = (MessageCodec.decode(d.bytes) as? Decoded.Ok)?.packet ?: continue
                val res = when (val m = p.message) {
                    is NetMessage.SnapshotHeader -> receiver.onHeader(m)
                    is NetMessage.SnapshotChunk -> receiver.onChunk(m)
                    else -> ReceiveResult.IGNORED
                }
                res.ack?.let { r.send(EndpointId(1), MessageCodec.encode(0, it)) }
                if (res.completed != null) done = res.completed
            }
            net.advanceTo(t)
            for (d in s.receive()) ((MessageCodec.decode(d.bytes) as? Decoded.Ok)?.packet?.message as? NetMessage.SnapshotAck)?.let { sender.onAck(it, t) }
            t += 10
        }
        return done
    }

    @Test fun headerDescribesTheSnapshot() {
        val b = bundle(10_000, 4_096)
        val h = b.header()
        assertEquals(3, h.chunkCount)
        assertEquals(10_000, h.totalBytes)
        assertArrayEquals(Hmac.sha256(b.bytes), h.digest)
        assertEquals(0xABCL, h.chain)
    }

    @Test fun chunksCoverTheBytesExactly() {
        val b = bundle(10_000, 4_096)
        val joined = (0 until b.chunkCount).flatMap { b.chunk(it).data.toList() }
        assertEquals(b.bytes.toList(), joined)
        assertEquals(10_000 - 2 * 4_096, b.chunk(2).data.size)
    }

    @Test fun exactMultipleHasNoShortChunk() {
        val b = bundle(8_192, 4_096)
        assertEquals(2, b.chunkCount)
        assertEquals(4_096, b.chunk(1).data.size)
    }

    @Test fun invalidBundlesAreRefused() {
        try { SnapshotBundle(1, 0, ByteArray(0), NetFixtures.hash(), 0L); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { assertTrue(true) }
        try { SnapshotBundle(1, 0, ByteArray(5), NetFixtures.hash(), 0L, chunkSize = 0); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { assertTrue(true) }
    }

    @Test fun cleanLinkDeliversTheSnapshot() {
        val b = bundle()
        val got = transfer(b, LinkProfile.PERFECT, 1) ?: error("not delivered")
        assertArrayEquals(b.bytes, got.bytes)
        assertEquals(120L, got.tick)
        assertEquals(0xABCL, got.chain)
        assertArrayEquals(NetFixtures.hash(2), got.stateHash)
    }

    @Test fun lossyReorderingDuplicatingLinkStillDelivers() {
        for (seed in 1L..6L) {
            val b = bundle(40_000, 2_048)
            val got = transfer(b, LinkProfile(lossPpm = 250_000, duplicatePpm = 150_000, minDelayMs = 0, maxDelayMs = 60), seed)
            assertNotNull("seed $seed", got)
            assertArrayEquals("seed $seed", b.bytes, got?.bytes)
        }
    }

    @Test fun corruptionIsCaughtAndRepaired() {
        val b = bundle(30_000, 2_048)
        val got = transfer(b, LinkProfile(corruptPpm = 200_000, lossPpm = 50_000), 9)
        assertNotNull(got)
        assertArrayEquals(b.bytes, got?.bytes)
    }

    @Test fun deadLinkNeverCompletes() {
        assertNull(transfer(bundle(), LinkProfile.lossy(1_000_000), 1, maxMs = 2_000))
    }

    @Test fun receiverIgnoresChunksOfAnotherSnapshot() {
        val r = SnapshotReceiver()
        val b = bundle()
        r.onHeader(b.header())
        val other = bundle(id = 99).chunk(0)
        assertEquals(0, r.received)
        assertNull(r.onChunk(other).ack)
        assertEquals(0, r.received)
    }

    @Test fun receiverRefusesWrongSizedOrDamagedChunks() {
        val r = SnapshotReceiver(); val b = bundle(10_000, 4_096)
        r.onHeader(b.header())
        val good = b.chunk(0)
        assertEquals(NetError.BAD_FRAME, r.onChunk(NetMessage.SnapshotChunk(3, 0, good.data.copyOf(10), SnapshotBundle.crc(good.data.copyOf(10)))).error)
        assertEquals(NetError.BAD_CHECKSUM, r.onChunk(NetMessage.SnapshotChunk(3, 0, good.data, good.crc + 1)).error)
        assertEquals(NetError.BAD_FRAME, r.onChunk(NetMessage.SnapshotChunk(3, 7, good.data, good.crc)).error)
        assertEquals(0, r.received)
        assertNull(r.onChunk(good).error)
        assertEquals(1, r.received)
    }

    @Test fun duplicateChunksAreCountedOnce() {
        val r = SnapshotReceiver(); val b = bundle(10_000, 4_096)
        r.onHeader(b.header())
        repeat(3) { r.onChunk(b.chunk(0)) }
        assertEquals(1, r.received)
        assertEquals(1, r.firstMissing())
    }

    @Test fun chunksMayArriveInAnyOrder() {
        val r = SnapshotReceiver(); val b = bundle(20_000, 2_048)
        r.onHeader(b.header())
        val order = (0 until b.chunkCount).toList().shuffled(java.util.Random(3))
        var done: ReceivedSnapshot? = null
        for (i in order) r.onChunk(b.chunk(i)).completed?.let { done = it }
        assertArrayEquals(b.bytes, done?.bytes)
    }

    @Test fun wrongDigestIsRejectedAndTheTransferReset() {
        val r = SnapshotReceiver(); val b = bundle(5_000, 4_096)
        val h = b.header()
        val forged = NetMessage.SnapshotHeader(h.snapshotId, h.tick, h.totalBytes, h.chunkSize, h.chunkCount, ByteArray(32), h.stateHash, h.chain)
        r.onHeader(forged)
        r.onChunk(b.chunk(0))
        val last = r.onChunk(b.chunk(1))
        assertEquals(NetError.BAD_CHECKSUM, last.error)
        assertNull(last.completed)
        assertEquals(0, r.total)
    }

    @Test fun resumeAsksForTheFirstMissingChunk() {
        val b = bundle(30_000, 2_048)
        val r = SnapshotReceiver()
        r.onHeader(b.header())
        for (i in 0 until 5) r.onChunk(b.chunk(i))
        r.onChunk(b.chunk(7))
        assertEquals(5, r.firstMissing())
        val sender = SnapshotSender(b)
        sender.begin(r.firstMissing())
        val first = sender.pump(0)
        assertTrue(first.first() is NetMessage.SnapshotHeader)
        assertEquals(5, (first[1] as NetMessage.SnapshotChunk).index)
        val got = transfer(b, LinkProfile.lossy(100_000), 4, receiver = r, sender = sender)
        assertArrayEquals(b.bytes, got?.bytes)
    }

    @Test fun repeatedHeaderKeepsProgress() {
        val r = SnapshotReceiver(); val b = bundle(10_000, 2_048)
        r.onHeader(b.header())
        r.onChunk(b.chunk(0)); r.onChunk(b.chunk(1))
        val ack = r.onHeader(b.header()).ack
        assertEquals(2, ack?.next)
        assertEquals(2, r.received)
    }

    @Test fun senderWindowLimitsWhatIsInFlight() {
        val b = bundle(100_000, 1_024)
        val s = SnapshotSender(b, window = 4)
        val first = s.pump(0)
        assertEquals(1 + 4, first.size)
        assertTrue(s.pump(1).isEmpty())
        s.onAck(NetMessage.SnapshotAck(3, 2, false), 2)
        assertEquals(2, s.pump(2).size)
    }

    @Test fun senderRetransmitsAfterSilence() {
        val b = bundle(100_000, 1_024)
        val s = SnapshotSender(b, window = 4, retransmitAfterMs = 100)
        s.pump(0)
        assertTrue(s.pump(50).isEmpty())
        val again = s.pump(150)
        assertTrue(again.any { it is NetMessage.SnapshotChunk && it.index == 0 })
    }

    @Test fun senderIgnoresAcksForOtherSnapshotsAndStopsWhenComplete() {
        val b = bundle(5_000, 1_024)
        val s = SnapshotSender(b)
        s.pump(0)
        s.onAck(NetMessage.SnapshotAck(77, 3, false), 1)
        assertEquals(0, s.chunksAcknowledged)
        s.onAck(NetMessage.SnapshotAck(3, 0, true), 2)
        assertTrue(s.complete)
        assertTrue(s.pump(1000).isEmpty())
    }

    @Test fun randomLinksNeverCorruptTheResult() {
        val rnd = DeterministicRandom(77)
        repeat(5) {
            val b = bundle(20_000 + rnd.nextInt(10_000), 1_024 + rnd.nextInt(2_000), id = it + 1L)
            val p = LinkProfile(lossPpm = rnd.nextInt(300_000), duplicatePpm = rnd.nextInt(200_000), corruptPpm = rnd.nextInt(100_000), minDelayMs = 0, maxDelayMs = rnd.nextInt(80).toLong())
            val got = transfer(b, p, rnd.nextLong())
            assertFalse(got == null)
            assertArrayEquals(b.bytes, got?.bytes)
        }
    }
}
