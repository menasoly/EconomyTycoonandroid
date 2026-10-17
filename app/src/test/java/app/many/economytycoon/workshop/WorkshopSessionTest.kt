package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.persistence.MemorySaveStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopSessionTest {
    private val quick = HarnessSettings(days = 10, sampleEvery = 10, checkEvery = 5, stallDays = 5)
    private fun session(store: MemorySaveStore = MemorySaveStore()) = WorkshopSession(Samples.content, store)
    private fun <T> value(o: Outcome<T>): T = (o as Outcome.Ok).value
    private fun <T> rule(o: Outcome<T>) = (o as Outcome.Rejected).rejection.rule

    @Test fun aNewSessionStartsWithABlankDraftAndAnEmptyShelf() {
        val s = session()
        assertEquals(0, s.library.size)
        assertNull(s.entryId)
        assertFalse(s.editor.dirty)
    }

    @Test fun slugsAreLowercaseDashedAndNeverEmpty() {
        assertEquals("my-big-farm", WorkshopSession.slug("  My Big Farm! "))
        assertEquals("scenario", WorkshopSession.slug("!!!"))
        assertTrue(WorkshopSession.slug("x".repeat(100)).length <= 40)
    }

    @Test fun startingFromATemplateNamesAndSeedsTheDraft() {
        val s = session()
        val d = value(s.startFrom("sprint", "Fast Money"))
        assertEquals("fast-money", d.id)
        assertEquals("Fast Money", d.title)
        assertEquals(DraftDefaults.seedFor("Fast Money"), d.seed)
        assertEquals(d, s.draft)
    }

    @Test fun anEmptyTitleFallsBackToTheTemplateName() {
        val s = session()
        assertEquals(DraftTemplates.byKey("sprint")?.name, value(s.startFrom("sprint", "  ")).title)
    }

    @Test fun unknownTemplatesAndBuiltInsAreRejected() {
        val s = session()
        assertEquals(Rules.UNKNOWN_ENTITY, rule(s.startFrom("nope", "x")))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(s.startFromBuiltIn("nope")))
    }

    @Test fun everyBuiltInScenarioCanBeRemixed() {
        val s = session()
        for (def in app.many.economytycoon.scenarios.ScenarioCatalog.all) {
            val d = value(s.startFromBuiltIn(def.id))
            assertEquals(def.id + "-remix", d.id)
        }
    }

    @Test fun savingFilesTheDraftAndClearsTheDirtyMark() {
        val s = session()
        s.startFrom("blank", "Alpha")
        s.editor.apply(DraftEdit.SetTagline("A tagline"))
        assertTrue(s.editor.dirty)
        val id = value(s.save("mine"))
        assertEquals(id, s.entryId)
        assertFalse(s.editor.dirty)
        assertEquals("mine", s.library.get(id)?.folder)
        assertEquals("A tagline", s.library.get(id)?.draft?.tagline)
    }

    @Test fun savingAgainUpdatesTheSameEntry() {
        val s = session()
        s.startFrom("blank", "Alpha")
        val id = value(s.save())
        s.editor.apply(DraftEdit.SetTagline("Changed"))
        assertEquals(id, value(s.save()))
        assertEquals(1, s.library.size)
        assertEquals("Changed", s.library.get(id)?.draft?.tagline)
    }

    @Test fun theShelfSurvivesANewSession() {
        val store = MemorySaveStore()
        val a = session(store)
        a.startFrom("blank", "Alpha"); a.save()
        val b = session(store)
        assertEquals(1, b.library.size)
        assertEquals("alpha", b.library.all().single().draft.id)
    }

    @Test fun newDraftsAvoidIdsAlreadyOnTheShelf() {
        val s = session()
        s.startFrom("blank", "Alpha"); s.save()
        assertEquals("alpha-2", value(s.startFrom("blank", "Alpha")).id)
    }

    @Test fun openingLoadsAnEntryAndForgetsStaleResults() {
        val s = session()
        s.startFrom("blank", "Alpha"); val id = value(s.save())
        s.startFrom("blank", "Beta")
        assertEquals("alpha", value(s.open(id)).id)
        assertEquals(id, s.entryId)
        assertNull(s.lastStudy)
        assertEquals(Rules.UNKNOWN_ENTITY, rule(s.open(99)))
    }

    @Test fun deletingRemovesTheEntryAndItsBaseline() {
        val s = session()
        s.startFrom("blank", "Alpha"); val id = value(s.save())
        assertTrue(s.delete(id))
        assertNull(s.entryId)
        assertEquals(0, session(MemorySaveStore()).library.size)
        assertFalse(s.delete(id))
    }

    @Test fun libraryChangesAreWrittenOut() {
        val store = MemorySaveStore()
        val s = session(store)
        s.startFrom("blank", "Alpha"); val id = value(s.save())
        s.changeLibrary { it.tag(id, "farm") }
        assertEquals(listOf("farm"), session(store).library.get(id)?.tags)
        assertTrue(s.changeLibrary { it.tag(id, "bad tag") } is Outcome.Rejected)
    }

    @Test fun shareTextRoundTripsThroughImport() {
        val a = session(); a.startFrom("land-rush", "Rush")
        val text = a.shareText()
        val b = session()
        val r = b.importText(text) as ImportResult.Shared
        assertEquals(a.draft, r.draft)
        assertEquals(a.draft, b.draft)
    }

    @Test fun importingASharedCodeUsesAFreeId() {
        val a = session(); a.startFrom("blank", "Rush"); a.save()
        val r = a.importText(a.shareText()) as ImportResult.Shared
        assertEquals("rush-2", r.draft.id)
    }

    @Test fun dslTextRoundTripsThroughImport() {
        val a = session(); a.startFrom("family-farm", "Farm")
        val r = session().importText(a.dslText()) as ImportResult.Written
        assertTrue(r.parse.ok)
        assertEquals(a.draft, r.parse.draft)
    }

    @Test fun badTextIsReportedWithAPosition() {
        val s = session()
        val before = s.draft
        val r = s.importText("scenario broken {\n  seed banana\n}")
        assertTrue(r is ImportResult.Failed)
        assertNotNull((r as ImportResult.Failed).at)
        assertEquals(before, s.draft)
        assertNotNull(s.lastParse)
    }

    @Test fun aDamagedCodeIsReportedNotThrown() {
        val s = session(); s.startFrom("blank", "x")
        val code = s.shareText().replaceFirst('0', '1').replaceFirst('2', '3')
        val r = session().importText(code.dropLast(9))
        assertTrue(r is ImportResult.Failed || r is ImportResult.Shared)
    }

    @Test fun convertingAValidDraftGivesParameters() {
        val s = session(); s.startFrom("sprint", "Quick")
        assertTrue(s.convert() is Outcome.Ok)
        assertNotNull(s.validation())
    }

    @Test fun studiesAreKeptAndBaselinesNeedASavedDraft() {
        val s = session(); s.startFrom("sandbox", "Play")
        assertEquals(Rules.WRONG_STATE, rule(s.captureBaseline("v1")))
        s.save()
        assertEquals(Rules.WRONG_STATE, rule(s.captureBaseline("v1")))
        assertEquals(Rules.WRONG_STATE, rule(s.checkBaseline()))
        value(s.runStudy(StudySpec(listOf(PolicyKind.IDLE), listOf(1L), quick)))
        assertNotNull(s.lastStudy)
        assertNull(s.baseline())
        value(s.captureBaseline("v1"))
        assertEquals("v1", s.baseline()?.name)
    }

    @Test fun anUnchangedDraftPassesItsBaseline() {
        val s = session(); s.startFrom("sandbox", "Play"); s.save()
        val spec = StudySpec(listOf(PolicyKind.IDLE), listOf(1L), quick)
        value(s.runStudy(spec)); value(s.captureBaseline("v1"))
        value(s.runStudy(spec))
        val rep = value(s.checkBaseline())
        assertTrue(rep.lines().joinToString("\n"), rep.identical)
        assertEquals(rep, s.regression)
    }

    @Test fun aChangedDraftFailsItsBaseline() {
        val s = session(); s.startFrom("sandbox", "Play"); s.save()
        val spec = StudySpec(listOf(PolicyKind.IDLE), listOf(1L), quick)
        value(s.runStudy(spec)); value(s.captureBaseline("v1"))
        s.editor.apply(DraftEdit.SetCapital(s.draft.company.capital + 25_000))
        value(s.runStudy(spec))
        assertFalse(value(s.checkBaseline()).passed)
    }

    @Test fun sweepsAreKept() {
        val s = session(); s.startFrom("sandbox", "Play")
        val rep = s.runSweep(SweepSpec(SweepParam.CAPITAL, listOf(50_000L, 100_000L), PolicyKind.IDLE, listOf(1L), quick))
        assertEquals(rep, s.lastSweep)
    }

    @Test fun aDamagedBaselineReadsAsNone() {
        val store = MemorySaveStore()
        val s = session(store); s.startFrom("sandbox", "Play"); val id = value(s.save())
        store.put("workshop.baseline.$id", byteArrayOf(9, 9))
        assertNull(s.baseline())
    }
}
