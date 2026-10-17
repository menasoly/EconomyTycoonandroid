package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingInvariantsTest {
    private val s = MarketingTestSupport.system()

    private fun problems() = MarketingInvariants.check(s.state)

    @Test fun aCleanStateHasNoProblems() {
        assertEquals(emptyList<String>(), problems())
        book(s)
        assertEquals(emptyList<String>(), problems())
    }

    @Test fun aBusyStateHasNoProblems() {
        assertEquals(emptyList<String>(), MarketingInvariants.check(MarketingTestSupport.busy().state))
    }

    @Test fun negativeDepositsAreCaught() {
        book(s).prepaid = Money.units(-1)
        assertTrue(problems().any { it.contains("negative deposit") })
    }

    @Test fun overrunBookingsAreCaught() {
        val c = book(s, request(days = 10))
        c.daysRun = 20
        assertTrue(problems().any { it.contains("more days than were booked") })
    }

    @Test fun endedCampaignsNeedAnEndDayAndNoDeposit() {
        val c = book(s, request(channel = Channel.TELEVISION, daily = 1_000))
        c.status = CampaignStatus.CANCELLED
        val p = problems()
        assertTrue(p.any { it.contains("without an end day") })
        assertTrue(p.any { it.contains("holding a deposit") })
    }

    @Test fun liveCampaignsMustNotHaveAnEndDay() {
        val c = book(s)
        c.endedAt = Tick(3)
        assertTrue(problems().any { it.contains("live but has an end day") })
    }

    @Test fun budgetsOutsideTheChannelAreCaught() {
        val c = book(s)
        c.dailyBudget = Money.units(1)
        assertTrue(problems().any { it.contains("outside the channel limits") })
    }

    @Test fun refundingMoreThanWasSpentIsCaught() {
        val c = book(s)
        c.refundedTotal = c.spentTotal + Money.units(5)
        assertTrue(problems().any { it.contains("refunded more") })
    }

    @Test fun brandsMoreLovedThanKnownAreCaught() {
        book(s)
        s.state.stocks().first().second.set(100_000L, 100_000L)
        s.state.stocks().first().second.restore(100_000L, 500_000L, 0L, LongArray(Channel.values().size))
        assertTrue(problems().any { it.contains("more loved than known") })
    }

    @Test fun twoLiveCampaignsInOneSlotAreCaught() {
        val a = book(s)
        val b = Campaign(CampaignId(50), a.company, a.good, a.region, a.city, a.channel, a.segment, Tick(0), Tick(0), a.dailyBudget, 10, Creative(500_000L))
        s.state.campaignMap[b.id] = b
        assertTrue(problems().any { it.contains("share the slot") })
    }

    @Test fun badPromotionsAreCaught() {
        val p = Promotion(PromotionId(1), MarketingTestSupport.acme, MarketingTestSupport.beer, MarketingTestSupport.north, Tick(0), 0, 0L, Money.units(1), Money.units(1), 0L)
        s.state.promotionMap[p.id] = p
        val found = problems()
        assertTrue(found.any { it.contains("discount out of range") })
        assertTrue(found.any { it.contains("no length") })
    }

    @Test fun runningTestsNeedTheirCampaign() {
        val t = AbTest(AbTestId(1), CampaignId(404), 1L, 2L, Tick(0), 10)
        s.state.testMap[t.id] = t
        assertTrue(problems().any { it.contains("campaign that is gone") })
    }

    @Test fun outlayAddsUpNetSpend() {
        val a = book(s, request(channel = Channel.TELEVISION, daily = 1_000, tier = CreativeTier.BASIC))
        s.campaigns.cancel(a.id, Tick(0))
        assertEquals(a.netSpend, MarketingInvariants.outlay(s.state))
    }
}
