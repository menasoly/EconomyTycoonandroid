package app.many.economytycoon.analysis

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CostStructureTest {
    private val f = AnalysisFixture

    // sales 100k; variable = cogs 40k + energy 2k + transport 3k = 45k; fixed = 20+3+1+2+4+2 = 32k
    private val cs = CostStructure.of(f.income())

    @Test fun costsSplitIntoVariableAndFixed() {
        assertEquals(Money.units(45_000), cs.variable)
        assertEquals(Money.units(32_000), cs.fixed)
        assertEquals(Money.units(77_000), cs.total)
    }

    @Test fun linesAreBiggestFirstAndSkipZeroes() {
        assertEquals("Materials", cs.lines.first().label)
        assertTrue(cs.lines.none { it.amount.isZero })
        assertNull(cs.line("Research"))
        assertNotNull(cs.line("Wages"))
    }

    @Test fun sharesAreOfRevenue() {
        assertEquals(400_000L, cs.line("Materials")!!.sharePpm)
        assertEquals(200_000L, cs.line("Wages")!!.sharePpm)
    }

    @Test fun contributionMarginIsWhatSalesLeaveAfterVariableCosts() {
        assertEquals(550_000L, cs.contributionMarginPpm)
    }

    @Test fun breakEvenCoversFixedCostsFromTheContribution() {
        // 32k / 0.55 = 58,181.81...
        val expected = Money.units(32_000).raw * 1_000_000L / 550_000L
        assertTrue(Math.abs(expected - cs.breakEvenRevenue!!.raw) <= 1L)
    }

    @Test fun marginOfSafetyIsHowFarSalesCanFall() {
        val mos = cs.marginOfSafetyPpm!!
        assertTrue("$mos", mos in 410_000L..420_000L)
    }

    @Test fun operatingLeverageAmplifiesProfitSwings() {
        // Contribution 55k over operating profit 23k.
        assertEquals(55_000L * 1_000_000L / 23_000L, cs.operatingLeveragePpm)
    }

    @Test fun aLossMakerHasNoOperatingLeverageFigure() {
        val loss = CostStructure.of(f.income(sales = 50_000))
        assertNull(loss.operatingLeveragePpm)
        assertEquals(true, (loss.marginOfSafetyPpm ?: 0L) < 0L)
    }

    @Test fun salesThatNeverCoverVariableCostsHaveNoBreakEven() {
        val bad = CostStructure.of(f.income(sales = 30_000))
        assertNull(bad.breakEvenRevenue)
    }

    @Test fun noRevenueGivesZeroSharesNotACrash() {
        val none = CostStructure.of(f.income(sales = 0))
        assertEquals(0L, none.contributionMarginPpm)
        assertTrue(none.lines.all { it.sharePpm == 0L })
        assertNull(none.marginOfSafetyPpm)
    }
}
