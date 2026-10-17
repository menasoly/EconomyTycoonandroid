package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.valuation.ValuationAssumptions
import app.many.economytycoon.valuation.ValuationFixtures
import app.many.economytycoon.valuation.ValuationSummary
import app.many.economytycoon.valuation.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuationPresenterTest {
    private val money: (Money) -> String = { it.wholeUnits().toString() }
    private fun model(price: Long? = null, outlook: Outlook = Outlook.STANDARD): ValuationModel {
        val i = ValuationFixtures.input(price = price)
        return ValuationPresenter.assemble("Acme", i, ValuationSummary.of(i, outlook.assumptions), outlook.assumptions, money)
    }

    @Test fun tilesCoverValueShareMarketAndConfidence() {
        val m = model()
        assertEquals(listOf("Estimated value", "Per share", "Market price", "Confidence"), m.tiles.map { it.label })
        assertEquals("-", m.tiles[2].value)
    }

    @Test fun everyMethodGetsARow() {
        assertEquals(app.many.economytycoon.valuation.Method.values().size, model().methods.size)
    }

    @Test fun inapplicableMethodsShowNotAvailable() {
        val i = ValuationFixtures.lossMaker
        val m = ValuationPresenter.assemble("Loss", i, ValuationSummary.of(i), ValuationAssumptions.DEFAULT, money)
        val row = m.methods.first { it.label == "Earnings multiple" }
        assertEquals("n/a", row.value)
        assertEquals("-", row.weight)
        assertTrue(!row.applicable)
    }

    @Test fun sensitivityGridIsFiveByFive() {
        val g = model().grid!!
        assertEquals(5, g.columns.size)
        assertEquals(5, g.rows.size)
        assertTrue(g.rows.all { it.second.size == 5 })
    }

    @Test fun noCashFlowMeansNoGrid() {
        val i = ValuationFixtures.lossMaker
        assertNull(ValuationPresenter.grid(i, ValuationAssumptions.DEFAULT, money))
    }

    @Test fun unlistedCompaniesHaveNoReverseReading() = assertTrue(model().reverse.isEmpty())

    @Test fun listedCompaniesShowWhatTheMarketExpects() {
        val m = model(price = 1_500)
        assertEquals(2, m.reverse.size)
        assertEquals("Growth the market expects", m.reverse[0].label)
    }

    @Test fun verdictWordingFollowsTheMarket() {
        val fair = ValuationSummary.of(ValuationFixtures.input()).perShare.wholeUnits()
        assertTrue(model(price = fair / 2).verdict.contains("below"))
        assertTrue(model(price = fair * 2).verdict.contains("above"))
        assertTrue(model(price = fair).verdict.contains("close"))
        assertTrue(model().verdict.startsWith("Not traded"))
    }

    @Test fun tonesFollowVerdicts() {
        assertEquals(Tone.GOOD, ValuationPresenter.verdictTone(Verdict.UNDERVALUED))
        assertEquals(Tone.WARN, ValuationPresenter.verdictTone(Verdict.OVERVALUED))
        assertEquals(Tone.INFO, ValuationPresenter.verdictTone(Verdict.FAIR))
        assertEquals(Tone.NEUTRAL, ValuationPresenter.verdictTone(Verdict.NOT_LISTED))
    }

    @Test fun outlooksOrderTheEstimate() {
        val c = model(outlook = Outlook.CAUTIOUS).tiles[0].value.toLong()
        val s = model(outlook = Outlook.STANDARD).tiles[0].value.toLong()
        val o = model(outlook = Outlook.OPTIMISTIC).tiles[0].value.toLong()
        assertTrue(c < s && s < o)
    }

    @Test fun liveSessionBuildsAModel() {
        val session = PresentSupport.session("Valuable Co", 4L).also { s -> repeat(40) { s.step() } }
        val m = ValuationPresenter.build(session)
        assertTrue(m != null)
        assertEquals(4, m!!.tiles.size)
    }
}
