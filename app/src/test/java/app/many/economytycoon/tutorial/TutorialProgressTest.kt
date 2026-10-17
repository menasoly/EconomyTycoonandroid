package app.many.economytycoon.tutorial

import app.many.economytycoon.core.ByteWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialProgressTest {
    @Test fun roundTrips() {
        val p = TutorialProgress(setOf("a", "b"), setOf("c"), setOf("a"), true)
        assertEquals(p, TutorialProgress.fromBytes(p.toBytes()))
    }

    @Test fun emptyRoundTrips() = assertEquals(TutorialProgress(), TutorialProgress.fromBytes(TutorialProgress().toBytes()))

    @Test fun missingOrDamagedBytesStartAfresh() {
        assertEquals(TutorialProgress(), TutorialProgress.fromBytes(null))
        assertEquals(TutorialProgress(), TutorialProgress.fromBytes(ByteArray(0)))
        assertEquals(TutorialProgress(), TutorialProgress.fromBytes(byteArrayOf(1, 1, 99)))
    }

    @Test fun unknownVersionsStartAfresh() {
        val w = ByteWriter(); w.writeVarInt(99); w.writeBoolean(true)
        assertEquals(TutorialProgress(), TutorialProgress.fromBytes(w.toByteArray()))
    }

    @Test fun implausibleCountsAreRefused() {
        val w = ByteWriter(); w.writeVarInt(TutorialProgress.VERSION); w.writeBoolean(false); w.writeVarInt(5_000_000)
        assertEquals(TutorialProgress(), TutorialProgress.fromBytes(w.toByteArray()))
    }

    @Test fun encodingIsOrderIndependent() {
        val a = TutorialProgress(linkedSetOf("x", "y", "z"))
        val b = TutorialProgress(linkedSetOf("z", "y", "x"))
        assertTrue(a.toBytes().contentEquals(b.toBytes()))
    }

    @Test fun isDoneCoversCompletedAndSkipped() {
        val p = TutorialProgress(setOf("a"), setOf("b"))
        assertTrue(p.isDone("a") && p.isDone("b"))
        assertFalse(p.isDone("c"))
    }
}
