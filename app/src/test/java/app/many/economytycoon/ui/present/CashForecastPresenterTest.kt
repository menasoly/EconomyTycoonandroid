package app.many.economytycoon.ui.present

import app.many.economytycoon.cashflow.CashOutlook
import app.many.economytycoon.cashflow.CashProjection
import app.many.economytycoon.cashflow.Lump
import app.many.economytycoon.cashflow.Scenario
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashForecastPresenterTest {
    private val money: (Money) -> String = { it.wholeUnits().toString() }
    private fun m(n: Long) = Money.units(n)
    private fun outlook(start: Long, perDay: Long, lumps: List<Lump> = emptyList(), horizon: Int = 28) = CashOutlook.build(m(start), 0, m(perDay), lumps, horizon)

    @Test fun healthyCashIsGood() {
        val o = outlook(1_000, 10)
        val m = CashForecastPresenter.model(o, Scenario.EXPECTED, money)
        assertEquals(Tone.GOOD, m.tone)
        assertTrue(m.verdict.contains("ends the period higher"))
    }

    @Test fun fallingCashAboveZeroIsInformational() {
        val o = outlook(10_000, -10)
        assertEquals(Tone.INFO, CashForecastPresenter.model(o, Scenario.EXPECTED, money).tone)
    }

    @Test fun shortfallOnTheChosenViewWarns() {
        val o = outlook(500, 100, listOf(Lump(10, "Loan", m(-1_550))), horizon = 28)
        val m = CashForecastPresenter.model(o, Scenario.EXPECTED, money)
        assertTrue(m.verdict, m.verdict.contains("runs out around day 10"))
        assertTrue(m.verdict.contains("about 50 "))
    }

    @Test fun certainShortfallIsBad() {
        val o = outlook(100, -50)
        val m = CashForecastPresenter.model(o, Scenario.HOPEFUL, money)
        assertEquals(Tone.BAD, m.tone)
        assertTrue(m.verdict.contains("even if things go well"))
    }

    @Test fun onlyCautiousRunningShortIsAWarning() {
        val o = CashOutlook.build(m(700), 0, m(100), listOf(Lump(28, "Big", m(-3_400))), 28)
        // Expected: 700 + 2800 - 3400 = 100 stays above zero; cautious earns 80 a day.
        assertTrue(o.atRisk)
        val model = CashForecastPresenter.model(o, Scenario.EXPECTED, money)
        assertEquals(Tone.WARN, model.tone)
        assertTrue(model.verdict.startsWith("Fine on the expected view"))
    }

    @Test fun weeksChunkTheProjectionBySeven() {
        val p = CashProjection.build(m(1_000), 0, m(10), emptyList(), 28)
        val rows = CashForecastPresenter.weeks(p, money)
        assertEquals(4, rows.size)
        assertEquals("Week 1, to day 7", rows[0].label)
        assertEquals("+70", rows[0].change)
        assertEquals("1070", rows[0].balance)
    }

    @Test fun partialLastWeekIsKept() {
        val p = CashProjection.build(m(1_000), 0, m(10), emptyList(), 10)
        assertEquals(2, CashForecastPresenter.weeks(p, money).size)
    }

    @Test fun negativeBalancesAreBadAndFallingWeeksWarn() {
        val p = CashProjection.build(m(100), 0, m(-10), emptyList(), 14)
        val rows = CashForecastPresenter.weeks(p, money)
        assertEquals(Tone.WARN, rows[0].tone)
        assertEquals(Tone.BAD, rows[1].tone)
    }

    @Test fun lumpsAreListedInTheirWeek() {
        val p = CashProjection.build(m(1_000), 0, Money.ZERO, listOf(Lump(9, "Loan 3", m(-100))), 14)
        val rows = CashForecastPresenter.weeks(p, money)
        assertTrue(rows[0].events.isEmpty())
        assertEquals(listOf("Day 9: Loan 3, -100"), rows[1].events)
    }

    @Test fun tilesReportLowestPointAndKnownPayments() {
        val o = outlook(1_000, 0, listOf(Lump(5, "A", m(-300)), Lump(9, "B", m(-200))))
        val m = CashForecastPresenter.model(o, Scenario.EXPECTED, money)
        assertEquals("500", m.tiles[2].value)
        assertEquals("-500", m.tiles[3].value)
    }

    @Test fun dueSoonIsCappedAtEight() {
        val lumps = (1L..12L).map { Lump(it, "L$it", m(-10)) }
        assertEquals(8, CashForecastPresenter.model(outlook(5_000, 0, lumps), Scenario.EXPECTED, money).dueSoon.size)
    }

    @Test fun scenarioFactsCoverAllThree() {
        val facts = CashForecastPresenter.model(outlook(1_000, 10), Scenario.EXPECTED, money).scenarios
        assertEquals(Scenario.values().map { it.label }, facts.map { it.label })
    }

    @Test fun liveSessionBuildsAModel() {
        val s = PresentSupport.session("Forecasters", 17L).also { x -> repeat(35) { x.step() } }
        val m = CashForecastPresenter.build(s)!!
        assertEquals(12, m.weeks.size)
        assertEquals(4, m.tiles.size)
    }
}
