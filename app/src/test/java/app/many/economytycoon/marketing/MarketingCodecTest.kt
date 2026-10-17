package app.many.economytycoon.marketing

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.MarketingTestSupport.audiences
import app.many.economytycoon.marketing.MarketingTestSupport.run
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MarketingCodecTest {
    private fun save(s: MarketingState): ByteArray = ByteWriter().also { MarketingCodec.write(s, it) }.toByteArray()

    private fun load(bytes: ByteArray): MarketingState = MarketingState().also { MarketingCodec.read(ByteReader(bytes), it) }

    @Test fun anEmptyStateRoundTrips() {
        val s = MarketingState()
        val back = load(save(s))
        assertTrue(back.isEmpty())
        assertEquals(s.stateHash(), back.stateHash())
    }

    @Test fun aBusyStateRoundTripsExactly() {
        val sys = MarketingTestSupport.busy()
        val back = load(save(sys.state))
        assertEquals(sys.state.stateHash(), back.stateHash())
        assertEquals(sys.state.campaigns().size, back.campaigns().size)
        assertEquals(sys.state.stocks().size, back.stocks().size)
        assertEquals(sys.state.promotions().size, back.promotions().size)
        assertEquals(sys.state.tests().size, back.tests().size)
        assertEquals(sys.state.damages(), back.damages())
    }

    @Test fun savingTwiceGivesTheSameBytes() {
        val sys = MarketingTestSupport.busy()
        assertArrayEquals(save(sys.state), save(sys.state))
        assertArrayEquals(save(sys.state), save(load(save(sys.state))))
    }

    @Test fun everyCampaignFieldSurvives() {
        val sys = MarketingTestSupport.busy()
        val back = load(save(sys.state))
        for (c in sys.state.campaigns()) {
            val r = back.campaign(c.id)!!
            assertEquals(c.status, r.status)
            assertEquals(c.dailyBudget, r.dailyBudget)
            assertEquals(c.prepaid, r.prepaid)
            assertEquals(c.spentTotal, r.spentTotal)
            assertEquals(c.refundedTotal, r.refundedTotal)
            assertEquals(c.feesTotal, r.feesTotal)
            assertEquals(c.daysRun, r.daysRun)
            assertEquals(c.daysRemaining, r.daysRemaining)
            assertEquals(c.city, r.city)
            assertEquals(c.segment, r.segment)
            assertEquals(c.creative.qualityPpm, r.creative.qualityPpm)
            assertEquals(c.creative.freshnessPpm, r.creative.freshnessPpm)
            assertEquals(c.creative.lastRefreshed, r.creative.lastRefreshed)
            assertEquals(c.endedAt, r.endedAt)
            assertEquals(c.impressions, r.impressions)
        }
    }

    @Test fun brandStocksIncludingExposureSurvive() {
        val sys = MarketingTestSupport.busy()
        val back = load(save(sys.state))
        for ((k, st) in sys.state.stocks()) {
            val r = back.stock(k)!!
            assertEquals(st.awarenessPpm, r.awarenessPpm)
            assertEquals(st.loyaltyPpm, r.loyaltyPpm)
            assertEquals(st.taintPpm, r.taintPpm)
            assertArrayEquals(st.adstockSnapshot(), r.adstockSnapshot())
        }
    }

    @Test fun idCountersSurviveSoIdsAreNeverReused() {
        val sys = MarketingTestSupport.busy()
        val back = load(save(sys.state))
        assertEquals(sys.state.ids.snapshot().toList(), back.ids.snapshot().toList())
        assertEquals(CampaignId(sys.state.ids.snapshot()[0]), back.ids.campaign())
    }

    @Test fun aLoadedStateKeepsRunningIdenticallyToTheOriginal() {
        val original = MarketingTestSupport.busy(days = 20)
        val copy = MarketingSystem(worldSeed = 7L, state = load(save(original.state)))
        run(original, 20, 40)
        for (d in 20 until 60) copy.engine.advance(Tick(d.toLong()), audiences())
        assertEquals(original.stateHash(), copy.stateHash())
    }

    @Test fun readingReplacesWhatWasThere() {
        val a = MarketingTestSupport.busy()
        val b = MarketingState()
        MarketingCodec.read(ByteReader(save(a.state)), b)
        MarketingCodec.read(ByteReader(save(MarketingState())), b)
        assertTrue(b.isEmpty())
    }

    @Test fun anUnknownVersionIsRefused() {
        val w = ByteWriter(); w.writeVarInt(MarketingCodec.VERSION + 1)
        try { load(w.toByteArray()); fail("expected a refusal") } catch (e: ByteReader.CorruptDataException) {
            assertTrue(e.message!!.contains("version"))
        }
    }

    @Test fun truncatedDataIsRefused() {
        val bytes = save(MarketingTestSupport.busy().state)
        try { load(bytes.copyOf(bytes.size / 2)); fail("expected a refusal") } catch (e: ByteReader.CorruptDataException) { }
    }

    @Test fun anUnknownEnumValueIsRefused() {
        val w = ByteWriter()
        w.writeVarInt(MarketingCodec.VERSION)
        w.writeVarInt(4); repeat(4) { w.writeVarLong(1) }
        w.writeVarInt(1)
        w.writeVarLong(1); w.writeVarLong(1); w.writeString("beer"); w.writeVarLong(1); w.writeBoolean(false)
        w.writeVarInt(99)
        try { load(w.toByteArray()); fail("expected a refusal") } catch (e: ByteReader.CorruptDataException) {
            assertTrue(e.message!!.contains("channel"))
        }
    }

    @Test fun aBadCounterBlockIsRefused() {
        val w = ByteWriter(); w.writeVarInt(MarketingCodec.VERSION); w.writeVarInt(2); w.writeVarLong(1); w.writeVarLong(1)
        try { load(w.toByteArray()); fail("expected a refusal") } catch (e: ByteReader.CorruptDataException) { }
    }

    @Test fun theFirstByteIsTheVersion() {
        assertEquals(MarketingCodec.VERSION, ByteReader(save(MarketingState())).readVarInt())
    }

    @Test fun savedSizeStaysCompact() {
        val bytes = save(MarketingTestSupport.busy().state)
        assertTrue("size ${bytes.size}", bytes.size < 4_000)
    }
}
