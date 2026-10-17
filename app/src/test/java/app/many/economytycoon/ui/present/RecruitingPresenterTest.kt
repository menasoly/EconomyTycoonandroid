package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.talent.CovenantStance
import app.many.economytycoon.talent.OfferStatus
import app.many.economytycoon.talent.TalentCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecruitingPresenterTest {
    private val rival = CompanyId(2)

    @Test fun ourApproachesAndTheirsAreSeparated() {
        val (_, mine) = TalentViews.busy()
        val (_, theirs) = TalentViews.busy(who = rival)
        val a = RecruitingPresenter.build(mine); val b = RecruitingPresenter.build(theirs)
        assertEquals(1, a.hunting.size)
        assertTrue(a.againstUs.isEmpty())
        assertEquals(1, b.againstUs.size)
        assertTrue(b.againstUs.single().title.startsWith("Approach by "))
        assertTrue(a.hunting.single().title.startsWith("Approach to "))
    }

    @Test fun pendingApproachesShowACountdown() {
        val (_, v) = TalentViews.busy(seed = 3L)
        val sc2 = TalentViews.busy(seed = 3L).first
        val pending = RecruitingPresenter.build(v).hunting.single()
        if (pending.status == "Waiting for an answer") assertTrue(pending.timing.startsWith("Answer in"))
        else assertEquals("Settled", pending.timing)
        assertTrue(sc2.day > 0L)
    }

    @Test fun statusWordsAndTonesDependOnWhoIsAsking() {
        assertEquals(Tone.GOOD, RecruitingPresenter.statusTone(OfferStatus.SUCCEEDED, true))
        assertEquals(Tone.BAD, RecruitingPresenter.statusTone(OfferStatus.SUCCEEDED, false))
        assertEquals(Tone.GOOD, RecruitingPresenter.statusTone(OfferStatus.COUNTERED, false))
        assertEquals(Tone.MUTED, RecruitingPresenter.statusTone(OfferStatus.BLOCKED, true))
        assertEquals("Stopped by a non-compete", RecruitingPresenter.statusLabel(OfferStatus.BLOCKED))
        assertEquals("buying out non-competes", RecruitingPresenter.stanceLabel(CovenantStance.BUY_OUT))
    }

    @Test fun covenantsCanBeReleasedWhileActive() {
        val (_, v) = TalentViews.busy(who = rival)
        val c = RecruitingPresenter.build(v).covenants.single()
        assertTrue(c.release is TalentCommand.ReleaseNonCompete)
        assertEquals(Tone.INFO, c.tone)
        assertTrue(c.detail.contains("until"))
    }

    @Test fun counterPolicyToggleFlipsTheCurrentSetting() {
        val (_, v) = TalentViews.busy(who = rival)
        val m = RecruitingPresenter.build(v)
        assertEquals("On, up to 20.0% over current pay", m.counterPolicy.value)
        val t = m.toggleCounter as TalentCommand.SetCounterPolicy
        assertFalse(t.enabled)
        assertEquals(200_000L, t.maxMatchPpm)
        val (_, plain) = TalentViews.quiet()
        val off = RecruitingPresenter.build(plain)
        assertEquals("Off", off.counterPolicy.value)
        assertTrue((off.toggleCounter as TalentCommand.SetCounterPolicy).enabled)
    }

    @Test fun retentionPoolsAreListed() {
        val (_, v) = TalentViews.busy(who = rival)
        val m = RecruitingPresenter.build(v)
        assertEquals(1, m.retention.size)
        assertTrue(m.retention.single().value.startsWith("Bonus pool at 40.0%"))
    }

    @Test fun runningCampaignsCanBeCancelled() {
        val (_, v) = TalentViews.busy()
        val c = RecruitingPresenter.build(v).campaigns.single()
        assertEquals("Running", c.status)
        assertTrue(c.cancel is TalentCommand.CancelCampaign)
        assertTrue(c.title.startsWith("Recruiting "))
    }

    @Test fun suggestionsTargetOpenPostsWithoutACampaign() {
        val (sc, _) = TalentViews.quiet(30)
        sc.workforce.rows.getValue(sc.engineers1).target = 90L
        val v = app.many.economytycoon.talent.TalentReader(sc.system).view(TalentViews.company, app.many.economytycoon.core.Tick(sc.day), sc.inputs(), sc.world)
        val s = RecruitingPresenter.build(v).suggestions
        assertTrue(s.isNotEmpty())
        val first = s.first().launch as TalentCommand.LaunchCampaign
        assertEquals(sc.engineers1.role, first.role)
        assertEquals(90, first.days)
        assertTrue(s.size <= 3)
    }

    @Test fun noSuggestionOnceACampaignRunsForTheRole() {
        val (sc, _) = TalentViews.busy()
        sc.workforce.rows.getValue(sc.machinists1).target = 500L
        val v = app.many.economytycoon.talent.TalentReader(sc.system).view(TalentViews.company, app.many.economytycoon.core.Tick(sc.day), sc.inputs(), sc.world)
        val s = RecruitingPresenter.build(v).suggestions
        assertTrue(s.none { (it.launch as TalentCommand.LaunchCampaign).role == sc.machinists1.role })
    }

    @Test fun brandRowsGiveAwarenessAndAppeal() {
        val (_, v) = TalentViews.busy()
        val b = RecruitingPresenter.build(v).brands
        assertTrue(b.isNotEmpty())
        assertTrue(b.all { it.known.endsWith("know the name") && it.appeal.endsWith("appeal") })
    }

    @Test fun tilesCountWhatIsHappening() {
        val (_, v) = TalentViews.busy(who = rival)
        val m = RecruitingPresenter.build(v)
        assertEquals(listOf("Approaches against you", "Your approaches", "Non-competes", "Campaigns"), m.tiles.map { it.label })
        assertEquals("1", m.tiles[2].value)
    }

    @Test fun anEmptyCompanyHasNothingToShow() {
        val (_, v) = TalentViews.busy(who = CompanyId(50))
        val m = RecruitingPresenter.build(v)
        assertTrue(m.hunting.isEmpty() && m.againstUs.isEmpty() && m.covenants.isEmpty() && m.campaigns.isEmpty() && m.suggestions.isEmpty())
        assertNull(m.covenants.firstOrNull())
    }
}
