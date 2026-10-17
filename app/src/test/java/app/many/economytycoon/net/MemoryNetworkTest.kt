package app.many.economytycoon.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryNetworkTest {
    private val a = EndpointId(1); private val b = EndpointId(2)

    private fun run(seed: Long, profile: LinkProfile, packets: Int = 200): List<String> {
        val net = MemoryNetwork(seed, profile)
        val ta = net.endpoint(a); val tb = net.endpoint(b)
        val log = ArrayList<String>()
        for (i in 0 until packets) {
            ta.send(b, byteArrayOf(i.toByte(), (i shr 8).toByte()))
            net.advance(3)
            tb.receive().forEach { log += "${net.now}:${it.from}:${it.bytes.toList()}" }
        }
        net.advance(1000)
        tb.receive().forEach { log += "${net.now}:${it.from}:${it.bytes.toList()}" }
        return log
    }

    @Test fun perfectLinkDeliversEverythingInOrderImmediately() {
        val net = MemoryNetwork(1)
        val ta = net.endpoint(a); val tb = net.endpoint(b)
        repeat(5) { ta.send(b, byteArrayOf(it.toByte())) }
        val got = tb.receive()
        assertEquals(listOf<Byte>(0, 1, 2, 3, 4), got.map { it.bytes[0] })
        assertTrue(got.all { it.from == a })
        assertTrue(tb.receive().isEmpty())
    }

    @Test fun delayedPacketsArriveOnlyWhenTheClockReachesThem() {
        val net = MemoryNetwork(1, LinkProfile.jittery(50, 50))
        val ta = net.endpoint(a); val tb = net.endpoint(b)
        ta.send(b, byteArrayOf(1))
        net.advance(49)
        assertTrue(tb.receive().isEmpty())
        assertEquals(1, net.inFlight())
        net.advance(1)
        assertEquals(1, tb.receive().size)
        assertEquals(0, net.inFlight())
    }

    @Test fun jitterReordersPackets() {
        val net = MemoryNetwork(7, LinkProfile.jittery(0, 100))
        val ta = net.endpoint(a); val tb = net.endpoint(b)
        repeat(50) { ta.send(b, byteArrayOf(it.toByte())) }
        net.advance(200)
        val order = tb.receive().map { it.bytes[0].toInt() }
        assertEquals(50, order.size)
        assertEquals((0 until 50).toSet(), order.toSet())
        assertFalse("expected some overtaking", order == order.sorted())
    }

    @Test fun lossDropsRoughlyTheConfiguredShare() {
        val net = MemoryNetwork(3, LinkProfile.lossy(300_000))
        val ta = net.endpoint(a); val tb = net.endpoint(b)
        repeat(2000) { ta.send(b, byteArrayOf(1)) }
        val got = tb.receive().size
        assertTrue("got $got", got in 1250..1550)
        assertEquals(2000 - got, net.stats().dropped)
    }

    @Test fun duplicationDeliversExtraCopies() {
        val net = MemoryNetwork(3, LinkProfile(duplicatePpm = 500_000))
        val ta = net.endpoint(a); val tb = net.endpoint(b)
        repeat(1000) { ta.send(b, byteArrayOf(1)) }
        val got = tb.receive().size
        assertTrue("got $got", got in 1400..1600)
        assertEquals(got - 1000, net.stats().duplicated)
    }

    @Test fun corruptionFlipsExactlyOneBitPerAffectedCopy() {
        val net = MemoryNetwork(3, LinkProfile(corruptPpm = 1_000_000))
        val ta = net.endpoint(a); val tb = net.endpoint(b)
        val original = ByteArray(16) { 0x55 }
        ta.send(b, original)
        val got = tb.receive().single().bytes
        var bits = 0
        for (i in got.indices) bits += Integer.bitCount((got[i].toInt() xor original[i].toInt()) and 0xFF)
        assertEquals(1, bits)
        assertEquals(1, net.stats().corrupted)
    }

    @Test fun senderBufferIsCopied() {
        val net = MemoryNetwork(1)
        val ta = net.endpoint(a); val tb = net.endpoint(b)
        val buf = byteArrayOf(1, 2, 3)
        ta.send(b, buf)
        buf[0] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3), tb.receive().single().bytes)
    }

    @Test fun sameSeedSameOutcome() {
        assertEquals(run(5, LinkProfile.HOSTILE), run(5, LinkProfile.HOSTILE))
        assertFalse(run(5, LinkProfile.HOSTILE) == run(6, LinkProfile.HOSTILE))
    }

    @Test fun partitionDiscardsAndHealingRestores() {
        val net = MemoryNetwork(1)
        val ta = net.endpoint(a); val tb = net.endpoint(b)
        net.partition(a, b, true)
        ta.send(b, byteArrayOf(1)); tb.send(a, byteArrayOf(2))
        assertTrue(tb.receive().isEmpty())
        assertEquals(2, net.stats().cut)
        net.partition(a, b, false)
        ta.send(b, byteArrayOf(3))
        assertEquals(1, tb.receive().size)
    }

    @Test fun sendingToNobodyIsHarmless() {
        val net = MemoryNetwork(1)
        net.endpoint(a).send(EndpointId(50), byteArrayOf(1))
        assertEquals(1, net.stats().cut)
    }

    @Test fun linksAreDirectional() {
        val net = MemoryNetwork(1)
        val ta = net.endpoint(a); val tb = net.endpoint(b)
        net.setLink(a, b, LinkProfile.lossy(1_000_000))
        ta.send(b, byteArrayOf(1)); tb.send(a, byteArrayOf(2))
        assertTrue(tb.receive().isEmpty())
        assertEquals(1, ta.receive().size)
    }

    @Test fun clockCannotRunBackwards() {
        val net = MemoryNetwork(1)
        net.advance(10)
        try { net.advanceTo(5); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { assertTrue(true) }
    }

    @Test fun profileRejectsNonsense() {
        try { LinkProfile(lossPpm = -1); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { assertTrue(true) }
        try { LinkProfile(minDelayMs = 5, maxDelayMs = 1); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { assertTrue(true) }
    }
}
