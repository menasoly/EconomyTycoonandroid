package app.many.economytycoon.companies

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliciesTest {
    private val f = FacilityId(1)
    private val grain = GoodId("grain")
    private val market = Price.of(100)
    private val cost = Money.units(60)

    private fun plan(rule: SellRule, avail: Long = 100, brand: Long = 0) =
        RulePlanner.sellPlan(rule, Quantity.units(avail), cost, market, brand)

    @Test fun marketStrategyAddsMarkup() {
        val p = plan(SellRule(f, grain, PriceStrategy.MARKET, markupPpm = 100_000))!!
        assertEquals(Money.units(110), p.limit.perUnit)
        assertEquals(Quantity.units(100), p.quantity)
    }

    @Test fun costPlusIgnoresTheMarket() {
        val p = plan(SellRule(f, grain, PriceStrategy.COST_PLUS, markupPpm = 500_000))!!
        assertEquals(Money.units(90), p.limit.perUnit)
    }

    @Test fun undercutNeverRaisesThePrice() {
        val p = plan(SellRule(f, grain, PriceStrategy.UNDERCUT, markupPpm = 200_000))!!
        assertEquals(Money.units(80), p.limit.perUnit)
        val q = plan(SellRule(f, grain, PriceStrategy.UNDERCUT, markupPpm = 0))!!
        assertEquals(Money.units(100), q.limit.perUnit)
    }

    @Test fun premiumAddsBrandStrength() {
        val p = plan(SellRule(f, grain, PriceStrategy.PREMIUM, markupPpm = 50_000), brand = 150_000)!!
        assertEquals(Money.units(120), p.limit.perUnit)
    }

    @Test fun minimumMarginFloorsTheAsk() {
        val p = plan(SellRule(f, grain, PriceStrategy.UNDERCUT, markupPpm = 700_000, minMarginPpm = 100_000))!!
        assertEquals(Money.units(66), p.limit.perUnit)
    }

    @Test fun reserveAndCapLimitTheQuantity() {
        val p = plan(SellRule(f, grain, reserve = Quantity.units(30), maxPerOrder = Quantity.units(50)))!!
        assertEquals(Quantity.units(50), p.quantity)
        assertNull(plan(SellRule(f, grain, reserve = Quantity.units(100))))
        assertNull(plan(SellRule(f, grain, enabled = false)))
    }

    @Test fun buyPlanTopsUpToTheTarget() {
        val rule = BuyRule(f, grain, Quantity.units(100), maxPriceMultiplePpm = 1_200_000)
        val p = RulePlanner.buyPlan(rule, Quantity.units(40), Quantity.units(10), market)!!
        assertEquals(Quantity.units(50), p.quantity)
        assertEquals(Money.units(120), p.limit.perUnit)
        assertNull(RulePlanner.buyPlan(rule, Quantity.units(80), Quantity.units(30), market))
        assertNull(RulePlanner.buyPlan(rule.copy(enabled = false), Quantity.ZERO, Quantity.ZERO, market))
    }

    @Test fun dividendKeepsAnOperatingReserve() {
        val policy = DividendPolicy(payoutPpm = 500_000, reserveTicks = 10)
        val perTick = Money.units(1_000)
        assertEquals(Money.units(5_000), RulePlanner.dividend(policy, Money.units(10_000), Money.units(100_000), perTick))
        assertEquals(Money.units(2_000), RulePlanner.dividend(policy, Money.units(10_000), Money.units(12_000), perTick))
        assertEquals(Money.ZERO, RulePlanner.dividend(policy, Money.units(10_000), Money.units(9_000), perTick))
        assertEquals(Money.ZERO, RulePlanner.dividend(policy.copy(payoutPpm = 0), Money.units(10_000), Money.units(100_000), perTick))
        assertEquals(Money.ZERO, RulePlanner.dividend(policy, Money.units(-5), Money.units(100_000), perTick))
    }

    @Test fun invalidRulesAreRejectedAtConstruction() {
        assertFalse(runCatching { SellRule(f, grain, markupPpm = 9_000_000) }.isSuccess)
        assertFalse(runCatching { BuyRule(f, grain, Quantity.units(-1)) }.isSuccess)
        assertFalse(runCatching { DividendPolicy(payoutPpm = 2_000_000) }.isSuccess)
        assertFalse(runCatching { DividendPolicy(intervalTicks = 0) }.isSuccess)
    }

    @Test fun bookReplacesRulesPerFacilityAndGood() {
        val book = PolicyBook(); val c = CompanyId(1)
        book.setSell(c, SellRule(f, grain, markupPpm = 10_000)).getOrThrow()
        book.setSell(c, SellRule(f, grain, markupPpm = 20_000)).getOrThrow()
        assertEquals(1, book.sellRules(c).size)
        assertEquals(20_000L, book.sellRule(c, f, grain)!!.markupPpm)
        assertTrue(book.removeSell(c, f, grain))
        assertFalse(book.removeSell(c, f, grain))
    }

    @Test fun bookLimitsRuleCount() {
        val book = PolicyBook(); val c = CompanyId(1)
        for (i in 0 until PolicyBook.MAX_RULES) book.setBuy(c, BuyRule(FacilityId(i.toLong()), grain, Quantity.units(1))).getOrThrow()
        assertEquals(Rules.LIMIT_EXCEEDED, book.setBuy(c, BuyRule(FacilityId(9_999), grain, Quantity.units(1))).rejectionOrNull()?.rule)
        book.setBuy(c, BuyRule(FacilityId(0), grain, Quantity.units(5))).getOrThrow()
    }

    @Test fun droppingAFacilityOrCompanyClearsRules() {
        val book = PolicyBook(); val c = CompanyId(1)
        book.setSell(c, SellRule(f, grain)).getOrThrow()
        book.setBuy(c, BuyRule(f, grain, Quantity.units(1))).getOrThrow()
        book.setBuy(c, BuyRule(FacilityId(2), grain, Quantity.units(1))).getOrThrow()
        book.dropFacility(c, f)
        assertTrue(book.sellRules(c).isEmpty())
        assertEquals(1, book.buyRules(c).size)
        book.setDividend(c, DividendPolicy(payoutPpm = 100_000))
        book.remove(c)
        assertTrue(book.buyRules(c).isEmpty())
        assertEquals(0L, book.dividend(c).payoutPpm)
        assertNotNull(book.dividend(c))
    }
}
