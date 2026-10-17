package app.many.economytycoon.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTest {
    @Test fun aLinkNeverHeardFromIsOffline() {
        assertEquals(Quality.OFFLINE, LinkStats().quality(1_000))
    }

    @Test fun silenceTurnsAGoodLinkOffline() {
        val s = LinkStats(offlineAfterMs = 3_000L)
        s.heard(1_000)
        assertEquals(Quality.FAIR, s.quality(1_500))
        assertEquals(Quality.OFFLINE, s.quality(4_500))
    }

    @Test fun roundTripTimeIsSmoothed() {
        val s = LinkStats()
        s.pinged(1, 0); assertEquals(40L, s.ponged(1, 40))
        assertEquals(40L, s.rttMs)
        s.pinged(2, 100); s.ponged(2, 180)
        assertEquals(45L, s.rttMs)
        assertTrue(s.jitterMs > 0)
    }

    @Test fun anUnknownPongIsIgnored() {
        val s = LinkStats()
        assertNull(s.ponged(99, 10))
        assertEquals(-1L, s.rttMs)
    }

    @Test fun qualityFollowsRoundTripTime() {
        fun q(rtt: Long): Quality { val s = LinkStats(); s.pinged(1, 0); s.ponged(1, rtt); return s.quality(rtt) }
        assertEquals(Quality.EXCELLENT, q(20))
        assertEquals(Quality.GOOD, q(100))
        assertEquals(Quality.FAIR, q(250))
        assertEquals(Quality.POOR, q(500))
    }

    @Test fun lostPingsRaiseLossAndLowerQuality() {
        val s = LinkStats()
        var t = 0L
        for (n in 1L..10L) { s.pinged(n, t, timeoutMs = 100); t += 200 }
        s.pinged(11, t, timeoutMs = 100)
        assertTrue(s.lossPpm() >= 900_000)
        s.heard(t)
        assertEquals(Quality.POOR, s.quality(t))
    }

    @Test fun lossDecaysAsPingsAreAnsweredAgain() {
        val s = LinkStats()
        var t = 0L
        for (n in 1L..4L) { s.pinged(n, t, timeoutMs = 50); t += 100 }
        s.pinged(5, t, timeoutMs = 50)
        val before = s.lossPpm()
        for (n in 6L..40L) { s.pinged(n, t, timeoutMs = 50); s.ponged(n, t + 10); t += 100 }
        assertTrue(s.lossPpm() < before)
        assertEquals(0, s.lossPpm())
    }

    @Test fun outstandingPingsAreBounded() {
        val s = LinkStats()
        for (n in 1L..100L) s.pinged(n, 0)
        assertNull(s.ponged(1, 5))
        assertTrue(s.ponged(100, 5) != null)
    }

    @Test fun cachedRepliesAreBounded() {
        val c = ServerConn(EndpointId(5), PlayerId(1), Role.PLAYER, 0L, 1)
        for (i in 1L..100L) c.remember(i, listOf(byteArrayOf(i.toByte())), 10)
        assertEquals(10, c.replies.size)
        assertEquals(90L, c.replies.keys.first() - 1)
        assertEquals(1L, c.nextSeq())
        assertEquals(2L, c.nextSeq())
    }
}
