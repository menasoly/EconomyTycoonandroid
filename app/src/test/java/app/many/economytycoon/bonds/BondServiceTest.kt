package app.many.economytycoon.bonds

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BondServiceTest {
    private val s = BondService(IdAllocators())
    private val country = CountryId(1)
    private fun bond(tenor: Int = 360, coupon: Long = 50_000L, units: Long = 500, tick: Long = 0): Bond =
        (s.issue(country, tenor, coupon, units, Tick(tick)) as Outcome.Ok).value

    @Test fun riskierAndLongerBorrowersPayMore() {
        val calm = s.marketYieldPpm(40_000L, 1_000_000L, 180)
        assertTrue(s.marketYieldPpm(40_000L, 300_000L, 180) > calm)
        assertTrue(s.marketYieldPpm(40_000L, 1_000_000L, 720) > calm)
        assertTrue(s.marketYieldPpm(80_000L, 1_000_000L, 180) > calm)
        assertEquals(40_000L + 2_000L, calm)
    }

    @Test fun couponRoundsUpToATenthOfAPercentAndAddsThePremium() {
        assertEquals(46_000L + s.params.premiumPpm, s.couponFor(45_100L))
        assertEquals(45_000L + s.params.premiumPpm, s.couponFor(45_000L))
    }

    @Test fun priceIsParWhenCouponMatchesTheYield() {
        val b = bond(); val t = b.subscriptionEnds
        assertEquals(1_000_000L, s.pricePpm(b, t, 50_000L))
    }

    @Test fun higherYieldsMeanLowerPricesAndViceVersa() {
        val b = bond(); val t = b.subscriptionEnds
        assertTrue(s.pricePpm(b, t, 70_000L) < 1_000_000L)
        assertTrue(s.pricePpm(b, t, 30_000L) > 1_000_000L)
        assertTrue(s.pricePpm(b, t, 90_000L) < s.pricePpm(b, t, 70_000L))
    }

    @Test fun longerBondsMoveMoreWithTheYield() {
        val short = bond(tenor = 180); val long = bond(tenor = 720)
        val dShort = 1_000_000L - s.pricePpm(short, short.subscriptionEnds, 80_000L)
        val dLong = 1_000_000L - s.pricePpm(long, long.subscriptionEnds, 80_000L)
        assertTrue(dLong > dShort * 3)
    }

    @Test fun priceDriftsBackToParAsMaturityApproaches() {
        val b = bond(); val far = s.pricePpm(b, b.subscriptionEnds, 80_000L)
        val near = s.pricePpm(b, Tick(b.maturesAt.value - 30), 80_000L)
        assertTrue(near > far)
        assertEquals(1_000_000L, s.pricePpm(b, b.maturesAt, 80_000L))
        assertEquals(1_000_000L, s.pricePpm(b, Tick(b.maturesAt.value + 50), 5_000L))
    }

    @Test fun pricesAreClamped() {
        val b = bond(tenor = 720)
        assertEquals(s.params.minPricePpm, s.pricePpm(b, b.subscriptionEnds, 900_000L))
        assertEquals(s.params.maxPricePpm, s.pricePpm(bond(coupon = 900_000L, tenor = 720), b.subscriptionEnds, 1_000L))
    }

    @Test fun theDealerBuysLowAndSellsHigh() {
        val b = bond()
        s.addSold(b, 1); s.allot(b, CompanyId(1), 1, b.faceEach)
        s.closeSubscriptions(b.subscriptionEnds) { Money.ZERO }
        val ask = (s.quote(b, true, 10, b.subscriptionEnds, 50_000L) as Outcome.Ok).value
        val bid = (s.quote(b, false, 10, b.subscriptionEnds, 50_000L) as Outcome.Ok).value
        assertEquals(1_000_000L + s.params.dealerSpreadPpm, ask.pricePpm); assertEquals(1_000_000L - s.params.dealerSpreadPpm, bid.pricePpm)
        assertEquals(Money(ask.pricePerUnit.raw * 10), ask.total)
        assertTrue(ask.total > bid.total)
    }

    @Test fun quotesNeedATradingBondAndARealQuantity() {
        val open = bond()
        assertEquals(Rules.WRONG_STATE, (s.quote(open, true, 1, Tick(0), 50_000L) as Outcome.Rejected).rejection.rule)
        s.addSold(open, 5); s.closeSubscriptions(open.subscriptionEnds) { Money.ZERO }
        assertEquals(Rules.INVALID_QUANTITY, (s.quote(open, true, 0, open.subscriptionEnds, 50_000L) as Outcome.Rejected).rejection.rule)
    }

    @Test fun issuesAreValidated() {
        assertEquals(Rules.INVALID_ARGUMENT, (s.issue(country, 100, 50_000L, 500, Tick(0)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_QUANTITY, (s.issue(country, 360, 50_000L, s.params.minUnits - 1, Tick(0)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_QUANTITY, (s.issue(country, 360, 50_000L, s.params.maxUnits + 1, Tick(0)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_PRICE, (s.issue(country, 360, 0L, 500, Tick(0)) as Outcome.Rejected).rejection.rule)
        assertTrue(s.all().isEmpty())
    }

    @Test fun aNewIssueIsOpenWithADeadlineAndAMaturity() {
        val b = bond(tenor = 360, tick = 10)
        assertTrue(b.isOpen); assertEquals(Tick(10L + s.params.subscriptionDays), b.subscriptionEnds)
        assertEquals(Tick(10L + s.params.subscriptionDays + 360), b.maturesAt)
        assertEquals(500L, b.unitsLeft)
    }

    @Test fun subscriptionsAreChecked() {
        val b = bond()
        assertNull(s.checkSubscription(b, CompanyId(1), 10))
        assertEquals(Rules.INVALID_QUANTITY, s.checkSubscription(b, CompanyId(1), 0)!!.rule)
        assertEquals(Rules.INSUFFICIENT_STOCK, s.checkSubscription(b, CompanyId(1), 501)!!.rule)
        s.addSold(b, 500)
        assertEquals(Rules.INSUFFICIENT_STOCK, s.checkSubscription(b, CompanyId(1), 1)!!.rule)
        s.closeSubscriptions(b.subscriptionEnds) { Money.ZERO }
        assertEquals(Rules.WRONG_STATE, s.checkSubscription(b, CompanyId(1), 1)!!.rule)
    }

    @Test fun onlySoManyInvestorsCanJoinOneIssue() {
        val b = bond(units = 2_000)
        for (i in 1..s.params.maxHoldersPerBond) s.allot(b, CompanyId(i.toLong()), 1, b.faceEach)
        assertEquals(Rules.LIMIT_EXCEEDED, s.checkSubscription(b, CompanyId(999), 1)!!.rule)
        assertNull(s.checkSubscription(b, CompanyId(1), 1))
    }

    @Test fun takingUnitsTakesTheirShareOfTheCost() {
        val b = bond(); val c = CompanyId(3)
        s.allot(b, c, 10, Money.units(9_000)); s.allot(b, c, 10, Money.units(11_000))
        assertEquals(20L, b.heldBy(c)); assertEquals(Money.units(20_000), b.costOf(c))
        assertEquals(Money.units(5_000), s.take(b, c, 5))
        assertEquals(15L, b.heldBy(c)); assertEquals(Money.units(15_000), b.costOf(c))
        assertEquals(Money.units(15_000), s.take(b, c, 99))
        assertTrue(b.holderIds().isEmpty())
        assertEquals(Money.ZERO, s.take(b, c, 1))
    }

    @Test fun closingWithNoBuyersCancelsAndWithBuyersStartsTheBond() {
        val empty = bond(); val sold = bond()
        s.addSold(sold, 40); s.allot(sold, CompanyId(1), 40, Money.units(40_000))
        assertTrue(s.closeSubscriptions(Tick(empty.subscriptionEnds.value - 1)) { Money.ZERO }.isEmpty())
        val events = s.closeSubscriptions(empty.subscriptionEnds) { it.faceTotal }
        assertEquals(2, events.size)
        assertEquals(BondStatus.CANCELLED, empty.status); assertEquals(BondStatus.OUTSTANDING, sold.status)
        assertTrue(events.any { it is BondEvent.NoSale && it.bond == empty.id })
        val placed = events.filterIsInstance<BondEvent.Placed>().single()
        assertEquals(40L, placed.units); assertEquals(Money.units(40_000), placed.raised)
    }

    @Test fun couponsFallOnTheScheduleBeforeMaturity() {
        val b = bond(tenor = 180); s.addSold(b, 10); s.closeSubscriptions(b.subscriptionEnds) { Money.ZERO }
        val start = b.subscriptionEnds.value
        assertTrue(s.couponDue(Tick(start)).isEmpty())
        assertTrue(s.couponDue(Tick(start + 29)).isEmpty())
        assertEquals(listOf(b), s.couponDue(Tick(start + 30)))
        assertEquals(listOf(b), s.couponDue(Tick(start + 150)))
        assertTrue("maturity pays through redemption", s.couponDue(Tick(start + 180)).isEmpty())
        assertEquals(listOf(b), s.maturing(Tick(start + 180)))
        assertTrue(s.maturing(Tick(start + 179)).isEmpty())
    }

    @Test fun aCouponIsAMonthsShareOfTheYearlyRate() {
        val b = bond(coupon = 60_000L)
        val per = s.couponPerUnit(b)
        assertTrue(per.raw in 49_999L..50_001L)
    }

    @Test fun owedCountsOffersInFullAndSoldBondsAtTheirFace() {
        val a = bond(units = 300); val b = bond(units = 400)
        assertEquals(Money(a.faceEach.raw * 700), s.owed(country))
        s.addSold(a, 100); s.closeSubscriptions(a.subscriptionEnds) { Money.ZERO }
        assertEquals(BondStatus.CANCELLED, b.status)
        assertEquals(Money(a.faceEach.raw * 100), s.owed(country))
        assertEquals(Money.ZERO, s.owed(CountryId(7)))
        s.markRedeemed(a, Tick(900))
        assertEquals(Money.ZERO, s.owed(country))
    }

    @Test fun redeemedBondsStopCountingAndOldHistoryIsTrimmed() {
        val first = bond(units = 300); s.addSold(first, 300); s.closeSubscriptions(first.subscriptionEnds) { Money.ZERO }
        s.markRedeemed(first, Tick(5))
        repeat(s.params.historyLength + 5) { val x = bond(); s.addSold(x, 0); x.status = BondStatus.REDEEMED; x.endedAt = Tick(10L + it) }
        s.prune()
        assertEquals(s.params.historyLength, s.history().size)
        assertTrue(s.get(first.id) == null)
    }

    @Test fun aWoundUpCompanysBondsPassToTheDealer() {
        val b = bond(); val c = CompanyId(5)
        s.allot(b, c, 7, Money.units(7_000)); s.allot(b, DEALER, 3, Money.units(2_900))
        s.transferAll(c)
        assertEquals(0L, b.heldBy(c)); assertEquals(10L, b.heldBy(DEALER)); assertEquals(Money.units(9_900), b.costOf(DEALER))
    }

    @Test fun theHashCoversHoldingsAndStatus() {
        fun h() = app.many.economytycoon.core.CanonicalHasher().also { s.hashInto(it) }.finish()
        val b = bond(); val base = h()
        s.allot(b, CompanyId(1), 5, Money.units(5_000))
        val held = h(); assertTrue(held != base)
        s.addSold(b, 5); s.closeSubscriptions(b.subscriptionEnds) { Money.ZERO }
        assertTrue(h() != held)
        s.dealerFunded = true
        assertNotNull(s.get(b.id)); assertTrue(h() != held)
    }
}
