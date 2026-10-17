package app.many.economytycoon.workshop

import app.many.economytycoon.scenarios.GoalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftDiffTest {
    private val base = Samples.full()

    @Test fun identicalDraftsHaveNoDifferences() {
        assertTrue(DraftDiff.changes(base, base).isEmpty())
        assertEquals(listOf("no differences"), DraftDiff.lines(base, base))
    }

    @Test fun orderOfListsThatNormaliseDoesNotMatter() {
        val shuffled = base.copy(events = base.events.reversed(), tags = base.tags.reversed())
        assertTrue(DraftDiff.changes(base, shuffled).isEmpty())
    }

    @Test fun aChangedValueNamesItsFieldAndBothSides() {
        val c = DraftDiff.changes(base, base.copy(seed = base.seed + 1)).single()
        assertEquals("seed", c.path)
        assertEquals(ChangeKind.CHANGED, c.kind)
        assertEquals("~ seed: ${base.seed} -> ${base.seed + 1}", c.toString())
    }

    @Test fun addedAndRemovedGoalsAreListed() {
        val more = base.copy(goals = base.goals + DraftGoal(GoalKind.CASH, 123, 2, true))
        val add = DraftDiff.changes(base, more).single()
        assertEquals("goals[${base.goals.size}]", add.path)
        assertEquals(ChangeKind.ADDED, add.kind)
        assertTrue(add.toString().startsWith("+ goals["))
        assertTrue(add.toString().endsWith("cash 123 weight 2 bonus"))
        val gone = DraftDiff.changes(more, base).single()
        assertEquals(ChangeKind.REMOVED, gone.kind)
        assertTrue(gone.toString().startsWith("- goals["))
    }

    @Test fun ruleOverridesAppearAndDisappear() {
        val plain = base.copy(rules = base.rules.without(RuleKey.BANK_MULTIPLE))
        val r = base.copy(rules = base.rules.with(RuleKey.BANK_MULTIPLE, 7))
        val added = DraftDiff.changes(plain, r)
        assertEquals(listOf("rules.bank_multiple"), added.map { it.path })
        assertEquals(ChangeKind.ADDED, added.single().kind)
        assertEquals(ChangeKind.REMOVED, DraftDiff.changes(r, plain).single().kind)
    }

    @Test fun emptyOptionalTextIsNotReportedAsAdded() {
        val bare = base.copy(tagline = "", story = "", tags = emptyList(), packs = emptyList())
        assertTrue(DraftDiff.changes(bare, bare.copy(id = bare.id)).isEmpty())
        val withTagline = DraftDiff.changes(bare, bare.copy(tagline = "Hello"))
        assertEquals(listOf(ChangeKind.ADDED), withTagline.map { it.kind })
    }

    @Test fun changesComeInAuthorOrder() {
        val d = base.copy(title = "Other Title", seed = 99, timing = base.timing.copy(years = base.timing.years + 1), company = base.company.copy(capital = base.company.capital + 1))
        assertEquals(listOf("title", "seed", "company.capital", "timing.years"), DraftDiff.changes(base, d).map { it.path })
    }

    @Test fun wordingChangesDoNotAffectPlay() {
        val words = DraftDiff.changes(base, base.copy(title = "New Title", hints = listOf("only hint")))
        assertTrue(words.isNotEmpty())
        assertFalse(words.any { DraftDiff.affectsPlay(it) })
        assertTrue(DraftDiff.affectsPlay(DraftDiff.changes(base, base.copy(seed = base.seed + 1)).single()))
    }

    @Test fun theSummaryLineCountsDifferences() {
        val lines = DraftDiff.lines(base, base.copy(seed = 5, title = "Another Name"))
        assertEquals("2 differences", lines.first())
        assertEquals(3, lines.size)
    }

    @Test fun diffingIsSymmetricInShape() {
        val d = base.copy(seed = 5, goals = base.goals.dropLast(1))
        val fwd = DraftDiff.changes(base, d); val back = DraftDiff.changes(d, base)
        assertEquals(fwd.map { it.path }, back.map { it.path })
        for ((f, b) in fwd.zip(back)) {
            assertEquals(f.before, b.after)
            assertEquals(f.after, b.before)
        }
    }

    @Test fun differenceBetweenTemplatesIsReadable() {
        val a = requireNotNull(DraftTemplates.byKey("sprint")).create("x", "Sprint Game")
        val b = requireNotNull(DraftTemplates.byKey("shoestring")).create("x", "Sprint Game")
        val c = DraftDiff.changes(a, b)
        assertTrue(c.any { it.path == "company.capital" })
        assertTrue(c.all { it.path != "id" && it.path != "title" })
    }
}
