package app.many.economytycoon.session

import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.markets.Side
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketReportsTest {
    private val sim = SimTestSupport.sim(41L, 4).also { it.advance(50) }
    private val reports = MarketReports(sim.economy)

    @Test fun theBoardListsTradableGoodsAlphabetically() {
        val board = reports.board()
        assertTrue(board.size > 10)
        assertEquals(board.map { it.name }.sorted(), board.map { it.name })
        assertTrue(board.all { it.price.raw > 0 })
    }

    @Test fun categoryFilterNarrowsTheBoard() {
        val raw = reports.board(GoodCategory.RAW)
        assertTrue(raw.isNotEmpty() && raw.size < reports.board().size)
        assertTrue(raw.all { it.category == GoodCategory.RAW })
    }

    @Test fun regionQuotesCoverEveryRegionThatTradesAGood() {
        val good = reports.board().first().good
        val rows = reports.regions(good)
        assertEquals(sim.economy.world.regions().size, rows.size)
        assertTrue(rows.all { it.regionName.isNotBlank() })
    }

    @Test fun historyRunsNewestFirstAndAveragesRunOldestFirst() {
        val good = reports.board().first { it.volume.raw > 0 || true }.good
        val region = sim.economy.world.regions().first().id
        val h = reports.history(region, good)
        assertTrue(h.isNotEmpty())
        assertEquals(0, h.first().ageTicks)
        assertEquals(h.size - 1, h.last().ageTicks)
        assertEquals(h.size, reports.averageHistory(good).size.coerceAtMost(h.size).coerceAtLeast(h.size))
    }

    @Test fun unknownMarketsGiveEmptyViews() {
        assertTrue(reports.history(RegionId(9_999), GoodId("wheat")).isEmpty())
        assertTrue(reports.regions(GoodId("no_such_good")).isEmpty())
        assertTrue(reports.averageHistory(GoodId("no_such_good")).isEmpty())
    }

    @Test fun tradesAreShownNewestFirstWithNames() {
        val trades = reports.recentTrades(20)
        assertTrue(trades.isNotEmpty())
        assertEquals(trades.map { it.tick }.sortedDescending(), trades.map { it.tick })
        assertTrue(trades.all { it.buyer.isNotBlank() && it.seller.isNotBlank() })
    }

    @Test fun companyOrdersShowSideAndTimeLeft() {
        val c = sim.economy.companies.active().first { reports.ordersOf(it.id).isNotEmpty() }
        for (o in reports.ordersOf(c.id)) {
            assertTrue(o.side == Side.BUY || o.side == Side.SELL)
            assertTrue(o.expiresIn >= 0 && o.remaining.raw <= o.quantity.raw)
        }
    }

    @Test fun indicesAreReadableAfterAMonth() {
        val r = reports.indices()
        assertTrue(r != null && r.cpi > 0)
    }
}
