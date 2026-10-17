package app.many.economytycoon.ui.present

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.trade.TradeLeg
import app.many.economytycoon.trade.TradeSort
import app.many.economytycoon.ui.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradePresenterTest {
    private val session by lazy { PresentSupport.session("Trade Works", 8L).also { s -> repeat(30) { s.step() } } }

    private fun leg(border: Boolean = false, freight: Long = 50, tariff: Long = 0, buy: Long = 10, sell: Long = 20) = TradeLeg(
        GoodId("wheat"), "Wheat", RegionId(1), "Northvale", RegionId(2), "Eastport", Money.units(buy), Money.units(sell),
        Quantity.units(100), Money.units(freight), Money.units(tariff), 3, border,
    )

    @Test fun marginTones() {
        assertEquals(Tone.GOOD, TradePresenter.marginTone(300_000))
        assertEquals(Tone.INFO, TradePresenter.marginTone(100_000))
        assertEquals(Tone.NEUTRAL, TradePresenter.marginTone(10_000))
    }

    @Test fun aRowNamesTheRouteAndPrices() {
        val r = TradePresenter.row(session, leg())
        assertEquals("Northvale to Eastport", r.route)
        assertTrue(r.title.startsWith("Wheat"))
        assertTrue(r.prices.startsWith("Buy ") && r.prices.contains("sell"))
        assertTrue(r.timing.startsWith("3 days"))
        assertNull(r.note)
    }

    @Test fun borderCrossingsAreFlagged() {
        val r = TradePresenter.row(session, leg(border = true, tariff = 40))
        assertTrue(r.note!!.contains("tariff"))
    }

    @Test fun heavyCarriageIsFlagged() {
        val r = TradePresenter.row(session, leg(freight = 800, buy = 10, sell = 20))
        assertTrue(r.note!!.contains("more than half"))
    }

    @Test fun theModelAlwaysHasThreeTilesAndTheSizes() {
        val m = TradePresenter.build(session)
        assertEquals(3, m.tiles.size)
        assertEquals(TradePresenter.sizes, m.sizes)
        assertEquals(TradePresenter.EMPTY, m.empty)
    }

    @Test fun oneRowPerGoodAtMost() {
        val m = TradePresenter.build(session, 100, TradeSort.PROFIT, limit = 100)
        assertEquals(m.rows.size, m.rows.map { it.good }.toSet().size)
    }

    @Test fun theLimitCapsTheRows() {
        assertTrue(TradePresenter.build(session, limit = 2).rows.size <= 2)
    }

    @Test fun theRouteRoundTrips() {
        assertEquals(Route.Trade, Route.parse(Route.Trade.key))
    }
}
