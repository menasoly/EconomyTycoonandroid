package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.grants.GrantKind
import app.many.economytycoon.grants.GrantProgram
import app.many.economytycoon.grants.GrantStatus
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantsPresenterTest {
    private fun announce(s: GameSession): GrantProgram {
        val e = s.economy
        val country = e.world.countries().first().id
        val r = e.grants.announce(country, GrantKind.JOBS, null, Money.units(150), Money.units(60_000), Money.units(200_000), e.tick)
        return (r as Outcome.Ok).value
    }

    @Test fun titlesNameTheKind() {
        val s = PresentSupport.session("Grant Works", 41L)
        val g = announce(s)
        assertEquals("New jobs", GrantsPresenter.title(g))
    }

    @Test fun everyStatusHasItsOwnLabel() {
        val labels = GrantStatus.values().map { GrantsPresenter.statusLabel(it) }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(Tone.GOOD, GrantsPresenter.statusTone(GrantStatus.PAID))
        assertEquals(Tone.MUTED, GrantsPresenter.statusTone(GrantStatus.NO_APPLICANTS))
    }

    @Test fun anOpenProgrammeOffersToApply() {
        val s = PresentSupport.session("Grant Works", 41L)
        announce(s)
        val m = GrantsPresenter.build(s)!!
        val row = m.open.single()
        assertTrue(row.canApply)
        assertFalse(row.canWithdraw)
        assertEquals("You have not applied.", row.standing)
        assertTrue(row.timing.startsWith("Applications close in"))
        assertEquals(4, row.terms.size)
        assertTrue(m.running.isEmpty())
    }

    @Test fun applyingMovesTheProgrammeToRunning() {
        val s = PresentSupport.session("Grant Works", 41L)
        val g = announce(s)
        val r = s.issueNow(Command.ApplyGrant(s.selectedCompany!!, g.id))
        assertTrue(r.toString(), r.isOk)
        val m = GrantsPresenter.build(s)!!
        assertTrue(m.open.isEmpty())
        val row = m.running.single()
        assertFalse(row.canApply)
        assertNotNull(row.standing)
        assertTrue(row.standing!!.startsWith("Applied"))
    }

    @Test fun withdrawingIsOnlyOfferedOnceApplicationsHaveClosed() {
        val s = PresentSupport.session("Grant Works", 41L)
        val g = announce(s)
        s.issueNow(Command.ApplyGrant(s.selectedCompany!!, g.id))
        s.economy.grants.closeApplications(app.many.economytycoon.core.Tick(s.tick.value + 11))
        val row = GrantsPresenter.build(s)!!.running.single()
        assertEquals("Counting growth", row.status)
        assertTrue(row.canWithdraw)
        assertTrue(row.standing!!.startsWith("Up "))
    }
}
