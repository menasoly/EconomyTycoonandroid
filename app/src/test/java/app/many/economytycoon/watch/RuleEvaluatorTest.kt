package app.many.economytycoon.watch

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEvaluatorTest {
    private val r1 = RegionId(1)
    private val wheat = GoodId("wheat")
    private fun entry(rule: WatchRule) = RuleEntry(7, rule)
    private fun check(rule: WatchRule, f: FakeFacts) = RuleEvaluator.check(entry(rule), f, FakeNames, plainMoney)

    @Test fun priceAboveFiresOnlyAboveTheLevel() {
        val rule = WatchRule.Price(r1, wheat, true, Money.units(10))
        val f = FakeFacts(prices = mutableMapOf((1L to "wheat") to Money.units(12)))
        assertTrue(check(rule, f)!!.message.contains("above 10"))
        f.prices[1L to "wheat"] = Money.units(10)
        assertNull(check(rule, f))
        f.prices[1L to "wheat"] = Money.units(8)
        assertNull(check(rule, f))
    }

    @Test fun priceBelowFiresOnlyBelowTheLevel() {
        val rule = WatchRule.Price(r1, wheat, false, Money.units(10))
        val f = FakeFacts(prices = mutableMapOf((1L to "wheat") to Money.units(8)))
        assertTrue(check(rule, f)!!.message.contains("below 10"))
        f.prices[1L to "wheat"] = Money.units(10)
        assertNull(check(rule, f))
    }

    @Test fun missingMarketNeverFires() {
        assertNull(check(WatchRule.Price(r1, wheat, true, Money.ZERO), FakeFacts()))
    }

    @Test fun cashBelowFires() {
        assertTrue(check(WatchRule.Cash(Money.units(20_000)), FakeFacts())!!.message.contains("below your 20000"))
        assertNull(check(WatchRule.Cash(Money.units(5_000)), FakeFacts()))
        assertNull(check(WatchRule.Cash(Money.units(10_000)), FakeFacts()))
    }

    @Test fun profitBelowFiresForLosses() {
        assertTrue(check(WatchRule.Profit(Money.ZERO), FakeFacts(monthlyProfit = Money.units(-5)))!!.message.contains("-5"))
        assertNull(check(WatchRule.Profit(Money.ZERO), FakeFacts(monthlyProfit = Money.units(5))))
    }

    @Test fun stockMovesFireEitherWay() {
        val rule = WatchRule.Stock(CompanyId(3), 50_000)
        assertTrue(check(rule, FakeFacts(stockMoves = mutableMapOf(3L to 60_000L)))!!.message.contains("+6.0%"))
        assertTrue(check(rule, FakeFacts(stockMoves = mutableMapOf(3L to -60_000L)))!!.message.contains("-6.0%"))
        assertNull(check(rule, FakeFacts(stockMoves = mutableMapOf(3L to 40_000L))))
        assertNull(check(rule, FakeFacts()))
    }

    @Test fun loanRuleWatchesTheWindow() {
        val rule = WatchRule.Loan(5)
        assertEquals("A loan payment is due today.", check(rule, FakeFacts(loanIn = 0))!!.message)
        assertEquals("A loan payment is due tomorrow.", check(rule, FakeFacts(loanIn = 1))!!.message)
        assertEquals("A loan payment is due in 5 days.", check(rule, FakeFacts(loanIn = 5))!!.message)
        assertNull(check(rule, FakeFacts(loanIn = 6)))
        assertNull(check(rule, FakeFacts(loanIn = null)))
        assertNull(check(rule, FakeFacts(loanIn = -1)))
    }

    @Test fun deliveryRuleWatchesTheWindow() {
        val rule = WatchRule.Delivery(3)
        assertTrue(check(rule, FakeFacts(deliveryIn = 2))!!.message.startsWith("A contract delivery"))
        assertNull(check(rule, FakeFacts(deliveryIn = 4)))
    }

    @Test fun idleRuleCountsFacilities() {
        assertTrue(check(WatchRule.Idle(2), FakeFacts(idleFacilities = 2))!!.message.startsWith("2 facilities"))
        assertNull(check(WatchRule.Idle(2), FakeFacts(idleFacilities = 1)))
    }

    @Test fun hitsCarrySeverityDayAndRule() {
        val h = check(WatchRule.Cash(Money.units(20_000), Severity.CRITICAL), FakeFacts(day = 42))!!
        assertEquals(Severity.CRITICAL, h.severity)
        assertEquals(42L, h.day)
        assertEquals(7L, h.ruleId)
    }

    @Test fun whenTextCoversTodayTomorrowAndLater() {
        assertEquals("today", RuleEvaluator.whenText(0))
        assertEquals("tomorrow", RuleEvaluator.whenText(1))
        assertEquals("in 9 days", RuleEvaluator.whenText(9))
    }

    @Test fun everyRuleKindDescribesItself() {
        val rules = listOf(
            WatchRule.Price(r1, wheat, true, Money.units(5)), WatchRule.Cash(Money.units(5)), WatchRule.Profit(Money.units(-5)),
            WatchRule.Stock(CompanyId(2), 50_000), WatchRule.Loan(7), WatchRule.Delivery(1), WatchRule.Idle(3),
        )
        assertEquals(WatchRule.Kind.values().toSet(), rules.map { it.kind }.toSet())
        for (r in rules) assertTrue(RuleEvaluator.describe(r, FakeNames, plainMoney).isNotBlank())
        assertEquals("Good wheat in Region 1 above 5", RuleEvaluator.describe(rules[0], FakeNames, plainMoney))
        assertEquals("Delivery due within 1 day", RuleEvaluator.describe(rules[5], FakeNames, plainMoney))
    }

    @Test fun invalidRulesAreRefused() {
        var refused = 0
        for (make in listOf<() -> Any>({ WatchRule.Stock(CompanyId(1), 0) }, { WatchRule.Loan(0) }, { WatchRule.Loan(91) }, { WatchRule.Delivery(0) }, { WatchRule.Idle(0) }, { RuleEntry(1, WatchRule.Idle(1), true, -1) })) {
            try { make() } catch (e: IllegalArgumentException) { refused++ }
        }
        assertEquals(6, refused)
    }
}
