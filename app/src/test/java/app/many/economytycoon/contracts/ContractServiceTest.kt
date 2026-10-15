package app.many.economytycoon.contracts

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.ContractId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractServiceTest {
    private val a = CompanyId(1)
    private val b = CompanyId(2)
    private val c = CompanyId(3)
    private val wheat = GoodId("wheat")

    private fun terms(qty: Long = 10, price: Long = 20, every: Int = 5, n: Int = 4) =
        ContractTerms(wheat, Quantity.units(qty), Price(Money.units(price)), every, n)

    private fun service(params: ContractParams = ContractParams()) = ContractService(IdAllocators(), params)

    private fun open(s: ContractService, side: ContractSide = ContractSide.SELLER): SupplyContract =
        (s.propose(a, b, side, FacilityId(11), terms(), Tick(0)) as Outcome.Ok).value

    @Test fun termsReportTheirValue() {
        val t = terms(qty = 10, price = 20, n = 4)
        assertEquals(Money.units(200), t.deliveryValue)
        assertEquals(Money.units(800), t.totalValue)
    }

    @Test fun sellerAndBuyerFollowTheProposersSide() {
        val s = service()
        val selling = open(s, ContractSide.SELLER)
        assertEquals(a, selling.seller); assertEquals(b, selling.buyer)
        val buying = open(s, ContractSide.BUYER)
        assertEquals(b, buying.seller); assertEquals(a, buying.buyer)
        assertEquals(FacilityId(11), buying.buyerFacility)
        assertNull(buying.sellerFacility)
    }

    @Test fun badTermsAreRefusedWithTheRuleTheyBreak() {
        val s = service()
        assertEquals(Rules.INVALID_QUANTITY, s.checkTerms(terms(qty = 0), null)?.rule)
        assertEquals(Rules.INVALID_PRICE, s.checkTerms(terms(price = 0), null)?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, s.checkTerms(terms(every = 0), null)?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, s.checkTerms(terms(every = 91), null)?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, s.checkTerms(terms(n = 0), null)?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, s.checkTerms(terms(n = 361), null)?.rule)
        assertNull(s.checkTerms(terms(), null))
    }

    @Test fun priceMustStayNearWhatTheGoodIsWorth() {
        val s = service()
        val reference = Money.units(20)
        assertNull(s.checkTerms(terms(price = 20), reference))
        assertNull(s.checkTerms(terms(price = 5), reference))
        assertEquals(Rules.INVALID_PRICE, s.checkTerms(terms(price = 3), reference)?.rule)
        assertEquals(Rules.INVALID_PRICE, s.checkTerms(terms(price = 101), reference)?.rule)
    }

    @Test fun aCompanyCannotContractWithItself() {
        val r = service().propose(a, a, ContractSide.SELLER, FacilityId(1), terms(), Tick(0))
        assertEquals(Rules.INVALID_ARGUMENT, (r as Outcome.Rejected).rejection.rule)
    }

    @Test fun openProposalsAreCapped() {
        val s = service(ContractParams(maxOpenProposals = 2))
        open(s); open(s)
        val r = s.propose(a, b, ContractSide.SELLER, FacilityId(11), terms(), Tick(0))
        assertEquals(Rules.LIMIT_EXCEEDED, (r as Outcome.Rejected).rejection.rule)
    }

    @Test fun onlyTheCounterpartyCanAccept() {
        val s = service(); val k = open(s)
        assertEquals(Rules.NOT_OWNER, (s.accept(k.id, a, FacilityId(1), Tick(1)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.NOT_OWNER, (s.accept(k.id, c, FacilityId(1), Tick(1)) as Outcome.Rejected).rejection.rule)
        assertTrue(k.isOpen)
    }

    @Test fun acceptingStartsTheScheduleFromThatTick() {
        val s = service(); val k = open(s)
        val r = s.accept(k.id, b, FacilityId(22), Tick(3))
        assertTrue(r is Outcome.Ok)
        assertEquals(ContractStatus.ACTIVE, k.status)
        assertEquals(FacilityId(22), k.sellerFacility.let { k.buyerFacility })
        assertEquals(Tick(8), k.nextDue)
        assertEquals(Tick(3), k.startedAt)
    }

    @Test fun anAnsweredOfferCannotBeAnsweredAgain() {
        val s = service(); val k = open(s)
        s.accept(k.id, b, FacilityId(22), Tick(1))
        assertEquals(Rules.WRONG_STATE, (s.accept(k.id, b, FacilityId(22), Tick(2)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.WRONG_STATE, (s.decline(k.id, b, Tick(2)) as Outcome.Rejected).rejection.rule)
    }

    @Test fun decliningClosesTheOffer() {
        val s = service(); val k = open(s)
        assertTrue(s.decline(k.id, b, Tick(2)) is Outcome.Ok)
        assertEquals(ContractStatus.DECLINED, k.status)
        assertEquals(Tick(2), k.endedAt)
        assertTrue(s.proposalsFor(b).isEmpty())
    }

    @Test fun onlyTheProposerCanWithdrawAnOffer() {
        val s = service(); val k = open(s)
        assertEquals(Rules.NOT_OWNER, (s.cancel(k.id, b, Tick(1)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.NOT_OWNER, (s.cancel(k.id, c, Tick(1)) as Outcome.Rejected).rejection.rule)
        assertTrue(s.cancel(k.id, a, Tick(1)) is Outcome.Ok)
        assertEquals(ContractStatus.CANCELLED, k.status)
    }

    @Test fun eitherSideCanLeaveARunningContract() {
        val s = service(); val k = open(s)
        s.accept(k.id, b, FacilityId(22), Tick(1))
        assertTrue(s.cancel(k.id, b, Tick(4)) is Outcome.Ok)
        assertEquals(ContractStatus.CANCELLED, k.status)
        assertEquals(Rules.WRONG_STATE, (s.cancel(k.id, a, Tick(5)) as Outcome.Rejected).rejection.rule)
    }

    @Test fun cancelFeeIsAShareOfOneDelivery() {
        val s = service(); val k = open(s)
        assertEquals(Money.units(50), s.cancelFee(k))
    }

    @Test fun unansweredOffersLapse() {
        val s = service(ContractParams(proposalLifetime = 10)); val k = open(s)
        assertTrue(s.expire(Tick(9)).isEmpty())
        val events = s.expire(Tick(10))
        assertEquals(1, events.size)
        assertTrue(events.single() is ContractEvent.Expired)
        assertEquals(ContractStatus.EXPIRED, k.status)
    }

    @Test fun dueListsOnlyRunningContractsWhoseDateHasCome() {
        val s = service(); val k = open(s)
        assertTrue(s.due(Tick(100)).isEmpty())
        s.accept(k.id, b, FacilityId(22), Tick(0))
        assertTrue(s.due(Tick(4)).isEmpty())
        assertEquals(listOf(k.id), s.due(Tick(5)).map { it.id })
    }

    @Test fun deliveriesAdvanceTheScheduleAndFinishTheContract() {
        val s = service(); val k = open(s)
        s.accept(k.id, b, FacilityId(22), Tick(0))
        var tick = 5L
        repeat(3) {
            val ev = s.recordDelivery(k, Tick(tick), Money.units(200))
            assertTrue(ev is ContractEvent.Delivered)
            tick += 5
        }
        assertEquals(Tick(20), k.nextDue)
        val last = s.recordDelivery(k, Tick(20), Money.units(200))
        assertTrue(last is ContractEvent.Fulfilled)
        assertEquals(ContractStatus.FULFILLED, k.status)
        assertEquals(4, k.delivered)
        assertEquals(Money.units(800), k.paid)
        assertEquals(0, k.remainingDeliveries)
    }

    @Test fun missesCountAsStrikesUntilTheContractIsBreached() {
        val s = service(ContractParams(strikesToBreach = 3)); val k = open(s)
        s.accept(k.id, b, FacilityId(22), Tick(0))
        assertEquals(1, s.recordMiss(k, Tick(5), MissReason.SELLER_SHORT).size)
        assertEquals(1, s.recordMiss(k, Tick(10), MissReason.SELLER_SHORT).size)
        val third = s.recordMiss(k, Tick(15), MissReason.SELLER_SHORT)
        assertEquals(2, third.size)
        val breach = third.last() as ContractEvent.Breached
        assertEquals(a, breach.at)
        assertEquals(ContractStatus.BREACHED, k.status)
        assertEquals(3, k.missed)
    }

    @Test fun aDeliveryClearsTheStrikes() {
        val s = service(); val k = open(s)
        s.accept(k.id, b, FacilityId(22), Tick(0))
        s.recordMiss(k, Tick(5), MissReason.SELLER_SHORT)
        s.recordMiss(k, Tick(10), MissReason.SELLER_SHORT)
        s.recordDelivery(k, Tick(15), Money.units(200))
        assertEquals(0, k.strikes)
        assertEquals(2, k.missed)
        assertEquals(ContractStatus.ACTIVE, k.status)
    }

    @Test fun aBuyerWhoCannotPayIsTheOneBreaching() {
        val s = service(ContractParams(strikesToBreach = 1)); val k = open(s)
        s.accept(k.id, b, FacilityId(22), Tick(0))
        val breach = s.recordMiss(k, Tick(5), MissReason.BUYER_BROKE).last() as ContractEvent.Breached
        assertEquals(b, breach.at)
    }

    @Test fun endingACompanyCancelsWhatItWasPartOf() {
        val s = service(); val k = open(s)
        val other = (s.propose(c, b, ContractSide.SELLER, FacilityId(33), terms(), Tick(0)) as Outcome.Ok).value
        s.accept(k.id, b, FacilityId(22), Tick(0))
        val events = s.endAllOf(a, Tick(2))
        assertEquals(1, events.size)
        assertEquals(ContractStatus.CANCELLED, k.status)
        assertTrue(other.isOpen)
    }

    @Test fun oldEndedContractsArePrunedButLiveOnesStay() {
        val s = service(); val k = open(s); val live = open(s)
        s.decline(k.id, b, Tick(1))
        assertEquals(0, s.prune(Tick(100)))
        assertEquals(1, s.prune(Tick(1_000)))
        assertNull(s.get(k.id))
        assertNotNull(s.get(live.id))
    }

    @Test fun lookupsSeparateProposalsFromRunningContracts() {
        val s = service(); val k = open(s); open(s)
        s.accept(k.id, b, FacilityId(22), Tick(0))
        assertEquals(1, s.proposalsFor(b).size)
        assertEquals(1, s.proposalsBy(a).size)
        assertEquals(1, s.activeOf(a).size)
        assertEquals(2, s.of(b).size)
        assertEquals(k.id, s.active().single().id)
        assertEquals(c, k.other(a).let { c })
        assertFalse(k.involves(c))
    }

    @Test fun hashChangesWhenAContractDoes() {
        val s = service(); val k = open(s)
        fun h() = app.many.economytycoon.core.CanonicalHasher().also { s.hashInto(it) }.finish().toHex()
        val before = h()
        s.accept(k.id, b, FacilityId(22), Tick(0))
        assertTrue(before != h())
    }

    @Test fun unknownContractsAreRefused() {
        val s = service()
        assertEquals(Rules.UNKNOWN_ENTITY, (s.accept(ContractId(99), b, FacilityId(1), Tick(0)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, (s.decline(ContractId(99), b, Tick(0)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, (s.cancel(ContractId(99), b, Tick(0)) as Outcome.Rejected).rejection.rule)
    }
}
