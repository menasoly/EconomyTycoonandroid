package app.many.economytycoon.banking

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankingActionsTest {
    private val me = CompanyId(11)
    private val inputs = BankingFx.calm
    private val country = BankingFx.country

    private class Fx(val s: BankingService, val id: BankId)

    private fun fx(capital: Long = 2_000_000L): Fx {
        val s = BankingService()
        val id = s.found(BankAction.FoundBank("Mine", BankingFx.country, CompanyId(11), Money.units(capital), true), BankingFx.calm, Tick(0), RandomProvider(1L)).getOrThrow().bank
        return Fx(s, id)
    }

    private fun Fx.act(a: BankAction) = s.apply(a, Tick(5), inputs, me)
    private fun Fx.rule(a: BankAction) = act(a).rejectionOrNull()?.rule
    private fun Fx.bank() = s.bank(id)!!
    private fun Fx.balanced() = assertEquals(Money.ZERO, bank().imbalance)

    // Policy ---------------------------------------------------------------------------------------------

    @Test fun depositPremiumIsAppliedAndBounded() {
        val f = fx()
        assertTrue(f.act(BankAction.SetDepositPremium(f.id, DepositProduct.SAVINGS, 120)).isOk)
        assertEquals(120L, f.bank().policy.premiumOf(DepositProduct.SAVINGS))
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.SetDepositPremium(f.id, DepositProduct.SAVINGS, 900)))
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.SetDepositPremium(f.id, DepositProduct.SAVINGS, -900)))
        assertEquals(0L, f.bank().policy.premiumOf(DepositProduct.DEMAND))
    }

    @Test fun loanMarginIsBounded() {
        val f = fx()
        assertTrue(f.act(BankAction.SetLoanMargin(f.id, -100)).isOk)
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.SetLoanMargin(f.id, 1_000)))
        assertEquals(-100L, f.bank().policy.loanMarginBps)
    }

    @Test fun lendingStandardsAreValidated() {
        val f = fx()
        assertTrue(f.act(BankAction.SetLendingStandards(f.id, CreditRating.BBB, Rate.percent(30))).isOk)
        assertEquals(CreditRating.BBB, f.bank().policy.minRating)
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.SetLendingStandards(f.id, CreditRating.D, Rate.percent(30))))
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.SetLendingStandards(f.id, CreditRating.BBB, Rate.percent(1))))
    }

    @Test fun sectorAppetiteAndGrowthAreRates() {
        val f = fx()
        assertTrue(f.act(BankAction.SetSectorAppetite(f.id, LoanSector.MORTGAGE, Rate.percent(90))).isOk)
        assertEquals(Rate.percent(90), f.bank().policy.appetiteOf(LoanSector.MORTGAGE))
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.SetSectorAppetite(f.id, LoanSector.MORTGAGE, Rate(2_000_000))))
        assertTrue(f.act(BankAction.SetGrowthAppetite(f.id, Rate.percent(80))).isOk)
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.SetGrowthAppetite(f.id, Rate(-1))))
    }

    @Test fun capitalAndFundingPoliciesAreBounded() {
        val f = fx()
        assertTrue(f.act(BankAction.SetCapitalPolicy(f.id, Rate.percent(5), Rate.percent(30))).isOk)
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.SetCapitalPolicy(f.id, Rate.percent(40), Rate.percent(30))))
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.SetCapitalPolicy(f.id, Rate.percent(5), Rate.percent(130))))
        assertTrue(f.act(BankAction.SetFundingPolicy(f.id, Rate.percent(20), Rate.percent(10), Rate.percent(150))).isOk)
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.SetFundingPolicy(f.id, Rate.percent(20), Rate.percent(10), Rate.percent(50))))
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.SetFundingPolicy(f.id, Rate.percent(90), Rate.percent(10), Rate.percent(150))))
    }

    // Capital --------------------------------------------------------------------------------------------

    @Test fun commonEquityAddsCashAndCapital() {
        val f = fx()
        val before = f.bank()
        val r = f.act(BankAction.RaiseCapital(f.id, CapitalInstrument.COMMON_EQUITY, Money.units(500_000))).getOrThrow()
        assertEquals(before.shareCapital + Money.units(500_000), f.bank().shareCapital)
        assertEquals(before.reserves + Money.units(500_000), f.bank().reserves)
        assertEquals(1, r.intents.size)
        assertTrue(r.events.single() is BankEvent.CapitalRaised)
        f.balanced()
    }

    @Test fun subordinatedDebtIsLimitedByRiskWeightedAssets() {
        val f = fx()
        assertEquals(Rules.LIMIT_EXCEEDED, f.rule(BankAction.RaiseCapital(f.id, CapitalInstrument.TIER2, Money.units(900_000_000))))
        assertTrue(f.act(BankAction.RaiseCapital(f.id, CapitalInstrument.ADDITIONAL_TIER1, Money.units(1_000))).isOk)
        assertEquals(Money.units(1_000), f.bank().additionalTier1)
        f.balanced()
    }

    @Test fun capitalAmountMustBePositive() {
        val f = fx()
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.RaiseCapital(f.id, CapitalInstrument.COMMON_EQUITY, Money.ZERO)))
    }

    // Securities -----------------------------------------------------------------------------------------

    @Test fun buyingSecuritiesSpendsExcessReservesOnly() {
        val f = fx()
        val position = f.s.view(f.id, inputs)!!.liquidity
        val ok = Money.min(position.excessReserves, Money.units(1_000))
        assertTrue(f.act(BankAction.BuySecurities(f.id, SecurityKind.GOVERNMENT_SHORT, ok)).isOk)
        assertEquals(Rules.INSUFFICIENT_FUNDS, f.rule(BankAction.BuySecurities(f.id, SecurityKind.GOVERNMENT_SHORT, Money.units(900_000_000))))
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.BuySecurities(f.id, SecurityKind.GOVERNMENT_SHORT, Money.ZERO)))
        f.balanced()
    }

    @Test fun sellingSecuritiesReturnsCashAtASmallDiscount() {
        val f = fx()
        val before = f.bank()
        val amount = before.securityOf(SecurityKind.GOVERNMENT_SHORT).times(Rate.percent(50))
        assertTrue(f.act(BankAction.SellSecurities(f.id, SecurityKind.GOVERNMENT_SHORT, amount)).isOk)
        val after = f.bank()
        assertTrue(after.reserves > before.reserves)
        assertTrue(after.retained < before.retained)
        f.balanced()
    }

    @Test fun cannotSellWhatIsNotHeld() {
        val f = fx()
        assertTrue(f.act(BankAction.SellSecurities(f.id, SecurityKind.CORPORATE_BONDS, Money.units(900_000_000))).isOk)
        assertEquals(Money.ZERO, f.bank().securityOf(SecurityKind.CORPORATE_BONDS))
        assertEquals(Rules.INSUFFICIENT_FUNDS, f.rule(BankAction.SellSecurities(f.id, SecurityKind.CORPORATE_BONDS, Money.units(5))))
        f.balanced()
    }

    // Wholesale ------------------------------------------------------------------------------------------

    @Test fun wholesaleBorrowingIsCappedByThePolicy() {
        val f = fx()
        val cap = f.bank().totalAssets.times(f.bank().policy.wholesaleCap)
        assertEquals(Rules.LIMIT_EXCEEDED, f.rule(BankAction.BorrowWholesale(f.id, cap + f.bank().totalAssets)))
    }

    @Test fun borrowAndRepayWholesaleRoundTrip() {
        val f = fx()
        val before = f.bank()
        assertTrue(f.act(BankAction.BorrowWholesale(f.id, Money.units(100_000))).isOk)
        assertEquals(before.wholesale + Money.units(100_000), f.bank().wholesale)
        f.balanced()
        assertTrue(f.act(BankAction.RepayWholesale(f.id, Money.units(100_000))).isOk)
        assertEquals(before.wholesale, f.bank().wholesale)
        f.balanced()
    }

    @Test fun repayingNothingOwedIsRejected() {
        val f = fx()
        f.act(BankAction.RepayWholesale(f.id, Money.units(900_000_000)))
        val left = f.bank().wholesale
        val b = f.s.book.banks.getValue(f.id)
        b.setWholesale(Money.ZERO, 0L)
        b.shareCapital += left
        f.balanced()
        assertEquals(Rules.WRONG_STATE, f.rule(BankAction.RepayWholesale(f.id, Money.units(1))))
    }

    @Test fun weakRatedBanksCannotBorrowWholesale() {
        val f = fx()
        f.s.book.banks.getValue(f.id).setRating(CreditRating.CCC, 100, 0, 0)
        assertEquals(Rules.CREDIT_LIMIT, f.rule(BankAction.BorrowWholesale(f.id, Money.units(1_000))))
    }

    // Central bank and confidence ------------------------------------------------------------------------

    @Test fun emergencyLiquidityAddsReservesAndDebt() {
        val f = fx()
        val before = f.bank()
        val r = f.act(BankAction.RequestEmergencyLiquidity(f.id, Money.units(10_000))).getOrThrow()
        assertEquals(Money.units(10_000), f.bank().centralBankEmergency)
        assertEquals(before.reserves + Money.units(10_000), f.bank().reserves)
        assertTrue(r.events.single() is BankEvent.FacilityUsed)
        f.balanced()
    }

    @Test fun emergencyLiquidityIsRefusedToInsolventBanks() {
        val f = fx()
        val b = f.s.book.banks.getValue(f.id)
        val loss = b.snapshot().cet1Capital
        b.reserves -= loss; b.retained -= loss
        assertEquals(Rules.CREDIT_LIMIT, f.rule(BankAction.RequestEmergencyLiquidity(f.id, Money.units(10))))
    }

    @Test fun reassuranceCostsMoneyAndBuysConfidence() {
        val f = fx()
        val before = f.bank()
        val r = f.act(BankAction.ReassureDepositors(f.id, Money.units(20_000))).getOrThrow()
        assertEquals(before.retained - Money.units(20_000), f.bank().retained)
        assertTrue(f.s.book.banks.getValue(f.id).supportPpm > 0L)
        assertTrue(r.intents.isNotEmpty())
        f.balanced()
    }

    @Test fun reassuranceIsCappedAndLimitedByCash() {
        val f = fx()
        assertEquals(Rules.INSUFFICIENT_FUNDS, f.rule(BankAction.ReassureDepositors(f.id, Money.units(900_000_000))))
        repeat(40) { f.act(BankAction.ReassureDepositors(f.id, Money.units(50_000))) }
        assertTrue(f.s.book.banks.getValue(f.id).supportPpm <= BankingFx.params.maxSupportPpm)
        assertEquals(Rules.LIMIT_EXCEEDED, f.rule(BankAction.ReassureDepositors(f.id, Money.units(50_000))))
    }

    // Stress, dividends, founding --------------------------------------------------------------------------

    @Test fun stressTestIsRecordedWithoutChangingTheBank() {
        val f = fx()
        val before = f.bank()
        assertTrue(f.act(BankAction.RunStressTest(f.id, "severe")).isOk)
        assertNotNull(f.s.lastStress(f.id))
        assertEquals("severe", f.s.lastStress(f.id)!!.scenarioId)
        assertEquals(before.reserves, f.bank().reserves)
        assertEquals(Rules.UNKNOWN_ENTITY, f.rule(BankAction.RunStressTest(f.id, "nonsense")))
    }

    @Test fun dividendsFollowCapacity() {
        val f = fx()
        val b = f.s.book.banks.getValue(f.id)
        repeat(3) { b.record(MonthlyResult.empty(30L * (it + 1)).copy(loanInterest = Money.units(60_000), averageEarningAssets = Money.units(10_000_000)), 24) }
        b.retained += Money.units(180_000); b.reserves += Money.units(180_000)
        val cap = f.s.view(f.id, inputs)!!.dividend.amount
        assertTrue(cap.isPositive)
        assertEquals(Rules.REGULATION, f.rule(BankAction.PayDividend(f.id, cap + Money.units(1))))
        val r = f.act(BankAction.PayDividend(f.id, cap)).getOrThrow()
        assertEquals(1, r.intents.size)
        assertTrue(r.events.single() is BankEvent.DividendPaid)
        f.balanced()
        assertEquals(Rules.INVALID_ARGUMENT, f.rule(BankAction.PayDividend(f.id, Money.ZERO)))
    }

    @Test fun foundingIsNotAnActionOnABank() {
        val f = fx()
        val r = f.s.apply(BankAction.FoundBank("X", country, me, Money.units(1_000_000), true), Tick(1), inputs, me)
        assertTrue(r.isRejected)
    }

    @Test fun everyAcceptedActionLeavesTheBooksBalanced() {
        val f = fx()
        val actions = listOf(
            BankAction.RaiseCapital(f.id, CapitalInstrument.TIER2, Money.units(1_000)),
            BankAction.BorrowWholesale(f.id, Money.units(50_000)),
            BankAction.BuySecurities(f.id, SecurityKind.GOVERNMENT_LONG, Money.units(1_000)),
            BankAction.SellSecurities(f.id, SecurityKind.GOVERNMENT_LONG, Money.units(500)),
            BankAction.ReassureDepositors(f.id, Money.units(1_000)),
            BankAction.RequestEmergencyLiquidity(f.id, Money.units(1_000)),
            BankAction.RepayWholesale(f.id, Money.units(10_000)),
        )
        for (a in actions) { f.act(a); f.balanced() }
    }
}
