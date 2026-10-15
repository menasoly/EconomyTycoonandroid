package app.many.economytycoon.ui.present

import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.unions.DemandStatus
import app.many.economytycoon.unions.ResponseKind
import app.many.economytycoon.workforce.CohortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnionsPresenterTest {
    private val session by lazy { PresentSupport.session("Union Hall", 8L) }
    private val e get() = session.economy
    private val me get() = session.selectedCompany!!
    private val region get() = e.world.regionsOf(e.companies.company(me)!!.country).first().id

    private fun organised(): app.many.economytycoon.unions.Union {
        val k = e.workforce.ensure(CohortKey(me, FacilityId(1), e.content.allRoles.first().id, region))
        k.headcount = 45; k.moralePpm = 300_000L; k.target = 45
        val u = (e.unions.form(me, region, 45, session.tick) as Outcome.Ok).value
        u.strengthPpm = 500_000L
        return u
    }

    @Test fun withoutAUnionThePageSaysSoAndShowsFourTiles() {
        val m = UnionsPresenter.build(session)!!
        assertEquals(4, m.tiles.size)
        assertTrue(m.unions.isEmpty() && m.demands.isEmpty() && m.history.isEmpty())
        assertNotNull(m.note)
    }

    @Test fun anOrganisedGroupShowsWithItsMembersAndNextRound() {
        organised()
        val m = UnionsPresenter.build(session)!!
        assertNull(m.note)
        val card = m.unions.single()
        assertEquals("45 members", card.members)
        assertTrue(card.status.startsWith("Next wage round in"))
        assertEquals(Tone.WARN, card.strengthTone)
    }

    @Test fun anOpenDemandShowsTheCostAndWhatTheyWouldTake() {
        val u = organised()
        val d = e.unions.makeDemand(u, 60_000L, session.tick)
        val m = UnionsPresenter.build(session)!!
        val card = m.demands.single()
        assertEquals(d.id, card.id)
        assertTrue(card.deadline.startsWith("Answer within"))
        assertTrue(card.acceptableCounter.startsWith("They are unlikely to take less than"))
        assertEquals(Tone.WARN, m.tiles[2].tone)
        assertEquals("Demand waiting", m.unions.single().status)
    }

    @Test fun theDeadlineGetsUrgentInTheLastDays() {
        val u = organised(); val d = e.unions.makeDemand(u, 60_000L, session.tick)
        assertFalse(UnionsPresenter.demandCard(session, d, u).urgent)
        e.tick = app.many.economytycoon.core.Tick(d.deadline.value - 2)
        assertTrue(UnionsPresenter.demandCard(session, d, u).urgent)
    }

    @Test fun answeredDemandsMoveToHistory() {
        val u = organised(); val d = e.unions.makeDemand(u, 50_000L, session.tick)
        assertTrue(session.issueNow(Command.RespondDemand(me, d.id, ResponseKind.ACCEPT, 0L)).isOk)
        val m = UnionsPresenter.build(session)!!
        assertTrue(m.demands.isEmpty())
        val past = m.history.single()
        assertEquals(Tone.GOOD, past.tone)
        assertTrue(past.summary.contains("raise agreed"))
    }

    @Test fun everyStatusHasALabelAndATone() {
        for (s in DemandStatus.values()) assertTrue(UnionsPresenter.statusLabel(s).isNotBlank())
        assertEquals(Tone.BAD, UnionsPresenter.statusTone(DemandStatus.STRUCK))
        assertEquals(Tone.GOOD, UnionsPresenter.statusTone(DemandStatus.SETTLED))
    }

    @Test fun aStrikeShowsAsBad() {
        val u = organised()
        val d = e.unions.makeDemand(u, 60_000L, session.tick)
        e.unions.struck(d, u, session.tick, 8, 0L)
        val card = UnionsPresenter.unionCard(session, u)
        assertTrue(card.status.startsWith("On strike"))
        assertEquals(Tone.BAD, card.statusTone)
    }
}
