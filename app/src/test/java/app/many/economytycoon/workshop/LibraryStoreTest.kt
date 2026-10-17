package app.many.economytycoon.workshop

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.persistence.MemorySaveStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LibraryStoreTest {
    private fun filled(): WorkshopLibrary {
        val lib = WorkshopLibrary()
        val a = (lib.add(Samples.full(), folder = "x/y", tags = listOf("t"), note = "hello") as Outcome.Ok).value
        lib.add(Samples.minimal("b"))
        lib.add(Samples.cheap("c"))
        lib.star(a, true)
        lib.remove((requireNotNull(lib.byScenarioId("b"))).id)
        return lib
    }

    @Test fun aLibraryRoundTripsThroughBytes() {
        val lib = filled()
        val back = LibraryCodec.fromBytes(LibraryCodec.toBytes(lib))
        assertEquals(lib.all(), back.all())
        assertEquals(lib.nextIdValue(), back.nextIdValue())
        assertEquals(lib.clockValue(), back.clockValue())
    }

    @Test fun idsAreNotReusedAfterReload() {
        val back = LibraryCodec.fromBytes(LibraryCodec.toBytes(filled()))
        val id = (back.add(Samples.minimal("z")) as Outcome.Ok).value
        assertEquals(4L, id)
    }

    @Test fun theBytesAreStable() {
        assertTrue(LibraryCodec.toBytes(filled()).contentEquals(LibraryCodec.toBytes(filled())))
    }

    @Test fun anEmptyLibraryRoundTrips() {
        assertEquals(0, LibraryCodec.fromBytes(LibraryCodec.toBytes(WorkshopLibrary())).size)
    }

    @Test fun everyCutOrFlippedFileIsRefused() {
        val bytes = LibraryCodec.toBytes(filled())
        val cases = listOf(ByteArray(0), bytes.copyOf(4), bytes.copyOf(bytes.size - 1), bytes.copyOf().also { it[0] = 0 },
            bytes.copyOf().also { it[bytes.size / 2] = (it[bytes.size / 2].toInt() xor 1).toByte() })
        for (bad in cases) {
            try { LibraryCodec.fromBytes(bad); fail("expected refusal") } catch (e: ByteReader.CorruptDataException) { /* expected */ }
        }
    }

    @Test fun aSmallerCapacityRefusesALargerFile() {
        val bytes = LibraryCodec.toBytes(filled())
        try { LibraryCodec.fromBytes(bytes, capacity = 1); fail("expected refusal") } catch (e: ByteReader.CorruptDataException) { /* expected */ }
    }

    @Test fun theStoreSavesAndLoads() {
        val mem = MemorySaveStore()
        val store = LibraryStore(mem)
        assertEquals(0, store.load().size)
        store.save(filled())
        assertTrue(mem.contains(LibraryStore.KEY))
        assertEquals(2, LibraryStore(mem).load().size)
    }

    @Test fun aDamagedFileLoadsAsEmptyAndIsReported() {
        val mem = MemorySaveStore()
        mem.put(LibraryStore.KEY, byteArrayOf(1, 2, 3))
        val store = LibraryStore(mem)
        assertTrue(store.damaged())
        assertEquals(0, store.load().size)
        assertTrue(mem.contains(LibraryStore.KEY))
        store.save(filled())
        assertFalse(store.damaged())
    }

    @Test fun resettingRemovesTheFile() {
        val mem = MemorySaveStore()
        val store = LibraryStore(mem)
        store.save(filled())
        store.reset()
        assertFalse(mem.contains(LibraryStore.KEY))
        assertFalse(store.damaged())
    }

    @Test fun separateKeysKeepSeparateShelves() {
        val mem = MemorySaveStore()
        LibraryStore(mem, "workshop.a").save(filled())
        assertEquals(0, LibraryStore(mem, "workshop.b").load().size)
        assertEquals(2, LibraryStore(mem, "workshop.a").load().size)
    }
}
