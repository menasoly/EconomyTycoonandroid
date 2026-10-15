package app.many.economytycoon.ui.present

import app.many.economytycoon.guide.Guide
import app.many.economytycoon.guide.GuideArea
import app.many.economytycoon.guide.GuideProgress
import app.many.economytycoon.guide.StepState
import app.many.economytycoon.guide.StepStatus
import app.many.economytycoon.ui.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidePresenterTest {
    private fun progress(done: Int): GuideProgress = GuideProgress(Guide.steps.mapIndexed { i, s ->
        StepStatus(s, when { i < done -> StepState.DONE; i == done -> StepState.CURRENT; else -> StepState.LATER })
    })

    @Test fun everyAreaPointsAtARealScreen() {
        for (a in GuideArea.values()) assertTrue(a.name, Route.parse(a.routeKey) != null)
    }

    @Test fun stateLabelsAndTonesDiffer() {
        val labels = StepState.values().map { GuidePresenter.stateLabel(it) }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(Tone.GOOD, GuidePresenter.stateTone(StepState.DONE))
        assertEquals(Tone.INFO, GuidePresenter.stateTone(StepState.CURRENT))
        assertEquals(Tone.MUTED, GuidePresenter.stateTone(StepState.LATER))
    }

    @Test fun theModelCountsWhatIsDone() {
        val m = GuidePresenter.model(progress(3))
        assertEquals("3 of ${Guide.steps.size} done", m.summary)
        assertEquals("First steps", m.headline)
        assertFalse(m.finished)
        assertEquals(3, m.rows.count { it.state == StepState.DONE })
        assertEquals("Next", m.rows[3].stateLabel)
    }

    @Test fun aFinishedGuideSaysSoAndHasNothingToNudge() {
        val p = progress(Guide.steps.size)
        val m = GuidePresenter.model(p)
        assertTrue(m.finished)
        assertEquals(1_000_000L, m.ppm)
        assertEquals("You know your way around", m.headline)
        assertNull(GuidePresenter.nudge(p))
    }

    @Test fun theNudgeNamesTheNextStepAndWhereToDoIt() {
        val n = GuidePresenter.nudge(progress(1))!!
        assertEquals(Guide.steps[1].title, n.title)
        assertEquals("Step 2 of ${Guide.steps.size}", n.position)
        assertEquals(Route.Facilities, n.route)
    }

    @Test fun everyRowCarriesARoute() {
        for (r in GuidePresenter.model(progress(0)).rows) assertTrue(r.id, r.route != null)
    }

    @Test fun aFreshSessionStartsAtTheFirstStep() {
        val s = PresentSupport.session("Guide Works", 61L)
        val n = GuidePresenter.nudge(s)
        assertNotNull(n)
        assertTrue(GuidePresenter.build(s)!!.rows.size == Guide.steps.size)
    }
}
