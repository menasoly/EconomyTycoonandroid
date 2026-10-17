package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.marketing.Channel
import app.many.economytycoon.marketing.RoiAnalysis
import app.many.economytycoon.marketing.RoiVerdict
import app.many.economytycoon.session.Formatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoiReportPresenterTest {
    private val money: (Money) -> String = { Formatting.money(it) }
    private fun report(spend: Long, revenue: Long, days: Int = 10) =
        RoiAnalysis.analyse(List(days) { Money.units(spend) }, List(days) { Money.units(revenue) }, Money.units(1_000), 300_000L)

    @Test fun aWinningCampaignIsGreenAndSaysWhen() {
        val m = RoiReportPresenter.build(report(100, 1_500), emptyMap(), money)
        assertEquals(Tone.GOOD, m.tone)
        assertTrue(m.headline, m.headline.startsWith("Paid for itself"))
        assertTrue(m.headline.contains("from day 1"))
        assertTrue(m.advice.startsWith("Keep it going"))
    }

    @Test fun aLosingCampaignSaysHowMuchWasLost() {
        val r = report(100, 1_050)
        val m = RoiReportPresenter.build(r, emptyMap(), money)
        assertEquals(Tone.BAD, m.tone)
        assertTrue(m.headline.startsWith("Lost ${money(-r.netProfit)}"))
        assertTrue(m.advice.contains("Cut the budget"))
    }

    @Test fun halfwayThereIsInfo() {
        val m = RoiReportPresenter.build(report(100, 1_200), emptyMap(), money)
        assertEquals(Tone.INFO, m.tone)
        assertTrue(m.headline.contains("60.0%"))
    }

    @Test fun earlyReportsAreMutedAndCautious() {
        val m = RoiReportPresenter.build(report(100, 1_500, days = 3), emptyMap(), money)
        assertEquals(Tone.MUTED, m.tone)
        assertTrue(m.headline.contains("3 days"))
        assertTrue(m.advice.contains("week"))
    }

    @Test fun tilesAndFactsShowTheNumbers() {
        val r = report(100, 1_500)
        val m = RoiReportPresenter.build(r, emptyMap(), money)
        assertEquals(listOf("Spent", "Extra profit", "Net result", "Lift in sales"), m.tiles.map { it.label })
        assertEquals(money(r.spend), m.tiles[0].value)
        assertEquals(Tone.GOOD, m.tiles[2].tone)
        assertEquals("on day 1", m.facts.last().value)
        assertEquals("10", m.facts.first().value)
    }

    @Test fun notPaidBackShowsNotYet() {
        val m = RoiReportPresenter.build(report(100, 1_050), emptyMap(), money)
        assertEquals("not yet", m.facts.last().value)
    }

    @Test fun channelSpendIsShownWithShares() {
        val m = RoiReportPresenter.build(report(100, 1_500), mapOf(Channel.RADIO to Money.units(750), Channel.ONLINE to Money.units(250)), money)
        assertEquals(listOf("Radio", "Online"), m.channels.map { it.channel })
        assertEquals("75.0%", m.channels[0].share)
        assertEquals("25.0%", m.channels[1].share)
    }

    @Test fun noChannelSpendGivesZeroShares() {
        val m = RoiReportPresenter.build(report(100, 1_500), mapOf(Channel.RADIO to Money.ZERO), money)
        assertEquals("0.0%", m.channels.single().share)
    }

    @Test fun everyVerdictHasAToneAndAdvice() {
        for (v in RoiVerdict.values()) assertTrue(v.label.isNotBlank())
        assertEquals(Tone.GOOD, MarketingPresenter.roiTone(RoiVerdict.PAID_BACK))
        assertEquals(Tone.INFO, MarketingPresenter.roiTone(RoiVerdict.PAYING_BACK))
        assertEquals(Tone.BAD, MarketingPresenter.roiTone(RoiVerdict.LOSING))
        assertEquals(Tone.MUTED, MarketingPresenter.roiTone(RoiVerdict.TOO_EARLY))
    }
}
