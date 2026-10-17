package app.many.economytycoon.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeqWindowTest {
    @Test fun firstUseIsFreshAndSecondIsDuplicate() {
        val w = SeqWindow()
        assertEquals(SeqVerdict.FRESH, w.accept(1))
        assertEquals(SeqVerdict.DUPLICATE, w.accept(1))
        assertEquals(1L, w.highest)
    }

    @Test fun zeroAndNegativeAreNeverAccepted() {
        val w = SeqWindow()
        assertEquals(SeqVerdict.STALE, w.accept(0))
        assertEquals(SeqVerdict.STALE, w.accept(-5))
    }

    @Test fun outOfOrderWithinTheWindowIsFreshOnce() {
        val w = SeqWindow()
        assertEquals(SeqVerdict.FRESH, w.accept(5))
        assertEquals(SeqVerdict.FRESH, w.accept(3))
        assertEquals(SeqVerdict.DUPLICATE, w.accept(3))
        assertEquals(SeqVerdict.FRESH, w.accept(4))
        assertEquals(SeqVerdict.DUPLICATE, w.accept(5))
        assertEquals(5L, w.highest)
    }

    @Test fun fallingOffTheBackIsStale() {
        val w = SeqWindow()
        w.accept(100)
        assertEquals(SeqVerdict.STALE, w.accept(100L - SeqWindow.SIZE))
        assertEquals(SeqVerdict.FRESH, w.accept(100L - SeqWindow.SIZE + 1))
    }

    @Test fun jumpingTooFarAheadIsRefusedAndChangesNothing() {
        val w = SeqWindow(maxJump = 10)
        w.accept(1)
        assertEquals(SeqVerdict.AHEAD, w.accept(50))
        assertEquals(1L, w.highest)
        assertEquals(SeqVerdict.FRESH, w.accept(11))
    }

    @Test fun bigAdvanceClearsOldMemory() {
        val w = SeqWindow(maxJump = 1000)
        w.accept(1)
        w.accept(500)
        assertEquals(SeqVerdict.STALE, w.accept(1))
        assertEquals(SeqVerdict.FRESH, w.accept(499))
    }

    @Test fun checkDoesNotRecord() {
        val w = SeqWindow()
        assertEquals(SeqVerdict.FRESH, w.check(7))
        assertEquals(SeqVerdict.FRESH, w.check(7))
        w.accept(7)
        assertEquals(SeqVerdict.DUPLICATE, w.check(7))
    }

    @Test fun everyNumberInAShuffledRunIsAcceptedExactlyOnce() {
        val w = SeqWindow()
        val order = (1..60).toList().shuffled(java.util.Random(4))
        val fresh = order.count { w.accept(it.toLong()) == SeqVerdict.FRESH }
        assertEquals(60, fresh)
        assertEquals(0, (1..60).count { w.accept(it.toLong()) == SeqVerdict.FRESH })
    }

    @Test fun stateRestoresExactly() {
        val a = SeqWindow(); listOf(3L, 9L, 7L).forEach { a.accept(it) }
        val b = SeqWindow(); b.restore(a.highest, a.maskBits())
        assertEquals(SeqVerdict.DUPLICATE, b.check(7))
        assertEquals(SeqVerdict.FRESH, b.check(8))
    }

    @Test fun rateLimiterAllowsABurstThenRefillsWithTime() {
        val r = RateLimiter(capacity = 3, ratePerSecond = 2)
        assertTrue(r.tryTake(0)); assertTrue(r.tryTake(0)); assertTrue(r.tryTake(0))
        assertFalse(r.tryTake(0))
        assertFalse(r.tryTake(400))
        assertTrue(r.tryTake(500))
        assertFalse(r.tryTake(500))
        assertTrue(r.tryTake(1000))
    }

    @Test fun rateLimiterNeverExceedsCapacity() {
        val r = RateLimiter(2, 100)
        assertEquals(2, r.available(0))
        assertEquals(2, r.available(1_000_000))
    }

    @Test fun rateLimiterChargesCost() {
        val r = RateLimiter(5, 1)
        assertTrue(r.tryTake(0, 4))
        assertFalse(r.tryTake(0, 2))
        assertTrue(r.tryTake(0, 1))
    }

    @Test fun rateLimiterIgnoresTimeGoingBackwards() {
        val r = RateLimiter(1, 1)
        assertTrue(r.tryTake(1000))
        assertFalse(r.tryTake(500))
        assertFalse(r.tryTake(1500))
        assertTrue(r.tryTake(2000))
    }
}
