package app.many.economytycoon.valuation

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuationSummaryTest {
    private fun s(i: ValuationInput = ValuationFixtures.healthy) = ValuationSummary.of(i)

    @Test fun blendLiesBetweenLowAndHigh() {
        val x = s()
        assertTrue(x.low <= x.equity && x.equity <= x.high)
    }

    @Test fun lossMakerUsesOnlyBookAndBreakUp() {
        val x = s(ValuationFixtures.lossMaker)
        assertEquals(setOf(Method.BOOK, Method.LIQUIDATION, Method.REVENUE), x.applicable.map { it.method }.toSet())
    }

    @Test fun noSalesStillValuesFromTheBalanceSheet() {
        val x = s(ValuationFixtures.noSales)
        assertEquals(setOf(Method.BOOK, Method.LIQUIDATION), x.applicable.map { it.method }.toSet())
        assertTrue(x.equity.raw > 0L)
    }

    @Test fun customWeightsMoveTheBlend() {
        val book = ValuationSummary.of(ValuationFixtures.healthy, weights = Method.values().associateWith { if (it == Method.BOOK) 1 else 0 })
        assertEquals(Money.units(390_000), book.equity)
    }

    @Test fun zeroTotalWeightGivesZero() {
        val x = ValuationSummary.of(ValuationFixtures.healthy, weights = Method.values().associateWith { 0 })
        assertEquals(Money.ZERO, x.equity)
    }

    @Test fun notListedHasNoVerdictOrPremium() {
        val x = s()
        assertEquals(Verdict.NOT_LISTED, x.verdict())
        assertEquals(null, x.premiumPpm)
    }

    @Test fun cheapSharesAreUndervalued() {
        val fair = s().perShare.wholeUnits()
        val x = s(ValuationFixtures.input(price = fair / 2))
        assertEquals(Verdict.UNDERVALUED, x.verdict())
        assertTrue(x.premiumPpm!! < 0)
    }

    @Test fun dearSharesAreOvervalued() {
        val fair = s().perShare.wholeUnits()
        val x = s(ValuationFixtures.input(price = fair * 2))
        assertEquals(Verdict.OVERVALUED, x.verdict())
        assertTrue(x.premiumPpm!! > 0)
    }

    @Test fun sharesNearTheEstimateAreFair() {
        val fair = s().perShare.wholeUnits()
        assertEquals(Verdict.FAIR, s(ValuationFixtures.input(price = fair)).verdict())
    }

    @Test fun toleranceWidensTheFairBand() {
        val fair = s().perShare.wholeUnits()
        val x = s(ValuationFixtures.input(price = fair * 12 / 10))
        assertEquals(Verdict.OVERVALUED, x.verdict(100_000L))
        assertEquals(Verdict.FAIR, x.verdict(300_000L))
    }

    @Test fun worthlessCompaniesCannotBeCompared() {
        val x = s(ValuationFixtures.input(revenue = 0, ebitda = 0, net = 0, fcf = 0, book = 0, cash = 0, receivables = 0, inventory = 0, fixed = 0, intangibles = 0, liabilities = 0, debt = 0, price = 5))
        assertEquals(Verdict.WORTHLESS, x.verdict())
    }

    @Test fun confidenceGrowsWithHistory() {
        val short = ValuationSummary.of(ValuationFixtures.input(history = 10)).confidencePpm
        val long = ValuationSummary.of(ValuationFixtures.input(history = 90)).confidencePpm
        assertTrue(short < long)
        assertEquals(1_000_000L, long)
    }

    @Test fun confidenceFallsWhenMethodsDoNotApply() {
        assertTrue(s(ValuationFixtures.lossMaker).confidencePpm < s().confidencePpm)
    }

    @Test fun confidenceIsZeroWithNoHistory() {
        assertEquals(0L, ValuationSummary.of(ValuationFixtures.input(history = 0)).confidencePpm)
    }

    @Test fun perShareFiguresFollowTheTotals() {
        val x = s()
        assertEquals(Money(x.equity.raw / 1_000), x.perShare)
        assertTrue(x.lowPerShare <= x.perShare && x.perShare <= x.highPerShare)
    }

    @Test fun verdictLabelsAreDistinct() {
        assertEquals(Verdict.values().size, Verdict.values().map { it.label }.toSet().size)
    }
}
