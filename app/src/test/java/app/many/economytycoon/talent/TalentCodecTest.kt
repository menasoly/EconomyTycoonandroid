package app.many.economytycoon.talent

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TalentCodecTest {

    private fun busy(days: Int = 400): Scenario {
        val sc = Scenario(21L)
        sc.run(20)
        sc.populate()
        sc.run(days)
        return sc
    }

    @Test fun anEmptyStateRoundTrips() {
        val s = TalentState()
        val back = TalentCodec.decode(TalentCodec.encode(s))
        assertEquals(s.stateHash(), back.stateHash())
    }

    @Test fun aBusyStateRoundTripsExactly() {
        val sc = busy()
        val bytes = TalentCodec.encode(sc.system.state)
        val back = TalentCodec.decode(bytes)
        assertEquals(sc.system.state.stateHash(), back.stateHash())
        assertArrayEquals(bytes, TalentCodec.encode(back))
    }

    @Test fun theBusyStateReallyHoldsEverything() {
        val s = busy(50).system.state
        assertTrue(s.institutions.isNotEmpty() && s.programmes.isNotEmpty() && s.providers.isNotEmpty())
        assertTrue(s.people.isNotEmpty() && s.positions.isNotEmpty() && s.successors.isNotEmpty())
        assertTrue(s.offers.isNotEmpty() && s.covenants.isNotEmpty() && s.counterPolicies.isNotEmpty() && s.retention.isNotEmpty())
        assertTrue(s.campaigns.isNotEmpty() && s.ladders.isNotEmpty() && s.brands.isNotEmpty())
        assertTrue("pools=${s.pools.size} markets=${s.markets.size} skills=${s.cohortSkills.size} policies=${s.policies.size}", s.pools.isNotEmpty() && s.markets.isNotEmpty() && s.cohortSkills.isNotEmpty() && s.policies.isNotEmpty())
        assertTrue(s.cooldowns.isNotEmpty())
    }

    @Test fun encodingTwiceGivesTheSameBytes() {
        val s = busy(100).system.state
        assertArrayEquals(TalentCodec.encode(s), TalentCodec.encode(s))
    }

    @Test fun aReloadedGameContinuesExactlyLikeTheOriginal() {
        val a = busy(200)
        val saved = TalentCodec.encode(a.system.state)
        val b = Scenario(21L)
        b.day = a.day
        for ((k, r) in a.workforce.copy().rows) b.workforce.rows[k] = r
        b.workforce.cash.putAll(a.workforce.cash)
        val loaded = TalentSystem(TalentCodec.decode(saved), b.taxonomy, 21L)
        val twin = Scenario(21L)
        twin.day = a.day
        for ((k, r) in a.workforce.copy().rows) twin.workforce.rows[k] = r
        twin.workforce.cash.putAll(a.workforce.cash)
        repeat(250) {
            val ra = a.step()
            val inputs = b.workforce.inputs(b.day)
            val rl = loaded.advance(Tick(b.day), b.world, inputs)
            b.workforce.apply(rl.effects); b.workforce.day(); b.day += 1L
            assertEquals(ra.events.size, rl.events.size)
            assertEquals(ra.effects, rl.effects)
        }
        assertEquals(a.system.stateHash(), loaded.stateHash())
        assertTrue(twin.day == a.day - 250L)
    }

    @Test fun unknownVersionsAreRefused() {
        val w = ByteWriter(); w.writeVarInt(99)
        try { TalentCodec.decode(w.toByteArray()); fail("expected a failure") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("99")) }
    }

    @Test fun truncatedDataIsRefused() {
        val bytes = TalentCodec.encode(busy(60).system.state)
        for (cut in listOf(1, bytes.size / 3, bytes.size - 1)) {
            try { TalentCodec.decode(bytes.copyOf(cut)); fail("cut at $cut should fail") } catch (e: ByteReader.CorruptDataException) { /* expected */ }
        }
    }

    @Test fun readingReplacesWhateverWasThere() {
        val full = busy(60).system.state
        val target = TalentCodec.decode(TalentCodec.encode(full))
        TalentCodec.read(ByteReader(TalentCodec.encode(TalentState())), target)
        assertEquals(TalentState().stateHash(), target.stateHash())
    }

    @Test fun savedStatesStaySound() {
        val sc = busy(300)
        val back = TalentCodec.decode(TalentCodec.encode(sc.system.state))
        assertEquals(emptyList<String>(), TalentInvariants.violations(back, sc.taxonomy, null, Tick(sc.day)))
    }
}
