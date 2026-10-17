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
import app.many.economytycoon.finance.Chart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonMarketTest {
    private val fx = CarbonTestSupport.Fixture()
    private val plants = listOf(emitter(1, acme), emitter(2, borea))
    private val market get() = fx.state.market(uk)!!

    private fun start() {
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.run(0, 5, plants)
    }

    private fun sell(c: app.many.economytycoon.core.CompanyId, t: Long, p: Long) = fx.exec(CarbonCommand.PlaceOrder(c, uk, PermitSide.SELL, Tonnes.of(t), Money.units(p)), 5)
    private fun buy(c: app.many.economytycoon.core.CompanyId, t: Long, p: Long) = fx.exec(CarbonCommand.PlaceOrder(c, uk, PermitSide.BUY, Tonnes.of(t), Money.units(p)), 5)

    @Test fun aSellOrderRestsUntilSomeoneBuys() {
        start()
        val e = sell(acme, 500, 25).value()
        assertTrue(e.trades.isEmpty())
        assertEquals(Money.units(25), market.bestAsk())
        assertNull(market.bestBid())
        assertEquals(Tonnes.of(500), market.reservedForSale(acme))
    }

    @Test fun aCrossingBuyTradesAtTheRestingPrice() {
        start()
        sell(acme, 500, 25).value()
        val e = buy(borea, 300, 30).value()
        assertEquals(1, e.trades.size)
        assertEquals(Money.units(25), e.trades.single().price)
        assertEquals(Tonnes.of(300), e.trades.single().tonnes)
        assertEquals(Tonnes.of(2_700), market.holdings.total(acme))
        assertEquals(Tonnes.of(3_300), market.holdings.total(borea))
        assertEquals(Tonnes.of(200), market.reservedForSale(acme))
    }

    @Test fun thePaymentGoesFromBuyerToSellerAtTheTradeValue() {
        start()
        sell(acme, 500, 25).value()
        val e = buy(borea, 300, 30).value()
        val pay = e.settlement.payments.single()
        assertEquals(borea.value, pay.payer.id)
        assertEquals(acme.value, pay.payee.id)
        assertEquals(Money.units(7_500), pay.total)
    }

    @Test fun sellingFreePermitsBooksTheWholePriceAsGain() {
        start()
        sell(acme, 100, 25).value()
        val e = buy(borea, 100, 25).value()
        val pay = e.settlement.payments.single()
        assertTrue(pay.lines.any { it.payeeCredit == Chart.REVENUE_OTHER && it.amount == Money.units(2_500) })
        assertTrue(e.settlement.adjustments.isEmpty())
    }

    @Test fun sellingBelowCostBooksALoss() {
        start()
        market.holdings.add(cyrus, 2000, Tonnes.of(100), Money.units(10_000))
        sell(cyrus, 100, 20).value()
        val e = buy(borea, 100, 20).value()
        assertEquals(1, e.settlement.adjustments.size)
        assertEquals(Money.units(8_000), e.settlement.adjustments.single().entries.first { it.amount.isPositive }.amount)
    }

    @Test fun theBuyerInheritsTheVintageAndPaysTheTradePriceAsCost() {
        start()
        market.holdings.add(acme, 2001, Tonnes.of(100), Money.units(1_000))
        sell(acme, 3_050, 25).value()
        buy(cyrus, 3_050, 25).value()
        val lots = market.holdings.lotsOf(cyrus)
        assertEquals(listOf(2000, 2001), lots.map { it.vintage })
        assertEquals(Tonnes.of(50), lots.first { it.vintage == 2001 }.tonnes)
        assertEquals(Tonnes.of(3_050).cost(Money.units(25)), Money.sum(lots.map { it.cost }))
    }

    @Test fun anOrderTakesMakesTheMarketPriceTheLastTrade() {
        start()
        sell(acme, 100, 22).value()
        buy(borea, 100, 22).value()
        assertEquals(Money.units(22), market.lastTradePrice)
        assertEquals(Money.units(22), fx.service.carbonPrice(uk, 2000))
        assertEquals(Tonnes.of(100), market.tradedVolume)
        assertEquals(1, market.trades().size)
    }

    @Test fun theCarbonPriceFallsBackOnTheStartingPriceBeforeAnyTrade() {
        start()
        assertEquals(CarbonDefaults.STARTING_PRICE, fx.service.carbonPrice(uk, 2000))
        assertEquals(Money.ZERO, fx.service.carbonPrice(CarbonTestSupport.de, 2000))
    }

    @Test fun anAuctionPriceBecomesTheReferenceUntilTheNextTrade() {
        start()
        fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(100), Money.units(50)), 5).value()
        fx.run(6, 15, plants)
        assertEquals(market.lastAuctionPrice, fx.service.carbonPrice(uk, 2000))
    }

    @Test fun anOrderNeedsPermitsToSellAndMoneyAndRoomToBuy() {
        start()
        assertEquals(Rules.INSUFFICIENT_STOCK, sell(cyrus, 10, 20).rule())
        assertEquals(Rules.INSUFFICIENT_STOCK, sell(acme, 3_001, 20).rule())
        fx.cash[borea] = Money.units(10)
        assertEquals(Rules.INSUFFICIENT_FUNDS, buy(borea, 100, 20).rule())
        fx.cash.remove(borea)
        assertEquals(Rules.LIMIT_EXCEEDED, buy(borea, 5_000, 20).rule())
    }

    @Test fun orderSizeAndPriceMustBeSensible() {
        start()
        assertEquals(Rules.INVALID_QUANTITY, fx.exec(CarbonCommand.PlaceOrder(acme, uk, PermitSide.SELL, Tonnes.ofKg(1), Money.units(20)), 5).rule())
        assertEquals(Rules.INVALID_PRICE, fx.exec(CarbonCommand.PlaceOrder(acme, uk, PermitSide.SELL, Tonnes.of(1), Money.ZERO), 5).rule())
    }

    @Test fun permitsAlreadyOfferedCannotBeOfferedTwice() {
        start()
        sell(acme, 2_000, 30).value()
        assertEquals(Rules.INSUFFICIENT_STOCK, sell(acme, 1_500, 31).rule())
        sell(acme, 1_000, 31).value()
    }

    @Test fun aCompanyMayOnlyRestSoManyOrders() {
        start()
        repeat(fx.params.maxOrdersPerCompany) { sell(acme, 10, 40L + it).value() }
        assertEquals(Rules.LIMIT_EXCEEDED, sell(acme, 10, 90).rule())
    }

    @Test fun anOrderCanBeWithdrawnOnlyByItsOwner() {
        start()
        sell(acme, 10, 40).value()
        val id = market.orders().single().id
        assertEquals(Rules.NOT_OWNER, fx.exec(CarbonCommand.CancelOrder(borea, uk, id), 5).rule())
        fx.exec(CarbonCommand.CancelOrder(acme, uk, id), 5).value()
        assertTrue(market.orders().isEmpty())
        assertEquals(Rules.UNKNOWN_ENTITY, fx.exec(CarbonCommand.CancelOrder(acme, uk, id), 5).rule())
    }

    @Test fun ordersDoNotCrossForTheSameCompany() {
        start()
        sell(acme, 100, 20).value()
        assertTrue(buy(acme, 100, 30).value().trades.isEmpty())
        assertEquals(2, market.orders().size)
    }

    @Test fun aBuyerCannotTradeBeyondTheHoldingLimitEvenViaARestingOrder() {
        start()
        val tight = capAndTrade().copy(holdingLimitPpm = 300_000L)
        fx.service.installRegime(tight, Tick(5)).value()
        assertEquals(Rules.LIMIT_EXCEEDED, buy(borea, 700, 20).rule())
    }

    @Test fun noTradingWithoutACapAndTradeRegime() {
        fx.service.installRegime(RegimePresets.carbonTax(uk, 2000, Money.units(10)), Tick(0)).value()
        fx.run(0, 3, plants)
        assertEquals(Rules.WRONG_STATE, fx.exec(CarbonCommand.PlaceOrder(acme, uk, PermitSide.SELL, Tonnes.of(10), Money.units(20))).rule())
        assertEquals(Rules.WRONG_STATE, fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(10), Money.units(20))).rule())
    }

    @Test fun permitsAreConservedAcrossATradingYear() {
        start()
        sell(acme, 1_000, 22).value(); buy(borea, 600, 23).value(); buy(cyrus, 700, 24).value()
        val before = market.holdings.totalAll()
        fx.run(6, 20, plants)
        assertEquals(before + market.supply(2000)!!.sold + market.supply(2000)!!.ceilingSold, market.holdings.totalAll())
    }
}
