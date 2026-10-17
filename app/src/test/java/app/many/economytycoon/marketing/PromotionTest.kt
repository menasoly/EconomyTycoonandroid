package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.MarketingTestSupport.acme
import app.many.economytycoon.marketing.MarketingTestSupport.beer
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.rejection
import app.many.economytycoon.marketing.MarketingTestSupport.request
import app.many.economytycoon.marketing.MarketingTestSupport.rival
import app.many.economytycoon.marketing.MarketingTestSupport.run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotionTest {
    private val params = MarketingParams()
    private val s = MarketingTestSupport.system()

    private fun promo(
        discount: Long = 100_000L, days: Int = 7, list: Long = 100, cost: Long = 60, elasticity: Long = 1_500_000L,
        clearance: Boolean = false, start: Tick? = null, company: app.many.economytycoon.core.CompanyId = acme,
    ) = PromotionRequest(company, beer, north, start, days, discount, Money.units(list), Money.units(cost), elasticity, clearance)

    // --- planner arithmetic ---

    @Test fun marginIsAShareOfPrice() {
        assertEquals(400_000L, PromotionPlanner.marginPpm(Money.units(100), Money.units(60)))
        assertEquals(-1_000_000L, PromotionPlanner.marginPpm(Money.ZERO, Money.units(1)))
        assertTrue(PromotionPlanner.marginPpm(Money.units(50), Money.units(60)) < 0L)
    }

    @Test fun aDiscountCutsTheMarginAndRaisesTheBreakEvenVolume() {
        val c = PromotionPlanner.check(Money.units(100), Money.units(60), 200_000L, 1_500_000L, 0L, params)
        assertEquals(Money.units(80), c.salePrice)
        assertEquals(400_000L, c.marginBeforePpm)
        assertEquals(250_000L, c.marginAfterPpm)
        assertEquals(1_000_000L, c.breakEvenUpliftPpm)
    }

    @Test fun upliftFollowsElasticityAndAwareness() {
        val unknown = PromotionPlanner.expectedUpliftPpm(1_500_000L, 200_000L, 0L)
        val known = PromotionPlanner.expectedUpliftPpm(1_500_000L, 200_000L, 1_000_000L)
        assertEquals(210_000L, unknown)
        assertTrue(known > unknown)
        assertEquals(0L, PromotionPlanner.expectedUpliftPpm(0L, 200_000L, 0L))
    }

    @Test fun aShallowCutOnAnElasticGoodPaysForItself() {
        val pays = PromotionPlanner.check(Money.units(100), Money.units(60), 100_000L, 6_000_000L, 500_000L, params)
        assertTrue(pays.pays)
        assertTrue(pays.profitChangePpm > 0L)
    }

    @Test fun aDeepCutOnAStickyGoodDoesNotPay() {
        val c = PromotionPlanner.check(Money.units(100), Money.units(60), 300_000L, 800_000L, 0L, params)
        assertFalse(c.pays)
        assertTrue(c.profitChangePpm < 0L)
        assertTrue(c.allowed)
    }

    @Test fun sellingBelowCostIsFlagged() {
        val c = PromotionPlanner.check(Money.units(100), Money.units(90), 200_000L, 1_500_000L, 0L, params)
        assertTrue(c.belowCost)
        assertFalse(c.pays)
        assertTrue(c.breakEvenUpliftPpm > 1_000_000L)
    }

    @Test fun marginFloorBlocksThinMargins() {
        val c = PromotionPlanner.check(Money.units(100), Money.units(77), 200_000L, 1_500_000L, 0L, params)
        assertTrue(c.belowFloor)
        assertFalse(c.allowed)
    }

    @Test fun aProductAlreadyAtOrUnderCostNeverBreaksEven() {
        val c = PromotionPlanner.check(Money.units(50), Money.units(60), 100_000L, 1_500_000L, 0L, params)
        assertEquals(0L, c.breakEvenUpliftPpm)
        assertEquals(0L, c.profitChangePpm)
    }

    // --- service ---

    @Test fun aValidPromotionRunsFromToday() {
        val l = s.promotions.launch(promo(), Tick(0)).getOrThrow()
        assertEquals(PromotionStatus.RUNNING, l.promotion.status)
        assertEquals(Money.units(90), l.promotion.salePrice)
        assertEquals(Tick(7), l.promotion.endTick)
        assertEquals(l.check.expectedUpliftPpm, l.promotion.expectedUpliftPpm)
    }

    @Test fun aLaterPromotionIsScheduled() {
        val l = s.promotions.launch(promo(start = Tick(5)), Tick(0)).getOrThrow()
        assertEquals(PromotionStatus.SCHEDULED, l.promotion.status)
    }

    @Test fun lengthAndDiscountAreBounded() {
        assertEquals(Rules.INVALID_ARGUMENT, rejection(s.promotions.launch(promo(days = 1), Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rejection(s.promotions.launch(promo(days = 90), Tick(0))))
        assertEquals(Rules.INVALID_PRICE, rejection(s.promotions.launch(promo(discount = 0L), Tick(0))))
        assertEquals(Rules.INVALID_PRICE, rejection(s.promotions.launch(promo(discount = 900_000L), Tick(0))))
    }

    @Test fun pricesMustBePositive() {
        assertEquals(Rules.INVALID_PRICE, rejection(s.promotions.launch(promo(list = 0), Tick(0))))
    }

    @Test fun cannotStartInThePast() {
        assertEquals(Rules.INVALID_ARGUMENT, rejection(s.promotions.launch(promo(start = Tick(1)), Tick(5))))
    }

    @Test fun belowCostIsRefusedUnlessItIsAClearance() {
        assertEquals(Rules.INVALID_PRICE, rejection(s.promotions.launch(promo(discount = 400_000L, cost = 70), Tick(0))))
        assertTrue(s.promotions.launch(promo(discount = 400_000L, cost = 70, clearance = true), Tick(0)) is Outcome.Ok)
    }

    @Test fun theMarginFloorRefusesThinSales() {
        assertEquals(Rules.INVALID_PRICE, rejection(s.promotions.launch(promo(cost = 77, discount = 200_000L), Tick(0))))
    }

    @Test fun onlyOnePromotionAtATimePerProduct() {
        s.promotions.launch(promo(), Tick(0))
        assertEquals(Rules.RATE_LIMITED, rejection(s.promotions.launch(promo(), Tick(1))))
        assertTrue(s.promotions.launch(promo(company = rival), Tick(1)) is Outcome.Ok)
    }

    @Test fun shoppersNeedTimeToForgetAnEndedSale() {
        s.promotions.launch(promo(days = 3), Tick(0))
        run(s, 0, 4)
        assertEquals(Rules.RATE_LIMITED, rejection(s.promotions.launch(promo(), Tick(5))))
        assertTrue(s.promotions.launch(promo(), Tick(3 + 14)) is Outcome.Ok)
    }

    @Test fun cancellingFreesTheProduct() {
        val p = s.promotions.launch(promo(), Tick(0)).getOrThrow().promotion
        s.promotions.cancel(p.id, Tick(1)).getOrThrow()
        assertEquals(PromotionStatus.CANCELLED, p.status)
        assertTrue(s.promotions.launch(promo(), Tick(2)) is Outcome.Ok)
        assertEquals(Rules.WRONG_STATE, rejection(s.promotions.cancel(p.id, Tick(3))))
        assertEquals(Rules.UNKNOWN_ENTITY, rejection(s.promotions.cancel(PromotionId(99), Tick(3))))
    }

    @Test fun priceOnDayShowsTheDiscountOnlyWhileItRuns() {
        s.promotions.launch(promo(days = 3, discount = 100_000L), Tick(0))
        assertEquals(Money.units(90), s.promotions.priceOn(acme, beer, north, Tick(1), Money.units(100)))
        assertEquals(Money.units(100), s.promotions.priceOn(acme, beer, north, Tick(3), Money.units(100)))
        assertEquals(Money.units(100), s.promotions.priceOn(rival, beer, north, Tick(1), Money.units(100)))
    }

    @Test fun previewDoesNotBookAnything() {
        s.promotions.preview(promo())
        assertTrue(s.state.promotions().isEmpty())
    }

    // --- effect on demand ---

    @Test fun demandRisesWhileOnSaleAndDipsAfterwards() {
        val p = s.promotions.launch(promo(days = 6, elasticity = 3_000_000L), Tick(0)).getOrThrow().promotion
        val during = s.promotions.multiplierPpm(acme, beer, north, Tick(2))
        val after = s.promotions.multiplierPpm(acme, beer, north, Tick(7))
        val later = s.promotions.multiplierPpm(acme, beer, north, Tick(30))
        assertEquals(1_000_000L + p.expectedUpliftPpm, during)
        assertTrue(after < 1_000_000L)
        assertEquals(1_000_000L, later)
    }

    @Test fun theDipNeverPassesHalfOfDemand() {
        val p = Promotion(PromotionId(1), acme, beer, north, Tick(0), 10, 400_000L, Money.units(100), Money.units(10), 9_000_000L)
        p.status = PromotionStatus.ENDED
        assertEquals(500_000L, PromotionPlanner.multiplierPpm(p, Tick(10), params))
    }

    @Test fun aCancelledPromotionHasNoEffect() {
        val p = s.promotions.launch(promo(), Tick(0)).getOrThrow().promotion
        s.promotions.cancel(p.id, Tick(1))
        assertEquals(1_000_000L, s.promotions.multiplierPpm(acme, beer, north, Tick(2)))
    }

    @Test fun theEngineStartsAndEndsPromotions() {
        val p = s.promotions.launch(promo(start = Tick(2), days = 3), Tick(0)).getOrThrow().promotion
        val reports = run(s, 0, 7)
        assertTrue(reports[2].events.any { it is MarketingEvent.PromotionStarted })
        assertTrue(reports[4].events.any { it is MarketingEvent.PromotionEnded })
        assertEquals(PromotionStatus.ENDED, p.status)
        assertEquals(Tick(4), p.endedAt)
    }

    @Test fun frequentSalesWearDownLoyalty() {
        book(s, request(daily = 1_500, tier = CreativeTier.BASIC))
        run(s, 0, 60)
        val before = s.state.stock(BrandKey(acme, beer, north))!!.loyaltyPpm
        assertTrue(before > 0L)
        s.promotions.launch(promo(days = 20, discount = 300_000L, cost = 30), Tick(60))
        val control = MarketingTestSupport.system()
        book(control, request(daily = 1_500, tier = CreativeTier.BASIC))
        run(control, 0, 60)
        run(s, 60, 20); run(control, 60, 20)
        assertTrue(s.state.stock(BrandKey(acme, beer, north))!!.loyaltyPpm < control.state.stock(BrandKey(acme, beer, north))!!.loyaltyPpm)
    }
}
