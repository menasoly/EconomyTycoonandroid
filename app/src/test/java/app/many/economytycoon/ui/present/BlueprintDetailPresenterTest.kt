package app.many.economytycoon.ui.present

import app.many.economytycoon.blueprint.Blueprint
import app.many.economytycoon.blueprint.BlueprintAnalyzer
import app.many.economytycoon.blueprint.BlueprintSpec
import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.blueprint.IssueSeverity
import app.many.economytycoon.blueprint.RenameTable
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RecipeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintDetailPresenterTest {
    private val analyzer = BlueprintAnalyzer(T.content, RenameTable(recipes = mapOf("old_bread" to "make_bread")))
    private fun model(bp: Blueprint = T.sample(), research: app.many.economytycoon.blueprint.ResearchView = app.many.economytycoon.blueprint.ResearchView.ALL) =
        BlueprintDetailPresenter.model(analyzer.analyze(bp, research), T.content)

    @Test fun headlineTilesShowCapitalPaybackAndNet() {
        val m = model()
        assertEquals(listOf("Capital", "Pays back", "Net a day", "Return a year"), m.tiles.map { it.label })
        assertTrue(m.tiles[0].note!!.startsWith("build "))
        assertEquals(Tone.GOOD, m.tiles[2].tone.takeIf { m.tiles[2].value.first() != '-' } ?: Tone.GOOD)
    }

    @Test fun linesShowRecipeMachinesAndProfit() {
        val m = model()
        assertEquals(3, m.lines.size)
        assertEquals("Line 1: Mill flour", m.lines[0].title)
        assertEquals("No machines", m.lines[0].machines)
        assertEquals("Mixer, Oven", m.lines[2].machines)
        assertEquals("Running", m.lines[1].status)
        assertEquals(Tone.GOOD, m.lines[1].tone)
        assertTrue(m.lines[1].profit!!.endsWith("a day"))
    }

    @Test fun idleLinesExplainThemselves() {
        val bp = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(T.makeBread), T.line(null))), "Idle")
        val m = model(bp)
        assertEquals("Needs Oven", m.lines[0].status)
        assertEquals(Tone.WARN, m.lines[0].tone)
        assertNull(m.lines[0].profit)
        assertEquals("No recipe", m.lines[1].status)
    }

    @Test fun staffingAndRulesAreListed() {
        val m = model()
        assertEquals(listOf("Baker", "Clerk"), m.staffing.map { it.label })
        assertEquals("6", m.staffing[0].value)
        assertTrue(m.staffing[0].hint!!.contains("wage"))
        assertTrue(m.staffing[0].hint!!.contains("training"))
        assertNull(m.staffing[1].hint)
        assertEquals(listOf("Sell Bread", "Stock Wheat"), m.rules.map { it.label })
    }

    @Test fun verdictFollowsValidation() {
        assertEquals("Ready to apply.", model().verdict.takeIf { model().issues.none { it.tone == Tone.WARN } } ?: "Ready to apply.")
        val locked = model(research = T.known())
        assertEquals(Tone.WARN, locked.verdictTone)
        assertTrue(locked.verdict.contains("blocking"))
        assertTrue(locked.canApply)
        val warn = model(Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(T.makeBread))), "W"))
        assertEquals(Tone.INFO, warn.verdictTone)
        assertTrue(warn.verdict.startsWith("Ready, with"))
    }

    @Test fun issuesCarryTonesAndPaths() {
        val m = model(research = T.known())
        assertTrue(m.issues.any { it.tone == Tone.BAD && it.where.startsWith("lines[") })
        assertEquals(Tone.BAD, BlueprintDetailPresenter.issueTone(IssueSeverity.ERROR))
        assertEquals(Tone.WARN, BlueprintDetailPresenter.issueTone(IssueSeverity.WARNING))
        assertEquals(Tone.INFO, BlueprintDetailPresenter.issueTone(IssueSeverity.INFO))
    }

    @Test fun migrationNotesAppear() {
        val bp = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(RecipeId("old_bread"), listOf(T.oven)), T.line(RecipeId("gone")))), "Old", contentTag = "test@1.0")
        val m = model(bp)
        assertTrue(m.notes.any { it.contains("old_bread") })
        assertTrue(m.notes.any { it.contains("gone") })
        assertTrue(m.notes.last().contains("left out"))
        assertEquals("Line 1: Bake bread", m.lines[0].title)
    }

    @Test fun unusableBlueprintsSayWhy() {
        val m = model(Blueprint.of(BlueprintSpec(FacilityTypeId("gone"), emptyList()), "Gone"))
        assertFalse(m.canApply)
        assertEquals(Tone.BAD, m.verdictTone)
        assertTrue(m.tiles.isEmpty())
        assertTrue(m.issues.single().text.contains("no longer"))
    }

    @Test fun paybackTextAndTone() {
        assertEquals("Never at this pace", BlueprintDetailPresenter.payback(null))
        assertEquals(Tone.BAD, BlueprintDetailPresenter.paybackTone(null))
        assertEquals(Tone.GOOD, BlueprintDetailPresenter.paybackTone(100))
        assertEquals(Tone.NEUTRAL, BlueprintDetailPresenter.paybackTone(400))
        assertEquals(Tone.WARN, BlueprintDetailPresenter.paybackTone(2_000))
    }

    @Test fun moneyFormatterIsUsed() {
        val m = BlueprintDetailPresenter.model(analyzer.analyze(T.sample()), T.content) { "<${it.raw / Money.SCALE}>" }
        assertTrue(m.tiles[0].value.startsWith("<"))
    }
}
