package app.many.economytycoon.markets

import app.many.economytycoon.core.CompanyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuctionTest {
    private var seq = 0L
    private fun bid(limit: Long, qty: Long, id: Long = seq) = BookEntry(null, Party.company(CompanyId(id)), limit, qty, seq++, 0)
    private fun ask(limit: Long, qty: Long, id: Long = seq) = BookEntry(null, Party.company(CompanyId(id)), limit, qty, seq++, 0)
    private val wide = 0L..Long.MAX_VALUE / 4

    @Test fun noCrossMeansNoTrade() {
        val r = Auction.clear(listOf(bid(90, 10)), listOf(ask(110, 10)), 100, wide.first, wide.last)
        assertEquals(0, r.volume)
        assertTrue(r.matches.isEmpty())
        assertEquals(10, r.unfilledBids)
    }

    @Test fun emptySideNeverTrades() {
        assertEquals(0, Auction.clear(emptyList(), listOf(ask(10, 1)), 10, 0, 100).volume)
        assertEquals(0, Auction.clear(listOf(bid(10, 1)), emptyList(), 10, 0, 100).volume)
    }

    @Test fun priceStaysAtReferenceInsideTheSpread() {
        val r = Auction.clear(listOf(bid(120, 10)), listOf(ask(80, 10)), 100, wide.first, wide.last)
        assertEquals(10, r.volume)
        assertEquals(100, r.price)
    }

    @Test fun priceIsPulledToTheFeasibleRange() {
        val r = Auction.clear(listOf(bid(120, 10)), listOf(ask(110, 10)), 100, wide.first, wide.last)
        assertEquals(110, r.price)
        val r2 = Auction.clear(listOf(bid(90, 10)), listOf(ask(80, 10)), 100, wide.first, wide.last)
        assertEquals(90, r2.price)
    }

    @Test fun excludedOrdersBoundThePrice() {
        // the second bid at 105 does not fit, so the price must stay at or above it to keep it out
        val bids = listOf(bid(130, 5), bid(105, 5))
        val asks = listOf(ask(90, 5), ask(120, 5))
        val r = Auction.clear(bids, asks, 95, wide.first, wide.last)
        assertEquals(5, r.volume)
        assertTrue("price ${r.price}", r.price in 105..120)
    }

    @Test fun volumeIsMaximisedAcrossLevels() {
        val bids = listOf(bid(110, 4), bid(105, 4), bid(100, 4))
        val asks = listOf(ask(95, 4), ask(100, 4), ask(108, 4))
        val r = Auction.clear(bids, asks, 100, wide.first, wide.last)
        assertEquals(8, r.volume)
    }

    @Test fun bestLimitsFillFirstAndTiesGoToTheOldest() {
        val early = bid(100, 5); val late = bid(100, 5); val best = bid(101, 5)
        val s = ask(90, 7)
        val r = Auction.clear(listOf(late, early, best), listOf(s), 100, wide.first, wide.last)
        assertEquals(7, r.volume)
        val byBuyer = r.matches.groupBy { it.buy }.mapValues { e -> e.value.sumOf { it.quantity } }
        assertEquals(5L, byBuyer[best])
        assertEquals(2L, byBuyer[early])
        assertEquals(null, byBuyer[late])
    }

    @Test fun marketOrdersCrossAnything() {
        val r = Auction.clear(listOf(bid(Long.MAX_VALUE, 3)), listOf(ask(0, 3)), 70, wide.first, wide.last)
        assertEquals(3, r.volume)
        assertEquals(70, r.price)
    }

    @Test fun bandLimitsTheClearingPrice() {
        val r = Auction.clear(listOf(bid(200, 10)), listOf(ask(150, 10)), 100, 95, 105)
        // price wants to be 150 but the band stops at 105 where the ask no longer accepts, so nothing trades
        assertEquals(0, r.volume)
    }

    @Test fun bandRespectedWhenOrdersAcceptIt() {
        val r = Auction.clear(listOf(bid(200, 10)), listOf(ask(50, 10)), 150, 95, 105)
        assertEquals(105, r.price)
        assertEquals(10, r.volume)
    }

    @Test fun everyMatchIsAtTheSamePrice() {
        val bids = listOf(bid(130, 3), bid(120, 3), bid(110, 3))
        val asks = listOf(ask(90, 2), ask(95, 4), ask(100, 3))
        val r = Auction.clear(bids, asks, 100, wide.first, wide.last)
        assertEquals(9, r.volume)
        assertEquals(9, r.matches.sumOf { it.quantity })
        for (m in r.matches) { assertTrue(m.buy.limit >= r.price); assertTrue(m.sell.limit <= r.price) }
    }
}
