package app.many.economytycoon.valuation

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuationMethodsTest {
    private val a = ValuationAssumptions.DEFAULT
    private val h = ValuationFixtures.healthy

    @Test fun bookIsTheBookEquity() = assertEquals(Money.units(390_000), ValuationMethods.book(h).equity)

    @Test fun negativeBookCountsAsNothing() {
        val v = ValuationMethods.book(ValuationFixtures.input(book = -5_000))
        assertEquals(Money.ZERO, v.equity)
        assertTrue(v.applicable)
    }

    @Test fun earningsAreProfitTimesTheMultiple() {
        val v = ValuationMethods.earnings(h, a)
        assertTrue(v.applicable)
        assertEquals(Money.units(180_000 * 12L), v.equity)
    }

    @Test fun earningsDoNotApplyToLosses() {
        val v = ValuationMethods.earnings(ValuationFixtures.lossMaker, a)
        assertFalse(v.applicable)
        assertEquals(Money.ZERO, v.equity)
        assertTrue(v.note.startsWith("Not applicable"))
    }

    @Test fun ebitdaSubtractsNetDebt() {
        val v = ValuationMethods.ebitda(h, a)
        assertEquals(Money.units(300_000 * 8L - 100_000), v.equity)
    }

    @Test fun ebitdaNeverGoesBelowZero() {
        val v = ValuationMethods.ebitda(ValuationFixtures.input(ebitda = 10_000, debt = 900_000, cash = 0), a)
        assertEquals(Money.ZERO, v.equity)
        assertTrue(v.applicable)
    }

    @Test fun revenueMultipleSubtractsNetDebt() {
        assertEquals(Money.units(1_200_000 - 100_000), ValuationMethods.revenue(h, a).equity)
        assertFalse(ValuationMethods.revenue(ValuationFixtures.noSales, a).applicable)
    }

    @Test fun cashFlowValueIsPositiveAndGrowsWithGrowth() {
        val slow = ValuationMethods.dcfEquity(h, a.copy(growth = Rate(0)))
        val fast = ValuationMethods.dcfEquity(h, a.copy(growth = Rate(80_000)))
        assertTrue(slow.raw > 0L)
        assertTrue(fast > slow)
    }

    @Test fun cashFlowValueFallsAsTheDiscountRises() {
        val low = ValuationMethods.dcfEquity(h, a.copy(discount = Rate(90_000)))
        val high = ValuationMethods.dcfEquity(h, a.copy(discount = Rate(160_000)))
        assertTrue(low > high)
    }

    @Test fun cashFlowDoesNotApplyWithoutSpareCash() {
        assertFalse(ValuationMethods.dcf(ValuationFixtures.lossMaker, a).applicable)
        assertFalse(ValuationMethods.dcf(ValuationFixtures.noSales, a).applicable)
    }

    @Test fun liquidationAppliesHaircutsAndPaysDebts() {
        val v = ValuationMethods.liquidation(h, a)
        // cash 100k + 80% of 50k + 60% of 80k + 50% of 400k - 260k liabilities
        assertEquals(Money.units(100_000 + 40_000 + 48_000 + 200_000 - 260_000), v.equity)
    }

    @Test fun liquidationIsFlooredAtZero() {
        val v = ValuationMethods.liquidation(ValuationFixtures.input(liabilities = 5_000_000), a)
        assertEquals(Money.ZERO, v.equity)
    }

    @Test fun liquidationIsBelowBookForAHealthyCompany() {
        assertTrue(ValuationMethods.liquidation(h, a).equity < ValuationMethods.book(h).equity)
    }

    @Test fun allListsEveryMethodOnce() {
        val all = ValuationMethods.all(h, a)
        assertEquals(Method.values().toList(), all.map { it.method })
    }

    @Test fun perShareDividesByShares() {
        val v = ValuationMethods.book(h)
        assertEquals(Money.units(390), v.perShare)
    }

    @Test fun zeroSharesGiveZeroPerShare() {
        assertEquals(Money.ZERO, ValuationMethods.book(ValuationFixtures.input(shares = 0)).perShare)
    }

    @Test fun cautiousIsBelowDefaultIsBelowOptimistic() {
        val c = ValuationSummary.of(h, ValuationAssumptions.CAUTIOUS).equity
        val d = ValuationSummary.of(h, ValuationAssumptions.DEFAULT).equity
        val o = ValuationSummary.of(h, ValuationAssumptions.OPTIMISTIC).equity
        assertTrue(c < d && d < o)
    }

    @Test fun discountMustExceedTerminalGrowth() {
        var refused = false
        try { ValuationAssumptions(discount = Rate(10_000), terminalGrowth = Rate(20_000)) } catch (e: IllegalArgumentException) { refused = true }
        assertTrue(refused)
    }

    @Test fun yearsMustBeInRange() {
        var refused = 0
        try { ValuationAssumptions(years = 0) } catch (e: IllegalArgumentException) { refused++ }
        try { ValuationAssumptions(years = 31) } catch (e: IllegalArgumentException) { refused++ }
        assertEquals(2, refused)
    }

    @Test fun netDebtIsDebtLessCash() = assertEquals(Money.units(100_000), h.netDebt)

    @Test fun marketCapIsPriceTimesShares() {
        assertEquals(null, h.marketCap)
        assertEquals(Money.units(500_000), ValuationFixtures.input(price = 500).marketCap)
    }
}
