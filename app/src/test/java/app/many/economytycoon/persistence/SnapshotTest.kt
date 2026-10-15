package app.many.economytycoon.persistence

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotTest {
    private fun fresh(ticks: Int, seed: Long = 11L): Simulation = SimTestSupport.sim(seed, 3).also { it.advance(ticks) }

    private fun fails(block: () -> Unit): Boolean =
        try { block(); false } catch (e: ByteReader.CorruptDataException) { true }

    @Test fun aSavedGameLoadsToTheSameState() {
        val sim = fresh(40)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
        assertEquals(sim.tick, copy.tick)
        assertEquals(sim.commandLog().size, copy.commandLog().size)
    }

    @Test fun continuingALoadedGameMatchesPlayingStraightThrough() {
        val straight = fresh(60)
        val loaded = SimulationSnapshot.load(SimulationSnapshot.save(fresh(60)), SimTestSupport.content)
        for (i in 0 until 60) {
            straight.advance(); loaded.advance()
            assertEquals("tick ${straight.tick.value}", straight.hash(), loaded.hash())
        }
    }

    @Test fun headerReportsTheGameWithoutRebuildingIt() {
        val sim = fresh(25, seed = 5L)
        val info = SimulationSnapshot.peek(SimulationSnapshot.save(sim))
        assertEquals(5L, info.seed)
        assertEquals(25L, info.tick)
        assertEquals(sim.hash(), info.stateHash)
        assertTrue(info.contentVersions.isNotEmpty())
    }

    @Test fun anyFlippedByteIsDetected() {
        val bytes = SimulationSnapshot.save(fresh(5))
        for (at in listOf(0, 9, bytes.size / 3, bytes.size / 2, bytes.size - 20, bytes.size - 1)) {
            val damaged = bytes.copyOf(); damaged[at] = (damaged[at].toInt() xor 0x5A).toByte()
            assertTrue("byte $at", fails { SimulationSnapshot.load(damaged, SimTestSupport.content) })
        }
    }

    @Test fun truncatedFilesAreRejected() {
        val bytes = SimulationSnapshot.save(fresh(5))
        assertTrue(fails { SimulationSnapshot.load(bytes.copyOf(bytes.size - 40), SimTestSupport.content) })
        assertTrue(fails { SimulationSnapshot.load(ByteArray(0), SimTestSupport.content) })
    }

    @Test fun commandsWaitingForTheNextTickSurviveASave() {
        val sim = fresh(10)
        val company = sim.economy.companies.all().first()
        sim.submit(app.many.economytycoon.simulation.Actor.Ai(company.id), app.many.economytycoon.simulation.Command.Rename(company.id, "Renamed Holdings"))
        val loaded = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(1, loaded.pendingCommands())
        sim.advance(); loaded.advance()
        assertEquals(sim.hash(), loaded.hash())
    }
}

class SnapshotMigrationTest {
    @Test fun olderSnapshotsGoThroughTheRegisteredSteps() {
        val m = Migrations(mapOf(1 to { s: List<Section> -> s + Section("added", ByteArray(1)) }, 2 to { s: List<Section> -> s.filter { it.name != "added" } }))
        val out = m.upgrade(listOf(Section("a", ByteArray(0))), 1, 3)
        assertEquals(listOf("a"), out.map { it.name })
        assertTrue(m.canUpgrade(1, 3))
        assertTrue(!m.canUpgrade(1, 4))
    }

    @Test fun newerSnapshotsAndMissingStepsAreRefused() {
        var refused = 0
        try { Migrations().upgrade(emptyList(), 3, 2) } catch (e: ByteReader.CorruptDataException) { refused++ }
        try { Migrations().upgrade(emptyList(), 1, 2) } catch (e: ByteReader.CorruptDataException) { refused++ }
        assertEquals(2, refused)
    }

    @Test fun contentMismatchIsReportedDistinctly() {
        val bytes = SimulationSnapshot.save(SimTestSupport.sim(2L, 2))
        var seen = false
        try { SimulationSnapshot.load(bytes, SimTestSupport.contentWithShed) } catch (e: IncompatibleContentException) { seen = true }
        assertTrue(seen)
    }
}
