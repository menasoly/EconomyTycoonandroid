package app.many.economytycoon.analysis

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyAnalysisTest {
    private val f = AnalysisFixture

    @Test fun aHealthyCompanyReadsAcrossEveryMeasure() {
        val a = CompanyAnalysis.of(f.input())
        assertTrue(a.readings.size >= 9)
        assertNotNull(a.overall)
        assertTrue(a.values.containsKey(Measure.NET_MARGIN))
        assertEquals(a.readings.size, a.values.size)
    }

    @Test fun marginsMatchTheStatements() {
        val a = CompanyAnalysis.of(f.input())
        assertEquals(600_000L, a.values[Measure.GROSS_MARGIN])
        assertEquals(f.income().netIncome.raw * 1_000_000L / f.income().revenue.raw, a.values[Measure.NET_MARGIN])
    }

    @Test fun noInterestMeansNoCoverFigure() {
        val a = CompanyAnalysis.of(f.input(income = f.income(interest = 0)))
        assertFalse(a.values.containsKey(Measure.INTEREST_COVER))
    }

    @Test fun noSalesDropsTheMarginAndReturnMeasures() {
        val a = CompanyAnalysis.of(f.input(income = f.income(sales = 0, cogs = 0)))
        assertFalse(a.values.containsKey(Measure.NET_MARGIN))
        assertFalse(a.values.containsKey(Measure.RETURN_ON_EQUITY))
        assertNull(a.dupont)
    }

    @Test fun aSquaredUpCompanyHasNoLiquidityRatios() {
        val b = f.balance(payables = 0, wagesPayable = 0, taxPayable = 0, shortDebt = 0)
        val a = CompanyAnalysis.of(f.input(balance = b))
        assertFalse(a.values.containsKey(Measure.CURRENT))
        assertFalse(a.values.containsKey(Measure.CASH))
    }

    @Test fun weakSpotsComeFromTheWorstReadings() {
        val a = CompanyAnalysis.of(f.input(balance = f.balance(cash = 1_000, shortDebt = 300_000)))
        assertTrue(a.weakSpots.isNotEmpty())
        assertTrue(a.weakSpots.all { it.grade.rank <= Grade.WATCH.rank })
    }

    @Test fun runwayComesFromTheCashFlow() {
        val a = CompanyAnalysis.of(f.input(cashFlow = f.cashFlow(opening = 80_000, closing = 50_000)))
        assertEquals(Money.units(1_000), a.runway.burnPerDay)
        assertEquals(50L, a.runway.days)
    }

    @Test fun outlookNeedsEnoughHistory() {
        val short = CompanyAnalysis.of(f.input(history = (1..4).map { Money.units(1_000L * it) }))
        assertNull(short.outlook)
        val long = CompanyAnalysis.of(f.input(history = (1..8).map { Money.units(1_000L * it) }))
        assertNotNull(long.outlook)
        assertTrue(long.outlook!!.reliable)
    }

    @Test fun costsAndEfficiencyAreIncluded() {
        val a = CompanyAnalysis.of(f.input())
        assertEquals(Money.units(45_000), a.costs.variable)
        assertNotNull(a.efficiency.assetTurnoverPpm)
    }

    @Test fun aLosingCompanyIsGradedWorseThanAProfitableOne() {
        val good = CompanyAnalysis.of(f.input())
        val bad = CompanyAnalysis.of(f.input(income = f.income(sales = 40_000, cogs = 38_000), balance = f.balance(cash = 2_000, shortDebt = 200_000)))
        assertTrue(bad.overall!!.rank < good.overall!!.rank)
    }
}
