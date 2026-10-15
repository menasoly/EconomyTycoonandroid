package app.many.economytycoon.persistence

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.grants.GrantCodec
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GrantSnapshotTest {
    @Test fun programmesAndApplicationsSurviveASave() {
        val sim = SimTestSupport.sim(77, ai = 5)
        sim.advance(220)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
        val a = sim.economy.grants.all(); val b = copy.economy.grants.all()
        assertTrue(a.isNotEmpty())
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(a.map { it.status }, b.map { it.status })
        assertEquals(a.map { it.paidTotal }, b.map { it.paidTotal })
        assertEquals(a.map { g -> g.applications.map { it.company to it.baseline } }, b.map { g -> g.applications.map { it.company to it.baseline } })
    }

    @Test fun aLoadedGameKeepsRunningTheSameGrants() {
        val sim = SimTestSupport.sim(78, ai = 5)
        sim.advance(60)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        for (i in 0 until 140) { sim.advance(); copy.advance(); assertEquals("tick ${sim.tick.value}", sim.hash(), copy.hash()) }
        assertEquals(sim.economy.grants.all().size, copy.economy.grants.all().size)
    }

    @Test fun codecRoundTripKeepsEveryField() {
        val sim = SimTestSupport.sim(79, ai = 5)
        sim.advance(250)
        val w = ByteWriter()
        GrantCodec.write(sim.economy.grants, w)
        val other = SimTestSupport.sim(79, ai = 5)
        GrantCodec.read(ByteReader(w.toByteArray()), other.economy.grants)
        val a = sim.economy.grants.all(); val b = other.economy.grants.all()
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i].kind, b[i].kind); assertEquals(a[i].family, b[i].family); assertEquals(a[i].rate, b[i].rate)
            assertEquals(a[i].endsAt, b[i].endsAt); assertEquals(a[i].endedAt, b[i].endedAt)
            assertEquals(a[i].applications.map { it.paid }, b[i].applications.map { it.paid })
            assertEquals(a[i].applications.map { it.settled }, b[i].applications.map { it.settled })
        }
    }

    @Test fun anUnknownVersionIsRefused() {
        val w = ByteWriter(); w.writeVarInt(99)
        try { GrantCodec.read(ByteReader(w.toByteArray()), SimTestSupport.sim(1, 0).economy.grants); fail("accepted a future version") }
        catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("version")) }
    }

    @Test fun anUnknownKindIsRefused() {
        val w = ByteWriter()
        w.writeVarInt(GrantCodec.VERSION); w.writeVarInt(1)
        w.writeVarLong(1); w.writeVarLong(1); w.writeVarInt(42)
        try { GrantCodec.read(ByteReader(w.toByteArray()), SimTestSupport.sim(1, 0).economy.grants); fail("accepted a bad kind") }
        catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("grant kind")) }
    }
}
