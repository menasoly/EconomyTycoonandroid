package app.many.economytycoon.ui.present

import app.many.economytycoon.blueprint.Blueprint
import app.many.economytycoon.blueprint.BlueprintAnalyzer
import app.many.economytycoon.blueprint.BlueprintSpec
import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintComparePresenterTest {
    private val analyzer = BlueprintAnalyzer(T.content)
    private fun a(bp: Blueprint) = analyzer.analyze(bp)

    private val plain = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(T.makeBread, listOf(T.oven)))), "Plain")
    private val pro = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(T.makeBreadPro, listOf(T.proOven)))), "Pro")

    @Test fun identicalBlueprintsHaveNoChanges() {
        val m = BlueprintComparePresenter.model(a(plain), a(plain.renamed("Copy")), T.content)
        assertTrue(m.identical)
        assertTrue(m.changes.isEmpty())
        assertEquals("No differences.", m.summary)
        assertTrue(m.rows.all { it.delta == null })
    }

    @Test fun costRowsShowDeltasAndTones() {
        val m = BlueprintComparePresenter.model(a(plain), a(pro), T.content)
        assertFalse(m.identical)
        val capital = m.rows.first { it.label == "Capital" }
        assertTrue(capital.delta!!.startsWith("+"))
        assertEquals(Tone.WARN, capital.tone)
        val net = m.rows.first { it.label == "Net a day" }
        assertTrue(net.delta!!.startsWith("+"))
        assertEquals(Tone.GOOD, net.tone)
        assertEquals("Plain", m.leftName)
        assertEquals("Pro", m.rightName)
    }

    @Test fun reversingFlipsTheDeltaSigns() {
        val m = BlueprintComparePresenter.model(a(pro), a(plain), T.content)
        assertTrue(m.rows.first { it.label == "Capital" }.delta!!.startsWith("-"))
        assertEquals(Tone.GOOD, m.rows.first { it.label == "Capital" }.tone)
    }

    @Test fun changesListTheDifferingSettings() {
        val m = BlueprintComparePresenter.model(a(plain), a(pro), T.content)
        assertTrue(m.changes.any { it.section == "Lines" && it.text.contains("Bake bread (pro)") })
        assertTrue(m.changes.any { it.section == "Machines" && it.symbol == "+" })
        assertTrue(m.changes.any { it.section == "Machines" && it.symbol == "-" })
    }

    @Test fun lineAndMachineCountsAreShown() {
        val m = BlueprintComparePresenter.model(a(T.sample()), a(plain), T.content)
        assertEquals(listOf("3", "1"), m.rows.first { it.label == "Lines" }.let { listOf(it.left, it.right) })
        assertEquals(listOf("3", "1"), m.rows.first { it.label == "Machines" }.let { listOf(it.left, it.right) })
        assertNull(m.rows.first { it.label == "Lines" }.delta)
    }

    @Test fun missingCostsDegradeGracefully() {
        val gone = Blueprint.of(BlueprintSpec(app.many.economytycoon.core.FacilityTypeId("gone"), emptyList()), "Gone")
        val m = BlueprintComparePresenter.model(a(plain), a(gone), T.content)
        val capital = m.rows.first { it.label == "Capital" }
        assertEquals("-", capital.right)
        assertNull(capital.delta)
    }
}
