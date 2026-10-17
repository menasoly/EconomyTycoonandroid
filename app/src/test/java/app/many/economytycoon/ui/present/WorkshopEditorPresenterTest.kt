package app.many.economytycoon.ui.present

import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.workshop.DifficultyTag
import app.many.economytycoon.workshop.DraftEditor
import app.many.economytycoon.workshop.DraftValidator
import app.many.economytycoon.workshop.EventKind
import app.many.economytycoon.workshop.FailKind
import app.many.economytycoon.workshop.RuleKey
import app.many.economytycoon.workshop.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopEditorPresenterTest {
    private fun editor() = DraftEditor(Samples.full())
    private fun model(e: DraftEditor) = WorkshopEditorPresenter.model(e, DraftValidator.validate(e.draft, Samples.content))
    private fun section(e: DraftEditor, key: String) = model(e).sections.first { it.key == key }
    private fun field(e: DraftEditor, path: String) = model(e).sections.flatMap { s -> s.fields + s.items.flatMap { it.fields } }.first { it.path == path }

    @Test fun templatesAreListedWithTheirNames() {
        val t = WorkshopEditorPresenter.templates()
        assertTrue(t.any { it.key == "blank" })
        assertTrue(t.all { it.name.isNotEmpty() && it.summary.isNotEmpty() })
    }

    @Test fun theModelListsEverySectionInOrder() {
        assertEquals(listOf("identity", "world", "company", "rules", "timing", "goals", "events", "fails", "hints"), model(editor()).sections.map { it.key })
    }

    @Test fun fieldsShowTheCurrentValues() {
        val e = editor()
        assertEquals(e.draft.title, field(e, "title").value)
        assertEquals(e.draft.company.capital.toString(), field(e, "company.capital").value)
        assertEquals(WorkshopEditorPresenter.let { FieldInput.NUMBER }, field(e, "seed").input)
        assertEquals(FieldInput.LONG_TEXT, field(e, "story").input)
    }

    @Test fun everyRuleHasAFieldWithItsRange() {
        val rules = section(editor(), "rules")
        assertEquals(RuleKey.values().size, rules.fields.size)
        assertTrue(rules.fields.all { it.hint?.startsWith("Allowed") == true })
    }

    @Test fun goalsEventsAndFailsBecomeItems() {
        val e = editor(); val d = e.draft
        assertEquals(d.goals.size, section(e, "goals").items.size)
        assertEquals(d.events.size, section(e, "events").items.size)
        assertEquals(d.fails.size, section(e, "fails").items.size)
        assertEquals(d.hints.size, section(e, "hints").items.size)
    }

    @Test fun typingANumberChangesTheDraft() {
        val e = editor()
        assertNull(WorkshopEditorPresenter.setField(e, "company.capital", "1_234,000"))
        assertEquals(1_234_000L, e.draft.company.capital)
        assertTrue(e.canUndo)
    }

    @Test fun typingTextChangesTheDraft() {
        val e = editor()
        assertNull(WorkshopEditorPresenter.setField(e, "title", "A New Name"))
        assertEquals("A New Name", e.draft.title)
        assertNull(WorkshopEditorPresenter.setField(e, "id", "  new-id "))
        assertEquals("new-id", e.draft.id)
    }

    @Test fun notANumberIsRefusedWithoutChangingAnything() {
        val e = editor(); val before = e.draft
        val msg = WorkshopEditorPresenter.setField(e, "company.capital", "lots")
        assertEquals("Capital must be a whole number.", msg)
        assertEquals(before, e.draft)
        assertFalse(e.canUndo)
    }

    @Test fun ruleFieldsSetOverrides() {
        val e = editor()
        assertNull(WorkshopEditorPresenter.setField(e, "rules.bank_multiple", "4"))
        assertEquals(4L, e.draft.rules[RuleKey.BANK_MULTIPLE])
        assertNotNull(WorkshopEditorPresenter.setField(e, "rules.bank_multiple", "x"))
    }

    @Test fun timingFieldsKeepTheOthers() {
        val e = editor(); val t = e.draft.timing
        WorkshopEditorPresenter.setField(e, "timing.years", "7")
        assertEquals(t.copy(years = 7), e.draft.timing)
        WorkshopEditorPresenter.setField(e, "timing.gold", "400")
        assertEquals(400, e.draft.timing.goldDays)
        WorkshopEditorPresenter.setField(e, "timing.silver", "900")
        assertEquals(900, e.draft.timing.silverDays)
    }

    @Test fun listItemFieldsEditTheRightItem() {
        val e = editor()
        assertNull(WorkshopEditorPresenter.setField(e, "goals[0].target", "777"))
        assertEquals(777L, e.draft.goals[0].target)
        assertNull(WorkshopEditorPresenter.setField(e, "goals[0].weight", "3"))
        assertEquals(3, e.draft.goals[0].weight)
        assertNull(WorkshopEditorPresenter.setField(e, "events[0].text", "hello"))
        assertEquals("hello", e.draft.events[0].text)
        assertNull(WorkshopEditorPresenter.setField(e, "fails[0].afterDay", "12"))
        assertEquals(12, e.draft.fails[0].afterDay)
        assertNull(WorkshopEditorPresenter.setField(e, "hints[0]", "new hint"))
        assertEquals("new hint", e.draft.hints[0])
    }

    @Test fun missingItemsAndUnknownFieldsAreExplained() {
        val e = editor()
        assertEquals("There is no goal 99.", WorkshopEditorPresenter.setField(e, "goals[98].target", "1"))
        assertEquals("There is no field 'nope' to change.", WorkshopEditorPresenter.setField(e, "nope", "1"))
        assertEquals("There is no field 'rules.zzz' to change.", WorkshopEditorPresenter.setField(e, "rules.zzz", "1"))
        assertNotNull(WorkshopEditorPresenter.setField(e, "hints[50]", "x"))
    }

    @Test fun addingAndRemovingGoals() {
        val e = editor(); val n = e.draft.goals.size
        assertNull(WorkshopEditorPresenter.addGoal(e, GoalKind.CASH))
        assertEquals(n + 1, e.draft.goals.size)
        assertEquals(WorkshopEditorPresenter.defaultTarget(GoalKind.CASH), e.draft.goals.last().target)
        assertNull(WorkshopEditorPresenter.removeGoal(e, n))
        assertEquals(n, e.draft.goals.size)
        assertNotNull(WorkshopEditorPresenter.removeGoal(e, 99))
    }

    @Test fun everyGoalKindHasASensibleDefaultTarget() {
        for (k in WorkshopEditorPresenter.goalKinds) assertTrue(k.name, WorkshopEditorPresenter.defaultTarget(k) > 0)
    }

    @Test fun addedEventsFollowTheLastOne() {
        val e = DraftEditor(Samples.minimal())
        WorkshopEditorPresenter.addEvent(e, EventKind.GRANT)
        WorkshopEditorPresenter.addEvent(e, EventKind.ANNOUNCE)
        assertEquals(listOf(30, 60), e.draft.events.map { it.day })
        assertEquals(0L, e.draft.events[1].amount)
        assertTrue(e.draft.events[1].text.isNotEmpty())
    }

    @Test fun failsAndHintsCanBeAddedAndRemoved() {
        val e = DraftEditor(Samples.minimal())
        assertNull(WorkshopEditorPresenter.addFail(e, FailKind.FACILITIES_BELOW))
        assertEquals(1L, e.draft.fails.single().threshold)
        assertNull(WorkshopEditorPresenter.addHint(e))
        assertNull(WorkshopEditorPresenter.removeFail(e, 0))
        assertNull(WorkshopEditorPresenter.removeHint(e, 0))
        assertTrue(e.draft.fails.isEmpty() && e.draft.hints.isEmpty())
    }

    @Test fun tagsToggle() {
        val e = DraftEditor(Samples.minimal())
        WorkshopEditorPresenter.toggleTag(e, "sprint")
        assertTrue(DifficultyTag.SPRINT in e.draft.tags)
        assertTrue(model(e).tags.first { it.key == "sprint" }.selected)
        WorkshopEditorPresenter.toggleTag(e, "sprint")
        assertFalse(DifficultyTag.SPRINT in e.draft.tags)
        assertEquals("There is no tag 'zzz'.", WorkshopEditorPresenter.toggleTag(e, "zzz"))
    }

    @Test fun undoAndRedoReportWhenThereIsNothingToDo() {
        val e = DraftEditor(Samples.minimal())
        assertEquals("Nothing to undo.", WorkshopEditorPresenter.undo(e))
        WorkshopEditorPresenter.setField(e, "title", "Changed Name")
        assertNull(WorkshopEditorPresenter.undo(e))
        assertEquals("Tiny Test", e.draft.title)
        assertNull(WorkshopEditorPresenter.redo(e))
        assertEquals("Nothing to redo.", WorkshopEditorPresenter.redo(e))
    }

    @Test fun theModelReflectsUndoStateAndDirtiness() {
        val e = DraftEditor(Samples.minimal())
        assertFalse(model(e).dirty)
        assertFalse(model(e).canUndo)
        WorkshopEditorPresenter.setField(e, "title", "Changed Name")
        val m = model(e)
        assertTrue(m.dirty && m.canUndo && !m.canRedo)
        assertEquals("Change title", m.undoLabel)
    }

    @Test fun problemsAreMarkedOnTheirFields() {
        val e = DraftEditor(Samples.minimal())
        WorkshopEditorPresenter.setField(e, "title", "x")
        assertEquals(Tone.BAD, field(e, "title").problem)
        assertEquals(Tone.BAD, section(e, "identity").problem)
        assertTrue(field(e, "tagline").problem != Tone.BAD)
    }

    @Test fun editingNumbersBeyondRangeLeavesTheJudgementToTheValidator() {
        val e = DraftEditor(Samples.full())
        assertNull(WorkshopEditorPresenter.setField(e, "timing.years", "99999"))
        assertEquals(Tone.BAD, field(e, "timing.years").problem)
    }

    @Test fun worldSizeChanges() {
        val e = DraftEditor(Samples.minimal())
        val other = WorkshopEditorPresenter.worldSizes.first { it != e.draft.world.size }
        assertNull(WorkshopEditorPresenter.setWorldSize(e, other))
        assertEquals(other, e.draft.world.size)
    }
}
