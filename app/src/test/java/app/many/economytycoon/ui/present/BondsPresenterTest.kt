package app.many.economytycoon.ui.present

import app.many.economytycoon.bonds.DEALER
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BondsPresenterTest {
    private val session by lazy { PresentSupport.session("Gilt Works", 8L) }
    private val e get() = session.economy
    private val me get() = session.selectedCompany!!
    private val country get() = e.companies.company(me)!!.country

    private fun issue(units: Long = 400, coupon: Long = 55_000L) =
        (e.bonds.issue(country, 360, coupon, units, session.tick) as Outcome.Ok).value

    private fun place(units: Long): app.many.economytycoon.bonds.Bond {
        val b = issue()
        assertTrue(session.issueNow(Command.SubscribeBond(me, b.id, units)).isOk)
        e.tick = b.subscriptionEnds
        e.bondRunner.run(e.tick)
        return b
    }

    @Test fun anEmptyMarketShowsFourTilesAndNoLists() {
        val m = BondsPresenter.build(session)!!
        assertEquals(4, m.tiles.size)
        assertTrue(m.offers.isEmpty() && m.holdings.isEmpty() && m.forSale.isEmpty())
        assertTrue(m.rate.endsWith("%"))
    }

    @Test fun anOpenIssueShowsCouponPriceAndHowMuchYouCanBuy() {
        val b = issue()
        val o = BondsPresenter.build(session)!!.offers.single()
        assertEquals(b.id, o.id)
        assertTrue(o.title.endsWith("360-day bond")); assertEquals("5.5% a year", o.coupon)
        assertTrue(o.left.startsWith("400 of 400")); assertTrue(o.closes.startsWith("Closes in"))
        assertTrue(o.canSubscribe); assertNull(o.yours)
        val cash = e.finance.ledger.cash(e.companies.require(me).owner)
        assertEquals(minOf(400L, cash.raw / b.faceEach.raw), o.maxUnits)
    }

    @Test fun subscribingShowsYourStakeAndLaterTheHolding() {
        val b = issue()
        session.issueNow(Command.SubscribeBond(me, b.id, 25))
        assertEquals("You hold 25 units", BondsPresenter.build(session)!!.offers.single().yours)
        e.tick = b.subscriptionEnds; e.bondRunner.run(e.tick)
        val m = BondsPresenter.build(session)!!
        assertTrue(m.offers.isEmpty())
        val h = m.holdings.single()
        assertEquals(25L, h.units); assertTrue(h.canSell); assertTrue(h.income.contains("every 30 days")); assertTrue(h.matures.startsWith("Repaid in"))
        assertEquals("1", m.tiles[1].value)
    }

    @Test fun aHoldingsGainIsRelativeToTheDealersBid() {
        val b = place(30)
        val h = BondsPresenter.build(session)!!.holdings.single()
        assertTrue(h.gain.isNotBlank())
        val spreadLoss = b.faceEach.raw * 30 * e.bonds.params.dealerSpreadPpm / 1_000_000L
        val price = e.bondRunner.pricePpm(b, session.tick)
        assertTrue(price > 0L && spreadLoss > 0L)
        assertEquals(Tones.bySign(e.bondRunner.worth(me, session.tick).raw - b.costOf(me).raw), h.gainTone)
    }

    @Test fun bondsSoldToTheDealerAppearForSaleAtTheAsk() {
        val b = place(30)
        assertTrue(BondsPresenter.build(session)!!.forSale.isEmpty())
        session.issueNow(Command.TradeBond(me, b.id, 10, false))
        val s = BondsPresenter.build(session)!!.forSale.single()
        assertEquals(b.id, s.id); assertEquals(10L, s.units); assertTrue(s.yieldText.startsWith("Yields ")); assertTrue(s.canBuy)
        assertEquals(10L, b.heldBy(DEALER))
    }

    @Test fun anIssueYouCannotAffordOffersNoPurchase() {
        val b = issue()
        val cash = e.finance.ledger.cash(e.companies.require(me).owner)
        e.finance.ledger.post(
            app.many.economytycoon.finance.TransactionDraft(
                "test:drain", Tick(0), app.many.economytycoon.finance.ReasonCode.TRANSFER,
                listOf(
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(e.companies.require(me).owner, app.many.economytycoon.finance.Chart.CASH), -cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(e.companies.require(me).owner, app.many.economytycoon.finance.Chart.OTHER_EXPENSE), cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(app.many.economytycoon.finance.LedgerOwner.government(country), app.many.economytycoon.finance.Chart.CASH), cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(app.many.economytycoon.finance.LedgerOwner.government(country), app.many.economytycoon.finance.Chart.REVENUE_OTHER), -cash),
                ),
                "drain",
            ),
        )
        val o = BondsPresenter.build(session)!!.offers.single()
        assertFalse(o.canSubscribe); assertEquals(0L, o.maxUnits); assertEquals(b.id, o.id)
    }

    @Test fun titlesNameTheCountryAndLength() {
        val b = issue()
        assertEquals("${e.world.country(country).name} 360-day bond", BondsPresenter.title(session, b))
        assertNotNull(BondsPresenter.build(session))
    }
}
