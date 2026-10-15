package app.many.economytycoon.ui.present

import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.finance.DistressState
import app.many.economytycoon.session.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewPresenterTest {
    @Test fun theOverviewDescribesTheSelectedCompany() {
        val s = PresentSupport.session()
        val m = OverviewPresenter.build(s)!!
        assertEquals("Presenter Works", m.companyName)
        assertEquals(6, m.tiles.size)
        assertTrue(m.tiles.first { it.label == "Cash" }.value.startsWith("$"))
        assertTrue(m.leaders.any { it.player })
        assertTrue(m.leaders.first().rank == 1)
    }

    @Test fun trendsGrowAsTheGameRuns() {
        val s = PresentSupport.session()
        repeat(40) { s.step() }
        val m = OverviewPresenter.build(s)!!
        assertTrue(m.netWorthTrend.size >= 2)
        assertEquals(m.netWorthTrend.size, m.cashTrend.size)
    }

    @Test fun anEmptyGameHasNoOverview() {
        val s = PresentSupport.session()
        val sim = s.simulation
        assertNotNull(OverviewPresenter.build(s))
        assertEquals(1, s.playerCompanies().size)
        assertTrue(sim.tick.value == 0L)
    }

    @Test fun standingTextCombinesCreditAndDistress() {
        assertEquals("Credit AA", OverviewPresenter.standing(CreditRating.AA, DistressState.HEALTHY))
        assertEquals("Credit B · In distress", OverviewPresenter.standing(CreditRating.B, DistressState.DISTRESSED))
        assertEquals("No credit history yet", OverviewPresenter.standing(null, null))
    }

    @Test fun tonesFollowSeverityAndDistress() {
        assertEquals(Tone.BAD, OverviewPresenter.severityTone(Severity.CRITICAL))
        assertEquals(Tone.WARN, OverviewPresenter.distressTone(DistressState.WATCH))
        assertEquals(Tone.GOOD, OverviewPresenter.distressTone(null))
        assertEquals(Tone.BAD, Tones.bySign(-5)); assertEquals(Tone.GOOD, Tones.health(900_000)); assertEquals(Tone.WARN, Tones.health(500_000))
    }
}
