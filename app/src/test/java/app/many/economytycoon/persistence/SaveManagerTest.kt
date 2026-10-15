package app.many.economytycoon.persistence

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SaveManagerTest {
    private var now = 1_000L
    private fun manager(store: SaveStore = MemorySaveStore()) = SaveManager(store, { now++ })
    private fun sim(ticks: Int = 5) = SimTestSupport.sim(3L, 2).also { it.advance(ticks) }

    @Test fun savingAndLoadingASlotRestoresTheGame() {
        val m = manager(); val s = sim()
        val slot = m.save("first", s, "  My first game  ")
        assertEquals("My first game", slot.label)
        assertEquals(5L, slot.tick)
        val loaded = m.load("first", SimTestSupport.content)
        assertEquals(s.hash(), loaded.simulation.hash())
        assertFalse(loaded.recoveredFromBackup)
    }

    @Test fun listingShowsNewestFirstWithLabels() {
        val m = manager()
        m.save("a", sim(2), "Alpha"); m.save("b", sim(4), "Beta")
        assertEquals(listOf("b", "a"), m.list().map { it.id })
        assertEquals(listOf(4L, 2L), m.list().map { it.tick })
    }

    @Test fun overwritingKeepsTheOldFileAsBackup() {
        val m = manager()
        m.save("x", sim(2), "One")
        val second = m.save("x", sim(3), "Two")
        assertTrue(second.hasBackup)
        assertEquals(3L, m.list().single().tick)
    }

    @Test fun aDamagedSaveFallsBackToItsBackup() {
        val store = MemorySaveStore(); val m = manager(store)
        val old = sim(2)
        m.save("x", old, "Old"); m.save("x", sim(4), "New")
        val bytes = store.get("x.etsave")!!; bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x33).toByte()
        store.put("x.etsave", bytes)
        assertTrue(m.list().single().damaged)
        val loaded = m.load("x", SimTestSupport.content)
        assertTrue(loaded.recoveredFromBackup)
        assertEquals(old.hash(), loaded.simulation.hash())
    }

    @Test fun withoutAUsableBackupTheErrorIsReported() {
        val store = MemorySaveStore(); val m = manager(store)
        m.save("x", sim(2), "Only")
        store.put("x.etsave", ByteArray(40))
        var failed = false
        try { m.load("x", SimTestSupport.content) } catch (e: ByteReader.CorruptDataException) { failed = true }
        assertTrue(failed)
    }

    @Test fun missingSlotsAreReported() {
        var missing = false
        try { manager().load("nothing", SimTestSupport.content) } catch (e: SaveNotFoundException) { missing = true }
        assertTrue(missing)
    }

    @Test fun autosavesAreSpacedOutAndRotate() {
        val m = SaveManager(MemorySaveStore(), { now++ }, autosaveSlots = 2, autosaveEveryTicks = 10)
        val s = sim(0)
        assertNotNull(m.autosaveIfDue(s))
        s.advance(5)
        assertNull(m.autosaveIfDue(s))
        s.advance(5); assertNotNull(m.autosaveIfDue(s))
        s.advance(10); val third = m.autosaveIfDue(s)!!
        assertEquals(setOf("auto-0", "auto-1"), m.list().map { it.id }.toSet())
        assertEquals(10L + 10L, third.tick)
        assertEquals(20L, m.list().maxOf { it.tick })
        assertTrue(m.list().all { it.kind == SlotKind.AUTO })
    }

    @Test fun deleteRemovesEveryFileOfTheSlot() {
        val store = MemorySaveStore(); val m = manager(store)
        m.save("x", sim(1), "A"); m.save("x", sim(2), "B")
        m.delete("x")
        assertTrue(store.keys().isEmpty())
    }

    @Test fun renamingChangesOnlyTheLabel() {
        val m = manager()
        m.save("x", sim(2), "Before")
        m.rename("x", "After")
        assertEquals("After", m.list().single().label)
        assertEquals(2L, m.list().single().tick)
    }

    @Test fun slotNamesCannotEscapeTheStore() {
        var rejected = 0
        for (bad in listOf("../x", "a/b", "", ".hidden", "x y")) try { manager().save(bad, sim(0), "x") } catch (e: IllegalArgumentException) { rejected++ }
        assertEquals(5, rejected)
    }

    @Test fun fileStoreWritesAtomicallyAndRoundTrips() {
        val dir = File.createTempFile("ets", "").let { it.delete(); it }
        try {
            val store = FileSaveStore(dir); val m = manager(store)
            val s = sim(3)
            m.save("disk", s, "On disk"); m.save("disk", s, "On disk again")
            assertTrue(File(dir, "disk.etsave.bak").isFile)
            assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
            assertEquals(s.hash(), manager(FileSaveStore(dir)).load("disk", SimTestSupport.content).simulation.hash())
            File(dir, "stale.etsave.tmp").writeText("x")
            assertEquals(1, store.cleanUp())
        } finally { dir.deleteRecursively() }
    }
}
