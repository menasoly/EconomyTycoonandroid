package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.MarketingTestSupport.acme
import app.many.economytycoon.marketing.MarketingTestSupport.beer
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.harbour
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.rival
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingReadModelTest {
    private val s = MarketingTestSupport.busy()
    private val now = Tick(25)
    private val rm = s.readModel()

    @Test fun theOverviewListsLiveCampaignsFirst() {
        val ov = rm.overview(acme, now)
        val states = ov.campaigns.map { it.isLive }
        assertEquals(states.sortedByDescending { it }, states)
        assertTrue(ov.campaigns.any { !it.isLive })
    }

    @Test fun campaignViewsCarryTheNumbersTheScreenNeeds() {
        val c = rm.overview(acme, now).campaigns.first { it.channel == Channel.RADIO }
        assertEquals("Radio: Beer", c.title)
        assertEquals("Region 1", c.place)
        assertEquals(Money.units(600), c.dailyBudget)
        assertTrue(c.reachPpm > 0L)
        assertTrue(c.impressions > 0L)
        assertTrue(c.awarenessPpm > 0L)
        assertNotNull(c.test)
        assertEquals(CampaignStatus.ACTIVE, c.status)
    }

    @Test fun cityCampaignsNameTheCityAndRegion() {
        val c = rm.overview(acme, now).campaigns.first { it.channel == Channel.IN_STORE }
        assertEquals("City ${harbour.value}, Region 1", c.place)
        assertEquals(CampaignStatus.CANCELLED, c.status)
    }

    @Test fun cancelTermsOnlyShowForLiveCampaigns() {
        val ov = rm.overview(acme, now)
        val tv = ov.campaigns.first { it.channel == Channel.TELEVISION }
        assertTrue(tv.isLive)
        assertEquals(Money.ZERO, ov.campaigns.first { !it.isLive }.cancelRefund)
        assertEquals(Money.ZERO, ov.campaigns.first { !it.isLive }.cancelFee)
    }

    @Test fun brandViewsShowAwarenessAndAppeal() {
        val ov = rm.overview(acme, now)
        val b = ov.brands.first { it.key.good == beer }
        assertTrue(b.awarenessPpm > 0L)
        assertTrue(b.appealPpm > 1_000_000L)
        assertTrue(b.liveCampaigns >= 1)
        assertTrue(b.title.contains("Beer"))
    }

    @Test fun promotionsAndDamagesAreIncludedForTheRightCompany() {
        val mine = rm.overview(acme, now)
        assertEquals(1, mine.promotions.size)
        assertEquals(PromotionStatus.RUNNING, mine.promotions[0].status)
        assertTrue(mine.promotions[0].daysLeft > 0)
        assertFalse(mine.promotions[0].belowCost)
        assertTrue(mine.damages.isEmpty())
        val theirs = rm.overview(rival, now)
        assertEquals(1, theirs.damages.size)
        assertEquals(DamageKind.RECALL, theirs.damages[0].kind)
        assertTrue(theirs.promotions.isEmpty())
    }

    @Test fun commitmentCountsOnlyRunningCampaigns() {
        val ov = rm.overview(acme, now)
        val running = ov.campaigns.filter { it.status == CampaignStatus.ACTIVE }
        assertEquals(Money.sum(running.map { it.billPerDay }), ov.dailyCommitment)
        assertTrue(ov.totalNetSpend.raw > 0L)
    }

    @Test fun canRefreshFollowsTheCooldown() {
        val c = s.state.campaigns().first { it.channel == Channel.RADIO }
        c.creative.lastRefreshed = null
        assertTrue(rm.campaign(c, now).canRefresh)
        c.creative.lastRefreshed = Tick(24)
        assertFalse(rm.campaign(c, now).canRefresh)
    }

    @Test fun testViewsCountDownDays() {
        val sys = MarketingTestSupport.system()
        val c = book(sys)
        sys.experiments.start(c.id, CreativeTier.STUDIO, 10, Tick(0), MarketingTestSupport.bank).getOrThrow()
        val t = sys.readModel().campaign(c, Tick(3)).test!!
        assertEquals(AbOutcome.RUNNING, t.outcome)
        assertEquals(7, t.daysLeft)
        assertEquals(0, sys.readModel().campaign(c, Tick(30)).test!!.daysLeft)
    }

    @Test fun roiReportsFlowThroughWhenProvided() {
        val report = RoiAnalysis.analyse(List(8) { Money.units(100) }, List(8) { Money.units(1_500) }, Money.units(1_000), 300_000L)
        val withRoi = s.readModel(roiOf = { report })
        assertEquals(report, withRoi.overview(acme, now).campaigns.first().roi)
        assertNull(rm.overview(acme, now).campaigns.first().roi)
    }

    @Test fun factsSummariseTheCompanyForTheAdvisor() {
        val f = rm.facts(acme, now, Money.units(80_000), Money.units(400_000), Money.units(20_000)) { 150_000L }
        assertEquals(80_000L, f.cashUnits)
        assertEquals(400_000L, f.monthRevenueUnits)
        assertTrue(f.campaigns.isNotEmpty())
        assertTrue(f.campaigns.none { it.channel == Channel.IN_STORE })
        assertTrue(f.brands.all { it.revenueSharePpm == 150_000L })
        assertEquals(1, f.promotions.size)
        assertTrue(f.dailyCommitmentUnits > 0L)
    }

    @Test fun factsFeedTheAdvisorWithoutErrors() {
        val f = rm.facts(acme, now, Money.units(100), Money.units(1_000), Money.units(900))
        val advice = s.advisor.advise(f)
        assertTrue(advice.any { it.code == "mkt-cash-cover" })
        assertTrue(advice.any { it.code == "mkt-overspend" })
    }

    @Test fun anUnknownCompanyHasAnEmptyOverview() {
        val ov = rm.overview(app.many.economytycoon.core.CompanyId(42), now)
        assertTrue(ov.campaigns.isEmpty() && ov.brands.isEmpty())
        assertEquals(Money.ZERO, ov.dailyCommitment)
    }

    @Test fun plainNamesAreReadable() {
        assertEquals("Beer", PlainNames.good(beer))
        assertEquals("Region 1", PlainNames.region(north))
        assertEquals("Fresh bread", PlainNames.good(app.many.economytycoon.core.GoodId("fresh_bread")))
    }

    @Test fun customNamesAreUsed() {
        val names = object : MarketingNames {
            override fun good(id: app.many.economytycoon.core.GoodId) = "G-" + id.key
            override fun region(id: app.many.economytycoon.core.RegionId) = "R-" + id.value
            override fun city(id: app.many.economytycoon.core.CityId) = "C-" + id.value
        }
        val c = s.readModel(names).overview(acme, now).campaigns.first { it.channel == Channel.RADIO }
        assertEquals("Radio: G-beer", c.title)
        assertEquals("R-1", c.place)
    }
}
