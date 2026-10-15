package app.many.economytycoon.ui

import app.many.economytycoon.companies.Department
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.logistics.StockRef
import app.many.economytycoon.markets.Side
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.model.BudgetForm
import app.many.economytycoon.ui.model.BuyRuleForm
import app.many.economytycoon.ui.model.DividendForm
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.model.LoanForm
import app.many.economytycoon.ui.model.OfferForm
import app.many.economytycoon.ui.model.OrderForm
import app.many.economytycoon.ui.model.RepayForm
import app.many.economytycoon.ui.model.SellRuleForm
import app.many.economytycoon.ui.model.ShipForm
import app.many.economytycoon.ui.model.StaffingForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormsTest {
    private val c = CompanyId(1)
    private val f = FacilityId(2)
    private val wheat = GoodId("wheat")

    @Test fun aSellOrderBuildsTheCommand() {
        val r = OrderForm(c, f, Side.SELL, wheat, "10", "2.50", 7, stockOnHand = Quantity.units(20)).validate()
        val cmd = r.commandOrNull as Command.PlaceOrder
        assertEquals(Quantity.units(10), cmd.quantity)
        assertEquals(Price(Money.units(2) + Money.cents(50)), cmd.limit)
        assertEquals(7, cmd.ttl)
    }

    @Test fun marketOrdersLeaveTheLimitOff() {
        val cmd = OrderForm(c, f, Side.BUY, wheat, "5").validate().commandOrNull as Command.PlaceOrder
        assertNull(cmd.limit)
    }

    @Test fun ordersExplainTheirProblems() {
        val r = OrderForm(c, f, Side.SELL, wheat, "50", "x", 99, stockOnHand = Quantity.units(20)).validate()
        assertTrue(r is FormResult.Errors)
        assertNotNull(r.errorFor("quantity")); assertNotNull(r.errorFor("limit")); assertNotNull(r.errorFor("ttl"))
        assertEquals("This field is required.", OrderForm(c, f, Side.BUY, wheat, "").validate().errorFor("quantity"))
    }

    @Test fun buyOrdersCannotExceedCash() {
        val r = OrderForm(c, f, Side.BUY, wheat, "100", "10", cash = Money.units(500)).validate()
        assertNotNull(r.errorFor("limit"))
        assertNotNull(OrderForm(c, f, Side.BUY, wheat, "10", "10", cash = Money.units(500)).validate().commandOrNull)
    }

    @Test fun staffingChecksTheMinimumWage() {
        val ok = StaffingForm(c, f, RoleId("farmhand"), "12", "90", Money.units(80)).validate().commandOrNull as Command.SetStaffing
        assertEquals(12L, ok.target); assertEquals(Money.units(90), ok.wage)
        assertNotNull(StaffingForm(c, f, RoleId("farmhand"), "12", "50", Money.units(80)).validate().errorFor("wage"))
        assertNull((StaffingForm(c, f, RoleId("farmhand"), "12", "", Money.units(80)).validate().commandOrNull as Command.SetStaffing).wage)
        assertNotNull(StaffingForm(c, f, RoleId("farmhand"), "-1").validate().errorFor("target"))
    }

    @Test fun loansRespectTheCreditLimitAndTermRange() {
        val ok = LoanForm(c, "50,000", 6, Money.units(100_000)).validate().commandOrNull as Command.Borrow
        assertEquals(Money.units(50_000), ok.amount); assertEquals(180, ok.termTicks)
        assertNotNull(LoanForm(c, "500,000", 6, Money.units(100_000)).validate().errorFor("amount"))
        assertNotNull(LoanForm(c, "1", 0).validate().errorFor("term"))
        assertNotNull(LoanForm(c, "0", 12).validate().errorFor("amount"))
    }

    @Test fun repaymentsCannotExceedWhatIsOwed() {
        assertNotNull(RepayForm(c, null, "900", Money.units(800)).validate().errorFor("amount"))
        assertNotNull(RepayForm(c, null, "100", Money.units(800)).validate().commandOrNull)
    }

    @Test fun sellRulesParsePercentagesIntoPpm() {
        val cmd = SellRuleForm(c, f, wheat, PriceStrategy.UNDERCUT, "5", "10", "25", "-2.5").validate().commandOrNull as Command.SetSellRule
        assertEquals(50_000L, cmd.rule.markupPpm)
        assertEquals(Quantity.units(10), cmd.rule.reserve)
        assertEquals(Quantity.units(25), cmd.rule.maxPerOrder)
        assertEquals(-25_000L, cmd.rule.minMarginPpm)
        assertNotNull(SellRuleForm(c, f, wheat, markupText = "900").validate().errorFor("markup"))
    }

    @Test fun buyRulesNeedATargetAndASanePriceCap() {
        val cmd = BuyRuleForm(c, f, wheat, "40", "120").validate().commandOrNull as Command.SetBuyRule
        assertEquals(Quantity.units(40), cmd.rule.target); assertEquals(1_200_000L, cmd.rule.maxPriceMultiplePpm)
        assertNotNull(BuyRuleForm(c, f, wheat, "", "120").validate().errorFor("target"))
        assertNotNull(BuyRuleForm(c, f, wheat, "10", "5").validate().errorFor("maxMultiple"))
    }

    @Test fun dividendPoliciesAreChecked() {
        val cmd = DividendForm(c, "40", 30, 60).validate().commandOrNull as Command.SetDividend
        assertEquals(400_000L, cmd.policy.payoutPpm)
        assertNotNull(DividendForm(c, "140").validate().errorFor("payout"))
        assertNotNull(DividendForm(c, "10", intervalDays = 1).validate().errorFor("interval"))
    }

    @Test fun budgetsTakeAMoneyAmount() {
        val cmd = BudgetForm(c, Department.MARKETING, "250").validate().commandOrNull as Command.SetBudget
        assertEquals(Money.units(250), cmd.perTick)
        assertNotNull(BudgetForm(c, Department.MARKETING, "abc").validate().errorFor("amount"))
    }

    @Test fun shipmentsNeedTwoDifferentPlacesAndEnoughStock() {
        val a = StockRef.facility(f); val b = StockRef.facility(FacilityId(9))
        assertNotNull(ShipForm(c, a, a, wheat, "5").validate().errorFor("to"))
        assertNotNull(ShipForm(c, null, b, wheat, "5").validate().errorFor("from"))
        assertNotNull(ShipForm(c, a, b, null, "5").validate().errorFor("good"))
        assertNotNull(ShipForm(c, a, b, wheat, "50", Quantity.units(10)).validate().errorFor("quantity"))
        assertNotNull(ShipForm(c, a, b, wheat, "5", Quantity.units(10), useFleet = true).validate().commandOrNull)
    }

    @Test fun offersCheckSharesPriceAndTarget() {
        assertNotNull(OfferForm(c, CompanyId(2), "100", "5.5", 1_000).validate().commandOrNull)
        assertNotNull(OfferForm(c, c, "1", "1").validate().errorFor("target"))
        assertNotNull(OfferForm(c, CompanyId(2), "5000", "1", 1_000).validate().errorFor("shares"))
        assertNotNull(OfferForm(c, CompanyId(2), "0", "1").validate().errorFor("shares"))
    }
}
