package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitLogTest {
    private fun entry(p: Long, s: Long) = CommitEntry(PlayerId(p), s, byteArrayOf(p.toByte(), s.toByte()))

    private fun filled(n: Int = 10): CommitLog {
        val log = CommitLog()
        for (t in 0 until n) {
            val entries = if (t % 3 == 0) emptyList() else listOf(entry(1, t.toLong()), entry(2, t.toLong() + 100))
            val transfers = if (t % 4 == 1) listOf(Transfer(TradeId(t.toLong()), 1, 0, TradeAsset.cash(1000L * t))) else emptyList()
            log.append(t.toLong(), entries, transfers)
        }
        return log
    }

    @Test fun appendMustFollowOnInOrder() {
        val log = CommitLog()
        log.append(0, emptyList(), emptyList())
        try { log.append(2, emptyList(), emptyList()); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { assertTrue(e.message.orEmpty().contains("expected commit for tick 1")) }
        assertEquals(1L, log.nextTick)
    }

    @Test fun chainDependsOnEverythingBeforeIt() {
        val a = CommitLog(); val b = CommitLog()
        a.append(0, listOf(entry(1, 1)), emptyList()); b.append(0, listOf(entry(1, 2)), emptyList())
        a.append(1, emptyList(), emptyList()); b.append(1, emptyList(), emptyList())
        assertFalse(a.head == b.head)
        assertFalse(a.get(0)?.chain == b.get(0)?.chain)
    }

    @Test fun chainIsSensitiveToEntryOrderAndTransfers() {
        val e = listOf(entry(1, 1), entry(2, 2))
        assertFalse(CommitChain.next(0, 5, e, emptyList()) == CommitChain.next(0, 5, e.reversed(), emptyList()))
        val t = listOf(Transfer(TradeId(1), 1, 2, TradeAsset.cash(5)))
        assertFalse(CommitChain.next(0, 5, e, emptyList()) == CommitChain.next(0, 5, e, t))
        assertFalse(CommitChain.next(0, 5, e, emptyList()) == CommitChain.next(0, 6, e, emptyList()))
        assertFalse(CommitChain.next(0, 5, e, emptyList()) == CommitChain.next(1, 5, e, emptyList()))
    }

    @Test fun rangeAndLookup() {
        val log = filled()
        assertEquals(10, log.size)
        assertEquals(listOf(3L, 4L, 5L), log.range(3, 3).map { it.tick })
        assertEquals(listOf(8L, 9L), log.range(8, 50).map { it.tick })
        assertTrue(log.range(10, 3).isEmpty())
        assertTrue(log.range(-1, 3).isEmpty())
        assertNull(log.get(10))
        assertEquals(log.get(4)?.chain, log.chainBefore(5))
        assertEquals(0L, log.chainBefore(0))
    }

    @Test fun committedTickMakesAMessage() {
        val c = filled().get(2) ?: error("missing")
        val m = c.toMessage()
        assertEquals(2L, m.tick)
        assertEquals(c.entries, m.entries)
        assertEquals(c.chain, m.chain)
    }

    @Test fun roundTripsThroughItsCodec() {
        val log = filled(30)
        val w = ByteWriter(); log.write(w)
        val copy = CommitLog(); copy.read(ByteReader(w.toByteArray()))
        assertEquals(log.all(), copy.all())
        assertEquals(log.head, copy.head)
        val w2 = ByteWriter(); copy.write(w2)
        assertArrayEquals(w.toByteArray(), w2.toByteArray())
    }

    @Test fun editedLogFailsTheChainCheck() {
        val w = ByteWriter(); filled(8).write(w)
        val bytes = w.toByteArray()
        var refused = 0
        for (i in 4 until bytes.size) {
            val copy = bytes.copyOf(); copy[i] = (copy[i].toInt() xor 1).toByte()
            try { CommitLog().read(ByteReader(copy)) } catch (e: ByteReader.CorruptDataException) { refused++ }
        }
        assertTrue("refused $refused of ${bytes.size - 4}", refused > (bytes.size - 4) * 9 / 10)
    }

    @Test fun truncatedAndImplausibleLogsAreRefused() {
        val w = ByteWriter(); filled(5).write(w)
        val bytes = w.toByteArray()
        for (n in 0 until bytes.size) {
            try { CommitLog().read(ByteReader(bytes.copyOf(n))); org.junit.Assert.fail("cut at $n") } catch (e: ByteReader.CorruptDataException) { assertTrue(true) }
        }
        val huge = ByteWriter(); huge.writeVarInt(1); huge.writeVarInt(Int.MAX_VALUE)
        try { CommitLog().read(ByteReader(huge.toByteArray())); org.junit.Assert.fail() } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message.orEmpty().contains("implausible")) }
        val bad = ByteWriter(); bad.writeVarInt(7)
        try { CommitLog().read(ByteReader(bad.toByteArray())); org.junit.Assert.fail() } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message.orEmpty().contains("version")) }
    }
}
