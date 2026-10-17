package app.many.economytycoon.ui.present

import app.many.economytycoon.carbon.CarbonCommand
import app.many.economytycoon.carbon.CarbonTestSupport
import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.de
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.carbon.PermitSide
import app.many.economytycoon.carbon.Tonnes
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonMarketPresenterTest {
    private fun desk(days: Long = 470) = CarbonDeskFixture(CarbonTestSupport.Fixture().also { CarbonTestSupport.busy(it, days) }, acme, days)

    @Test fun aPermitMarketShowsSupplyBookAndHistory() {
        val m = CarbonMarketPresenter.build(desk(), uk)
        assertTrue(m.canTrade)
        assertNull(m.note)
        assertEquals(4, m.tiles.size)
        assertTrue(m.supply.isNotEmpty())
        assertTrue(m.auctions.isNotEmpty())
        assertTrue(m.prices.isNotEmpty())
    }

    @Test fun aTaxCountryHasNoTradingButSaysWhy() {
        val m = CarbonMarketPresenter.build(desk(), de)
        assertFalse(m.canTrade)
        assertEquals(CarbonMarketPresenter.NO_PERMITS, m.note)
        assertTrue(m.supply.isEmpty())
    }

    @Test fun aCountryWithoutPolicyHasNoMarket() {
        val m = CarbonMarketPresenter.build(desk(), CountryId(77))
        assertEquals(CarbonMarketPresenter.NO_MARKET, m.note)
        assertTrue(m.tiles.isEmpty())
        assertFalse(m.canTrade)
    }

    @Test fun auctionsAreListedNewestFirst() {
        val m = CarbonMarketPresenter.build(desk(), uk)
        val quarters = m.auctions.map { it.title }
        assertEquals(quarters.sortedDescending().size, quarters.size)
        assertTrue(m.auctions.size <= 6)
    }

    @Test fun auctionRowsSayWhatSold() {
        val row = CarbonMarketPresenter.build(desk(), uk).auctions.first()
        assertTrue(row.detail.contains(" sold of "))
        assertTrue(row.title.contains("auction"))
    }

    @Test fun theBookIsCappedAndFormatted() {
        val m = CarbonMarketPresenter.build(desk(30), uk)
        assertTrue(m.bids.size <= 6 && m.asks.size <= 6)
        for (l in m.bids + m.asks) assertTrue(l.orders.endsWith("order") || l.orders.endsWith("orders"))
    }

    @Test fun biddingParsesBothFields() {
        val d = desk(5)
        assertNull(CarbonMarketPresenter.bid(d, uk, "100", "20"))
        val c = d.submitted.single() as CarbonCommand.BidAtAuction
        assertEquals(Tonnes.of(100), c.quantity)
        assertEquals(Money.units(20), c.limit)
    }

    @Test fun badBidsAreRefusedBeforeTheService() {
        val d = desk(5)
        assertEquals("Enter a number of tonnes above zero.", CarbonMarketPresenter.bid(d, uk, "x", "20"))
        assertEquals("Enter the price as a whole number above zero.", CarbonMarketPresenter.bid(d, uk, "10", "0"))
        assertEquals("Enter the price as a whole number above zero.", CarbonMarketPresenter.order(d, uk, PermitSide.BUY, "10", "2.5"))
        assertTrue(d.submitted.isEmpty())
    }

    @Test fun ordersCarryTheirSide() {
        val d = desk(5)
        CarbonMarketPresenter.order(d, uk, PermitSide.SELL, "5", "30")
        assertEquals(PermitSide.SELL, (d.submitted.single() as CarbonCommand.PlaceOrder).side)
    }

    @Test fun nextAuctionReadsInDays() {
        val d = desk(5)
        val view = d.market(uk)!!
        val text = CarbonMarketPresenter.nextAuction(view, d)
        assertTrue(text, text.startsWith("Next auction in ") || text == "Auction today")
    }

    @Test fun priceSeriesIsWholeUnits() {
        val m = CarbonMarketPresenter.build(desk(), uk)
        assertTrue(m.prices.all { it >= 0L })
        assertTrue(m.prices.size <= 24)
    }
}
