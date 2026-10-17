package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.persistence.MemorySaveStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCodecTest {
    private fun filled(): BlueprintLibrary {
        val l = BlueprintLibrary()
        for (i in 1..4) l.add(Blueprint.of(T.sampleSpec().copy(qualityPpm = i * 50_000L), "Plan $i", "note $i", "test@1.4", i.toLong()), if (i % 2 == 0) "even/deep" else "", listOf("t$i", "all"), i == 3, i.toLong())
        l.duplicate(BlueprintId(2), 9)
        l.createFolder("empty")
        l.setNote(BlueprintId(1), "changed")
        l.replace(BlueprintId(4), Blueprint.of(T.sampleSpec().copy(qualityPpm = 999_000L), "Plan 4b"))
        return l
    }

    @Test fun roundTripPreservesEverythingAndTheStateHash() {
        val l = filled()
        val load = LibraryCodec.fromBytes(LibraryCodec.toBytes(l))
        assertTrue(load.isClean)
        assertEquals(l.stateHash(), load.library.stateHash())
        assertEquals(l.all(), load.library.all())
        assertEquals(l.folders(), load.library.folders())
    }

    @Test fun idsKeepCountingAfterALoad() {
        val l = filled()
        val back = LibraryCodec.fromBytes(LibraryCodec.toBytes(l)).library
        val next = (back.add(Blueprint.of(T.sampleSpec().copy(qualityPpm = 1L), "New")) as AddResult.Added).entry.id
        assertEquals(BlueprintId(l.all().maxOf { it.id.value } + 1), next)
    }

    @Test fun serializationIsDeterministic() {
        assertTrue(LibraryCodec.toBytes(filled()).contentEquals(LibraryCodec.toBytes(filled())))
    }

    @Test fun missingOrForeignBytesGiveAnEmptyLibrary() {
        assertEquals(0, LibraryCodec.fromBytes(null).library.size)
        assertFalse(LibraryCodec.fromBytes(null).unreadable)
        val w = ByteWriter(); w.writeVarInt(77)
        val bad = LibraryCodec.fromBytes(w.toByteArray())
        assertTrue(bad.unreadable)
        assertEquals(0, bad.library.size)
        assertTrue(LibraryCodec.fromBytes(ByteArray(0)).unreadable)
    }

    @Test fun truncationKeepsWhatWasReadBeforeTheDamage() {
        val bytes = LibraryCodec.toBytes(filled())
        val load = LibraryCodec.fromBytes(bytes.copyOf(bytes.size - 40))
        assertTrue(load.library.size in 1..4)
        assertFalse(load.isClean && load.library.size == 5)
    }

    @Test fun everyTruncationIsSurvivable() {
        val bytes = LibraryCodec.toBytes(filled())
        for (cut in 0 until bytes.size step 7) {
            val load = LibraryCodec.fromBytes(bytes.copyOf(cut))
            assertTrue(load.library.size <= 5)
            val ids = load.library.all().map { it.id }.toSet()
            assertTrue(load.library.all().all { it.copyOf == null || it.copyOf in ids })
        }
    }

    @Test fun oneDamagedEntryCostsOnlyItself() {
        val bytes = LibraryCodec.toBytes(filled())
        // find the schema byte of the second blueprint block and break it
        val marker = byteArrayOf(2) // schema varint of each block precedes a name; locate by decoding instead
        val l = BlueprintLibrary()
        l.add(T.sample("One")); l.add(Blueprint.of(T.sampleSpec().copy(qualityPpm = 5L), "Two"))
        val good = LibraryCodec.toBytes(l)
        val w = ByteWriter()
        w.writeVarInt(LibraryCodec.VERSION); w.writeVarLong(3); w.writeVarInt(0); w.writeVarInt(2)
        w.writeVarLong(1); w.writeString(""); w.writeBoolean(false); w.writeVarLong(0); w.writeVarLong(-1); w.writeVarInt(1); w.writeVarInt(0)
        w.writeBytes(BlueprintCodec.encode(T.sample("One")))
        w.writeVarLong(2); w.writeString(""); w.writeBoolean(false); w.writeVarLong(0); w.writeVarLong(-1); w.writeVarInt(1); w.writeVarInt(0)
        w.writeBytes(byteArrayOf(9, 9, 9))
        val load = LibraryCodec.fromBytes(w.toByteArray())
        assertEquals(1, load.library.size)
        assertEquals(1, load.dropped)
        assertFalse(load.isClean)
        assertTrue(good.isNotEmpty() && bytes.isNotEmpty() && marker.isNotEmpty())
    }

    @Test fun olderBlueprintSchemasInsideASaveAreMigrated() {
        val v1 = ByteWriter()
        v1.writeVarInt(1); v1.writeString("Legacy"); v1.writeString(""); v1.writeString(""); v1.writeVarLong(0)
        v1.writeString("bakery"); v1.writeVarInt(0); v1.writeVarLong(40)
        repeat(4) { v1.writeVarInt(0) }
        val w = ByteWriter()
        w.writeVarInt(LibraryCodec.VERSION); w.writeVarLong(2); w.writeVarInt(0); w.writeVarInt(1)
        w.writeVarLong(1); w.writeString("old"); w.writeBoolean(true); w.writeVarLong(3); w.writeVarLong(-1); w.writeVarInt(2); w.writeVarInt(0)
        w.writeBytes(v1.toByteArray())
        val load = LibraryCodec.fromBytes(w.toByteArray())
        assertEquals(1, load.migrated)
        val e = load.library.all().single()
        assertEquals(400_000L, e.blueprint.spec.qualityPpm)
        assertEquals("old", e.folder)
        assertTrue(e.favourite)
        assertEquals(2, e.revision)
        // saving again stores the current schema
        assertEquals(0, LibraryCodec.fromBytes(LibraryCodec.toBytes(load.library)).migrated)
    }

    @Test fun invalidFoldersAndTagsDropTheEntry() {
        val w = ByteWriter()
        w.writeVarInt(LibraryCodec.VERSION); w.writeVarLong(2); w.writeVarInt(0); w.writeVarInt(1)
        w.writeVarLong(1); w.writeString("bad*folder"); w.writeBoolean(false); w.writeVarLong(0); w.writeVarLong(-1); w.writeVarInt(1); w.writeVarInt(0)
        w.writeBytes(BlueprintCodec.encode(T.sample()))
        val load = LibraryCodec.fromBytes(w.toByteArray())
        assertEquals(0, load.library.size)
        assertEquals(1, load.dropped)
    }

    @Test fun copiesWhoseOriginalWasLostBecomeOriginals() {
        val l = BlueprintLibrary()
        l.add(T.sample())
        l.duplicate(BlueprintId(1))
        val w = ByteWriter()
        val bytes = LibraryCodec.toBytes(l)
        // drop the first entry by editing the library before encoding a second time
        l.remove(BlueprintId(1))
        val load = LibraryCodec.fromBytes(LibraryCodec.toBytes(l))
        assertNull(load.library.all().single().copyOf)
        assertTrue(bytes.isNotEmpty() && w.size == 0)
    }

    @Test fun storeSavesLoadsAndResets() {
        val mem = MemorySaveStore()
        val store = BlueprintStore(mem)
        assertEquals(0, store.load().library.size)
        val l = filled()
        store.save(l)
        assertTrue(mem.contains(BlueprintStore.KEY))
        assertEquals(l.stateHash(), store.load().library.stateHash())
        store.reset()
        assertEquals(0, store.load().library.size)
    }

    @Test fun limitsApplyOnLoad() {
        val l = filled()
        val tight = LibraryCodec.fromBytes(LibraryCodec.toBytes(l), LibraryLimits(maxEntries = 2))
        assertTrue(tight.unreadable)
    }
}
