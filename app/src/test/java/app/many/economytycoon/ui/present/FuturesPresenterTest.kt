package app.many.economytycoon.ui.present

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.futures.PositionStatus
import app.many.economytycoon.futures.Side
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.FuturesTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuturesPresenterTest {
    private val session by lazy { PresentSupport.session("Forward Hall", 8L) }
    private val e get() = session.economy
    private val me get() = session.selectedCompany!!
    private val wheat = GoodId("wheat")
    private val ref get() = e.content.good(wheat).referencePrice.perUnit

    private fun open(side: Side = Side.LONG, contracts: Long = 4, tenor: Int = 30): app.many.economytycoon.futures.Position {
        FuturesTestSupport.setSpot(e, wheat, ref)
        return (e.futuresRunner.open(me, wheat, side, contracts, tenor, session.tick) as Outcome.Ok).value
    }

    @Test fun anEmptyBookHasFourTilesAndEveryGoodToChooseFrom() {
        val m = FuturesPresenter.build(session)!!
        assertEquals(4, m.tiles.size)
        assertTrue(m.positions.isEmpty() && m.history.isEmpty())
        assertEquals(e.content.allGoods.size, m.goods.size)
        assertEquals(m.goods.map { it.name }, m.goods.map { it.name }.sorted())
        assertEquals(e.futures.params.tenors, m.tenors)
    }

    @Test fun anOpenPositionShowsItsTermsAndHowFarItIsFromACall() {
        val p = open(Side.SHORT)
        val card = FuturesPresenter.build(session)!!.positions.single()
        assertEquals(p.id.value, card.id)
        assertTrue(card.title.startsWith("Sell 4 contracts of"))
        assertTrue(card.expires.startsWith("Settles in"))
        assertNull(card.call)
        assertTrue(card.cover in 0.0..1.0 && card.coverTone == Tone.GOOD)
    }

    @Test fun aProfitShowsGreenAndALossRed() {
        val p = open(Side.LONG)
        p.let { e.futures.markTo(it, Money(ref.raw * 103 / 100)) }
        assertEquals(Tone.GOOD, FuturesPresenter.card(session, p).resultTone)
        e.futures.markTo(p, Money(ref.raw * 97 / 100))
        assertEquals(Tone.BAD, FuturesPresenter.card(session, p).resultTone)
        e.futures.markTo(p, p.entry)
        assertEquals(Tone.NEUTRAL, FuturesPresenter.card(session, p).resultTone)
    }

    @Test fun aCalledPositionSaysHowMuchToAdd() {
        val p = open(Side.LONG)
        e.futures.markTo(p, Money(ref.raw * 92 / 100)); e.futures.call(p, session.tick)
        val card = FuturesPresenter.card(session, p)
        assertNotNull(card.call); assertTrue(card.call!!.startsWith("Margin call: add"))
        assertEquals(Tone.BAD, card.coverTone); assertTrue(card.shortfall.raw > 0L)
        assertEquals(Tone.BAD, FuturesPresenter.build(session)!!.tiles[3].tone)
    }

    @Test fun closedPositionsMoveToHistoryWithTheirResult() {
        val p = open()
        assertTrue(session.issueNow(Command.CloseFuture(me, p.id.value)).isOk)
        val m = FuturesPresenter.build(session)!!
        assertTrue(m.positions.isEmpty())
        val past = m.history.single()
        assertTrue(past.summary.startsWith("Closed early"))
        assertEquals(Tone.BAD, past.tone)
    }

    @Test fun everyStatusAndSideHasALabel() {
        for (s in PositionStatus.values()) assertTrue(FuturesPresenter.statusLabel(s).isNotBlank())
        assertEquals("Buy", FuturesPresenter.sideLabel(Side.LONG)); assertEquals("Sell", FuturesPresenter.sideLabel(Side.SHORT))
    }

    @Test fun marginLockedTileAddsUpAllPositions() {
        val a = open(); val b = open(Side.SHORT)
        val tile = FuturesPresenter.build(session)!!.tiles[1]
        assertEquals(session.settings.money(a.margin + b.margin), tile.value)
    }
}
