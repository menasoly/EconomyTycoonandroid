package app.many.economytycoon.cashflow

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CashProjectionTest {
    private fun m(n: Long) = Money.units(n)
    private fun build(start: Long = 1_000, perDay: Long = 10, lumps: List<Lump> = emptyList(), horizon: Int = 10, s: Scenario = Scenario.EXPECTED) =
        CashProjection.build(m(start), 100, m(perDay), lumps, horizon, s)

    @Test fun steadyRunRateAddsUp() {
        val p = build()
        assertEquals(10, p.points.size)
        assertEquals(m(1_100), p.end)
        assertEquals(101L, p.points.first().day)
        assertEquals(110L, p.points.last().day)
    }

    @Test fun lumpsLandOnTheirDay() {
        val p = build(lumps = listOf(Lump(103, "Loan", m(-500))))
        assertEquals(m(1_000 + 30 - 500), p.balanceOn(103))
        assertEquals(listOf("Loan"), p.points[2].lumps.map { it.label })
        assertEquals(m(-490), p.points[2].change)
    }

    @Test fun lumpsOutsideTheHorizonAreIgnored() {
        val p = build(lumps = listOf(Lump(50, "Old", m(-500)), Lump(500, "Far", m(-500))))
        assertEquals(m(1_100), p.end)
    }

    @Test fun twoLumpsOnADayBothApply() {
        val p = build(perDay = 0, lumps = listOf(Lump(101, "B", m(-100)), Lump(101, "A", m(40))))
        assertEquals(m(940), p.points.first().balance)
        assertEquals(listOf("A", "B"), p.points.first().lumps.map { it.label })
    }

    @Test fun cautiousShrinksGainsAndGrowsLosses() {
        assertEquals(m(1_000 + 80), build(perDay = 10, s = Scenario.CAUTIOUS).end)
        assertEquals(m(1_000 - 115), build(perDay = -10, s = Scenario.CAUTIOUS).end)
    }

    @Test fun hopefulGrowsGainsAndShrinksLosses() {
        assertEquals(m(1_000 + 115), build(perDay = 10, s = Scenario.HOPEFUL).end)
        assertEquals(m(1_000 - 90), build(perDay = -10, s = Scenario.HOPEFUL).end)
    }

    @Test fun scenariosAreOrderedByOutcome() {
        for (rate in listOf(-20L, 0L, 20L)) {
            val c = build(perDay = rate, s = Scenario.CAUTIOUS).end
            val e = build(perDay = rate, s = Scenario.EXPECTED).end
            val h = build(perDay = rate, s = Scenario.HOPEFUL).end
            assertTrue("rate $rate", c <= e && e <= h)
        }
    }

    @Test fun lowestAndHighestPoints() {
        val p = build(perDay = 0, lumps = listOf(Lump(103, "Out", m(-400)), Lump(106, "In", m(900))))
        assertEquals(103L, p.lowest!!.day)
        assertEquals(m(600), p.lowest!!.balance)
        assertEquals(106L, p.highest!!.day)
    }

    @Test fun shortfallIsFoundOnItsFirstDay() {
        val p = build(start = 100, perDay = -30)
        assertEquals(104L, p.firstShortfall!!.day)
        assertTrue(p.gapToCover.raw > 0L)
    }

    @Test fun noShortfallMeansNoGap() {
        val p = build()
        assertNull(p.firstShortfall)
        assertEquals(Money.ZERO, p.gapToCover)
    }

    @Test fun gapIsTheDeepestHole() {
        val p = build(start = 0, perDay = 0, lumps = listOf(Lump(102, "A", m(-300)), Lump(104, "B", m(100)), Lump(106, "C", m(-50))))
        assertEquals(m(300), p.gapToCover)
    }

    @Test fun totalsSplitInAndOut() {
        val p = build(perDay = 0, lumps = listOf(Lump(101, "In", m(70)), Lump(102, "Out", m(-30))))
        assertEquals(m(70), p.totalIn)
        assertEquals(m(-30), p.totalOut)
        assertEquals(m(70), p.lumpIn)
        assertEquals(m(-30), p.lumpOut)
    }

    @Test fun balanceOnClampsToTheEnds() {
        val p = build()
        assertEquals(m(1_000), p.balanceOn(90))
        assertEquals(m(1_000), p.balanceOn(100))
        assertEquals(p.end, p.balanceOn(999))
    }

    @Test fun horizonMustBeInRange() {
        var refused = 0
        try { build(horizon = 0) } catch (e: IllegalArgumentException) { refused++ }
        try { build(horizon = 721) } catch (e: IllegalArgumentException) { refused++ }
        assertEquals(2, refused)
    }

    @Test fun outlookCoversAllThreeScenarios() {
        val o = CashOutlook.build(m(1_000), 0, m(10), emptyList(), 30)
        assertEquals(Scenario.values().toList(), o.all.map { it.scenario })
        for (s in Scenario.values()) assertEquals(s, o.of(s).scenario)
    }

    @Test fun certainShortfallNeedsEvenTheHopefulCaseToFail() {
        assertTrue(CashOutlook.build(m(10), 0, m(-100), emptyList(), 30).certainShortfall)
        assertFalse(CashOutlook.build(m(10_000), 0, m(5), emptyList(), 30).certainShortfall)
    }

    @Test fun atRiskMeansOnlyTheCautiousCaseFails() {
        // Earning 100 a day against a lump of 3,400 on day 30: cautious earns 80 a day so falls short.
        val o = CashOutlook.build(m(700), 0, m(100), listOf(Lump(30, "Big", m(-3_400))), 30)
        assertTrue(o.cautious.firstShortfall != null)
        assertTrue(o.expected.firstShortfall == null)
        assertTrue(o.atRisk)
        assertFalse(o.certainShortfall)
    }
}
