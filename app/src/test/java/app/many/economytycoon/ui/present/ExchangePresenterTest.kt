package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangePresenterTest {
    private fun listedSession(): app.many.economytycoon.session.GameSession {
        val s = PresentSupport.session("Float Works", 11L)
        val e = s.economy
        e.finance.mint(e.tick, e.finance.households, Money.units(50_000_000), "test:households")
        e.finance.mint(e.tick, e.companies.company(s.selectedCompany!!)!!.owner, Money.units(500_000), "test:capital")
        s.simulation.advance(31)
        return s
    }

    @Test fun anEmptyExchangeShowsTheIndexAtItsStart() {
        val s = PresentSupport.session("Quiet Works")
        val m = ExchangePresenter.build(s)!!
        assertEquals(4, m.tiles.size)
        assertEquals("100.00", m.tiles[0].value)
        assertTrue(m.board.isEmpty() && m.holdings.isEmpty())
        assertFalse(m.flotation.listed)
        assertNotNull(m.flotation.problem)
    }

    @Test fun aYoungCompanyIsToldWhyItCannotList() {
        val s = PresentSupport.session("Young Works")
        assertTrue(ExchangePresenter.flotation(s).problem!!.contains("traded for"))
    }

    @Test fun flotationPreviewDescribesTheIssue() {
        val s = listedSession()
        val f = ExchangePresenter.flotation(s, 250_000L)
        assertNull(f.problem)
        assertTrue(f.preview!!.startsWith("Sells "))
        assertEquals(5L, f.minPercent)
        assertEquals(49L, f.maxPercent)
    }

    @Test fun afterListingTheBoardShowsTheCompanyAsMine() {
        val s = listedSession()
        val r = s.issueNow(Command.ListCompany(s.selectedCompany!!, 250_000L))
        assertTrue(r.toString(), r.isOk)
        val m = ExchangePresenter.build(s)!!
        val row = m.board.single()
        assertTrue(row.mine)
        assertEquals(s.selectedCompany, row.id)
        assertTrue(m.flotation.listed)
        assertEquals("1", m.tiles[1].value)
        val d = ExchangePresenter.detail(s, row.id)!!
        assertTrue(d.mine && d.canDelist && d.canBuyBack)
        assertFalse(d.canBuy)
        assertEquals(7, d.facts.size)
    }

    @Test fun detailOfAnUnlistedCompanyIsNull() {
        val s = PresentSupport.session("None Works")
        assertNull(ExchangePresenter.detail(s, s.selectedCompany!!))
    }

    @Test fun quotesFlagTradesThePlayerCannotMake() {
        val s = listedSession()
        val me = s.selectedCompany!!
        s.issueNow(Command.ListCompany(me, 250_000L))
        assertNotNull(ExchangePresenter.quote(s, me, false, 5).problem)
        assertNull(ExchangePresenter.quote(s, me, true, 0).problem)
        val buy = ExchangePresenter.quote(s, me, true, 10)
        assertTrue(buy.total.startsWith("Pay"))
    }

    @Test fun changeTonesFollowTheSign() {
        assertEquals(Tone.GOOD, ExchangePresenter.changeTone(30_000L))
        assertEquals(Tone.BAD, ExchangePresenter.changeTone(-30_000L))
        assertEquals(Tone.NEUTRAL, ExchangePresenter.changeTone(1_000L))
    }
}
