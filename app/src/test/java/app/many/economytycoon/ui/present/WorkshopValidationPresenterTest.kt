package app.many.economytycoon.ui.present

import app.many.economytycoon.workshop.DraftIssue
import app.many.economytycoon.workshop.DraftValidator
import app.many.economytycoon.workshop.IssueSeverity
import app.many.economytycoon.workshop.Samples
import app.many.economytycoon.workshop.SourceMap
import app.many.economytycoon.workshop.TextPosition
import app.many.economytycoon.workshop.ValidationReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopValidationPresenterTest {
    private fun issue(sev: IssueSeverity, path: String, fix: String? = null) = DraftIssue("code.$path", sev, path, "message for $path", fix)

    @Test fun aCleanReportSaysSo() {
        val m = WorkshopValidationPresenter.model(ValidationReport(emptyList()))
        assertEquals(WorkshopValidationPresenter.CLEAN, m.headline)
        assertEquals(Tone.GOOD, m.tone)
        assertTrue(m.groups.isEmpty())
        assertTrue(m.playable)
    }

    @Test fun errorsMakeTheHeadlineBadAndNotPlayable() {
        val m = WorkshopValidationPresenter.model(ValidationReport(listOf(issue(IssueSeverity.ERROR, "title"), issue(IssueSeverity.WARNING, "seed"))))
        assertEquals(Tone.BAD, m.tone)
        assertFalseOk(m)
        assertTrue(m.headline.startsWith("Cannot be played yet"))
    }

    private fun assertFalseOk(m: ValidationModel) = assertEquals(false, m.playable)

    @Test fun warningsOnlyStillPlayable() {
        val m = WorkshopValidationPresenter.model(ValidationReport(listOf(issue(IssueSeverity.WARNING, "seed"))))
        assertEquals(Tone.WARN, m.tone)
        assertTrue(m.playable)
        assertTrue(m.headline.startsWith("Can be played"))
    }

    @Test fun groupsComeWorstFirstWithCounts() {
        val r = ValidationReport(listOf(issue(IssueSeverity.NOTE, "a"), issue(IssueSeverity.ERROR, "b"), issue(IssueSeverity.ERROR, "c"), issue(IssueSeverity.WARNING, "d")))
        val g = WorkshopValidationPresenter.model(r).groups
        assertEquals(listOf("2 errors to fix", "1 warning", "1 note"), g.map { it.title })
        assertEquals(listOf(Tone.BAD, Tone.WARN, Tone.INFO), g.map { it.tone })
    }

    @Test fun tilesCountEachKind() {
        val r = ValidationReport(listOf(issue(IssueSeverity.ERROR, "b"), issue(IssueSeverity.NOTE, "a"), issue(IssueSeverity.NOTE, "c")))
        assertEquals(listOf("1", "0", "2"), WorkshopValidationPresenter.model(r).tiles.map { it.value })
    }

    @Test fun rowsCarryThePlaceInTheText() {
        val map = SourceMap(mapOf("goals[0]" to TextPosition(7, 3)))
        val row = WorkshopValidationPresenter.row(issue(IssueSeverity.ERROR, "goals[0].target", "Use 10"), map)
        assertEquals("line 7, column 3", row.where)
        assertEquals("Use 10", row.fix)
        assertEquals("error", row.severity)
        assertNull(WorkshopValidationPresenter.row(issue(IssueSeverity.ERROR, "title")).where)
    }

    @Test fun findingsForAFieldIncludeThoseBelowIt() {
        val r = ValidationReport(listOf(issue(IssueSeverity.ERROR, "goals[1].target"), issue(IssueSeverity.NOTE, "goals[1]"), issue(IssueSeverity.ERROR, "goals[2]")))
        assertEquals(2, WorkshopValidationPresenter.forField(r, "goals[1]").size)
        assertEquals(Tone.BAD, WorkshopValidationPresenter.worstAt(r, "goals[1]"))
        assertNull(WorkshopValidationPresenter.worstAt(r, "timing"))
    }

    @Test fun aRealBrokenDraftListsItsProblems() {
        val d = Samples.cheap().copy(title = "x", goals = emptyList())
        val m = WorkshopValidationPresenter.model(DraftValidator.validate(d, Samples.content))
        assertTrue(m.groups.first().rows.isNotEmpty())
        assertTrue(m.groups.first().rows.any { it.path == "title" })
    }
}
