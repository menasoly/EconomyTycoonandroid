package app.many.economytycoon.ui.present

import app.many.economytycoon.banking.BankAction
import app.many.economytycoon.banking.BankingFx
import app.many.economytycoon.banking.BankingService
import app.many.economytycoon.banking.StaticBankingInputs
import app.many.economytycoon.banking.StressScenario
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import app.many.economytycoon.session.Formatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankStressPresenterTest {
    private val inputs = StaticBankingInputs()
    private val country = BankingFx.country
    private val money: (Money) -> String = { Formatting.compactMoney(it) }

    private fun service(): BankingService = BankingService().also { it.seedRivals(country, 4, inputs, Tick(0), RandomProvider(2L)) }

    @Test fun everyScenarioHasARow() {
        val rows = BankStressPresenter.scenarios()
        assertEquals(StressScenario.ALL.size, rows.size)
        assertEquals("baseline", rows.first().id)
    }

    @Test fun baselineHasNoShocks() {
        assertEquals("No shocks", BankStressPresenter.shocks(StressScenario.BASELINE))
    }

    @Test fun severeScenarioListsItsShocks() {
        val text = BankStressPresenter.shocks(StressScenario.SEVERE)
        assertTrue(text.contains("rates"))
        assertTrue(text.contains("deposits leave"))
        assertTrue(text.contains("wholesale"))
    }

    @Test fun viewMatchesTheReport() {
        val s = service()
        val report = s.stressReport(country, StressScenario.SEVERE, inputs, Tick(10))
        val v = BankStressPresenter.build(report, money)
        assertEquals(report.results.size, v.rows.size)
        assertEquals(report.failures.size.toString(), v.tiles[1].value)
        assertEquals("Severe depression", v.title)
    }

    @Test fun rowsAreSortedWeakestFirst() {
        val report = service().stressReport(country, StressScenario.SEVERE, inputs, Tick(10))
        val v = BankStressPresenter.build(report, money)
        val sorted = report.results.sortedBy { it.minCet1Ratio.ppm }.map { it.name }
        assertEquals(sorted, v.rows.map { it.name })
    }

    @Test fun verdictToneFollowsOutcome() {
        val report = service().stressReport(country, StressScenario.SEVERE, inputs, Tick(10))
        for ((r, row) in report.results.sortedBy { it.minCet1Ratio.ppm }.zip(BankStressPresenter.build(report, money).rows)) {
            assertEquals(if (r.passed) Tone.GOOD else Tone.BAD, row.tone)
        }
    }

    @Test fun emptySectorSaysSo() {
        val report = BankingService().stressReport(country, StressScenario.ADVERSE, inputs, Tick(1))
        val v = BankStressPresenter.build(report, money)
        assertEquals("There are no banks to test.", v.summary)
        assertTrue(v.rows.isEmpty())
    }

    @Test fun baselinePassesCleanly() {
        val v = BankStressPresenter.build(service().stressReport(country, StressScenario.BASELINE, inputs, Tick(1)), money)
        assertEquals(Tone.GOOD, v.tiles[1].tone)
    }
}
