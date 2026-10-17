package app.many.economytycoon.ip

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.EngineRig
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.c
import app.many.economytycoon.ip.IpTestSupport.eu
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.ovens
import app.many.economytycoon.ip.IpTestSupport.efficiency
import app.many.economytycoon.ip.IpTestSupport.us
import app.many.economytycoon.ip.IpTestSupport.usage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class IpCodecTest {
    private fun roundTrip(s: IpState): IpState = IpCodec.decode(IpCodec.encode(s))

    @Test fun emptyStateRoundTrips() {
        val s = IpState(42L)
        val back = roundTrip(s)
        assertEquals(42L, back.worldSeed)
        assertEquals(s.stateHash(), back.stateHash())
        assertArrayEquals(IpCodec.encode(s), IpCodec.encode(back))
    }

    @Test fun busyStateRoundTripsExactly() {
        val r = IpTestSupport.busyWorld()
        val s = r.engine.state
        val back = roundTrip(s)
        assertEquals(s.stateHash(), back.stateHash())
        assertArrayEquals(IpCodec.encode(s), IpCodec.encode(back))
        assertEquals(s.patentMap.size, back.patentMap.size)
        assertEquals(s.licenseMap.size, back.licenseMap.size)
        assertEquals(s.disputeMap.size, back.disputeMap.size)
        assertEquals(s.npeMap.size, back.npeMap.size)
        assertEquals(s.designAroundMap.size, back.designAroundMap.size)
        assertEquals(s.compulsoryMap.size, back.compulsoryMap.size)
        assertEquals(s.clearanceSet, back.clearanceSet)
        assertEquals(s.policyMap, back.policyMap)
        assertEquals(s.creditYearMap, back.creditYearMap)
    }

    @Test fun worldIsReallyBusy() {
        val s = IpTestSupport.busyWorld().engine.state
        assertTrue(s.patentMap.size >= 3)
        assertTrue(s.secretMap.isNotEmpty() && s.licenseMap.size >= 2 && s.disputeMap.isNotEmpty())
        assertTrue(s.designAroundMap.isNotEmpty() && s.compulsoryMap.isNotEmpty() && s.npeMap.size == 3 && s.clearanceSet.isNotEmpty())
        assertTrue(s.patentMap.values.any { it.isListed })
        assertTrue(s.patentMap.values.any { it.standardEssential })
    }

    @Test fun derivedIndexesAreRebuilt() {
        val r = IpTestSupport.busyWorld()
        val p = r.granted(a, ovens)
        val q = r.granted(b, milling)
        val cited = Patent(r.engine.state.ids.patent(), c, efficiency, "x", TechField.values()[0], null, Tick(1), 500_000L, 5, 400_000L, listOf(p.id, q.id), Money.units(10), false, IpOwner.company(c))
        r.engine.state.patentMap[cited.id] = cited
        r.engine.state.rebuildIndexes()
        val back = roundTrip(r.engine.state)
        assertEquals(1, back.forwardCitations(p.id))
        assertEquals(1, back.forwardCitations(q.id))
    }

    @Test fun poolsRoundTrip() {
        val r = IpTestSupport.busyWorld()
        val pm = r.engine.state.patentsOf(a).first()
        val run = IpRun(Tick(r.day), r.ctx, Wallet(r.ctx), IpOutput(r.engine.state.ids))
        val pool = r.engine.pools.form(run, a, "Mill pool", pm.field, RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(2)), 100_000L).getOrThrow()
        r.engine.pools.join(run, pool.id, a, listOf(pm.id)).getOrThrow()
        val back = roundTrip(r.engine.state)
        val bp = back.poolMap.getValue(pool.id)
        assertEquals(listOf(pm.id), bp.allPatents())
        assertEquals(pool.name, bp.name)
        assertEquals(r.engine.state.stateHash(), back.stateHash())
    }

    @Test fun decodedStateContinuesIdenticallyToTheOriginal() {
        val r1 = IpTestSupport.busyWorld()
        val bytes = IpCodec.encode(r1.engine.state)
        val r2 = EngineRig(7L)
        r2.known.addAll(r1.known)
        r2.ctx.homes.putAll(r1.ctx.homes); r2.ctx.rdSpend.putAll(r1.ctx.rdSpend); r2.ctx.practicers.putAll(r1.ctx.practicers); r2.ctx.health.addAll(r1.ctx.health)
        r2.ctx.techsOf.putAll(r1.ctx.techsOf)
        val engine2 = IpEngine(7L, IpTestSupport.content, TechHolding { co, t -> (co to t) in r2.known }, state = IpCodec.decode(bytes))
        var day = r1.day
        val limit = day + 500
        while (day < limit) {
            day++
            val x = r1.engine.step(Tick(day), r1.ctx)
            val y = engine2.step(Tick(day), r2.ctx)
            assertEquals("events differ on day $day", x.events, y.events)
            assertEquals("charges differ on day $day", x.charges, y.charges)
        }
        assertEquals(r1.engine.stateHash(), engine2.stateHash())
    }

    @Test fun hashChangesWhenAnythingChanges() {
        val r = IpTestSupport.busyWorld()
        val before = r.hash()
        r.engine.state.patentMap.values.first().let { p -> p.filingMap.values.first().feesPaid += Money(1) }
        assertNotEquals(before, r.hash())
    }

    @Test fun hashIsStableAcrossRepeatedCalls() {
        val r = IpTestSupport.busyWorld()
        assertEquals(r.hash(), r.hash())
    }

    @Test fun twoIdenticalWorldsHaveIdenticalBytes() {
        assertArrayEquals(IpCodec.encode(IpTestSupport.busyWorld().engine.state), IpCodec.encode(IpTestSupport.busyWorld().engine.state))
    }

    @Test fun differentSeedsGiveDifferentBytes() {
        assertFalse(IpCodec.encode(IpState(1)).contentEquals(IpCodec.encode(IpState(2))))
    }

    @Test fun firstByteIsTheVersion() {
        assertEquals(IpCodec.VERSION, IpCodec.encode(IpState(1))[0].toInt())
    }

    @Test fun unknownVersionIsRejected() {
        val bytes = IpCodec.encode(IpState(1))
        bytes[0] = 99
        try { IpCodec.decode(bytes); fail("expected a corrupt data error") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("version")) }
    }

    @Test fun everyTruncationIsRejectedCleanly() {
        val bytes = IpCodec.encode(IpTestSupport.busyWorld().engine.state)
        var checked = 0
        var cut = 0
        while (cut < bytes.size) {
            try { IpCodec.decode(bytes.copyOf(cut)); fail("accepted a truncated save at $cut") } catch (e: ByteReader.CorruptDataException) { checked++ }
            cut += maxOf(1, bytes.size / 60)
        }
        assertTrue(checked > 30)
    }

    @Test fun badEnumIndexIsRejected() {
        val w = ByteWriter()
        w.writeByte(IpCodec.VERSION); w.writeVarLong(1); w.writeVarInt(99)
        try { IpCodec.decode(w.toByteArray()); fail("expected failure") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("regime")) }
    }

    @Test fun invalidIdCounterIsRejected() {
        val w = ByteWriter()
        w.writeByte(IpCodec.VERSION); w.writeVarLong(1); w.writeVarInt(0); w.writeVarInt(0)
        w.writeVarInt(1); w.writeString("patent"); w.writeVarLong(0)
        try { IpCodec.decode(w.toByteArray()); fail("expected failure") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("counter")) }
    }

    @Test fun outOfRangePolicyIsRejected() {
        val w = ByteWriter()
        IpCodec.writePolicy(IpPolicy(), w)
        val ok = IpCodec.readPolicy(ByteReader(w.toByteArray()))
        assertEquals(IpPolicy(), ok)
        val bad = ByteWriter()
        bad.writeVarInt(0); bad.writeVarLong(5_000_000L); bad.writeVarInt(0); bad.writeBoolean(true); bad.writeVarInt(0)
        try { IpCodec.readPolicy(ByteReader(bad.toByteArray())); fail("expected failure") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("monitoring")) }
    }

    @Test fun idsKeepCountingAfterALoad() {
        val r = IpTestSupport.busyWorld()
        val back = roundTrip(r.engine.state)
        assertEquals(r.engine.state.ids.patent().value, back.ids.patent().value)
        assertEquals(r.engine.state.ids.charge(), back.ids.charge())
    }
}
