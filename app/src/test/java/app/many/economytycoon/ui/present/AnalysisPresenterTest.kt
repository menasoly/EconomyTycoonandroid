package app.many.economytycoon.ui.present

import app.many.economytycoon.analysis.AnalysisFixture
import app.many.economytycoon.analysis.CompanyAnalysis
import app.many.economytycoon.analysis.Grade
import app.many.economytycoon.analysis.Measure
import app.many.economytycoon.analysis.Standing
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.session.AnalysisView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPresenterTest {
    private val money: (Money) -> String = { it.wholeUnits().toString() }
    private val healthy = CompanyAnalysis.of(AnalysisFixture.input(history = (1..8).map { Money.units(1_000L * it) }))
    private fun view(a: CompanyAnalysis = healthy, standings: List<Standing> = emptyList(), peers: Int = 0) = AnalysisView(CompanyId(1), "Test Works", a, standings, peers)

    @Test fun valuesUseTheUnitThatSuitsTheMeasure() {
        assertEquals("1.50x", AnalysisPresenter.value(Measure.CURRENT, 1_500_000))
        assertEquals("12.5%", AnalysisPresenter.value(Measure.NET_MARGIN, 125_000))
        assertEquals("1 day", AnalysisPresenter.value(Measure.CASH_CYCLE, 1))
        assertEquals("45 days", AnalysisPresenter.value(Measure.CASH_CYCLE, 45))
    }

    @Test fun gradesMapToTones() {
        assertEquals(Tone.GOOD, AnalysisPresenter.gradeTone(Grade.STRONG))
        assertEquals(Tone.INFO, AnalysisPresenter.gradeTone(Grade.SOUND))
        assertEquals(Tone.WARN, AnalysisPresenter.gradeTone(Grade.WATCH))
        assertEquals(Tone.BAD, AnalysisPresenter.gradeTone(Grade.WEAK))
    }

    @Test fun everyGradeHasAHeadline() {
        val lines = Grade.values().map { AnalysisPresenter.headline(it) } + AnalysisPresenter.headline(null)
        assertEquals(lines.size, lines.toSet().size)
    }

    @Test fun modelCarriesEverySection() {
        val m = AnalysisPresenter.model(view(), money)
        assertEquals("Test Works", m.company)
        assertEquals(healthy.readings.size, m.readings.size)
        assertEquals(6, m.efficiency.size)
        assertEquals(5, m.returns.size)
        assertEquals(5, m.breakEven.size)
        assertEquals(3, m.outlook.size)
        assertTrue(m.costs.isNotEmpty())
        assertTrue(m.costs.all { it.ratio in 0.0..1.0 })
    }

    @Test fun readingsShowTheirHintAndGrade() {
        val m = AnalysisPresenter.model(view(), money)
        val r = m.readings.first { it.label == Measure.NET_MARGIN.label }
        assertEquals(Measure.NET_MARGIN.hint, r.hint)
        assertTrue(r.grade in Grade.values().map { it.label })
    }

    @Test fun anOutlookWithoutHistoryExplainsItself() {
        val bare = CompanyAnalysis.of(AnalysisFixture.input())
        val m = AnalysisPresenter.model(view(bare), money)
        assertEquals(1, m.outlook.size)
        assertEquals(Tone.MUTED, m.outlook.single().tone)
    }

    @Test fun anUnanalysableCompanyIsMutedNotBroken() {
        val none = CompanyAnalysis.of(AnalysisFixture.input(income = AnalysisFixture.income(sales = 0, cogs = 0)))
        val m = AnalysisPresenter.model(view(none), money)
        assertEquals("n/a", m.returns.single().value)
    }

    @Test fun aRunwayOfWeeksIsAWarning() {
        val burning = CompanyAnalysis.of(AnalysisFixture.input(cashFlow = AnalysisFixture.cashFlow(opening = 80_000, closing = 50_000)))
        val f = AnalysisPresenter.runway(burning, money)
        assertTrue(f.value, f.value.startsWith("50 days at 1000 a day"))
        assertEquals(Tone.WARN, f.tone)
    }

    @Test fun aRunwayUnderAMonthIsBad() {
        val burning = CompanyAnalysis.of(AnalysisFixture.input(cashFlow = AnalysisFixture.cashFlow(opening = 150_000, closing = 50_000)))
        val f = AnalysisPresenter.runway(burning, money)
        assertTrue(f.value, f.value.startsWith("15 days"))
        assertEquals(Tone.BAD, f.tone)
    }

    @Test fun aLongRunwayIsFine() {
        val burning = CompanyAnalysis.of(AnalysisFixture.input(cashFlow = AnalysisFixture.cashFlow(opening = 56_000, closing = 50_000)))
        val f = AnalysisPresenter.runway(burning, money)
        assertTrue(f.value, f.value.startsWith("250 days"))
        assertEquals(Tone.GOOD, f.tone)
    }

    @Test fun runwayIsCalmWhenCashGrows() {
        val f = AnalysisPresenter.runway(healthy, money)
        assertEquals(Tone.GOOD, f.tone)
        assertEquals("Cash is not shrinking", f.value)
    }

    @Test fun peerNoteSaysWhenThereAreTooFew() {
        val few = AnalysisPresenter.model(view(peers = 2), money)
        assertTrue(few.peerNote, few.peerNote.contains("at least three"))
        assertTrue(few.peers.isEmpty())
    }

    @Test fun standingsBecomeRowsWithAPlace() {
        val s = Standing(Measure.NET_MARGIN, 150_000, 100_000, 200_000, 900_000, 5)
        val m = AnalysisPresenter.model(view(standings = listOf(s), peers = 5), money)
        val row = m.peers.single()
        assertEquals("Top quarter", row.place)
        assertEquals("15.0%", row.you)
        assertEquals("10.0%", row.median)
        assertEquals("20.0%", row.best)
        assertEquals(Tone.GOOD, row.tone)
    }

    @Test fun placesCoverEveryQuartile() {
        fun place(rank: Long) = AnalysisPresenter.place(Standing(Measure.CASH, 0, 0, 0, rank, 5))
        assertEquals("Top quarter", place(800_000))
        assertEquals("Above the middle", place(600_000))
        assertEquals("Below the middle", place(300_000))
        assertEquals("Bottom quarter", place(100_000))
    }

    @Test fun worriesNameTheWeakReadings() {
        val bad = CompanyAnalysis.of(AnalysisFixture.input(balance = AnalysisFixture.balance(cash = 500, shortDebt = 300_000)))
        val m = AnalysisPresenter.model(view(bad), money)
        assertTrue(m.worries.isNotEmpty())
        assertTrue(m.worries.all { it.contains(".") })
    }

    @Test fun buildForARunningGameWorks() {
        val s = PresentSupport.session("Gauge Works", 3L).also { repeat(40) { _ -> it.step() } }
        val m = AnalysisPresenter.build(s)
        assertNotNull(m)
        assertEquals("Gauge Works", m!!.company)
    }
}
