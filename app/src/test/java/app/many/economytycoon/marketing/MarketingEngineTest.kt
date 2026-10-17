package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementResult
import app.many.economytycoon.finance.SettlementStatus
import app.many.economytycoon.marketing.MarketingTestSupport.acme
import app.many.economytycoon.marketing.MarketingTestSupport.audiences
import app.many.economytycoon.marketing.MarketingTestSupport.beer
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.request
import app.many.economytycoon.marketing.MarketingTestSupport.rival
import app.many.economytycoon.marketing.MarketingTestSupport.run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingEngineTest {
    private val s = MarketingTestSupport.system()
    private val key = BrandKey(acme, beer, north)

    private fun stock(k: BrandKey = key) = s.state.stock(k)!!

    @Test fun aRunningCampaignIsBilledEveryDay() {
        book(s, request(daily = 500, tier = CreativeTier.BASIC))
        val reports = run(s, 0, 3)
        for ((i, r) in reports.withIndex()) {
            assertEquals(1, r.intents.size)
            assertEquals(Money.units(500), r.intents[0].total)
            assertEquals("mkt:spd:1:$i", r.intents[0].key)
            assertEquals(Money.units(500), r.spendByCompany[acme])
        }
    }

    @Test fun aDepositPaysForTheFirstDaysBeforeAnyBilling() {
        book(s, request(channel = Channel.TELEVISION, daily = 1_000, days = 30, tier = CreativeTier.BASIC))
        val reports = run(s, 0, 9)
        for (i in 0 until 7) assertTrue("day $i", reports[i].intents.isEmpty())
        assertEquals(1, reports[7].intents.size)
        assertEquals(Money.units(1_000), reports[7].intents[0].total)
    }

    @Test fun aCampaignFinishesAfterItsBookedDays() {
        val c = book(s, request(days = 5, tier = CreativeTier.BASIC))
        val reports = run(s, 0, 7)
        assertEquals(CampaignStatus.COMPLETED, c.status)
        assertEquals(5, c.daysRun)
        assertEquals(0, c.daysRemaining)
        assertEquals(Tick(4), c.endedAt)
        assertTrue(reports[4].events.any { it is MarketingEvent.CampaignFinished })
        assertTrue(reports[5].intents.isEmpty())
        assertEquals(Money.units(2_500), c.spentTotal)
    }

    @Test fun spendingBuildsAwarenessAndPausingStopsIt() {
        val c = book(s, request(daily = 1_500, tier = CreativeTier.BASIC))
        run(s, 0, 20)
        val built = stock().awarenessPpm
        assertTrue(built > 20_000L)
        s.campaigns.pause(c.id)
        run(s, 20, 20)
        assertTrue(stock().awarenessPpm < built)
        assertEquals(20, c.daysRun)
    }

    @Test fun aPausedCampaignIsNotBilled() {
        val c = book(s)
        s.campaigns.pause(c.id)
        val reports = run(s, 0, 4)
        assertTrue(reports.all { it.intents.isEmpty() })
        assertEquals(4, c.pausedDays)
    }

    @Test fun aCampaignPausedTooLongIsEndedWithItsDepositReturned() {
        val c = book(s, request(channel = Channel.TELEVISION, daily = 1_000, days = 30, tier = CreativeTier.BASIC))
        s.campaigns.pause(c.id)
        val reports = run(s, 0, MarketingParams().maxPauseDays + 2)
        assertEquals(CampaignStatus.CANCELLED, c.status)
        assertTrue(reports.any { r -> r.events.any { it is MarketingEvent.CampaignAutoEnded } })
        val refund = reports.flatMap { it.intents }.single()
        assertEquals(c.refundedTotal, refund.total)
        assertTrue(c.feesTotal.raw > 0L)
    }

    @Test fun aBookedCampaignStartsOnItsDay() {
        val c = book(s, request(start = Tick(3)))
        val reports = run(s, 0, 5)
        assertTrue(reports[0].intents.isEmpty() && reports[2].intents.isEmpty())
        assertEquals(1, reports[3].intents.size)
        assertTrue(reports[3].events.any { it is MarketingEvent.CampaignStarted })
        assertEquals(CampaignStatus.ACTIVE, c.status)
    }

    @Test fun creativesWearOutAndSayWhenTheyDo() {
        val c = book(s, request(channel = Channel.ONLINE, daily = 4_000, days = 200, tier = CreativeTier.BASIC))
        val events = run(s, 0, 120).flatMap { it.events }
        assertTrue(c.creative.freshnessPpm < 350_000L)
        assertEquals(1, events.count { it is MarketingEvent.CreativeWornOut })
    }

    @Test fun wornCreativesBuyLessThanFreshOnes() {
        val fresh = MarketingTestSupport.system(); val worn = MarketingTestSupport.system()
        book(fresh, request(daily = 1_000, tier = CreativeTier.BASIC)); val w = book(worn, request(daily = 1_000, tier = CreativeTier.BASIC))
        w.creative.freshnessPpm = 0L
        run(fresh, 0, 25); run(worn, 0, 25)
        assertTrue(fresh.state.stock(key)!!.awarenessPpm > worn.state.stock(key)!!.awarenessPpm)
    }

    @Test fun aBetterCreativeBuysMoreAwareness() {
        val basic = MarketingTestSupport.system(); val studio = MarketingTestSupport.system()
        book(basic, request(daily = 1_000, tier = CreativeTier.BASIC)); book(studio, request(daily = 1_000, tier = CreativeTier.STUDIO))
        run(basic, 0, 30); run(studio, 0, 30)
        assertTrue(studio.state.stock(key)!!.awarenessPpm > basic.state.stock(key)!!.awarenessPpm)
    }

    @Test fun slowStartingChannelsRampUp() {
        val c = book(s, request(channel = Channel.TELEVISION, daily = 2_000, days = 60, tier = CreativeTier.BASIC))
        run(s, 0, 1)
        val first = stock().adstock(Channel.TELEVISION)
        run(s, 1, 14)
        assertTrue(stock().adstock(Channel.TELEVISION) > first * 3)
        assertEquals(15, c.daysRun)
    }

    @Test fun reachAndImpressionsAreRecorded() {
        val c = book(s, request(daily = 2_000, tier = CreativeTier.BASIC))
        run(s, 0, 2)
        assertTrue(c.lastReachPpm > 0L)
        assertTrue(c.impressions > 0L)
    }

    @Test fun aLoudRivalBluntsYourMessage() {
        val alone = MarketingTestSupport.system(); val crowded = MarketingTestSupport.system()
        book(alone, request(daily = 1_000, tier = CreativeTier.BASIC))
        book(crowded, request(daily = 1_000, tier = CreativeTier.BASIC))
        book(crowded, request(company = rival, daily = 12_000, tier = CreativeTier.BASIC))
        run(alone, 0, 40); run(crowded, 0, 40)
        assertTrue(alone.state.stock(key)!!.awarenessPpm > crowded.state.stock(key)!!.awarenessPpm)
    }

    @Test fun rivalsInAnotherMarketDoNotBlunt() {
        val alone = MarketingTestSupport.system(); val other = MarketingTestSupport.system()
        book(alone, request(daily = 1_000, tier = CreativeTier.BASIC))
        book(other, request(daily = 1_000, tier = CreativeTier.BASIC))
        book(other, request(company = rival, good = MarketingTestSupport.bread, daily = 12_000, tier = CreativeTier.BASIC))
        run(alone, 0, 30); run(other, 0, 30)
        assertEquals(alone.state.stock(key)!!.awarenessPpm, other.state.stock(key)!!.awarenessPpm)
    }

    @Test fun deliveredQualityShapesLoyalty() {
        val good = MarketingTestSupport.system(); val bad = MarketingTestSupport.system()
        book(good, request(daily = 1_500, tier = CreativeTier.BASIC)); book(bad, request(daily = 1_500, tier = CreativeTier.BASIC))
        run(good, 0, 60) { 1_300_000L }
        run(bad, 0, 60) { 500_000L }
        assertTrue(good.state.stock(key)!!.loyaltyPpm > bad.state.stock(key)!!.loyaltyPpm)
    }

    @Test fun cityCampaignsBuildLessThanRegionWideOnes() {
        val region = MarketingTestSupport.system(); val city = MarketingTestSupport.system()
        book(region, request(daily = 1_000, tier = CreativeTier.BASIC)); book(city, request(daily = 1_000, tier = CreativeTier.BASIC, city = MarketingTestSupport.harbour))
        run(region, 0, 30); run(city, 0, 30)
        assertTrue(region.state.stock(key)!!.awarenessPpm != city.state.stock(key)!!.awarenessPpm)
    }

    @Test fun anUnknownPlaceBuysNothing() {
        val c = book(s)
        val empty = FixedAudienceSource(emptyList())
        repeat(3) { s.engine.advance(Tick(it.toLong()), empty) }
        assertEquals(0L, s.state.stock(key)?.awarenessPpm ?: 0L)
        assertEquals(3, c.daysRun)
    }

    @Test fun brandsFadeAndAreForgottenWhenNothingIsLeft() {
        book(s, request(days = 5, tier = CreativeTier.BASIC))
        run(s, 0, 4000)
        assertNull(s.state.stock(key))
    }

    @Test fun endedCampaignsAreKeptForAWhileThenForgotten() {
        val c = book(s, request(days = 3, tier = CreativeTier.BASIC))
        run(s, 0, 50)
        assertNotNull(s.state.campaign(c.id))
        run(s, 50, 100)
        assertNull(s.state.campaign(c.id))
    }

    @Test fun leftoverDepositIsRefundedInFullWhenACampaignFinishes() {
        val c = book(s, request(channel = Channel.TELEVISION, daily = 1_000, days = 10, tier = CreativeTier.BASIC))
        s.campaigns.setBudget(c.id, Money.units(400), Tick(0), MarketingTestSupport.bank)
        val reports = run(s, 0, 11)
        val refunds = reports.flatMap { it.intents }.filter { it.key.startsWith("mkt:ref") }
        assertEquals(1, refunds.size)
        assertEquals(Money.units(7_000 - 4_000), refunds[0].total)
        assertEquals(Money.ZERO, c.feesTotal)
        assertEquals(Money.units(4_000), c.netSpend)
    }

    @Test fun unpaidDaysAreNotChargedAndEventuallySuspendTheCampaign() {
        val c = book(s, request(daily = 500, tier = CreativeTier.BASIC))
        var suspended = false
        for (day in 0 until 3) {
            val r = s.engine.advance(Tick(day.toLong()), audiences())
            val results = r.intents.map { SettlementResult(it, SettlementStatus.UNPAID, Money.ZERO) }
            suspended = s.engine.reportPayments(Tick(day.toLong()), results).any { it is MarketingEvent.CampaignSuspended }
        }
        assertTrue(suspended)
        assertEquals(CampaignStatus.SUSPENDED, c.status)
        assertEquals(Money.ZERO, c.spentTotal)
        assertEquals(3, c.missedPayments)
    }

    @Test fun aPaidDayResetsTheMissedCount() {
        val c = book(s, request(daily = 500, tier = CreativeTier.BASIC))
        val r1 = s.engine.advance(Tick(0), audiences())
        s.engine.reportPayments(Tick(0), r1.intents.map { SettlementResult(it, SettlementStatus.UNPAID, Money.ZERO) })
        assertEquals(1, c.missedPayments)
        val r2 = s.engine.advance(Tick(1), audiences())
        s.engine.reportPayments(Tick(1), r2.intents.map { SettlementResult(it, SettlementStatus.PAID, it.total) })
        assertEquals(0, c.missedPayments)
        assertEquals(Tick(1), c.lastPaidTick)
        assertEquals(CampaignStatus.ACTIVE, c.status)
    }

    @Test fun deferredPaymentsCountAsPaid() {
        val c = book(s, request(daily = 500, tier = CreativeTier.BASIC))
        val r = s.engine.advance(Tick(0), audiences())
        s.engine.reportPayments(Tick(0), r.intents.map { SettlementResult(it, SettlementStatus.DEFERRED, Money.ZERO) })
        assertEquals(0, c.missedPayments)
        assertEquals(Money.units(500), c.spentTotal)
    }

    @Test fun unrelatedSettlementResultsAreIgnored() {
        val c = book(s)
        val other = app.many.economytycoon.finance.SettlementIntent.simple(
            "wages:1", app.many.economytycoon.finance.LedgerOwner.company(acme), s.ledger.agency, Money.units(1),
            app.many.economytycoon.finance.SettlementPriority.WAGES, app.many.economytycoon.finance.ReasonCode.WAGES,
            app.many.economytycoon.finance.Chart.WAGES_EXPENSE, app.many.economytycoon.finance.Chart.REVENUE_OTHER,
        )
        assertTrue(s.engine.reportPayments(Tick(0), listOf(SettlementResult(other, SettlementStatus.UNPAID, Money.ZERO))).isEmpty())
        assertEquals(0, c.missedPayments)
    }

    @Test fun theSameInputsGiveTheSameHash() {
        val a = MarketingTestSupport.system(seed = 5L); val b = MarketingTestSupport.system(seed = 5L)
        for (sys in listOf(a, b)) {
            book(sys, request(daily = 800)); book(sys, request(company = rival, channel = Channel.ONLINE, daily = 900))
            run(sys, 0, 45)
        }
        assertEquals(a.stateHash(), b.stateHash())
    }

    @Test fun differentInputsGiveDifferentHashes() {
        val a = MarketingTestSupport.system(); val b = MarketingTestSupport.system()
        book(a, request(daily = 800)); book(b, request(daily = 801))
        run(a, 0, 10); run(b, 0, 10)
        assertTrue(a.stateHash() != b.stateHash())
    }

    @Test fun invariantsHoldThroughoutALongRun() {
        book(s, request(daily = 900)); book(s, request(channel = Channel.TELEVISION, daily = 3_000, days = 90))
        book(s, request(company = rival, channel = Channel.SPONSORSHIP, daily = 700, days = 100))
        for (day in 0 until 150) {
            s.engine.advance(Tick(day.toLong()), audiences())
            assertEquals("day $day", emptyList<String>(), s.invariants())
        }
    }

    @Test fun outlayMatchesWhatTheCampaignsPaid() {
        val c = book(s, request(daily = 500, days = 10, tier = CreativeTier.BASIC))
        run(s, 0, 12)
        assertEquals(c.netSpend, MarketingInvariants.outlay(s.state))
        assertEquals(Money.units(5_000), c.netSpend)
    }
}
