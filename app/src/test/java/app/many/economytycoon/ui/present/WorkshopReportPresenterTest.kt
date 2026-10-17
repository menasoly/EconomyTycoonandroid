package app.many.economytycoon.ui.present

import app.many.economytycoon.workshop.BalanceAnalyst
import app.many.economytycoon.workshop.CompatReport
import app.many.economytycoon.workshop.CompatFinding
import app.many.economytycoon.workshop.DifficultyBand
import app.many.economytycoon.workshop.DraftValidator
import app.many.economytycoon.workshop.IssueSeverity
import app.many.economytycoon.workshop.Regression
import app.many.economytycoon.workshop.RunEnd
import app.many.economytycoon.workshop.Samples
import app.many.economytycoon.workshop.ScoreFormula
import app.many.economytycoon.workshop.StudyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopReportPresenterTest {
    private val d = Samples.cheap()
    private val runs = listOf(
        Samples.run(d, "idle", 1, 100, end = listOf(0L, 0L), netWorth = 300_000L),
        Samples.run(d, "greedy", 1, 100, end = listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON, wonOn = 90, netWorth = 900_000L),
        Samples.run(d, "greedy", 2, 100, end = listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON, wonOn = 95, netWorth = 950_000L),
    )
    private val study = StudyResult(runs, BalanceAnalyst.analyse(d, runs))

    @Test fun noStudyGivesAnInvitation() {
        val m = WorkshopReportPresenter.model(null)
        assertEquals(WorkshopReportPresenter.NOTHING_YET, m.empty)
        assertTrue(m.tiles.isEmpty() && m.policies.isEmpty())
        assertEquals(Tone.MUTED, m.tone)
    }

    @Test fun aStudyShowsVerdictTilesAndOneBlockPerPolicy() {
        val m = WorkshopReportPresenter.model(study)
        assertNull(m.empty)
        assertEquals(5, m.tiles.size)
        assertEquals(study.report.digests.map { it.policy }, m.policies.map { it.policy })
        assertTrue(m.headline.contains("of 100"))
    }

    @Test fun aWinningPolicyIsGoodAndAnIdleOneIsNot() {
        val m = WorkshopReportPresenter.model(study)
        assertEquals(Tone.GOOD, m.policies.first { it.policy == "greedy" }.tone)
        assertTrue(m.policies.first { it.policy == "idle" }.tone != Tone.GOOD)
    }

    @Test fun policyBlocksListTheirFacts() {
        val facts = WorkshopReportPresenter.model(study).policies.first { it.policy == "greedy" }.facts
        assertEquals("2 (2 won, 0 lost)", facts.first { it.label == "Runs" }.value)
        assertTrue(facts.first { it.label == "Goals met" }.value.startsWith("day "))
    }

    @Test fun curvesAreScaledToEightLevels() {
        assertEquals(listOf(0, 4, 7), WorkshopReportPresenter.curve(listOf(0L, 50L, 100L)))
        assertEquals(listOf(3, 3), WorkshopReportPresenter.curve(listOf(5L, 5L)))
        assertTrue(WorkshopReportPresenter.curve(emptyList()).isEmpty())
        assertTrue(WorkshopReportPresenter.curve(listOf(1L, 2L), 0).isEmpty())
    }

    @Test fun longCurvesAreThinnedKeepingBothEnds() {
        val c = WorkshopReportPresenter.curve((0L..99L).toList(), 10)
        assertEquals(10, c.size)
        assertEquals(0, c.first()); assertEquals(7, c.last())
        assertEquals(c.sorted(), c)
    }

    @Test fun sparklineUsesBlockCharacters() {
        assertEquals("▁▄█", WorkshopReportPresenter.spark(listOf(0, 3, 7)))
        assertEquals("▁█", WorkshopReportPresenter.spark(listOf(-4, 40)))
    }

    @Test fun findingsCarryWhichRunTheyAreAbout() {
        val f = app.many.economytycoon.workshop.BalanceFinding("x", IssueSeverity.WARNING, "message", "greedy", 3)
        val row = WorkshopReportPresenter.finding(f)
        assertEquals("greedy, seed 3", row.about)
        assertEquals(Tone.WARN, row.tone)
        assertNull(WorkshopReportPresenter.finding(app.many.economytycoon.workshop.BalanceFinding("y", IssueSeverity.NOTE, "m")).about)
    }

    @Test fun everyBandHasATone() {
        for (b in DifficultyBand.values()) assertNotNull(WorkshopReportPresenter.bandTone(b))
        assertEquals(Tone.BAD, WorkshopReportPresenter.bandTone(DifficultyBand.IMPOSSIBLE))
    }

    @Test fun sweepAndRegressionHaveEmptyStates() {
        assertEquals(listOf("No sweep has been run."), WorkshopReportPresenter.sweepLines(null))
        assertEquals(listOf("No baseline comparison yet."), WorkshopReportPresenter.regressionLines(null))
        assertEquals(Tone.MUTED, WorkshopReportPresenter.regressionTone(null))
    }

    @Test fun regressionToneFollowsTheOutcome() {
        val b = Regression.capture("v1", d, runs)
        assertEquals(Tone.GOOD, WorkshopReportPresenter.regressionTone(Regression.compare(b, d, runs)))
        val changed = runs.map { it.copy(stateHash = "x") }
        assertEquals(Tone.INFO, WorkshopReportPresenter.regressionTone(Regression.compare(b, d, changed)))
        val worse = listOf(runs[0], runs[1].let { it.copy(samples = it.samples.dropLast(1) + it.samples.last().copy(netWorth = 1L)) }, runs[2])
        assertEquals(Tone.BAD, WorkshopReportPresenter.regressionTone(Regression.compare(b, d, worse)))
    }

    @Test fun compatHeadlinesAndRows() {
        assertEquals("Fits the loaded content.", WorkshopReportPresenter.compatHeadline(CompatReport(emptyList())))
        val note = CompatReport(listOf(CompatFinding("pack.newer", IssueSeverity.NOTE, "packs[0]", "newer")))
        assertEquals("Fits the loaded content, with 1 remarks.", WorkshopReportPresenter.compatHeadline(note))
        val bad = CompatReport(listOf(CompatFinding("pack.missing", IssueSeverity.ERROR, "packs[0]", "gone")))
        assertEquals("Does not fit the loaded content: 1 errors.", WorkshopReportPresenter.compatHeadline(bad))
        assertEquals("packs[0]: gone", WorkshopReportPresenter.compatRows(bad).single().text)
    }

    @Test fun scoreFactsEndWithTheTotal() {
        val facts = WorkshopReportPresenter.scoreFacts(ScoreFormula.score(d, runs[1]))
        assertEquals("Score", facts.last().label)
        assertEquals("Difficulty multiplier", facts[facts.size - 2].label)
        assertTrue(facts.dropLast(2).all { it.value.startsWith("+") || it.value.startsWith("-") })
    }

    @Test fun validationOfTheSampleStillWorksAlongside() {
        assertTrue(DraftValidator.validate(d, Samples.content).ok)
    }
}
