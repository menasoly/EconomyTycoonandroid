package app.many.economytycoon.energygrid

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyGridCodecTest {
    private val world by lazy { richWorld() }

    @Test fun anEmptyStateRoundTrips() {
        val s = EnergyGridState()
        val back = EnergyGridCodec.fromBytes(EnergyGridCodec.toBytes(s))
        assertTrue(back.isEmpty)
        assertEquals(s.stateHash(), back.stateHash())
    }

    @Test fun aBusyStateRoundTripsWithTheSameHash() {
        val back = EnergyGridCodec.fromBytes(EnergyGridCodec.toBytes(world.state))
        assertEquals(world.state.stateHash(), back.stateHash())
        GridInvariants.check(back)
    }

    @Test fun reEncodingGivesTheSameBytes() {
        val once = EnergyGridCodec.toBytes(world.state)
        val twice = EnergyGridCodec.toBytes(EnergyGridCodec.fromBytes(once))
        assertArrayEquals(once, twice)
    }

    @Test fun everyKindOfRecordSurvives() {
        val back = EnergyGridCodec.fromBytes(EnergyGridCodec.toBytes(world.state))
        assertEquals(world.state.grids.size, back.grids.size)
        assertEquals(world.state.links.size, back.links.size)
        assertEquals(world.state.ppas.size, back.ppas.size)
        for (g in world.state.grids) {
            val b = back.grid(g.id) ?: error("grid missing")
            assertEquals(g.units.size, b.units.size)
            assertEquals(g.loads.size, b.loads.size)
            assertEquals(g.history, b.history)
            assertEquals(g.lastDay, b.lastDay)
            assertEquals(g.blackouts.size, b.blackouts.size)
            assertEquals(g.outages, b.outages)
            assertEquals(g.capacityRound, b.capacityRound)
            assertEquals(g.tariff, b.tariff)
            assertEquals(g.params, b.params)
            assertEquals(g.windCarryPpm, b.windCarryPpm)
        }
        assertTrue(world.grid.history.size >= 45)
        assertTrue(world.grid.units.any { it.tech == Technology.STORAGE })
    }

    @Test fun countersSurviveSoIdsAreNeverReused() {
        val back = EnergyGridCodec.fromBytes(EnergyGridCodec.toBytes(world.state))
        val c = world.state.counters
        assertEquals(c.nextUnit, back.counters.nextUnit)
        assertEquals(c.nextPpa, back.counters.nextPpa)
        assertEquals(c.nextBlackout, back.counters.nextBlackout)
        assertEquals(c.nextGrid, back.counters.nextGrid)
        val svc = EnergyGridService(back)
        val u = svc.addUnit(world.grid.id, Technology.GAS, 1_000L, null, app.many.economytycoon.core.Tick(100)).getOrThrow()
        assertEquals(UnitId(c.nextUnit), u.id)
    }

    @Test fun readingReplacesWhatWasThere() {
        val target = richWorld(5).state
        EnergyGridCodec.read(ByteReader(EnergyGridCodec.toBytes(world.state)), target)
        assertEquals(world.state.stateHash(), target.stateHash())
    }

    @Test fun theFirstNumberIsTheVersion() {
        val bytes = EnergyGridCodec.toBytes(EnergyGridState())
        assertEquals(EnergyGridCodec.VERSION, ByteReader(bytes).readVarInt())
    }

    @Test(expected = ByteReader.CorruptDataException::class) fun anUnknownVersionIsRefused() {
        val w = ByteWriter(); w.writeVarInt(99)
        EnergyGridCodec.fromBytes(w.toByteArray())
    }

    @Test(expected = ByteReader.CorruptDataException::class) fun truncatedDataIsRefused() {
        val bytes = EnergyGridCodec.toBytes(world.state)
        EnergyGridCodec.fromBytes(bytes.copyOf(bytes.size / 2))
    }

    @Test(expected = ByteReader.CorruptDataException::class) fun aCorruptedGridRecordIsRefused() {
        val s = EnergyGridState()
        EnergyGridService(s).apply {
            addGrid(app.many.economytycoon.core.RegionId(1), app.many.economytycoon.core.CountryId(1), "G", app.many.economytycoon.world.ClimateZone.COLD)
        }
        val good = EnergyGridCodec.toBytes(s)
        // corrupt the climate zone byte of the only grid: version, 8 header numbers, grid count, id, region, country, name
        val bad = good.copyOf()
        val nameEnd = 1 + 1 + 1 + 6 + 1 + 1 + 1 + 1 + 1 + 1
        bad[nameEnd] = 40
        EnergyGridCodec.fromBytes(bad)
    }

    @Test fun invalidLinkTermsAreReportedAsCorruption() {
        val w = ByteWriter()
        w.writeVarInt(EnergyGridCodec.VERSION)
        repeat(2) { w.writeVarLong(-1L) }
        repeat(6) { w.writeVarLong(1L) }
        w.writeVarInt(0) // grids
        w.writeVarInt(1) // one link
        w.writeVarLong(1L); w.writeVarLong(1L); w.writeVarLong(1L) // a link from a grid to itself
        w.writeVarLong(100L); w.writeVarLong(0L); w.writeMoney(Money.ZERO)
        w.writeBoolean(true); repeat(3) { w.writeVarLong(0L) }; w.writeVarInt(0)
        val result = runCatching { EnergyGridCodec.fromBytes(w.toByteArray()) }
        assertTrue(result.exceptionOrNull() is ByteReader.CorruptDataException)
    }

    // ----- hashing -----

    @Test fun theHashIsStableAcrossRuns() {
        assertEquals(richWorld().state.stateHash(), world.state.stateHash())
    }

    @Test fun theHashChangesWhenAnythingChanges() {
        val base = world.state.stateHash()
        world.day()
        assertNotEquals(base, world.state.stateHash())
        val h2 = world.state.stateHash()
        world.service.setHouseholdCap(world.grid.id, Money.units(40))
        assertNotEquals(h2, world.state.stateHash())
        val h3 = world.state.stateHash()
        world.grid.units.first().capacityKw += 1L
        assertNotEquals(h3, world.state.stateHash())
    }

    @Test fun theSectionHashEqualsHashingTheSectionAlone() {
        val a = world.state.stateHash()
        val b = CanonicalHasher().also { world.state.hashInto(it) }.finish()
        assertEquals(a, b)
    }

    @Test fun twoStatesWithTheSameContentHashAlike() {
        val x = richWorld(20)
        val y = richWorld(20)
        assertEquals(x.state.stateHash(), y.state.stateHash())
        assertEquals(x.state.stateHash(), EnergyGridCodec.fromBytes(EnergyGridCodec.toBytes(y.state)).stateHash())
    }
}
