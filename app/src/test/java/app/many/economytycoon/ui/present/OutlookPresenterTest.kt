package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.forecast.EndReason
import app.many.economytycoon.forecast.Forecast
import app.many.economytycoon.forecast.ForecastPoint
import app.many.economytycoon.session.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlookPresenterTest {
    private val s = GameSettings(autosave = false, pauseOnCritical = false)
    private val c = CompanyId(1)
    private fun pt(day: Long, cash: Long, worth: Long, debt: Long = 0, revenue: Long = 0) =
        ForecastPoint(day, Money.units(cash), Money.units(worth), Money.units(debt), Money.units(revenue))
    private fun run(vararg p: ForecastPoint, ended: EndReason = EndReason.COMPLETED) = Forecast(c, p.first().day, p.toList(), ended)

    @Test fun horizonsHaveReadableNames() {
        assertEquals("1 month", OutlookPresenter.horizonLabel(30))
        assertEquals("1 year", OutlookPresenter.horizonLabel(360))
        assertEquals("45 days", OutlookPresenter.horizonLabel(45))
        assertEquals(OutlookPresenter.horizons.size, OutlookPresenter.horizons.map { OutlookPresenter.horizonLabel(it) }.toSet().size)
    }

    @Test fun theHeadlineSaysWhichWayNetWorthGoes() {
        assertTrue(OutlookPresenter.headline(run(pt(0, 10, 100), pt(30, 10, 150)), s, 30).startsWith("Net worth would rise by"))
        assertTrue(OutlookPresenter.headline(run(pt(0, 10, 100), pt(30, 10, 60)), s, 30).startsWith("Net worth would fall by"))
        assertTrue(OutlookPresenter.headline(run(pt(0, 10, 100), pt(30, 10, 100)), s, 30).contains("stay about where it is"))
    }

    @Test fun aCompanyThatEndsIsToldSo() {
        assertEquals("The company would not survive.", OutlookPresenter.headline(run(pt(0, 1, 1), ended = EndReason.COMPANY_GONE), s, 90))
        assertTrue(OutlookPresenter.headline(run(pt(0, 1, 1), ended = EndReason.NOT_ACTIVE), s, 90).contains("stop trading"))
    }

    @Test fun tonesRankTheOutcomes() {
        assertEquals(Tone.GOOD, OutlookPresenter.tone(run(pt(0, 10, 100), pt(30, 10, 150))))
        assertEquals(Tone.WARN, OutlookPresenter.tone(run(pt(0, 10, 100), pt(30, 10, 60))))
        assertEquals(Tone.BAD, OutlookPresenter.tone(run(pt(0, 10, 100), pt(30, -5, 150))))
        assertEquals(Tone.BAD, OutlookPresenter.tone(run(pt(0, 10, 100), ended = EndReason.NOT_ACTIVE)))
        assertEquals(Tone.NEUTRAL, OutlookPresenter.tone(run(pt(0, 10, 100), pt(30, 10, 100))))
    }

    @Test fun overdrawnCashIsWarnedAboutWithTheDay() {
        val w = OutlookPresenter.warnings(run(pt(0, 10, 100), pt(10, -2, 90)), s)
        assertTrue(w.toString(), w.contains("Cash runs out on day 10."))
    }

    @Test fun aDipThatStaysPositiveNamesItsLowPoint() {
        val w = OutlookPresenter.warnings(run(pt(0, 100, 100), pt(10, 20, 100), pt(20, 80, 100)), s)
        assertTrue(w.toString(), w.single().startsWith("Cash is lowest on day 10"))
    }

    @Test fun risingDebtIsMentioned() {
        val w = OutlookPresenter.warnings(run(pt(0, 100, 100, debt = 10), pt(10, 100, 100, debt = 50)), s)
        assertTrue(w.toString(), w.any { it.startsWith("Debt grows by") })
    }

    @Test fun aCalmRunHasNoWarnings() {
        assertTrue(OutlookPresenter.warnings(run(pt(0, 100, 100), pt(10, 120, 130)), s).isEmpty())
    }

    @Test fun seriesStartAtDayZeroOfTheRun() {
        val f = run(pt(40, 100, 500), pt(45, 110, 520))
        val series = OutlookPresenter.series(f)
        assertEquals(2, series.size)
        assertEquals(listOf(0.0, 5.0), series[0].points.map { it.x })
        assertEquals(500.0, series[0].points.first().y, 0.001)
        assertEquals(110.0, series[1].points.last().y, 0.001)
    }

    @Test fun modelCarriesTilesAndTheCaveat() {
        val m = OutlookPresenter.model(run(pt(0, 100, 100, revenue = 0), pt(30, 140, 180, revenue = 90)), s, 30)
        assertEquals(30, m.days)
        assertEquals(5, m.tiles.size)
        assertEquals(OutlookPresenter.CAVEAT, m.caveat)
        assertFalse(m.warnings.isNotEmpty())
    }

    @Test fun anEmptyRunStillMakesAModel() {
        val m = OutlookPresenter.model(Forecast(c, 0, emptyList(), EndReason.COMPANY_GONE), s, 30)
        assertTrue(m.tiles.isEmpty())
        assertEquals(Tone.BAD, m.tone)
    }

    @Test fun aRealRunProducesAModelWithoutTouchingTheGame() {
        val session = PresentSupport.session("Outlook Works", 99L)
        val before = session.simulation.hash()
        val input = OutlookPresenter.input(session)
        assertNotNull(input)
        val m = OutlookPresenter.run(input!!, 10, session.settings)
        assertEquals(10, m.days)
        assertTrue(m.series.all { it.points.size == 3 })
        assertEquals(before, session.simulation.hash())
    }
}
