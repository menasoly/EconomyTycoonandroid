package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderMatcherTest {
    private var next = 1L
    private val book = ArrayList<PermitOrder>()
    private val settled = ArrayList<PermitTrade>()
    private val holdings = hashMapOf(acme to 1_000L, borea to 1_000L, cyrus to 1_000L)
    private val cash = hashMapOf<CompanyId, Long>(acme to 1_000_000_000L, borea to 1_000_000_000L, cyrus to 1_000_000_000L)

    private val capacity = object : OrderMatcher.Capacity {
        override fun canBuy(company: CompanyId, price: Money) = Tonnes(cash.getValue(company) * Tonnes.SCALE / price.raw)

        override fun canSell(company: CompanyId) = Tonnes.of(holdings.getValue(company))
        override fun settle(trade: PermitTrade) {
            settled += trade
            cash[trade.buyer] = cash.getValue(trade.buyer) - trade.value.raw
            cash[trade.seller] = cash.getValue(trade.seller) + trade.value.raw
            holdings[trade.seller] = holdings.getValue(trade.seller) - trade.tonnes.wholeTonnes()
            holdings[trade.buyer] = holdings.getValue(trade.buyer) + trade.tonnes.wholeTonnes()
        }
    }

    private fun order(company: CompanyId, side: PermitSide, tonnes: Long, limit: Long) =
        PermitOrder(PermitOrderId(next++), company, side, Money.units(limit), Tonnes.of(tonnes), Tick(0))

    private fun place(o: PermitOrder): OrderMatcher.Result {
        val r = OrderMatcher.cross(book, o, Tick(1), capacity)
        if (o.isOpen) book += o
        return r
    }

    @Test fun anOrderWithNothingToMatchRests() {
        val r = place(order(acme, PermitSide.SELL, 100, 20))
        assertTrue(r.trades.isEmpty())
        assertEquals(1, book.size)
    }

    @Test fun aBuyCrossesTheCheapestAskAtTheRestingPrice() {
        place(order(acme, PermitSide.SELL, 100, 25))
        place(order(borea, PermitSide.SELL, 100, 20))
        val r = place(order(cyrus, PermitSide.BUY, 150, 30))
        assertEquals(2, r.trades.size)
        assertEquals(Money.units(20), r.trades[0].price)
        assertEquals(Tonnes.of(100), r.trades[0].tonnes)
        assertEquals(borea, r.trades[0].seller)
        assertEquals(Money.units(25), r.trades[1].price)
        assertEquals(Tonnes.of(50), r.trades[1].tonnes)
    }

    @Test fun aSellCrossesTheHighestBid() {
        place(order(acme, PermitSide.BUY, 100, 18))
        place(order(borea, PermitSide.BUY, 100, 22))
        val r = place(order(cyrus, PermitSide.SELL, 120, 15))
        assertEquals(borea, r.trades[0].buyer)
        assertEquals(Money.units(22), r.trades[0].price)
        assertEquals(acme, r.trades[1].buyer)
        assertEquals(Tonnes.of(20), r.trades[1].tonnes)
    }

    @Test fun equalPricesAreFilledInTimeOrder() {
        place(order(acme, PermitSide.SELL, 50, 20))
        place(order(borea, PermitSide.SELL, 50, 20))
        val r = place(order(cyrus, PermitSide.BUY, 60, 20))
        assertEquals(listOf(acme, borea), r.trades.map { it.seller })
        assertEquals(Tonnes.of(10), r.trades[1].tonnes)
    }

    @Test fun ordersDoNotCrossWhenThePricesDoNotMeet() {
        place(order(acme, PermitSide.SELL, 100, 30))
        val r = place(order(borea, PermitSide.BUY, 100, 29))
        assertTrue(r.trades.isEmpty())
        assertEquals(2, book.size)
    }

    @Test fun aCompanyNeverTradesWithItself() {
        place(order(acme, PermitSide.SELL, 100, 20))
        val r = place(order(acme, PermitSide.BUY, 100, 25))
        assertTrue(r.trades.isEmpty())
    }

    @Test fun aPartialFillLeavesTheRestResting() {
        val ask = order(acme, PermitSide.SELL, 100, 20)
        place(ask)
        place(order(borea, PermitSide.BUY, 40, 20))
        assertEquals(Tonnes.of(60), ask.remaining)
        assertTrue(ask.isOpen)
        assertTrue(ask in book)
    }

    @Test fun aFilledRestingOrderLeavesTheBook() {
        val ask = order(acme, PermitSide.SELL, 40, 20)
        place(ask)
        place(order(borea, PermitSide.BUY, 40, 20))
        assertFalse(ask in book)
    }

    @Test fun aBuyerLimitedByCashOnlyTakesWhatItCanPayFor() {
        cash[borea] = Money.units(500).raw
        place(order(acme, PermitSide.SELL, 100, 20))
        val r = place(order(borea, PermitSide.BUY, 100, 20))
        assertEquals(Tonnes.of(25), Tonnes.sum(r.trades.map { it.tonnes }))
    }

    @Test fun aRestingSellerWhoHasSoldEverythingElsewhereIsDropped() {
        val ask = order(acme, PermitSide.SELL, 100, 20)
        place(ask)
        holdings[acme] = 0L
        val r = place(order(borea, PermitSide.BUY, 100, 25))
        assertTrue(r.trades.isEmpty())
        assertEquals(listOf(ask), r.dropped)
        assertFalse(ask in book)
    }

    @Test fun depthAggregatesByPriceBestFirst() {
        place(order(acme, PermitSide.SELL, 10, 25))
        place(order(borea, PermitSide.SELL, 20, 25))
        place(order(cyrus, PermitSide.SELL, 5, 22))
        val asks = OrderMatcher.depth(book, PermitSide.SELL)
        assertEquals(listOf(Money.units(22), Money.units(25)), asks.map { it.price })
        assertEquals(Tonnes.of(30), asks[1].tonnes)
        assertEquals(2, asks[1].orders)
        place(order(acme, PermitSide.BUY, 10, 10))
        place(order(borea, PermitSide.BUY, 10, 12))
        assertEquals(Money.units(12), OrderMatcher.depth(book, PermitSide.BUY).first().price)
    }

    @Test fun everyTradeConservesPermits() {
        place(order(acme, PermitSide.SELL, 300, 20))
        place(order(borea, PermitSide.BUY, 120, 21))
        place(order(cyrus, PermitSide.BUY, 500, 22))
        assertEquals(3_000L, holdings.values.sum())
        assertEquals(Tonnes.of(300), Tonnes.sum(settled.map { it.tonnes }))
    }
}
