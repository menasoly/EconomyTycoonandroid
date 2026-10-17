package app.many.economytycoon.valuation

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitivityTest {
    private val a = ValuationAssumptions.DEFAULT
    private val h = ValuationFixtures.healthy

    @Test fun gridHasOneRowPerDiscountAndOneColumnPerGrowth() {
        val g = Sensitivity.dcfGrid(h, a)
        assertEquals(Sensitivity.DEFAULT_DISCOUNTS.size, g.size)
        assertTrue(g.all { it.size == Sensitivity.DEFAULT_GROWTHS.size })
    }

    @Test fun valuesRiseAlongGrowthAndFallDownDiscount() {
        val g = Sensitivity.dcfGrid(h, a).map { row -> row.map { it!! } }
        for (row in g) for (c in 1 until row.size) assertTrue(row[c] >= row[c - 1])
        for (c in g[0].indices) for (r in 1 until g.size) assertTrue(g[r][c] <= g[r - 1][c])
    }

    @Test fun noCashFlowGivesAnEmptyGrid() {
        assertTrue(Sensitivity.dcfGrid(ValuationFixtures.lossMaker, a).all { row -> row.all { it == null } })
    }

    @Test fun discountAtOrBelowTerminalGrowthIsNull() {
        assertEquals(null, Sensitivity.cell(h, a, Rate(20_000), Rate(30_000)))
        assertEquals(null, Sensitivity.cell(h, a, Rate(10_000), Rate(30_000)))
    }

    @Test fun cellMatchesTheDirectCalculation() {
        val c = Sensitivity.cell(h, a, Rate(100_000), Rate(40_000))!!
        assertEquals(ValuationMethods.dcfEquity(h, a.copy(discount = Rate(100_000), growth = Rate(40_000))), c)
    }

    @Test fun impliedGrowthReproducesTheTarget() {
        val target = ValuationMethods.dcfEquity(h, a.copy(growth = Rate(50_000)))
        val g = Sensitivity.impliedGrowth(h, a, target)!!
        assertTrue(Math.abs(g.ppm - 50_000L) <= 5L)
    }

    @Test fun impliedGrowthIsNullWhenOutOfReach() {
        assertEquals(null, Sensitivity.impliedGrowth(h, a, Money.units(900_000_000)))
        assertEquals(null, Sensitivity.impliedGrowth(h, a, Money.ZERO))
        assertEquals(null, Sensitivity.impliedGrowth(ValuationFixtures.lossMaker, a, Money.units(1_000)))
    }

    @Test fun higherTargetsNeedMoreGrowth() {
        val lo = Sensitivity.impliedGrowth(h, a, Money.units(1_200_000))!!
        val hi = Sensitivity.impliedGrowth(h, a, Money.units(2_000_000))!!
        assertTrue(hi > lo)
    }

    @Test fun breakEvenDiscountReproducesTheTarget() {
        val target = ValuationMethods.dcfEquity(h, a.copy(discount = Rate(110_000)))
        val d = Sensitivity.breakEvenDiscount(h, a, target)!!
        assertTrue(Math.abs(d.ppm - 110_000L) <= 5L)
    }

    @Test fun breakEvenDiscountIsNullWhenImpossible() {
        assertEquals(null, Sensitivity.breakEvenDiscount(h, a, Money.units(900_000_000)))
        assertEquals(null, Sensitivity.breakEvenDiscount(ValuationFixtures.noSales, a, Money.units(10)))
    }

    @Test fun defaultAxesAreSortedAscending() {
        assertEquals(Sensitivity.DEFAULT_DISCOUNTS.sortedBy { it.ppm }, Sensitivity.DEFAULT_DISCOUNTS)
        assertEquals(Sensitivity.DEFAULT_GROWTHS.sortedBy { it.ppm }, Sensitivity.DEFAULT_GROWTHS)
    }
}
