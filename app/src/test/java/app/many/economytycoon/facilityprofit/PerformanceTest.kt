package app.many.economytycoon.facilityprofit

import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RecipeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTest {
    private fun m(n: Long) = Money.units(n)

    private fun line(id: Long, out: Long = 1_000, input: Long = 400, labour: Long = 100, energy: Long = 50, util: Long = 1_000_000, days: Int = 2, enabled: Boolean = true, recipe: Boolean = true) =
        LinePerformance(LineId(id), if (recipe) RecipeId("r$id") else null, if (recipe) "Recipe $id" else null, enabled, util, m(out), m(input), m(labour), m(energy), 0L, days)

    private fun facility(vararg lines: LinePerformance, upkeep: Long = 20, overhead: Long = 30, capital: Long = 10_000) =
        FacilityPerformance(FacilityId(1), "Mill", "Flour mill", "Northvale", lines.toList(), m(upkeep), m(overhead), m(capital))

    @Test fun lineProfitFollowsTheCosts() {
        val l = line(1)
        assertEquals(m(450), l.profitPerBatch)
        assertEquals(m(550), l.costPerBatch)
        assertEquals(m(225), l.profitPerDay)
    }

    @Test fun pausedAndEmptyLinesEarnNothing() {
        assertEquals(Money.ZERO, line(1, enabled = false).profitPerDay)
        assertEquals(Money.ZERO, line(2, recipe = false).profitPerDay)
        assertTrue(line(1, enabled = false).idle && line(2, recipe = false).idle)
    }

    @Test fun potentialIgnoresUtilisation() {
        val l = line(1, util = 250_000)
        assertEquals(m(225), l.potentialPerDay)
        assertEquals(Money(m(225).raw / 4), l.profitPerDay)
    }

    @Test fun losingLinesAreFlagged() {
        assertTrue(line(1, out = 100).losing)
        assertFalse(line(1).losing)
        assertFalse(line(1, out = 100, enabled = false).losing)
    }

    @Test fun facilityNetSubtractsUpkeepAndOverhead() {
        val f = facility(line(1), line(2))
        assertEquals(m(450), f.grossPerDay)
        assertEquals(m(400), f.netPerDay)
    }

    @Test fun facilityCountsIdleAndLosingLines() {
        val f = facility(line(1), line(2, out = 100), line(3, enabled = false))
        assertEquals(1, f.idleLines)
        assertEquals(1, f.losingLines)
    }

    @Test fun bestAndWorstLinesIgnoreIdleOnes() {
        val f = facility(line(1, out = 2_000), line(2), line(3, enabled = false, out = 9_999))
        assertEquals(LineId(1), f.bestLine?.line)
        assertEquals(LineId(2), f.worstLine?.line)
    }

    @Test fun noActiveLinesMeansNoBestOrWorst() {
        val f = facility(line(1, enabled = false))
        assertNull(f.bestLine)
        assertNull(f.worstLine)
    }

    @Test fun unusedMoneyIsPotentialLessActual() {
        val f = facility(line(1, util = 500_000), line(2))
        assertEquals(Money(m(225).raw / 2), f.unusedPerDay)
    }

    @Test fun returnAndPaybackUseNetEarnings() {
        val f = facility(line(1))
        assertEquals(m(175), f.netPerDay)
        assertEquals(58L, f.paybackDays)
        assertTrue(f.annualReturnPpm > 0L)
    }

    @Test fun lossMakingFacilityNeverPaysBack() {
        val f = facility(line(1, out = 100), capital = 5_000)
        assertNull(f.paybackDays)
        assertTrue(f.annualReturnPpm < 0L)
    }

    @Test fun portfolioTotalsAndRanking() {
        val a = facility(line(1), capital = 1_000)
        val b = FacilityPerformance(FacilityId(2), "Farm", "Grain farm", "Eastport", listOf(line(2, out = 100)), m(10), m(10), m(2_000))
        val p = PortfolioPerformance(listOf(a, b), emptyList())
        assertEquals(a.netPerDay + b.netPerDay, p.netPerDay)
        assertEquals(m(3_000), p.capital)
        assertEquals(listOf(FacilityId(1), FacilityId(2)), p.ranked.map { it.facility })
        assertEquals(listOf(FacilityId(2)), p.losers.map { it.facility })
    }

    @Test fun potentialGainSumsTheSwitches() {
        val p = PortfolioPerformance(emptyList(), listOf(Switch(FacilityId(1), LineId(1), "a", RecipeId("b"), "B", m(10)), Switch(FacilityId(1), LineId(2), null, RecipeId("c"), "C", m(5))))
        assertEquals(m(15), p.potentialGainPerDay)
    }

    @Test fun emptyPortfolioIsAllZero() {
        val p = PortfolioPerformance(emptyList(), emptyList())
        assertEquals(Money.ZERO, p.netPerDay)
        assertEquals(0L, p.annualReturnPpm)
        assertTrue(p.ranked.isEmpty() && p.losers.isEmpty())
    }
}
