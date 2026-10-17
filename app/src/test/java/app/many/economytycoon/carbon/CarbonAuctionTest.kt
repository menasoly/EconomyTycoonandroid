package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.capAndTrade
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.carbon.CarbonTestSupport.rule
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.carbon.CarbonTestSupport.value
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.OwnerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonAuctionTest {
    private val fx = CarbonTestSupport.Fixture()
    private val plants = listOf(emitter(1, acme), emitter(2, borea))

    private fun start(regime: CarbonRegime = capAndTrade()) {
        fx.service.installRegime(regime, Tick(0)).value()
        fx.run(0, 9, plants)
    }

    private val market get() = fx.state.market(uk)!!

    @Test fun theYearsSupplyIsOpenedWithItsFreeAndAuctionShares() {
        start()
        val s = market.supply(2000)!!
        assertEquals(Tonnes.of(12_000), s.cap)
        assertEquals(Tonnes.of(6_000), s.free)
        assertEquals(Tonnes.of(6_000), s.auctionPlanned)
        assertEquals(Tonnes.of(6_000), s.remaining)
    }

    @Test fun freePermitsGoToCoveredCompaniesInProportionToTheirMeasuredEmissions() {
        start()
        assertEquals(Tonnes.of(3_000), market.holdings.total(acme))
        assertEquals(Tonnes.of(3_000), market.holdings.total(borea))
        assertEquals(Money.ZERO, market.holdings.totalCost(acme))
        assertEquals(Tonnes.of(3_000), fx.state.compliance.account(acme, uk)!!.freeThisYear)
        assertEquals(Tonnes.of(6_000), market.supply(2000)!!.freeIssued)
        assertEquals(Tonnes.ZERO, market.pendingFree)
    }

    @Test fun aBiggerEmitterGetsAProportionallyBiggerFreeShare() {
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.run(0, 2, listOf(emitter(1, acme, coalUnits = 300), emitter(2, borea, coalUnits = 100)))
        assertEquals(Tonnes.of(4_500), market.holdings.total(acme))
        assertEquals(Tonnes.of(1_500), market.holdings.total(borea))
    }

    @Test fun uncoveredIndustriesAndSmallEmittersGetNoFreePermits() {
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.run(0, 2, listOf(emitter(1, acme), emitter(2, borea, family = app.many.economytycoon.content.IndustryFamily.FOOD), emitter(3, cyrus, coalUnits = 1)))
        assertEquals(Tonnes.of(6_000), market.holdings.total(acme))
        assertEquals(Tonnes.ZERO, market.holdings.total(borea))
        assertEquals(Tonnes.ZERO, market.holdings.total(cyrus))
    }

    @Test fun aBidNeedsAPriceAQuantityAndRoom() {
        start()
        assertEquals(Rules.INVALID_QUANTITY, fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.ofKg(10), Money.units(20))).rule())
        assertEquals(Rules.INVALID_PRICE, fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(10), Money.ZERO)).rule())
        assertEquals(Rules.LIMIT_EXCEEDED, fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(10_000), Money.units(20))).rule())
        fx.cash[acme] = Money.units(100)
        assertEquals(Rules.INSUFFICIENT_FUNDS, fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(100), Money.units(20))).rule())
        assertEquals(Rules.WRONG_STATE, fx.exec(CarbonCommand.BidAtAuction(acme, CarbonTestSupport.de, Tonnes.of(100), Money.units(20))).rule())
    }

    @Test fun aCompanyMayOnlyHaveSoManyBidsWaiting() {
        start()
        repeat(fx.params.maxBidsPerCompany) { assertNotNull(fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(10), Money.units(20))).value()) }
        assertEquals(Rules.LIMIT_EXCEEDED, fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(10), Money.units(20))).rule())
    }

    @Test fun aBidCanBeWithdrawnOnlyByItsOwner() {
        start()
        fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(10), Money.units(20)))
        val id = market.bids().single().id
        assertEquals(Rules.NOT_OWNER, fx.exec(CarbonCommand.CancelBid(borea, uk, id)).rule())
        fx.exec(CarbonCommand.CancelBid(acme, uk, id)).value()
        assertTrue(market.bids().isEmpty())
        assertEquals(Rules.UNKNOWN_ENTITY, fx.exec(CarbonCommand.CancelBid(acme, uk, id)).rule())
    }

    @Test fun theAuctionClearsOnTheFifteenthDayOfTheQuarterAndFillsTheBidders() {
        start()
        fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(1_000), Money.units(30)), 9)
        fx.exec(CarbonCommand.BidAtAuction(borea, uk, Tonnes.of(1_000), Money.units(20)), 9)
        fx.run(10, 15, plants)
        val r = market.auctions().single()
        assertEquals(0, r.quarter)
        assertEquals(Tonnes.of(1_500), r.supply)
        assertEquals(Money.units(20), r.price)
        assertEquals(Tonnes.of(1_500), r.sold)
        assertEquals(Tonnes.ZERO, r.unsold)
        assertEquals(2, r.bidders)
        assertEquals(Tonnes.of(4_000), market.holdings.total(acme))
        assertEquals(Tonnes.of(3_500), market.holdings.total(borea))
        assertTrue(market.bids().isEmpty())
        assertEquals(Money.units(20), market.lastAuctionPrice)
    }

    @Test fun winnersPayTheClearingPriceToTheGovernment() {
        start()
        fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(1_000), Money.units(30)), 9)
        fx.run(10, 15, plants)
        val pay = fx.payments.single { it.key.startsWith("carbon:auction") }
        assertEquals(Money.units(5_000), pay.total)
        assertEquals(OwnerKind.GOVERNMENT, pay.payee.kind)
        assertEquals(Money.units(5_000), fx.state.receipts(uk))
        assertEquals(Money.units(1_500), fx.state.greenFund(uk))
        assertEquals(Money.units(5_000), market.holdings.totalCost(acme))
    }

    @Test fun anAuctionWithoutBidsSendsEverythingToTheReserve() {
        start()
        fx.run(10, 15, plants)
        val r = market.auctions().single()
        assertEquals(Tonnes.of(1_500), r.unsold)
        assertEquals(Tonnes.of(1_500), market.reserve)
        assertTrue(market.prices().isEmpty())
        assertEquals(Money.ZERO, market.lastAuctionPrice)
    }

    @Test fun theFloorSetsThePriceWhenDemandIsThin() {
        start()
        fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(100), Money.units(50)), 9)
        fx.run(10, 15, plants)
        assertEquals(Money.units(5), market.auctions().single().price)
        assertEquals(Money.units(500), fx.payments.single { it.key.startsWith("carbon:auction") }.total)
    }

    @Test fun onlyOneAuctionIsHeldEachQuarterAndFourInAYear() {
        start()
        fx.run(10, 359, plants)
        assertEquals(listOf(15L, 105L, 195L, 285L), fx.eventsOf<CarbonEvent.AuctionCleared>().map { it.tick.value })
        assertEquals(4, market.supply(2000)!!.auctionsHeld)
        assertEquals(Tonnes.of(6_000), market.supply(2000)!!.offered)
    }

    @Test fun thePriceHistoryRecordsEachAuctionThatSold() {
        start()
        for (q in 0 until 3) {
            fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(100), Money.units(10L + q)), q * 90L + 9)
            fx.run(q * 90L + 10, q * 90L + 99, plants)
        }
        assertEquals(3, market.prices().size)
    }

    @Test fun aBidderWhoCannotPayAtClearingIsTrimmed() {
        start()
        fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(1_000), Money.units(30)), 9)
        fx.cash[acme] = Money.units(3_000)
        fx.run(10, 15, plants)
        val won = market.holdings.total(acme) - Tonnes.of(3_000)
        assertEquals(Tonnes.of(100), won)
    }

    @Test fun biddersKeepTheirHoldingLimit() {
        start(capAndTrade().copy(holdingLimitPpm = 300_000L))
        assertEquals(Rules.LIMIT_EXCEEDED, fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(1_000), Money.units(20))).rule())
    }

    @Test fun anInactiveCompanyCannotBid() {
        start()
        fx.inactive += acme
        assertEquals(Rules.UNKNOWN_ENTITY, fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(100), Money.units(20))).rule())
        assertNull(fx.state.market(CarbonTestSupport.de))
    }

    @Test fun theCeilingReserveTopsUpWhenBidsAtTheCeilingOutrunSupply() {
        start(capAndTrade().let { it.copy(steps = listOf(it.steps.single().copy(ceiling = Money.units(40)))) })
        fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(1_000), Money.units(70)), 9).value()
        fx.exec(CarbonCommand.BidAtAuction(borea, uk, Tonnes.of(1_000), Money.units(70)), 9).value()
        assertEquals(Rules.INVALID_PRICE, fx.exec(CarbonCommand.BidAtAuction(borea, uk, Tonnes.of(1_000), Money.units(90)), 9).rule())
        fx.run(10, 15, plants)
        val r = market.auctions().single()
        assertEquals(Money.units(40), r.price)
        assertEquals(Tonnes.of(500), r.ceilingSold)
        assertEquals(Tonnes.of(500), market.ceilingReleased)
        assertEquals(Tonnes.of(8_000), market.holdings.total(acme) + market.holdings.total(borea))
    }
}
