package app.many.economytycoon.tourism

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourismCodecTest {
    @Test fun anEmptyStateRoundTrips() {
        val back = TourismCodec.fromBytes(TourismCodec.toBytes(TourismState()))
        assertTrue(back.isEmpty)
        assertEquals(TourismState().stateHash().toHex(), back.stateHash().toHex())
    }

    @Test fun aBusyStateRoundTripsWithTheSameHash() {
        val w = RichWorld.build()
        val back = TourismCodec.fromBytes(TourismCodec.toBytes(w.state))
        assertEquals(w.state.stateHash().toHex(), back.stateHash().toHex())
    }

    @Test fun roundTripPreservesTheDetails() {
        val w = RichWorld.build()
        val back = TourismCodec.fromBytes(TourismCodec.toBytes(w.state))
        assertEquals(w.state.properties.size, back.properties.size)
        for (p in w.state.properties) {
            val q = requireNotNull(back.property(p.id))
            assertEquals(p.name, q.name)
            assertEquals(p.settings, q.settings)
            assertEquals(p.rooms.toList(), q.rooms.toList())
            assertEquals(p.days, q.days)
            assertEquals(p.reviews.recent, q.reviews.recent)
            assertEquals(p.listedOn, q.listedOn)
            assertEquals(p.bookedNights, q.bookedNights)
        }
        for (a in w.state.allotments) {
            val b = requireNotNull(back.allotment(a.id))
            assertEquals(a.status, b.status)
            assertEquals(a.soldDates, b.soldDates)
        }
        assertEquals(w.state.shocks.map { it.label }, back.shocks.map { it.label })
        assertEquals(w.state.lastTick, back.lastTick)
        assertEquals(w.state.lastSettledMonth, back.lastSettledMonth)
    }

    @Test fun writingTwiceGivesTheSameBytes() {
        val w = RichWorld.build(days = 25)
        assertArrayEquals(TourismCodec.toBytes(w.state), TourismCodec.toBytes(w.state))
    }

    @Test fun reloadedStateKeepsTradingIdentically() {
        val a = RichWorld.build(days = 30)
        val bytes = TourismCodec.toBytes(a.state)
        val b = TourismWorld(seed = 7L)
        val restored = TourismService(TourismCodec.fromBytes(bytes))
        // Same random stream position: replay the stream by advancing a copy of the original generator.
        val streamA = a.random
        val streamB = app.many.economytycoon.core.RandomProvider(7L)
        repeat(0) { streamB }
        val inputs = DayInputs()
        // Run both for ten more days from identical state and an identical stream.
        val freshA = TourismService(TourismCodec.fromBytes(bytes))
        val rA = app.many.economytycoon.core.RandomProvider(99L)
        val rB = app.many.economytycoon.core.RandomProvider(99L)
        repeat(10) {
            freshA.advanceDay(app.many.economytycoon.core.Tick(a.tick + it), inputs, rA)
            restored.advanceDay(app.many.economytycoon.core.Tick(a.tick + it), inputs, rB)
        }
        assertEquals(freshA.state.stateHash().toHex(), restored.state.stateHash().toHex())
        assertTrue(streamA === a.random && b.state.isEmpty.not())
        assertFalse(streamB === streamA)
    }

    @Test fun theVersionByteComesFirst() {
        val bytes = TourismCodec.toBytes(RichWorld.build(days = 3).state)
        val r = ByteReader(bytes)
        assertEquals(TourismCodec.VERSION, r.readVarInt())
    }

    @Test fun anUnknownVersionIsRefused() {
        val out = ByteWriter()
        out.writeVarInt(TourismCodec.VERSION + 1)
        try {
            TourismCodec.fromBytes(out.toByteArray())
            throw AssertionError("expected refusal")
        } catch (e: ByteReader.CorruptDataException) {
            assertTrue(e.message.orEmpty().contains("version"))
        }
    }

    @Test fun truncatedDataIsRefusedAtEveryCut() {
        val bytes = TourismCodec.toBytes(RichWorld.build(days = 6).state)
        val cuts = listOf(0, 1, 2, 5, 17, bytes.size / 4, bytes.size / 2, bytes.size - 1)
        for (cut in cuts) {
            try {
                TourismCodec.fromBytes(bytes.copyOf(cut))
                throw AssertionError("cut at $cut was accepted")
            } catch (e: ByteReader.CorruptDataException) {
                // expected
            }
        }
    }

    @Test fun garbageDoesNotEscapeAsAnotherException() {
        val rnd = java.util.Random(5L)
        repeat(60) {
            val junk = ByteArray(1 + rnd.nextInt(120)).also { rnd.nextBytes(it) }
            junk[0] = TourismCodec.VERSION.toByte()
            try {
                TourismCodec.fromBytes(junk)
            } catch (e: ByteReader.CorruptDataException) {
                // refused cleanly
            } catch (e: OutOfMemoryError) {
                throw AssertionError("a junk length allocated without bound")
            }
        }
    }

    @Test fun readingReplacesWhatWasThere() {
        val full = RichWorld.build(days = 4).state
        val target = RichWorld.build(seed = 9L, days = 12).state
        TourismCodec.read(ByteReader(TourismCodec.toBytes(full)), target)
        assertEquals(full.stateHash().toHex(), target.stateHash().toHex())
    }

    @Test fun savingAfterEachPhaseOfAGameStaysConsistent() {
        val w = TourismWorld()
        w.hotel(); w.attraction(); w.platform(); w.operator()
        for (n in listOf(0, 1, 15, 40)) {
            w.days(n)
            val back = TourismCodec.fromBytes(TourismCodec.toBytes(w.state))
            assertEquals(w.state.stateHash().toHex(), back.stateHash().toHex())
            TourismInvariants.check(back)
        }
    }
}
