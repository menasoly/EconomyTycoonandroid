package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoiAnalysisTest {
    private fun days(n: Int, v: Long) = List(n) { Money.units(v) }

    @Test fun baselineIsTheAverageOfThePast() {
        assertEquals(Money.units(200), RoiAnalysis.baseline(listOf(Money.units(100), Money.units(300))))
        assertNull(RoiAnalysis.baseline(emptyList()))
    }

    @Test fun aCampaignThatLiftsSalesPaysItselfBack() {
        val r = RoiAnalysis.analyse(days(10, 100), days(10, 1_500), Money.units(1_000), 300_000L)
        assertEquals(Money.units(15_000), r.actualRevenue)
        assertEquals(Money.units(5_000), r.incrementalRevenue)
        assertEquals(Money.units(1_500), r.incrementalProfit)
        assertEquals(Money.units(500), r.netProfit)
        assertEquals(500_000L, r.liftPpm)
        assertEquals(500_000L, r.roiPpm)
        assertEquals(1, r.paybackDay)
        assertEquals(RoiVerdict.PAID_BACK, r.verdict)
    }

    @Test fun aWeakLiftNeverPaysBack() {
        val r = RoiAnalysis.analyse(days(10, 100), days(10, 1_050), Money.units(1_000), 300_000L)
        assertNull(r.paybackDay)
        assertEquals(RoiVerdict.LOSING, r.verdict)
        assertTrue(r.roiPpm < 0L)
    }

    @Test fun halfwayThereIsPayingBack() {
        val r = RoiAnalysis.analyse(days(10, 100), days(10, 1_200), Money.units(1_000), 300_000L)
        assertEquals(RoiVerdict.PAYING_BACK, r.verdict)
    }

    @Test fun tooFewDaysIsTooEarly() {
        val r = RoiAnalysis.analyse(days(3, 100), days(3, 1_500), Money.units(1_000), 300_000L)
        assertEquals(RoiVerdict.TOO_EARLY, r.verdict)
    }

    @Test fun paybackDayIsTheFirstDayCumulativeProfitCoversCumulativeSpend() {
        val spend = days(10, 100)
        val revenue = List(10) { Money.units(if (it < 4) 1_000L else 1_900L) }
        val r = RoiAnalysis.analyse(spend, revenue, Money.units(1_000), 300_000L)
        // The first four days earn nothing; afterwards profit is 270 a day against 100 spent. Day 7: 810 against 700.
        assertEquals(7, r.paybackDay)
    }

    @Test fun salesBelowBaselineMeanNegativeLift() {
        val r = RoiAnalysis.analyse(days(8, 50), days(8, 800), Money.units(1_000), 300_000L)
        assertTrue(r.liftPpm < 0L)
        assertTrue(r.incrementalProfit.raw < 0L)
        assertEquals(RoiVerdict.LOSING, r.verdict)
    }

    @Test fun noSpendHasZeroRoi() {
        val r = RoiAnalysis.analyse(days(8, 0), days(8, 1_200), Money.units(1_000), 300_000L)
        assertEquals(0L, r.roiPpm)
        assertNull(r.paybackDay)
    }

    @Test fun noBaselineHasZeroLift() {
        val r = RoiAnalysis.analyse(days(8, 10), days(8, 100), Money.ZERO, 300_000L)
        assertEquals(0L, r.liftPpm)
    }

    @Test(expected = IllegalArgumentException::class) fun theSeriesMustLineUp() {
        RoiAnalysis.analyse(days(3, 1), days(4, 1), Money.units(1), 300_000L)
    }

    @Test fun paybackDaysRoundUp() {
        assertEquals(4, RoiAnalysis.paybackDays(Money.units(1_000), Money.units(300)))
        assertEquals(1, RoiAnalysis.paybackDays(Money.units(100), Money.units(300)))
        assertNull(RoiAnalysis.paybackDays(Money.units(100), Money.ZERO))
        assertNull(RoiAnalysis.paybackDays(Money.units(100), Money.units(-5)))
    }

    @Test fun spendIsGroupedByChannelInOrder() {
        val s = MarketingTestSupport.system()
        val a = book(s, request(channel = Channel.ONLINE, daily = 300, days = 5, tier = CreativeTier.BASIC))
        val b = book(s, request(channel = Channel.RADIO, daily = 200, days = 5, tier = CreativeTier.BASIC))
        MarketingTestSupport.run(s, 0, 6)
        val by = RoiAnalysis.spendByChannel(listOf(a, b))
        assertEquals(listOf(Channel.RADIO, Channel.ONLINE), by.keys.toList())
        assertEquals(Money.units(1_000), by.getValue(Channel.RADIO))
        assertEquals(Money.units(1_500), by.getValue(Channel.ONLINE))
        assertEquals(Tick(0), a.createdAt)
    }
}
