package app.many.economytycoon.marketing

import app.many.economytycoon.advisor.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingAdvisorTest {
    private val advisor = MarketingAdvisor()

    private fun campaign(
        id: Long = 1, channel: Channel = Channel.RADIO, daily: Long = 500, reach: Long = 300_000L, fresh: Long = 900_000L, days: Int = 20,
        awareness: Long = 400_000L, loyalty: Long = 200_000L, running: Boolean = true, roi: Long? = null, suspended: Boolean = false,
    ) = CampaignFact(id, "Beer in North", channel, daily, reach, fresh, days, awareness, loyalty, running, roi, suspended)

    private fun brand(
        advertised: Boolean = true, awareness: Long = 400_000L, loyalty: Long = 300_000L, taint: Long = 0L, share: Long = 100_000L,
        voice: Long = 400_000L, rival: Long = 400_000L, channels: Int = 2, daily: Long = 300L, loyaltyChannel: Boolean = false,
    ) = BrandFact("Beer in North", awareness, loyalty, taint, advertised, loyaltyChannel, share, voice, rival, channels, daily)

    private fun codes(f: MarketingFacts) = advisor.advise(f).map { it.code }

    @Test fun aQuietCompanyHearsNothing() {
        assertTrue(advisor.advise(MarketingFacts()).isEmpty())
    }

    @Test fun aHealthyCampaignHearsNothing() {
        val f = MarketingFacts(cashUnits = 50_000, monthRevenueUnits = 400_000, monthMarketingUnits = 15_000, campaigns = listOf(campaign()), brands = listOf(brand()))
        assertEquals(emptyList<String>(), codes(f))
    }

    @Test fun saturatedAwarenessIsFlaggedForAwarenessChannels() {
        val f = MarketingFacts(campaigns = listOf(campaign(awareness = 900_000L)))
        assertTrue("mkt-saturated-1" in codes(f))
        val loyal = MarketingFacts(campaigns = listOf(campaign(channel = Channel.LOYALTY_PROGRAMME, awareness = 900_000L)))
        assertTrue("mkt-saturated-1" !in codes(loyal))
    }

    @Test fun lowReachSpendIsFlagged() {
        val f = MarketingFacts(campaigns = listOf(campaign(reach = 20_000L, daily = 800)))
        val insight = advisor.advise(f).first { it.code == "mkt-low-reach-1" }
        assertTrue(insight.title.contains("low-reach"))
        assertEquals(Topic.MARKET, insight.topic)
    }

    @Test fun cheapLowReachIsNotWorthMentioning() {
        assertTrue("mkt-low-reach-1" !in codes(MarketingFacts(campaigns = listOf(campaign(reach = 20_000L, daily = 50)))))
    }

    @Test fun wornCreativesAreFlagged() {
        assertTrue("mkt-worn-1" in codes(MarketingFacts(campaigns = listOf(campaign(fresh = 200_000L)))))
    }

    @Test fun payingCampaignsAboutToEndAreSuggestedForExtension() {
        assertTrue("mkt-ending-1" in codes(MarketingFacts(campaigns = listOf(campaign(days = 2, roi = 400_000L)))))
        assertTrue("mkt-ending-1" !in codes(MarketingFacts(campaigns = listOf(campaign(days = 2, roi = -100_000L)))))
        assertTrue("mkt-ending-1" !in codes(MarketingFacts(campaigns = listOf(campaign(days = 15, roi = 400_000L)))))
    }

    @Test fun moneyLosersAreFlagged() {
        assertTrue("mkt-losing-1" in codes(MarketingFacts(campaigns = listOf(campaign(roi = -800_000L)))))
        assertTrue("mkt-losing-1" !in codes(MarketingFacts(campaigns = listOf(campaign(roi = null)))))
    }

    @Test fun aBigUnadvertisedSellerIsFlagged() {
        val f = MarketingFacts(brands = listOf(brand(advertised = false, awareness = 100_000L, share = 400_000L)))
        assertTrue("mkt-unadvertised" in codes(f))
        assertTrue("mkt-unadvertised" !in codes(MarketingFacts(brands = listOf(brand(advertised = false, awareness = 600_000L, share = 400_000L)))))
    }

    @Test fun knownButUnlovedBrandsNeedLoyalty() {
        val f = MarketingFacts(brands = listOf(brand(awareness = 800_000L, loyalty = 100_000L)))
        assertTrue("mkt-loyalty-gap" in codes(f))
        assertTrue("mkt-loyalty-gap" !in codes(MarketingFacts(brands = listOf(brand(awareness = 800_000L, loyalty = 100_000L, loyaltyChannel = true)))))
    }

    @Test fun beingOutshoutedIsFlagged() {
        val f = MarketingFacts(brands = listOf(brand(voice = 100_000L, rival = 700_000L)))
        assertTrue(codes(f).any { it.startsWith("mkt-outshouted") })
        assertTrue(codes(MarketingFacts(brands = listOf(brand(voice = 450_000L, rival = 500_000L)))).none { it.startsWith("mkt-outshouted") })
    }

    @Test fun damageWarnsAgainstABigPush() {
        assertTrue("mkt-damaged" in codes(MarketingFacts(brands = listOf(brand(taint = 500_000L)))))
    }

    @Test fun overspendingComparedWithRevenueIsFlagged() {
        assertTrue("mkt-overspend" in codes(MarketingFacts(monthRevenueUnits = 100_000, monthMarketingUnits = 30_000)))
        assertTrue("mkt-overspend" !in codes(MarketingFacts(monthRevenueUnits = 100_000, monthMarketingUnits = 5_000)))
        assertTrue("mkt-overspend" !in codes(MarketingFacts(monthRevenueUnits = 0, monthMarketingUnits = 5_000)))
    }

    @Test fun thinCashAgainstCommitmentsIsUrgent() {
        val f = MarketingFacts(cashUnits = 3_000, campaigns = listOf(campaign(daily = 500), campaign(id = 2, daily = 500)))
        val insight = advisor.advise(f).first { it.code == "mkt-cash-cover" }
        assertEquals(Topic.FINANCE, insight.topic)
        assertTrue(insight.priority >= 70)
        assertTrue("mkt-cash-cover" !in codes(MarketingFacts(cashUnits = 100_000, campaigns = listOf(campaign()))))
    }

    @Test fun pausedCampaignsDoNotCountAsCommitments() {
        val f = MarketingFacts(cashUnits = 10, campaigns = listOf(campaign(running = false)))
        assertEquals(0L, f.dailyCommitmentUnits)
        assertTrue("mkt-cash-cover" !in codes(f))
    }

    @Test fun suspensionIsReported() {
        val f = MarketingFacts(campaigns = listOf(campaign(running = false, suspended = true), campaign(id = 2, running = false, suspended = true)))
        val i = advisor.advise(f).first { it.code == "mkt-suspended" }
        assertTrue(i.title.startsWith("2 campaigns were"))
    }

    @Test fun relyingOnOneChannelIsFlagged() {
        assertTrue("mkt-one-channel" in codes(MarketingFacts(brands = listOf(brand(channels = 1, daily = 900L)))))
        assertTrue("mkt-one-channel" !in codes(MarketingFacts(brands = listOf(brand(channels = 1, daily = 100L)))))
    }

    @Test fun promotionsWithThinMarginAreFlagged() {
        val thin = MarketingFacts(promotions = listOf(PromotionFact("Beer in North", 20_000L, 5, false)))
        val loss = MarketingFacts(promotions = listOf(PromotionFact("Beer in North", -50_000L, 5, true)))
        assertTrue("mkt-promo-margin" in codes(thin))
        assertTrue(advisor.advise(loss).first { it.code == "mkt-promo-margin" }.priority > advisor.advise(thin).first { it.code == "mkt-promo-margin" }.priority)
    }

    @Test fun adviceComesMostPressingFirstAndCanBeLimited() {
        val f = MarketingFacts(
            cashUnits = 10, monthRevenueUnits = 1_000, monthMarketingUnits = 900,
            campaigns = listOf(campaign(fresh = 100_000L, roi = -900_000L)),
        )
        val all = advisor.advise(f)
        assertTrue(all.size >= 3)
        assertEquals(all.sortedByDescending { it.priority }.map { it.priority }, all.map { it.priority })
        assertEquals(all.take(2), advisor.advise(f, limit = 2))
    }

    @Test fun everyCodeIsPrefixedAndPrioritiesAreInRange() {
        val f = MarketingFacts(
            cashUnits = 5, monthRevenueUnits = 100, monthMarketingUnits = 60,
            campaigns = listOf(campaign(awareness = 950_000L, reach = 10_000L, fresh = 10_000L, roi = -900_000L, days = 2)),
            brands = listOf(brand(advertised = false, awareness = 800_000L, loyalty = 50_000L, taint = 600_000L, share = 500_000L, voice = 0, rival = 900_000L)),
            promotions = listOf(PromotionFact("x", -1L, 2, true)),
        )
        for (i in advisor.advise(f)) {
            assertTrue(i.code, i.code.startsWith("mkt-"))
            assertTrue(i.priority in 0..100)
            assertTrue(i.title.isNotBlank() && i.detail.isNotBlank())
        }
    }

    @Test fun codesAreUniquePerFinding() {
        val f = MarketingFacts(campaigns = listOf(campaign(id = 1, fresh = 100_000L), campaign(id = 2, fresh = 100_000L)))
        val c = codes(f)
        assertEquals(c.size, c.toSet().size)
    }
}
