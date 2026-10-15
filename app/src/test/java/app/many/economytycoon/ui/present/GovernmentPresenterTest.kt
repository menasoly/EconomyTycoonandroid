package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.regulation.Fine
import app.many.economytycoon.regulation.LawStatus
import app.many.economytycoon.regulation.Lever
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GovernmentPresenterTest {
    private val session by lazy { PresentSupport.session("Politics Works", 8L) }
    private val e get() = session.economy
    private val me get() = session.selectedCompany!!
    private val country get() = e.world.country(e.companies.company(me)!!.country)

    private fun propose(l: Lever = Lever.CORPORATE_TAX, raise: Boolean = true) = run {
        e.regulationRunner.ensureGovernments(session.tick)
        val g = e.regulation.government(country.id)!!
        (e.regulation.propose(country, l, e.regulation.target(l, l.valueIn(country), raise, g.baselineWage)!!, session.tick) as Outcome.Ok).value
    }

    @Test fun theModelNamesTheGovernmentAndListsEveryRule() {
        val m = GovernmentPresenter.build(session)!!
        assertEquals(country.name, m.country)
        assertTrue(m.stance.isNotBlank() && m.stanceBlurb.isNotBlank())
        assertEquals(Lever.values().map { it.label }, m.rules.map { it.label })
        assertEquals(4, m.tiles.size)
        assertTrue(m.open.isEmpty() && m.coming.isEmpty() && m.fines.isEmpty())
        assertNull(m.standing)
    }

    @Test fun ruleValuesReadAsPercentagesOrDailyWages() {
        val m = GovernmentPresenter.build(session)!!
        assertTrue(m.rules.first { it.lever == Lever.CORPORATE_TAX }.value.endsWith("%"))
        assertTrue(m.rules.first { it.lever == Lever.MINIMUM_WAGE }.value.endsWith(" a day"))
    }

    @Test fun aLawBeforeParliamentShowsInTheListAndMarksItsRule() {
        val law = propose()
        val m = GovernmentPresenter.build(session)!!
        val card = m.open.single()
        assertEquals(law.id, card.id)
        assertTrue(card.title.startsWith("A rise to the corporate tax"))
        assertEquals("Debate", card.status); assertTrue(card.canLobby); assertTrue(card.timing.startsWith("Vote in"))
        assertNull(card.yours)
        val row = m.rules.first { it.lever == Lever.CORPORATE_TAX }
        assertNotNull(row.note); assertTrue(row.note!!.startsWith("Debate:"))
    }

    @Test fun lobbyingShowsInThePoolsAndOnTheCard() {
        val law = propose(Lever.SALES_TAX)
        assertTrue(session.issueNow(Command.LobbyLaw(me, law.id, true, Money.units(2_000))).isOk)
        val card = GovernmentPresenter.build(session)!!.open.single()
        assertEquals(session.settings.money(Money.units(2_000)), card.backing)
        assertEquals(session.settings.money(Money.ZERO), card.opposing)
        assertTrue(card.yours!!.startsWith("You back it"))
        assertTrue(session.issueNow(Command.LobbyLaw(me, law.id, false, Money.units(1_500))).isOk)
        assertTrue(GovernmentPresenter.build(session)!!.open.single().yours!!.contains("and oppose it"))
    }

    @Test fun outlookWordsFollowTheLeaning() {
        assertEquals("Likely to pass", GovernmentPresenter.outlook(700_000L).first)
        assertEquals("Leaning to pass", GovernmentPresenter.outlook(550_000L).first)
        assertEquals("Too close to call", GovernmentPresenter.outlook(450_000L).first)
        assertEquals("Likely to fail", GovernmentPresenter.outlook(100_000L).first)
        assertEquals(Tone.GOOD, GovernmentPresenter.outlook(700_000L).second); assertEquals(Tone.BAD, GovernmentPresenter.outlook(100_000L).second)
    }

    @Test fun finishedLawsMoveToHistoryWithTheirVerdict() {
        val a = propose(Lever.TARIFF); val b = propose(Lever.SALES_TAX)
        e.regulation.lobby(a.id, me, true, Money.units(200_000)); e.regulation.lobby(b.id, me, false, Money.units(200_000))
        e.regulation.vote(a.voteAt) { e.random.stream("regulation.test", it.value) }
        val m = GovernmentPresenter.build(session)!!
        assertTrue(m.open.isEmpty())
        assertEquals(setOf(LawStatus.PASSED, LawStatus.REJECTED), setOf(a.status, b.status))
        assertEquals(1, m.coming.size); assertEquals("Voted through", m.coming.single().outlook)
        assertEquals(listOf("Voted down"), m.history.map { it.outlook })
    }

    @Test fun finesAppearNewestFirstWithTheirDetail() {
        e.regulationRunner.ensureGovernments(session.tick)
        val f = e.production.facilitiesOf(me).firstOrNull()
        val id = f?.id ?: app.many.economytycoon.core.FacilityId(1)
        e.regulation.recordFine(Fine(me, id, country.id, Tick(10), 800_000L, 600_000L, Money.units(300), false))
        e.regulation.recordFine(Fine(me, id, country.id, Tick(40), 900_000L, 600_000L, Money.units(700), true))
        val m = GovernmentPresenter.build(session)!!
        assertEquals(2, m.fines.size)
        assertTrue(m.fines[0].closed && !m.fines[1].closed)
        assertTrue(m.fines[0].detail.contains("limit of"))
        assertEquals("2", m.tiles[3].value)
    }

    @Test fun lobbyBudgetIsTheCompanysCash() {
        val cash = e.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(me))
        assertEquals(cash, GovernmentPresenter.lobbyBudget(session))
    }

    @Test fun lawLabelsCoverEveryStatus() {
        for (s in LawStatus.values()) { assertTrue(GovernmentPresenter.statusLabel(s).isNotBlank()); GovernmentPresenter.statusTone(s) }
        assertFalse(GovernmentPresenter.stanceLabel(app.many.economytycoon.regulation.Stance.GREEN).isBlank())
    }
}
