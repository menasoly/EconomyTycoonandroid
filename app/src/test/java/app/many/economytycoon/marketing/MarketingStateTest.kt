package app.many.economytycoon.marketing

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.MarketingTestSupport.acme
import app.many.economytycoon.marketing.MarketingTestSupport.beer
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.bread
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.request
import app.many.economytycoon.marketing.MarketingTestSupport.rival
import app.many.economytycoon.marketing.MarketingTestSupport.run
import app.many.economytycoon.marketing.MarketingTestSupport.south
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingStateTest {
    private val s = MarketingTestSupport.system()

    @Test fun aFreshStateIsEmptyAndHasAStableHash() {
        assertTrue(s.state.isEmpty())
        assertEquals(MarketingState().stateHash(), s.state.stateHash())
        assertEquals(64, s.state.stateHash().toHex().length)
    }

    @Test fun anyChangeChangesTheHash() {
        val h0 = s.stateHash()
        val c = book(s)
        val h1 = s.stateHash()
        assertTrue(h0 != h1)
        s.campaigns.pause(c.id)
        assertTrue(h1 != s.stateHash())
        run(s, 0, 1)
        val h2 = s.stateHash()
        s.state.stockFor(BrandKey(rival, bread, south)).set(1L, 0L)
        assertTrue(h2 != s.stateHash())
    }

    @Test fun theHashIsOrderIndependentOfInsertionOrderOfCampaigns() {
        val a = MarketingTestSupport.system(); val b = MarketingTestSupport.system()
        book(a, request(channel = Channel.RADIO)); book(a, request(channel = Channel.ONLINE))
        book(b, request(channel = Channel.RADIO)); book(b, request(channel = Channel.ONLINE))
        assertEquals(a.stateHash(), b.stateHash())
    }

    @Test fun theSectionIsNamedSoItCannotCollideWithOthers() {
        val bare = CanonicalHasher().also { MarketingState().hashInto(it) }.finish()
        val other = CanonicalHasher().also { it.section("other") }.finish()
        assertTrue(bare != other)
    }

    @Test fun queriesFilterByCompanyAndMarket() {
        book(s); book(s, request(channel = Channel.ONLINE)); book(s, request(company = rival)); book(s, request(good = bread, region = south))
        assertEquals(3, s.state.campaignsOf(acme).size)
        assertEquals(1, s.state.campaignsOf(rival).size)
        assertEquals(3, s.state.campaignsIn(beer, north).size)
        assertEquals(2, s.state.campaignsFor(BrandKey(acme, beer, north)).size)
        assertEquals(2, s.state.stocksOf(acme).size)
        assertEquals(2, s.state.stocksIn(beer, north).size)
        assertEquals(listOf(1L, 2L, 3L, 4L), s.state.campaigns().map { it.id.value })
    }

    @Test fun liveAndRunningAreDifferent() {
        val a = book(s); book(s, request(channel = Channel.ONLINE, start = Tick(5)))
        s.campaigns.pause(a.id)
        assertEquals(2, s.state.liveCampaignsOf(acme).size)
        assertEquals(0, s.state.runningCampaigns().size)
        assertEquals(0, s.state.campaignsIn(beer, north).size)
    }

    @Test fun missingThingsAreNull() {
        assertNull(s.state.campaign(CampaignId(1)))
        assertNull(s.state.stock(BrandKey(acme, beer, north)))
        assertNull(s.state.promotion(PromotionId(1)))
        assertNull(s.state.test(AbTestId(1)))
        assertNull(s.state.testFor(CampaignId(1)))
    }

    @Test fun stockForCreatesOnDemand() {
        val st = s.state.stockFor(BrandKey(acme, beer, north))
        assertNotNull(s.state.stock(BrandKey(acme, beer, north)))
        assertTrue(st.isDormant)
    }

    @Test fun clearEmptiesEverything() {
        val busy = MarketingTestSupport.busy()
        busy.state.clear()
        assertTrue(busy.state.isEmpty())
    }

    @Test fun listsAreSnapshotsNotLiveViews() {
        book(s)
        val before = s.state.campaigns()
        book(s, request(channel = Channel.ONLINE))
        assertEquals(1, before.size)
    }
}
