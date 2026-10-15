package app.many.economytycoon.reporting

import app.many.economytycoon.consolidation.GroupFixture
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.IncomeStatement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnualReportTest {
    private fun report(
        index: Int = 1,
        income: IncomeStatement = GroupFixture.income(),
        opening: BalanceSheet = GroupFixture.balance(),
        closing: BalanceSheet = GroupFixture.balance(cash = 7_000, fixed = 22_000, debt = 8_000, capital = 21_000),
        prior: AnnualReport? = null,
        partial: Boolean = false,
    ) = AnnualReport(CompanyId(1), "Test Works", index, Tick(index * 360L), Tick(index * 360L + 360), partial, income, opening, closing, prior)

    @Test fun theCalendarYearFollowsTheIndex() {
        assertEquals(2000, report(0).calendarYear)
        assertEquals(2003, report(3).calendarYear)
        assertEquals(360L, report().days)
    }

    @Test fun marginsAreShareOfRevenue() {
        val r = report(income = GroupFixture.income(sales = 10_000, cogs = 4_000, wages = 2_000, tax = 500, interest = 100))
        assertEquals(600_000L, r.grossMarginPpm)
        // Net income is 3,400 of 10,000.
        assertEquals(340_000L, r.netMarginPpm)
    }

    @Test fun aYearWithoutSalesHasNoMargins() {
        val r = report(income = GroupFixture.income(sales = 0, cogs = 0, wages = 0, tax = 0, interest = 0))
        assertNull(r.netMarginPpm)
        assertNull(r.grossMarginPpm)
        assertEquals(Verdict.QUIET, r.verdict)
    }

    @Test fun balanceChangesCompareClosingWithOpening() {
        val r = report()
        assertEquals(Money.units(2_000), r.cashChange)
        assertEquals(Money.ZERO, r.debtChange)
        assertEquals(Money.units(4_000), r.netWorthChange)
    }

    @Test fun returnOnEquityUsesTheAverageOfBothEnds() {
        val r = report(income = GroupFixture.income(sales = 10_000, cogs = 4_000, wages = 2_000, tax = 500, interest = 100))
        // Net income 3,400 on average equity of 19,000.
        assertEquals(3_400L * 1_000_000L / 19_000L, r.returnOnEquityPpm)
    }

    @Test fun noPositiveEquityMeansNoReturn() {
        val broke = GroupFixture.balance(cash = 0, fixed = 0, debt = 5_000, capital = 0)
        assertNull(report(opening = broke, closing = broke).returnOnEquityPpm)
    }

    @Test fun growthComparesRevenueWithThePriorYear() {
        val last = report(0, income = GroupFixture.income(sales = 10_000))
        val now = report(1, income = GroupFixture.income(sales = 12_000), prior = last)
        assertEquals(200_000L, now.revenueGrowthPpm)
        assertEquals(now.netIncome - last.netIncome, now.netIncomeChange)
        assertNull(last.revenueGrowthPpm)
    }

    @Test fun priorYearWithoutRevenueGivesNoGrowthFigure() {
        val last = report(0, income = GroupFixture.income(sales = 0, cogs = 0, wages = 0, tax = 0, interest = 0))
        assertNull(report(1, prior = last).revenueGrowthPpm)
    }

    @Test fun lossesAreCalledLosses() {
        val r = report(income = GroupFixture.income(sales = 1_000, cogs = 4_000))
        assertEquals(Verdict.LOSS, r.verdict)
        assertFalse(r.bestNetIncomeSoFar)
    }

    @Test fun aBetterYearThanEverBeforeIsARecord() {
        val a = report(0, income = GroupFixture.income(sales = 10_000))
        val b = report(1, income = GroupFixture.income(sales = 11_000), prior = a)
        val c = report(2, income = GroupFixture.income(sales = 15_000), prior = b)
        assertTrue(c.bestNetIncomeSoFar)
        assertEquals(Verdict.RECORD, c.verdict)
    }

    @Test fun anEarlierBestYearStopsALaterOneBeingARecord() {
        val a = report(0, income = GroupFixture.income(sales = 30_000))
        val b = report(1, income = GroupFixture.income(sales = 10_000), prior = a)
        val c = report(2, income = GroupFixture.income(sales = 12_000), prior = b)
        assertFalse(c.bestNetIncomeSoFar)
        assertEquals(Verdict.GROWTH, c.verdict)
    }

    @Test fun theFirstYearIsNeverCalledARecord() {
        assertEquals(Verdict.STEADY, report(0).verdict)
    }

    @Test fun shrinkingRevenueIsSlipping() {
        val a = report(0, income = GroupFixture.income(sales = 30_000))
        val b = report(1, income = GroupFixture.income(sales = 25_000, cogs = 4_000), prior = a)
        // Smaller than the best year but still profitable.
        assertEquals(Verdict.SLIP, b.verdict)
    }

    @Test fun smallMovesCountAsSteady() {
        val a = report(0, income = GroupFixture.income(sales = 30_000))
        val b = report(1, income = GroupFixture.income(sales = 30_600, cogs = 6_000), prior = a)
        assertEquals(Verdict.STEADY, b.verdict)
    }

    @Test fun everyVerdictHasADistinctLabel() {
        assertEquals(Verdict.values().size, Verdict.values().map { it.label }.toSet().size)
    }
}
