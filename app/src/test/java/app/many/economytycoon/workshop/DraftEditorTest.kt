package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.world.WorldSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftEditorTest {
    private fun editor(d: ScenarioDraft = Samples.minimal()) = DraftEditor(d)

    private fun DraftEditor.ok(e: DraftEdit): ScenarioDraft = (apply(e) as Outcome.Ok).value

    private fun DraftEditor.rejected(e: DraftEdit): String {
        val r = apply(e)
        assertTrue("expected a refusal of $e", r is Outcome.Rejected)
        return (r as Outcome.Rejected).rejection.rule
    }

    @Test fun anEditChangesTheDraft() {
        val ed = editor()
        ed.ok(DraftEdit.SetTitle("Brand New"))
        assertEquals("Brand New", ed.draft.title)
        assertTrue(ed.canUndo)
        assertFalse(ed.canRedo)
    }

    @Test fun undoAndRedoWalkTheHistory() {
        val ed = editor()
        val start = ed.draft
        ed.ok(DraftEdit.SetCapital(111_000))
        ed.ok(DraftEdit.SetRivals(5))
        assertTrue(ed.undo())
        assertEquals(start.world.rivals, ed.draft.world.rivals)
        assertEquals(111_000L, ed.draft.company.capital)
        assertTrue(ed.undo())
        assertEquals(start, ed.draft)
        assertFalse(ed.undo())
        assertTrue(ed.redo())
        assertTrue(ed.redo())
        assertEquals(5, ed.draft.world.rivals)
        assertFalse(ed.redo())
    }

    @Test fun aNewEditClearsWhatCouldHaveBeenRedone() {
        val ed = editor()
        ed.ok(DraftEdit.SetRivals(1))
        ed.undo()
        assertTrue(ed.canRedo)
        ed.ok(DraftEdit.SetRivals(2))
        assertFalse(ed.canRedo)
    }

    @Test fun anEditThatChangesNothingLeavesNoHistory() {
        val ed = editor()
        ed.ok(DraftEdit.SetRivals(ed.draft.world.rivals))
        assertFalse(ed.canUndo)
        assertEquals(0L, ed.revision)
    }

    @Test fun consecutiveEditsOfTheSameFieldShareOneUndoStep() {
        val ed = editor()
        val start = ed.draft
        for (t in listOf("Bra", "Bran", "Brand", "Brand New")) ed.ok(DraftEdit.SetTitle(t))
        assertEquals(1, ed.history().size)
        ed.undo()
        assertEquals(start, ed.draft)
    }

    @Test fun editsOfDifferentFieldsAreSeparateSteps() {
        val ed = editor()
        ed.ok(DraftEdit.SetTitle("Brand New"))
        ed.ok(DraftEdit.SetTagline("Tag"))
        ed.ok(DraftEdit.SetTitle("Brand Newer"))
        assertEquals(3, ed.history().size)
    }

    @Test fun anUndoBreaksTheCoalescingChain() {
        val ed = editor()
        ed.ok(DraftEdit.SetTitle("One One"))
        ed.undo(); ed.redo()
        ed.ok(DraftEdit.SetTitle("Two Two"))
        assertEquals(2, ed.history().size)
    }

    @Test fun historyIsLimited() {
        val ed = DraftEditor(Samples.minimal(), historyLimit = 3)
        for (i in 1..6) ed.ok(DraftEdit.AddHint("hint $i"))
        assertEquals(3, ed.history().size)
        repeat(3) { assertTrue(ed.undo()) }
        assertFalse(ed.undo())
        assertEquals(3, ed.draft.hints.size)
    }

    @Test fun labelsNameWhatCanBeUndoneAndRedone() {
        val ed = editor()
        assertNull(ed.undoLabel)
        ed.ok(DraftEdit.AddGoal(DraftGoal(GoalKind.CASH, 900_000)))
        assertEquals("Add goal", ed.undoLabel)
        ed.undo()
        assertEquals("Add goal", ed.redoLabel)
        assertNull(ed.undoLabel)
    }

    @Test fun revisionCountsEveryChange() {
        val ed = editor()
        ed.ok(DraftEdit.SetRivals(1))
        ed.undo()
        ed.redo()
        assertEquals(3L, ed.revision)
    }

    @Test fun dirtyTracksTheSavedDraft() {
        val ed = editor()
        assertFalse(ed.dirty)
        ed.ok(DraftEdit.SetRivals(1))
        assertTrue(ed.dirty)
        ed.markSaved()
        assertFalse(ed.dirty)
        ed.undo()
        assertTrue(ed.dirty)
        ed.redo()
        assertFalse(ed.dirty)
    }

    @Test fun applyAllIsOneStepAndAllOrNothing() {
        val ed = editor()
        val before = ed.draft
        val good = listOf(DraftEdit.SetRivals(2), DraftEdit.SetCapital(321_000), DraftEdit.AddHint("one"))
        assertTrue(ed.applyAll("Tune", good) is Outcome.Ok)
        assertEquals(listOf("Tune"), ed.history())
        ed.undo()
        assertEquals(before, ed.draft)
        ed.redo()
        val bad = listOf(DraftEdit.SetRivals(9), DraftEdit.RemoveGoal(7))
        assertTrue(ed.applyAll("Broken", bad) is Outcome.Rejected)
        assertEquals(2, ed.draft.world.rivals)
        assertEquals(listOf("Tune"), ed.history())
    }

    @Test fun clearHistoryKeepsTheDraft() {
        val ed = editor()
        ed.ok(DraftEdit.SetRivals(4))
        ed.clearHistory()
        assertFalse(ed.canUndo)
        assertEquals(4, ed.draft.world.rivals)
    }

    // ---- individual edits -------------------------------------------------------------------

    @Test fun goalsCanBeAddedUpdatedMovedAndRemoved() {
        val ed = editor()
        ed.ok(DraftEdit.AddGoal(DraftGoal(GoalKind.CASH, 700_000)))
        ed.ok(DraftEdit.AddGoal(DraftGoal(GoalKind.FACILITIES, 6)))
        ed.ok(DraftEdit.UpdateGoal(1, DraftGoal(GoalKind.CASH, 800_000, weight = 4)))
        assertEquals(800_000L, ed.draft.goals[1].target)
        ed.ok(DraftEdit.MoveGoal(2, 0))
        assertEquals(listOf(GoalKind.FACILITIES, GoalKind.NET_WORTH, GoalKind.CASH), ed.draft.goals.map { it.kind })
        ed.ok(DraftEdit.RemoveGoal(1))
        assertEquals(listOf(GoalKind.FACILITIES, GoalKind.CASH), ed.draft.goals.map { it.kind })
    }

    @Test fun goalListHasALimit() {
        val ed = editor(Samples.minimal().copy(goals = emptyList()))
        for (k in GoalKind.values().take(DraftLimits.MAX_GOALS)) ed.ok(DraftEdit.AddGoal(DraftGoal(k, 5)))
        assertEquals(Rules.LIMIT_EXCEEDED, ed.rejected(DraftEdit.AddGoal(DraftGoal(GoalKind.values().last(), 5))))
    }

    @Test fun editsOfMissingItemsAreRefusedWithUnknownEntity() {
        val ed = editor()
        assertEquals(Rules.UNKNOWN_ENTITY, ed.rejected(DraftEdit.RemoveGoal(4)))
        assertEquals(Rules.UNKNOWN_ENTITY, ed.rejected(DraftEdit.UpdateGoal(-1, DraftGoal(GoalKind.CASH, 5))))
        assertEquals(Rules.UNKNOWN_ENTITY, ed.rejected(DraftEdit.MoveGoal(0, 3)))
        assertEquals(Rules.UNKNOWN_ENTITY, ed.rejected(DraftEdit.RemoveEvent(0)))
        assertEquals(Rules.UNKNOWN_ENTITY, ed.rejected(DraftEdit.RemoveFail(0)))
        assertEquals(Rules.UNKNOWN_ENTITY, ed.rejected(DraftEdit.RemoveHint(0)))
        assertEquals(Rules.UNKNOWN_ENTITY, ed.rejected(DraftEdit.RemoveStarter(0)))
        assertEquals(Rules.UNKNOWN_ENTITY, ed.rejected(DraftEdit.RemoveTag(DifficultyTag.PUZZLE)))
        assertEquals(Rules.UNKNOWN_ENTITY, ed.rejected(DraftEdit.RemovePack("nope:1.0")))
    }

    @Test fun eventsStayInDayOrderWhateverIsAddedOrChanged() {
        val ed = editor()
        ed.ok(DraftEdit.AddEvent(ScriptedEvent(50, EventKind.GRANT, 10)))
        ed.ok(DraftEdit.AddEvent(ScriptedEvent(10, EventKind.GRANT, 20)))
        ed.ok(DraftEdit.AddEvent(ScriptedEvent(30, EventKind.GRANT, 30)))
        assertEquals(listOf(10, 30, 50), ed.draft.events.map { it.day })
        ed.ok(DraftEdit.UpdateEvent(0, ScriptedEvent(70, EventKind.GRANT, 20)))
        assertEquals(listOf(30, 50, 70), ed.draft.events.map { it.day })
        ed.ok(DraftEdit.RemoveEvent(1))
        assertEquals(listOf(30, 70), ed.draft.events.map { it.day })
    }

    @Test fun eventListHasALimit() {
        val ed = editor()
        repeat(DraftLimits.MAX_EVENTS) { ed.ok(DraftEdit.AddEvent(ScriptedEvent(it, EventKind.ANNOUNCE, 0, "e$it"))) }
        assertEquals(Rules.LIMIT_EXCEEDED, ed.rejected(DraftEdit.AddEvent(ScriptedEvent(1, EventKind.ANNOUNCE, 0, "one more"))))
    }

    @Test fun tagsAreUniqueAndLimited() {
        val ed = editor(Samples.minimal().copy(tags = emptyList()))
        ed.ok(DraftEdit.AddTag(DifficultyTag.HARSH))
        assertEquals(Rules.DUPLICATE, ed.rejected(DraftEdit.AddTag(DifficultyTag.HARSH)))
        for (t in DifficultyTag.values().filter { it != DifficultyTag.HARSH }.take(5)) ed.ok(DraftEdit.AddTag(t))
        assertEquals(DraftLimits.MAX_TAGS, ed.draft.tags.size)
        assertEquals(Rules.LIMIT_EXCEEDED, ed.rejected(DraftEdit.AddTag(DifficultyTag.values().first { it !in ed.draft.tags })))
        ed.ok(DraftEdit.RemoveTag(DifficultyTag.HARSH))
        assertFalse(DifficultyTag.HARSH in ed.draft.tags)
    }

    @Test fun packsAreUniqueSortedAndRemovable() {
        val ed = editor()
        ed.ok(DraftEdit.AddPack("food:1.0"))
        ed.ok(DraftEdit.AddPack("base:1.0"))
        assertEquals(listOf("base:1.0", "food:1.0"), ed.draft.packs)
        assertEquals(Rules.DUPLICATE, ed.rejected(DraftEdit.AddPack("base:1.0")))
        ed.ok(DraftEdit.RemovePack("base:1.0"))
        assertEquals(listOf("food:1.0"), ed.draft.packs)
    }

    @Test fun rulesCanBeSetAndReset() {
        val ed = editor()
        ed.ok(DraftEdit.SetRule(RuleKey.SPENDING_PROPENSITY, 25_000))
        assertEquals(25_000L, ed.draft.rules[RuleKey.SPENDING_PROPENSITY])
        ed.ok(DraftEdit.ClearRule(RuleKey.SPENDING_PROPENSITY))
        assertTrue(ed.draft.rules.isEmpty())
    }

    @Test fun startingSitesAreEditable() {
        val ed = editor()
        ed.ok(DraftEdit.AddStarter(StarterSite("grow_wheat", 0)))
        ed.ok(DraftEdit.UpdateStarter(0, StarterSite("grow_corn", 2)))
        assertEquals(StarterSite("grow_corn", 2), ed.draft.company.starters.single())
        repeat(DraftLimits.MAX_STARTERS - 1) { ed.ok(DraftEdit.AddStarter(StarterSite("grow_wheat", 1))) }
        assertEquals(Rules.LIMIT_EXCEEDED, ed.rejected(DraftEdit.AddStarter(StarterSite("grow_wheat", 1))))
        ed.ok(DraftEdit.RemoveStarter(0))
        assertEquals(DraftLimits.MAX_STARTERS - 1, ed.draft.company.starters.size)
    }

    @Test fun failuresHintsAndTimingAreEditable() {
        val ed = editor()
        ed.ok(DraftEdit.AddFail(FailCondition(FailKind.CASH_BELOW, 100, 10)))
        ed.ok(DraftEdit.UpdateFail(0, FailCondition(FailKind.CASH_BELOW, 200, 10)))
        assertEquals(200L, ed.draft.fails[0].threshold)
        ed.ok(DraftEdit.AddHint("a"))
        ed.ok(DraftEdit.UpdateHint(0, "b"))
        assertEquals(listOf("b"), ed.draft.hints)
        ed.ok(DraftEdit.SetTiming(Timing(3, 400, 700)))
        assertEquals(3, ed.draft.timing.years)
        ed.ok(DraftEdit.SetWorldSize(WorldSize.MEDIUM))
        ed.ok(DraftEdit.SetCountry(1))
        assertEquals(WorldSize.MEDIUM, ed.draft.world.size)
        assertEquals(1, ed.draft.world.countryIndex)
        ed.ok(DraftEdit.RemoveHint(0)); ed.ok(DraftEdit.RemoveFail(0))
        assertTrue(ed.draft.hints.isEmpty() && ed.draft.fails.isEmpty())
    }

    @Test fun replaceAllSwapsTheWholeDraftAndCanBeUndone() {
        val ed = editor()
        val before = ed.draft
        ed.ok(DraftEdit.ReplaceAll(Samples.full(), "Import"))
        assertEquals(Samples.full(), ed.draft)
        assertEquals("Import", ed.undoLabel)
        ed.undo()
        assertEquals(before, ed.draft)
    }

    @Test fun theEditorNormalisesWhatItHolds() {
        val ed = editor(Samples.minimal().copy(tags = listOf(DifficultyTag.HARSH, DifficultyTag.GENTLE)))
        assertEquals(listOf(DifficultyTag.GENTLE, DifficultyTag.HARSH), ed.draft.tags)
    }

    // ---- validation through the editor ---------------------------------------------------------

    @Test fun validationFollowsTheDraft() {
        val ed = editor()
        assertTrue(ed.validate().ok)
        ed.ok(DraftEdit.SetTitle("x"))
        assertTrue(ed.validate().has("title.length"))
        ed.undo()
        assertFalse(ed.validate().has("title.length"))
    }

    @Test fun validationIsKeptWhileTheDraftStaysTheSame() {
        val ed = editor()
        assertSame(ed.validate(), ed.validate())
        val withContent = ed.validate(Samples.content)
        assertSame(withContent, ed.validate(Samples.content))
    }
}
