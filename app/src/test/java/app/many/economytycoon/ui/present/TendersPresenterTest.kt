package app.many.economytycoon.ui.present

import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.tenders.Tender
import app.many.economytycoon.tenders.TenderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TendersPresenterTest {
    private val wheat = GoodId("wheat")

    private class Setup(val s: app.many.economytycoon.session.GameSession, val tender: Tender, val farm: app.many.economytycoon.production.Facility)

    private fun setup(stock: Long = 100): Setup {
        val s = PresentSupport.session("Tender Works", 31L)
        val e = s.economy
        val me = s.selectedCompany!!
        val region = e.world.regions().first().id
        val farm = e.production.grantFacility(me, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        if (stock > 0) farm.inventory.add(wheat, Quantity.units(stock), Money.units(stock * 8))
        val ref = e.content.good(wheat).referencePrice.perUnit
        val t = (e.tenders.issue(e.world.countries().first().id, wheat, Quantity.units(60), Money(ref.raw * 13 / 10), e.tick) as Outcome.Ok).value
        return Setup(s, t, farm)
    }

    @Test fun anOpenTenderAppearsWithItsTerms() {
        val x = setup()
        val m = TendersPresenter.build(x.s)!!
        assertEquals(4, m.tiles.size)
        assertEquals("1", m.tiles[0].value)
        val card = m.open.single()
        assertTrue(card.title.startsWith("Wheat for"))
        assertEquals("Open", card.status)
        assertTrue(card.canBid)
        assertFalse(card.canWithdraw)
        assertNull(card.yourBid)
        assertTrue(card.timing.startsWith("Bidding closes in 8 days"))
        assertTrue(card.value.startsWith("up to "))
    }

    @Test fun placingABidShowsItAndMarksTheLeader() {
        val x = setup()
        val price = TendersPresenter.suggestedPrice(x.s, x.tender.id)!!
        val r = x.s.issueNow(Command.BidTender(x.s.selectedCompany!!, x.tender.id, x.farm.id, price))
        assertTrue(r.toString(), r.isOk)
        val card = TendersPresenter.build(x.s)!!.open.single()
        assertNotNull(card.yourBid)
        assertEquals(1, card.bids)
        assertTrue(card.winning)
        assertTrue(card.canWithdraw)
        assertEquals("1", TendersPresenter.build(x.s)!!.tiles[1].value)
    }

    @Test fun theSuggestedPriceStaysInsideTheBand() {
        val x = setup()
        val p = TendersPresenter.suggestedPrice(x.s, x.tender.id)!!
        assertTrue(p >= x.s.economy.tenderRunner.floorFor(x.tender) && p <= x.tender.ceiling)
    }

    @Test fun anAwardedTenderShowsStockProgress() {
        val x = setup(stock = 30)
        val me = x.s.selectedCompany!!
        x.s.economy.tenders.bid(x.tender.id, me, x.farm.id, x.tender.ceiling, Money.ZERO, x.s.tick)
        x.s.economy.tenders.closeBidding(x.tender.closesAt) { true }
        val card = TendersPresenter.build(x.s)!!.won.single()
        assertEquals("Awarded", card.status)
        assertEquals(0.5, card.progress, 0.0001)
        assertTrue(card.progressText!!.contains("30"))
        assertFalse(card.canBid)
    }

    @Test fun facilitiesAreListedBestStockedFirst() {
        val x = setup(stock = 80)
        val e = x.s.economy
        val empty = e.production.grantFacility(x.s.selectedCompany!!, FacilityTypeId("grain_farm"), x.farm.region, null, null, null, e.tick)
        val list = TendersPresenter.facilitiesFor(x.s, x.tender.id)
        assertEquals(x.farm.id, list.first().id)
        assertTrue(list.first().enough)
        assertEquals(empty.id, list.last().id)
        assertFalse(list.last().enough)
        assertEquals("no stock yet", list.last().cost)
    }

    @Test fun finishedTendersMoveToHistory() {
        val x = setup()
        val me = x.s.selectedCompany!!
        x.s.economy.tenders.bid(x.tender.id, me, x.farm.id, x.tender.ceiling, Money.ZERO, x.s.tick)
        x.s.economy.tenders.closeBidding(x.tender.closesAt) { true }
        x.s.economy.tenders.delivered(x.tender, x.tender.closesAt, Money.units(100))
        val m = TendersPresenter.build(x.s)!!
        assertTrue(m.won.isEmpty())
        assertEquals("Delivered", m.history.single().status)
        assertEquals(Tone.GOOD, m.history.single().statusTone)
        assertTrue(m.tiles[3].value != "0.00")
    }

    @Test fun standingTextFollowsTheFailureRecord() {
        val x = setup()
        val me = x.s.selectedCompany!!
        assertNull(TendersPresenter.build(x.s)!!.standing)
        repeat(3) {
            val t = (x.s.economy.tenders.issue(x.tender.country, wheat, Quantity.units(1), x.tender.ceiling, x.s.tick) as Outcome.Ok).value
            x.s.economy.tenders.bid(t.id, me, x.farm.id, x.tender.ceiling, Money.ZERO, x.s.tick)
            x.s.economy.tenders.closeBidding(t.closesAt) { true }
            x.s.economy.tenders.failed(t, t.closesAt, Money.ZERO)
            x.s.economy.tenders.prune(t.closesAt)
        }
        assertTrue(TendersPresenter.build(x.s)!!.standing!!.startsWith("You are barred"))
        x.s.economy.tenders.issue(x.tender.country, wheat, Quantity.units(5), x.tender.ceiling, x.s.economy.tick)
        assertFalse(TendersPresenter.build(x.s)!!.open.single().canBid)
    }

    @Test fun everyStatusHasLabelAndTone() {
        for (st in TenderStatus.values()) {
            assertTrue(TendersPresenter.statusLabel(st).isNotBlank())
            TendersPresenter.statusTone(st)
        }
    }
}
