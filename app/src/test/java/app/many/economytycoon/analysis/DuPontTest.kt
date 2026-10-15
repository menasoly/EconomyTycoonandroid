package app.many.economytycoon.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuPontTest {
    private val f = AnalysisFixture

    @Test fun theThreeFactorsMultiplyToTheReturn() {
        val d = DuPont.of(f.income(), f.balance(), 30L)!!
        val product = d.marginPpm * d.turnoverPpm / 1_000_000L * d.leveragePpm / 1_000_000L
        assertTrue("${d.returnOnEquityPpm} vs $product", Math.abs(d.returnOnEquityPpm - product) <= 2L)
    }

    @Test fun marginIsNetIncomeOverRevenue() {
        val i = f.income()
        val d = DuPont.of(i, f.balance(), 30L)!!
        assertEquals(i.netIncome.raw * 1_000_000L / i.revenue.raw, d.marginPpm)
    }

    @Test fun leverageIsAssetsOverEquity() {
        val b = f.balance()
        val d = DuPont.of(f.income(), b, 30L)!!
        assertEquals(b.totalAssets.raw * 1_000_000L / b.totalEquity.raw, d.leveragePpm)
    }

    @Test fun missingRevenueOrEquityGivesNothing() {
        assertNull(DuPont.of(f.income(sales = 0), f.balance(), 30L))
        assertNull(DuPont.of(f.income(), f.balance(shortDebt = 500_000), 30L))
    }

    @Test fun thinMarginIsTheWeakestWhenTheRestIsFine() {
        val d = DuPont(10_000L, 1_500_000L, 1_500_000L, 22_500L)
        assertEquals(Driver.MARGIN, d.weakest())
    }

    @Test fun slowTurnoverIsFoundWhenMarginIsFine() {
        val d = DuPont(200_000L, 200_000L, 1_500_000L, 60_000L)
        assertEquals(Driver.TURNOVER, d.weakest())
    }

    @Test fun heavyBorrowingIsFoundWhenTheOthersAreFine() {
        val d = DuPont(200_000L, 2_000_000L, 9_000_000L, 3_600_000L)
        assertEquals(Driver.LEVERAGE, d.weakest())
    }

    @Test fun nothingIsWeakWhenAllThreeAreHealthy() {
        assertNull(DuPont(150_000L, 1_000_000L, 2_000_000L, 300_000L).weakest())
    }

    @Test fun describeNamesAllThree() {
        val s = DuPont(100_000L, 1_000_000L, 2_000_000L, 200_000L).describe()
        assertTrue(s, s.contains("margin 10%") && s.contains("turnover 100%") && s.contains("leverage 200%"))
        assertNotNull(Driver.values().firstOrNull { s.contains(it.label.substringBefore(' ')) } ?: Driver.MARGIN)
    }
}
