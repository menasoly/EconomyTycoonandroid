package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.CampaignStatus
import app.many.economytycoon.marketing.Channel
import app.many.economytycoon.marketing.MarketingTestSupport
import app.many.economytycoon.marketing.MarketingTestSupport.acme
import app.many.economytycoon.marketing.RoiAnalysis
import app.many.economytycoon.session.Formatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingPresenterTest {
    private val s = MarketingTestSupport.busy()
    private val rm = s.readModel()
    private val now = Tick(25)
    private val money: (Money) -> String = { Formatting.money(it) }
    private val overview = rm.overview(acme, now)

    @Test fun statusTonesFollowTheSeverity() {
        assertEquals(Tone.GOOD, MarketingPresenter.statusTone(CampaignStatus.ACTIVE))
        assertEquals(Tone.INFO, MarketingPresenter.statusTone(CampaignStatus.SCHEDULED))
        assertEquals(Tone.WARN, MarketingPresenter.statusTone(CampaignStatus.PAUSED))
        assertEquals(Tone.BAD, MarketingPresenter.statusTone(CampaignStatus.SUSPENDED))
        assertEquals(Tone.MUTED, MarketingPresenter.statusTone(CampaignStatus.COMPLETED))
        assertEquals(Tone.MUTED, MarketingPresenter.statusTone(CampaignStatus.CANCELLED))
    }

    @Test fun everyStatusHasAWordedSummary() {
        for (c in overview.campaigns) assertTrue(MarketingPresenter.row(c, money).summary.isNotBlank())
        val running = overview.campaigns.first { it.status == CampaignStatus.ACTIVE }
        val text = MarketingPresenter.row(running, money).summary
        assertTrue(text, text.contains("a day") && text.contains("left") && text.contains("reaches"))
    }

    @Test fun labelsGrowWithQualityAndFreshness() {
        assertEquals("Excellent", MarketingPresenter.qualityLabel(780_000L))
        assertEquals("Good", MarketingPresenter.qualityLabel(600_000L))
        assertEquals("Average", MarketingPresenter.qualityLabel(450_000L))
        assertEquals("Weak", MarketingPresenter.qualityLabel(100_000L))
        assertEquals("Fresh", MarketingPresenter.freshnessLabel(900_000L))
        assertEquals("Familiar", MarketingPresenter.freshnessLabel(500_000L))
        assertEquals("Tired", MarketingPresenter.freshnessLabel(300_000L))
        assertEquals("Worn out", MarketingPresenter.freshnessLabel(10_000L))
    }

    @Test fun theListSplitsLiveFromEnded() {
        val m = MarketingPresenter.list(overview, money)
        assertEquals(overview.campaigns.count { it.isLive }, m.live.size)
        assertEquals(overview.campaigns.count { !it.isLive }, m.ended.size)
        assertEquals(4, m.tiles.size)
        assertTrue(m.promotions.isNotEmpty())
        assertTrue(m.note.contains("Awareness"))
    }

    @Test fun anEmptyOverviewShowsTheInvitation() {
        val m = MarketingPresenter.list(rm.overview(app.many.economytycoon.core.CompanyId(77), now), money)
        assertEquals(MarketingPresenter.EMPTY, m.note)
        assertTrue(m.live.isEmpty() && m.brands.isEmpty())
        assertEquals("0", m.tiles[0].value)
        assertEquals(Tone.MUTED, m.tiles[0].tone)
    }

    @Test fun brandRowsWarnAboutDamageAndNeglect() {
        val damaged = rm.overview(MarketingTestSupport.rival, now)
        val row = MarketingPresenter.list(damaged, money).brands.first()
        assertNotNull(row.note)
        assertEquals(Tone.WARN, row.tone)
        assertEquals(4, row.facts.size)
    }

    @Test fun anUnadvertisedBrandSaysItIsFading() {
        val b = overview.brands.first().copy(taintPpm = 0L, liveCampaigns = 0, awarenessPpm = 300_000L)
        val row = MarketingPresenter.brandRow(b, money)
        assertTrue(row.note!!.contains("fading"))
        assertEquals("None", row.facts.last().value)
    }

    @Test fun damagesAreListedAsFacts() {
        val rival = rm.overview(MarketingTestSupport.rival, now)
        val m = MarketingPresenter.list(rival, money)
        assertEquals(1, m.damages.size)
        assertEquals(Tone.BAD, m.damages[0].tone)
        assertTrue(m.damages[0].hint!!.contains("awareness"))
    }

    @Test fun promotionRowsShowMarginAndTime() {
        val p = MarketingPresenter.list(overview, money).promotions.first()
        assertEquals("On sale", p.status)
        assertTrue(p.summary.contains("10.0% off"))
        assertTrue(p.summary.contains("left"))
    }

    @Test fun belowCostPromotionsAreRed() {
        val view = overview.promotions.first().copy(belowCost = true)
        assertEquals(Tone.BAD, MarketingPresenter.promotionRow(view, money).tone)
    }

    @Test fun detailGathersBookingEffectAndCreative() {
        val c = overview.campaigns.first { it.channel == Channel.TELEVISION }
        val d = MarketingPresenter.detail(c, money)
        assertEquals(c.id, d.id)
        assertEquals(8, d.booking.size)
        assertEquals(4, d.effect.size)
        assertEquals(3, d.creative.size)
        assertEquals("Paused", d.status)
        assertTrue(d.actions.canResume)
        assertFalse(d.actions.canPause)
        assertTrue(d.actions.canCancel && d.actions.canExtend)
    }

    @Test fun detailExplainsTheCancellationTerms() {
        val c = overview.campaigns.first { it.channel == Channel.TELEVISION }
            .copy(status = CampaignStatus.ACTIVE, cancelRefund = Money.units(500), cancelFee = Money.units(100))
        val note = MarketingPresenter.detail(c, money).actions.cancelNote
        assertNotNull(note)
        assertTrue(note!!.contains("fee"))
    }

    @Test fun endedCampaignsOfferNoActions() {
        val c = overview.campaigns.first { !it.isLive }
        val a = MarketingPresenter.detail(c, money).actions
        assertFalse(a.canPause || a.canResume || a.canExtend || a.canCancel || a.canRefresh)
        assertNull(a.cancelNote)
    }

    @Test fun warningsAppearWhenSomethingNeedsAttention() {
        val c = overview.campaigns.first { it.channel == Channel.RADIO }
        val worn = c.copy(freshnessPpm = 100_000L, missedPayments = 2, daysRemaining = 2, status = CampaignStatus.ACTIVE)
        val w = MarketingPresenter.detail(worn, money).warnings
        assertEquals(3, w.size)
        assertTrue(w[0].contains("worn out"))
        assertTrue(MarketingPresenter.detail(c.copy(freshnessPpm = 900_000L, missedPayments = 0, daysRemaining = 30), money).warnings.isEmpty())
    }

    @Test fun suspendedCampaignsSayWhy() {
        val c = overview.campaigns.first().copy(status = CampaignStatus.SUSPENDED)
        assertTrue(MarketingPresenter.detail(c, money).warnings.any { it.contains("non-payment") })
    }

    @Test fun aRunningTestIsDescribed() {
        val sys = MarketingTestSupport.system()
        val camp = MarketingTestSupport.book(sys)
        sys.experiments.start(camp.id, app.many.economytycoon.marketing.CreativeTier.STUDIO, 10, Tick(0), MarketingTestSupport.bank).getOrThrow()
        val d = MarketingPresenter.detail(sys.readModel().campaign(camp, Tick(2)), money)
        assertTrue(d.test!!.contains("running"))
        assertFalse(d.actions.canRefresh)
    }

    @Test fun roiFactsAppearOnceMeasured() {
        val report = RoiAnalysis.analyse(List(10) { Money.units(100) }, List(10) { Money.units(1_500) }, Money.units(1_000), 300_000L)
        val c = s.readModel(roiOf = { report }).overview(acme, now).campaigns.first()
        val d = MarketingPresenter.detail(c, money)
        assertEquals(6, d.roi.size)
        assertEquals("Paid for itself", d.roi[0].value)
        assertEquals(Tone.GOOD, d.roi[0].tone)
        assertTrue(MarketingPresenter.detail(overview.campaigns.first(), money).roi.isEmpty())
    }

    @Test fun actionsKeepTheirCampaign() {
        val id = overview.campaigns.first().id
        for (a in listOf(CampaignAction.Pause(id), CampaignAction.Resume(id), CampaignAction.Extend(id, 30), CampaignAction.Cancel(id), CampaignAction.RefreshCreative(id))) {
            assertEquals(id, a.campaign)
        }
    }
}
