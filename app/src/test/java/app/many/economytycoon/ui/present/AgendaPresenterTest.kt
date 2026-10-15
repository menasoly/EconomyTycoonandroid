package app.many.economytycoon.ui.present

import app.many.economytycoon.agenda.Agenda
import app.many.economytycoon.agenda.AgendaItem
import app.many.economytycoon.agenda.AgendaKind
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaPresenterTest {
    private val money: (Money) -> String = { it.wholeUnits().toString() }

    @Test fun datesReadAsRelativeDays() {
        assertEquals("Today", AgendaPresenter.whenText(10, 10))
        assertEquals("Tomorrow", AgendaPresenter.whenText(11, 10))
        assertEquals("In 7 days", AgendaPresenter.whenText(17, 10))
        assertEquals("Today", AgendaPresenter.whenText(5, 10))
    }

    @Test fun rowsColourMoneyBySign() {
        val pay = AgendaPresenter.row(AgendaItem(3, AgendaKind.LOAN_PAYMENT, "Loan 1", "Payment 1 of 3", Money.units(-500), risky = true), money)
        assertEquals(Tone.WARN, pay.cashTone); assertEquals("-500", pay.cash); assertTrue(pay.risky)
        val got = AgendaPresenter.row(AgendaItem(3, AgendaKind.DELIVERY_OUT, "Wheat", "", Money.units(80)), money)
        assertEquals(Tone.GOOD, got.cashTone); assertEquals("+80", got.cash)
        val none = AgendaPresenter.row(AgendaItem(3, AgendaKind.TENDER_CLOSES, "Wheat", ""), money)
        assertNull(none.cash); assertEquals(Tone.NEUTRAL, none.cashTone); assertEquals("Tender closes", none.kind)
    }

    @Test fun aWarningAppearsOnlyWhenCashFallsShort() {
        val a = Agenda(0, 90, listOf(AgendaItem(5, AgendaKind.LOAN_PAYMENT, "Loan 1", "", Money.units(-1_000))))
        assertTrue(AgendaPresenter.warning(a, Money.units(400), money)!!.contains("more than"))
        assertNull(AgendaPresenter.warning(a, Money.units(5_000), money))
        assertNull(AgendaPresenter.warning(a, null, money))
        assertNull(AgendaPresenter.warning(Agenda(0, 90, emptyList()), Money.units(1), money))
    }

    @Test fun anEmptyAgendaStillHasTiles() {
        val s = PresentSupport.session("Agenda Presenter", 14L)
        val m = AgendaPresenter.build(s)!!
        assertEquals(4, m.tiles.size)
        assertTrue(m.days.isEmpty())
        assertNull(m.warning)
        assertEquals(AgendaPresenter.EMPTY, m.empty)
    }

    @Test fun aLoanShowsUpInTheModel() {
        val s = PresentSupport.session("Agenda Presenter", 15L)
        s.economy.finance.mint(s.economy.tick, s.economy.finance.bank, Money.units(5_000_000), "test:lender")
        assertTrue(s.issueNow(Command.Borrow(s.selectedCompany!!, Money.units(40_000), 90)) is Outcome.Ok)
        val m = AgendaPresenter.build(s)!!
        assertTrue(m.days.isNotEmpty())
        val rows = m.days.flatMap { it.rows }
        assertTrue(rows.any { it.kind == "Loan payment" && it.risky })
        assertEquals("Costly to miss", m.tiles[3].label)
        assertTrue(m.tiles[3].value.toInt() >= 1)
    }

    @Test fun theRouteRoundTrips() {
        assertEquals(Route.Agenda, Route.parse(Route.Agenda.key))
    }

    @Test fun everyKindIsListed() {
        assertEquals(AgendaKind.values().size, AgendaPresenter.kinds().size)
    }
}
