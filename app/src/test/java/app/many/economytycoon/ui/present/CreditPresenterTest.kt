package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.credit.FactorState
import app.many.economytycoon.finance.CreditInputs
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.finance.Ratios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditPresenterTest {
    private fun ratios(cover: Long, current: Long, gearing: Long, margin: Long, equity: Long) = Ratios(
        Rate(current), Rate(500_000), Rate(gearing), Rate(cover), Rate(300_000), Rate(margin), Rate(100_000), Rate(50_000), Rate(2_000_000), Rate(equity),
    )
    private val sound = CreditInputs(ratios(2_000_000, 1_500_000, 800_000, 100_000, 450_000), 720, 0, Money.ZERO, Money.units(100_000), Money.units(50_000))
    private val strong = CreditInputs(ratios(7_000_000, 3_500_000, 100_000, 350_000, 700_000), 3_600, 0, Money.ZERO, Money.units(100_000), Money.units(500_000))
    private val weak = CreditInputs(ratios(500_000, 500_000, 5_000_000, -200_000, -100_000), 90, 1, Money.units(20_000), Money.units(100_000), Money.units(-5))

    @Test fun tonesFollowTheRatingBands() {
        assertEquals(Tone.GOOD, CreditPresenter.ratingTone(CreditRating.AA))
        assertEquals(Tone.INFO, CreditPresenter.ratingTone(CreditRating.BBB))
        assertEquals(Tone.WARN, CreditPresenter.ratingTone(CreditRating.B))
        assertEquals(Tone.BAD, CreditPresenter.ratingTone(CreditRating.D))
    }

    @Test fun pointsUseARealMinusSign() {
        assertEquals("+40", CreditPresenter.pointsText(40))
        assertEquals("−60", CreditPresenter.pointsText(-60))
        assertEquals("0", CreditPresenter.pointsText(0))
    }

    @Test fun everyStateHasALabelAndTone() {
        assertEquals(FactorState.values().size, FactorState.values().map { CreditPresenter.stateLabel(it) }.toSet().size)
        assertEquals(Tone.WARN, CreditPresenter.stateTone(FactorState.WEAK))
    }

    @Test fun theHeadlineSaysWhetherTheRatingWillMove() {
        assertTrue(CreditPresenter.explain(strong, CreditRating.BBB).headline.contains("already earn"))
        assertTrue(CreditPresenter.explain(weak, CreditRating.A).headline.contains("only earn"))
    }

    @Test fun anOutlookIsOnlyGivenWhenTheRatingWillMove() {
        assertNotNull(CreditPresenter.explain(strong, CreditRating.BBB).outlook)
        assertNotNull(CreditPresenter.explain(weak, CreditRating.A).outlook)
        val steady = CreditPresenter.explain(sound, app.many.economytycoon.finance.CreditScoring.target(app.many.economytycoon.finance.CreditScoring.score(sound)))
        assertNull(steady.outlook)
        assertTrue(steady.headline.contains("in line with"))
    }

    @Test fun strongFactorsGetNoAdviceAndWeakOnesDo() {
        val m = CreditPresenter.explain(weak, CreditRating.B)
        val margin = m.factors.first { it.label == "Profit margin" }
        assertEquals("Weak", margin.state)
        assertNotNull(margin.advice)
        val s = CreditPresenter.explain(strong, CreditRating.AA)
        assertNull(s.factors.first { it.label == "Interest cover" }.advice)
    }

    @Test fun scoreAndStepsAreWritten() {
        val m = CreditPresenter.explain(sound, CreditRating.BBB)
        assertTrue(m.score.startsWith("Score ") && m.score.endsWith(" of 1000"))
        assertTrue(m.nextStep!!.contains("for "))
        assertTrue(m.cushion!!.contains("above the line"))
        assertEquals(9, m.factors.size)
    }

    @Test fun leversNameTheFactorAndWhatToDo() {
        val m = CreditPresenter.explain(weak, CreditRating.B)
        assertTrue(m.levers.isNotEmpty())
        assertTrue(m.levers.all { it.contains(": ") })
    }

    @Test fun aNewGameHasNoRatingUntilTheFirstClose() {
        val s = PresentSupport.session("Credit Works", 29L)
        assertNull(CreditPresenter.build(s))
        s.simulation.advance(35)
        val m = CreditPresenter.build(s)!!
        assertTrue(m.rating.isNotBlank())
        assertFalse(m.factors.isEmpty())
    }

    @Test fun theHeldRatingShownIsTheOneTheLedgerKeeps() {
        val s = PresentSupport.session("Credit Works", 29L)
        s.simulation.advance(35)
        val e = s.economy
        val owner = e.companies.require(s.selectedCompany!!).owner
        assertEquals(e.finance.company(owner)!!.rating.rating.label, CreditPresenter.build(s)!!.rating)
        assertEquals(e.finance.company(owner)!!.rating.lastScore, s.credit()!!.score)
    }
}
