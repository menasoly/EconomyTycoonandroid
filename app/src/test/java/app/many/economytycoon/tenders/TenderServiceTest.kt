package app.many.economytycoon.tenders

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TenderServiceTest {
    private val service = TenderService(IdAllocators())
    private val country = CountryId(1)
    private val wheat = GoodId("wheat")
    private val a = CompanyId(1)
    private val b = CompanyId(2)
    private val c = CompanyId(3)

    private fun open(tick: Long = 0, qty: Long = 100, ceiling: Long = 30): Tender =
        (service.issue(country, wheat, Quantity.units(qty), Money.units(ceiling), Tick(tick)) as Outcome.Ok).value

    private fun bid(t: Tender, who: CompanyId, price: Long, tick: Long = 0, floor: Long = 5) =
        service.bid(t.id, who, FacilityId(who.value * 10), Money.units(price), Money.units(floor), Tick(tick))

    @Test fun aNewTenderIsOpenWithDeadlines() {
        val t = open(tick = 10)
        assertTrue(t.isOpen)
        assertEquals(Tick(18), t.closesAt)
        assertEquals(Tick(43), t.deliverBy)
        assertEquals(Money.units(3_000), t.ceilingValue)
        assertEquals(listOf(t), service.openIn(country))
    }

    @Test fun emptyOrPricelessTendersAreRefused() {
        assertEquals(Rules.INVALID_QUANTITY, (service.issue(country, wheat, Quantity.ZERO, Money.units(1), Tick(0)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_PRICE, (service.issue(country, wheat, Quantity.units(1), Money.ZERO, Tick(0)) as Outcome.Rejected).rejection.rule)
    }

    @Test fun aCountryCanOnlyHaveSoManyOpenTenders() {
        repeat(service.params.maxOpenPerCountry) { open() }
        val extra = service.issue(country, wheat, Quantity.units(1), Money.units(1), Tick(0)) as Outcome.Rejected
        assertEquals(Rules.LIMIT_EXCEEDED, extra.rejection.rule)
        assertTrue(service.issue(CountryId(2), wheat, Quantity.units(1), Money.units(1), Tick(0)) is Outcome.Ok)
    }

    @Test fun bidsMustBeInsideThePriceBand() {
        val t = open()
        assertEquals(Rules.INVALID_PRICE, (bid(t, a, 31) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_PRICE, (bid(t, a, 4) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_PRICE, (bid(t, a, 0) as Outcome.Rejected).rejection.rule)
        assertTrue(bid(t, a, 30) is Outcome.Ok)
    }

    @Test fun aSecondBidReplacesTheFirst() {
        val t = open()
        bid(t, a, 25); bid(t, a, 20)
        assertEquals(1, t.bids.size)
        assertEquals(Money.units(20), t.bidOf(a)!!.unitPrice)
    }

    @Test fun bidsCanBeWithdrawnOnlyWhilstOpen() {
        val t = open()
        bid(t, a, 25)
        assertTrue(service.withdraw(t.id, a) is Outcome.Ok)
        assertEquals(Rules.UNKNOWN_ENTITY, (service.withdraw(t.id, a) as Outcome.Rejected).rejection.rule)
        bid(t, a, 25)
        service.closeBidding(Tick(8)) { true }
        assertEquals(Rules.WRONG_STATE, (service.withdraw(t.id, a) as Outcome.Rejected).rejection.rule)
    }

    @Test fun nobodyCanBidOnceTheTimeIsUp() {
        val t = open()
        assertEquals(Rules.WRONG_STATE, (bid(t, a, 20, tick = 8) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, (service.bid(app.many.economytycoon.core.TenderId(99), a, FacilityId(1), Money.units(1), Money.ZERO, Tick(0)) as Outcome.Rejected).rejection.rule)
    }

    @Test fun theLowestEligibleBidWins() {
        val t = open()
        bid(t, a, 24); bid(t, b, 19); bid(t, c, 22)
        val events = service.closeBidding(Tick(8)) { true }
        val won = events.single() as TenderEvent.Awarded
        assertEquals(b, won.winner)
        assertEquals(Money.units(19), t.awardedPrice)
        assertTrue(t.isAwarded)
        assertEquals(FacilityId(20), t.winnerFacility)
    }

    @Test fun tiesGoToTheEarlierBid() {
        val t = open()
        bid(t, c, 20); bid(t, a, 20); bid(t, b, 20)
        service.closeBidding(Tick(8)) { true }
        assertEquals(c, t.winner)
    }

    @Test fun ineligibleBiddersAreSkipped() {
        val t = open()
        bid(t, a, 15); bid(t, b, 20)
        service.closeBidding(Tick(8)) { it.company != a }
        assertEquals(b, t.winner)
    }

    @Test fun noEligibleBidMeansNoAward() {
        val t = open(); val none = open()
        bid(t, a, 15)
        val events = service.closeBidding(Tick(8)) { false }
        assertEquals(2, events.size)
        assertTrue(events.all { it is TenderEvent.NoBids })
        assertEquals(TenderStatus.NO_BIDS, t.status)
        assertEquals(TenderStatus.NO_BIDS, none.status)
    }

    @Test fun biddingStaysOpenUntilItsCloseTick() {
        val t = open()
        bid(t, a, 15)
        assertTrue(service.closeBidding(Tick(7)) { true }.isEmpty())
        assertTrue(t.isOpen)
    }

    @Test fun repeatedFailuresBarACompanyForAWhile() {
        val failing = (1..3).map { open() }
        for (t in failing) { bid(t, a, 20); }
        service.closeBidding(Tick(8)) { true }
        for (t in failing) service.failed(t, Tick(30), Money.ZERO)
        assertEquals(3, service.failuresOf(a, Tick(40)))
        assertTrue(service.isBarred(a, Tick(40)))
        val fresh = open(tick = 40)
        assertEquals(Rules.REGULATION, (bid(fresh, a, 20, tick = 40) as Outcome.Rejected).rejection.rule)
        assertFalse(service.isBarred(a, Tick(30L + service.params.failureMemoryDays)))
    }

    @Test fun deliveryAndFailureCloseTheTender() {
        val t1 = open(); val t2 = open()
        bid(t1, a, 20); bid(t2, b, 20)
        service.closeBidding(Tick(8)) { true }
        val d = service.delivered(t1, Tick(12), Money.units(2_000)) as TenderEvent.Delivered
        assertEquals(a, d.winner)
        val f = service.failed(t2, Tick(40), Money.units(300)) as TenderEvent.Failed
        assertEquals(Money.units(300), f.penalty)
        assertEquals(TenderStatus.DELIVERED, t1.status)
        assertEquals(TenderStatus.FAILED, t2.status)
        assertEquals(2, service.history().size)
        assertTrue(service.awarded().isEmpty())
    }

    @Test fun committedMoneyCountsOpenCeilingsAndAwardedPrices() {
        val t1 = open(qty = 100, ceiling = 30)
        val t2 = open(qty = 10, ceiling = 30)
        assertEquals(Money.units(3_300), service.committed(country))
        bid(t2, a, 20)
        service.closeBidding(Tick(8)) { true }
        // t1 got no bids and drops out; t2 is held at the winning price.
        assertEquals(Money.units(200), service.committed(country))
        assertEquals(Money.ZERO, service.committed(CountryId(9)))
        service.delivered(t2, Tick(12), Money.units(200))
        assertEquals(Money.ZERO, service.committed(country))
        assertEquals(TenderStatus.NO_BIDS, t1.status)
    }

    @Test fun oldFinishedTendersArePruned() {
        val t = open(); bid(t, a, 20)
        service.closeBidding(Tick(8)) { true }
        service.delivered(t, Tick(12), Money.units(1))
        service.prune(Tick(12L + service.params.keepClosedDays - 1))
        assertEquals(1, service.all().size)
        service.prune(Tick(12L + service.params.keepClosedDays + 1))
        assertTrue(service.all().isEmpty())
    }

    @Test fun aCompanyHasALimitOnOpenBids() {
        val small = TenderService(IdAllocators(), TenderParams(maxBidsPerCompany = 2, maxOpenPerCountry = 10))
        val ts = (1..3).map { (small.issue(country, wheat, Quantity.units(1), Money.units(30), Tick(0)) as Outcome.Ok).value }
        assertTrue(small.bid(ts[0].id, a, FacilityId(1), Money.units(10), Money.ZERO, Tick(0)) is Outcome.Ok)
        assertTrue(small.bid(ts[1].id, a, FacilityId(1), Money.units(10), Money.ZERO, Tick(0)) is Outcome.Ok)
        assertEquals(Rules.LIMIT_EXCEEDED, (small.bid(ts[2].id, a, FacilityId(1), Money.units(10), Money.ZERO, Tick(0)) as Outcome.Rejected).rejection.rule)
        // Changing an existing bid is not a new bid.
        assertTrue(small.bid(ts[0].id, a, FacilityId(1), Money.units(9), Money.ZERO, Tick(0)) is Outcome.Ok)
    }

    @Test fun endingACompanyRemovesBidsAndFailsAwards() {
        val stillOpen = open(tick = 5)
        val won = open()
        bid(won, a, 15)
        service.closeBidding(Tick(8)) { true }
        bid(stillOpen, a, 20, tick = 6)
        val events = service.endAllOf(a, Tick(9))
        assertNull(stillOpen.bidOf(a))
        assertEquals(TenderStatus.FAILED, won.status)
        assertTrue(events.single() is TenderEvent.Failed)
        assertEquals(0, service.failuresOf(a, Tick(9)))
    }
}
