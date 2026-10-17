package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanPricingTest {
    private val params = BankingFx.params
    private val inputs = BankingFx.calm

    private fun liquidity(b: Bank) = LiquidityEngine.position(b.snapshot(), params.liquidity, params.deposits::insuredShare)

    private fun quote(b: Bank, sector: LoanSector = LoanSector.CORPORATE, rating: CreditRating = CreditRating.BBB, i: BankingInputs = inputs) =
        LoanPricing.quote(i, b.snapshot(), liquidity(b), sector, rating, params)

    @Test fun quoteIsTheSumOfItsParts() {
        val q = quote(BankingFx.bank())
        assertEquals(q.baseRate.ppm + q.creditSpread.ppm + q.termPremium.ppm + q.liquidityPremium.ppm + q.margin.ppm, q.total.ppm)
        assertEquals(5, q.parts().size)
    }

    @Test fun baseRateFollowsThePolicyRateAndIsNeverNegative() {
        val b = BankingFx.bank()
        assertEquals(Rate.percent(4), quote(b).baseRate)
        assertEquals(Rate.ZERO, quote(b, i = inputs.copy(policy = Rate.percent(-2))).baseRate)
    }

    @Test fun worseBorrowersPayMore() {
        val b = BankingFx.bank()
        var last = 0L
        for (r in RiskTables.LENDABLE) {
            val t = quote(b, rating = r).total.ppm
            assertTrue("$r", t >= last)
            last = t
        }
    }

    @Test fun riskierSectorsPayMoreForTheSameRating() {
        val b = BankingFx.bank()
        assertTrue(quote(b, LoanSector.COMMERCIAL_PROPERTY, CreditRating.BB).creditSpread >= quote(b, LoanSector.MORTGAGE, CreditRating.BB).creditSpread)
    }

    @Test fun managementMarginMovesThePrice() {
        val b = BankingFx.bank()
        val base = quote(b).total.ppm
        b.changePolicy(b.policy.copy(loanMarginBps = 100))
        assertEquals(base + 10_000L, quote(b).total.ppm)
        b.changePolicy(b.policy.copy(loanMarginBps = -100))
        assertEquals(base - 10_000L, quote(b).total.ppm)
    }

    @Test fun totalNeverFallsBelowBaseRate() {
        val b = BankingFx.bank()
        b.changePolicy(b.policy.copy(loanMarginBps = -150))
        val q = quote(b, LoanSector.MORTGAGE, CreditRating.AAA)
        assertTrue(q.total >= q.baseRate)
    }

    @Test fun longerLoansCarryATermPremiumCappedByTheModel() {
        val b = BankingFx.bank()
        val s = b.snapshot()
        val short = LoanPricing.termPremium(inputs, s, 6, params.lending)
        val long = LoanPricing.termPremium(inputs, s, 240, params.lending)
        assertTrue(long >= short)
        assertTrue(long <= params.lending.maxTermPremium)
    }

    @Test fun liquidityStrainAddsAPremium() {
        val b = BankingFx.bank()
        val calm = quote(b).liquidityPremium
        b.changePolicy(b.policy.copy(targetLcr = Rate.percent(2_000)))
        val strained = quote(b).liquidityPremium
        assertTrue(strained > calm)
        assertTrue(strained <= params.lending.maxLiquidityPremium)
    }

    @Test fun wholesaleDependenceAddsAPremium() {
        val b = BankingFx.bank()
        val before = LoanPricing.liquidityPremium(b.snapshot(), liquidity(b), Rate.ZERO, params.lending)
        val extra = Money.units(2_000_000)
        b.setWholesale(b.wholesale + extra, 50_000L); b.reserves += extra
        assertTrue(LoanPricing.liquidityPremium(b.snapshot(), liquidity(b), Rate.ZERO, params.lending) > before)
    }

    @Test fun fundingCostRisesWithThePolicyRate() {
        val b = BankingFx.bank()
        DepositMarket.reprice(b, Rate.percent(1), params.deposits)
        val low = LoanPricing.fundingCost(b.snapshot(), inputs.copy(policy = Rate.percent(1)), params.facilities)
        DepositMarket.reprice(b, Rate.percent(8), params.deposits)
        val high = LoanPricing.fundingCost(b.snapshot(), inputs.copy(policy = Rate.percent(8)), params.facilities)
        assertTrue(high > low)
    }
}
