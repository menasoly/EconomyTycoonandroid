package app.many.economytycoon.ui.present

import app.many.economytycoon.consolidation.GroupFixture
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.reporting.AnnualReport
import app.many.economytycoon.reporting.Verdict
import app.many.economytycoon.session.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsPresenterTest {
    private val settings = GameSettings(autosave = false, pauseOnCritical = false)

    private fun report(index: Int, sales: Long, prior: AnnualReport? = null, partial: Boolean = false, cogs: Long = 4_000) = AnnualReport(
        CompanyId(1), "Test Works", index, Tick(index * 360L), Tick(index * 360L + if (partial) 100 else 360), partial,
        GroupFixture.income(sales = sales, cogs = cogs), GroupFixture.balance(), GroupFixture.balance(cash = 7_000, fixed = 22_000, debt = 8_000, capital = 21_000), prior,
    )

    @Test fun everyVerdictHasATone() {
        val tones = Verdict.values().map { ReportsPresenter.verdictTone(it) }
        assertEquals(Tone.GOOD, ReportsPresenter.verdictTone(Verdict.RECORD))
        assertEquals(Tone.BAD, ReportsPresenter.verdictTone(Verdict.LOSS))
        assertEquals(Tone.WARN, ReportsPresenter.verdictTone(Verdict.SLIP))
        assertEquals(Verdict.values().size, tones.size)
    }

    @Test fun aFinishedYearIsTitledWithItsNumber() {
        val c = ReportsPresenter.card(report(2, 10_000), 1, settings)
        assertEquals("2002", c.title)
        assertFalse(c.partial)
    }

    @Test fun theYearInProgressSaysSoFar() {
        val c = ReportsPresenter.card(report(2, 10_000, partial = true), null, settings)
        assertEquals("2002 so far", c.title)
        assertTrue(c.partial)
    }

    @Test fun rankIsShownOnlyWhenThereIsOne() {
        assertTrue(ReportsPresenter.card(report(0, 10_000), 3, settings).tiles.any { it.label == "Rank by net worth" && it.value == "#3" })
        assertFalse(ReportsPresenter.card(report(0, 10_000), null, settings).tiles.any { it.label == "Rank by net worth" })
    }

    @Test fun marginReadsNotApplicableWithoutSales() {
        val c = ReportsPresenter.card(report(0, 0, cogs = 0), null, settings)
        assertEquals("n/a", c.tiles.first { it.label == "Net margin" }.value)
    }

    @Test fun changesCompareWithTheYearBefore() {
        val a = report(0, 10_000)
        val b = report(1, 12_000, prior = a)
        val lines = ReportsPresenter.changes(b, settings)
        assertTrue(lines.toString(), lines.any { it.startsWith("Revenue +20") })
        assertTrue(lines.toString(), lines.any { it.startsWith("Net income up") })
        assertTrue(lines.toString(), lines.any { it == "The most profitable year so far" })
    }

    @Test fun aFirstYearHasNoComparisons() {
        val lines = ReportsPresenter.changes(report(0, 10_000), settings)
        assertTrue(lines.toString(), lines.none { it.startsWith("Revenue") && it.contains("year before") })
    }

    @Test fun fallingProfitReadsAsDown() {
        val a = report(0, 20_000)
        val b = report(1, 10_000, prior = a)
        assertTrue(ReportsPresenter.changes(b, settings).any { it.startsWith("Net income down") })
    }

    @Test fun modelListsNewestYearFirst() {
        val a = report(0, 10_000); val b = report(1, 12_000, prior = a)
        val m = ReportsPresenter.model("Test Works", listOf(a, b), { null }, settings)
        assertEquals(listOf(1, 0), m.years.map { it.index })
        assertEquals(3, m.years.first().income.size)
        assertEquals(3, m.years.first().balance.size)
    }

    @Test fun aNewGameHasASingleYearInProgress() {
        val s = PresentSupport.session("Yearly Works", 83L)
        val m = ReportsPresenter.build(s)!!
        assertEquals(1, m.years.size)
        assertTrue(m.years.single().partial)
    }

    @Test fun noCompanyNoModel() {
        val s = PresentSupport.session("Yearly Works", 83L)
        assertNull(ReportsPresenter.build(s, CompanyId(9_999)))
        assertNotNull(ReportsPresenter.build(s))
    }
}
