package app.many.economytycoon.forecast

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonTest {
    private val c = CompanyId(1)
    private fun run(vararg worth: Long, debt: Long = 0, cash: (Int) -> Long = { 1_000 }) = Forecast(
        c, 0, worth.mapIndexed { i, w -> ForecastPoint(i * 5L, Money.units(cash(i)), Money.units(w), Money.units(debt), Money.ZERO) }, EndReason.COMPLETED,
    )

    @Test fun differencesAreScenarioMinusBaseline() {
        val cmp = Comparison(run(100, 110, 120), run(100, 90, 150, debt = 40))
        assertEquals(Money.units(30), cmp.netWorthDifference)
        assertEquals(Money.units(40), cmp.debtDifference)
        assertEquals(Money.ZERO, cmp.cashDifference)
        assertTrue(cmp.worthIt)
    }

    @Test fun anInvestmentThatPaysBackCrossesOverOnce() {
        val cmp = Comparison(run(100, 110, 120, 130), run(100, 90, 115, 160))
        assertEquals(15L, cmp.breakEvenDay)
        assertFalse(cmp.scenario.points[1].netWorth > cmp.baseline.points[1].netWorth)
    }

    @Test fun neverCatchingUpGivesNoBreakEven() {
        val cmp = Comparison(run(100, 110, 120), run(100, 90, 100))
        assertNull(cmp.breakEvenDay)
        assertFalse(cmp.worthIt)
    }

    @Test fun beingAheadFromTheStartIsNotACrossing() {
        val cmp = Comparison(run(100, 110, 120), run(100, 120, 140))
        assertNull(cmp.breakEvenDay)
        assertTrue(cmp.worthIt)
    }

    @Test fun aShortTermLossForALongTermGainChangesSign() {
        val cmp = Comparison(run(100, 110, 120, 130, 140), run(100, 80, 90, 160, 200))
        assertTrue(cmp.worthIt)
        assertTrue(cmp.changesSign)
    }

    @Test fun aSteadyAdvantageDoesNotChangeSign() {
        val cmp = Comparison(run(100, 110, 120), run(110, 130, 150))
        assertFalse(cmp.changesSign)
    }

    @Test fun runsOfDifferentLengthAreCompareableWhereTheyOverlap() {
        val cmp = Comparison(run(100, 110, 120, 130), run(100, 90))
        assertNull(cmp.breakEvenDay)
        assertEquals(Money.units(90 - 130), cmp.netWorthDifference)
    }

    @Test fun anEmptyRunHasNoFigures() {
        val empty = Forecast(c, 0, emptyList(), EndReason.COMPANY_GONE)
        assertNull(empty.first)
        assertNull(empty.last)
        assertEquals(Money.ZERO, empty.netWorthChange)
        assertEquals(Money.ZERO, empty.netWorthPerDay)
        assertNull(empty.lowestCash)
        assertNull(empty.firstOverdrawn)
    }

    @Test fun refusedActionsAreSeparatedOut() {
        val f = Forecast(c, 0, emptyList(), EndReason.COMPLETED, listOf(ActionResult("Build farm", null), ActionResult("Borrow", "limit")))
        assertEquals(listOf("Borrow"), f.refused.map { it.description })
        assertTrue(f.actions.first().accepted)
    }

    @Test fun everyEndReasonHasADistinctLabel() {
        assertEquals(EndReason.values().size, EndReason.values().map { it.label }.toSet().size)
    }
}
