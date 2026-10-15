package app.many.economytycoon.ui.present

import app.many.economytycoon.tutorial.StepState
import app.many.economytycoon.tutorial.TutorialEngine
import app.many.economytycoon.tutorial.TutorialFacts
import app.many.economytycoon.tutorial.TutorialProgress
import app.many.economytycoon.tutorial.TutorialScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialPresenterTest {
    private val engine = TutorialEngine()
    private fun model(p: TutorialProgress = TutorialProgress(), f: TutorialFacts = TutorialFacts()) = TutorialPresenter.model(engine.view(p, f))

    @Test fun freshModelStartsAtTheWelcome() {
        val m = model()
        assertEquals("Ready when you are.", m.headline)
        assertEquals("Welcome", m.current?.title)
        assertTrue(m.current!!.needsAcknowledgement)
        assertFalse(m.finished)
    }

    @Test fun groupsFollowTheLessons() {
        val m = model()
        assertEquals(app.many.economytycoon.tutorial.Lesson.values().map { it.label }, m.groups.map { it.title })
        assertEquals(TutorialScript.steps.size, m.groups.sumOf { it.rows.size })
    }

    @Test fun summariesCountDoneSteps() {
        val m = model(engine.acknowledge(TutorialProgress(), "welcome"))
        assertTrue(m.groups.first().summary.startsWith("1 of "))
        assertEquals("1 of ${TutorialScript.steps.size} lessons done.", m.headline)
    }

    @Test fun rowsCarryStatusAndTone() {
        val m = model(engine.acknowledge(TutorialProgress(), "welcome"))
        val rows = m.groups.flatMap { it.rows }
        assertEquals("Done", rows.first().status)
        assertEquals(Tone.GOOD, rows.first().tone)
        assertEquals("Now", rows[1].status)
        assertEquals("Later", rows.last().status)
    }

    @Test fun statusWordsAndTonesCoverEveryStatus() {
        for (s in StepState.Status.values()) {
            assertTrue(TutorialPresenter.statusText(s).isNotBlank())
            TutorialPresenter.statusTone(s)
        }
        assertEquals(StepState.Status.values().size, StepState.Status.values().map { TutorialPresenter.statusText(it) }.toSet().size)
    }

    @Test fun finishedGuideHasNoCurrentStep() {
        val p = TutorialProgress(completed = TutorialScript.ids.toSet())
        val m = model(p)
        assertTrue(m.finished)
        assertNull(m.current)
        assertEquals(1.0, m.progress, 0.0001)
        assertEquals("You have been through every lesson.", m.headline)
    }

    @Test fun stepsWithAScreenOfferToTakeYouThere() {
        val p = engine.acknowledge(TutorialProgress(), "welcome")
        assertEquals("market", model(p).current?.screen)
    }

    @Test fun liveSessionBuildsAModel() {
        val s = PresentSupport.session("Lessons Ltd", 6L)
        val m = TutorialPresenter.build(s)!!
        assertEquals("Welcome", m.current?.title)
    }

    @Test fun visitingScreensAdvancesTheLiveGuide() {
        val s = PresentSupport.session("Wanderers", 6L)
        s.tutorial.acknowledge("welcome")
        s.noteVisit("market")
        val m = TutorialPresenter.build(s)!!
        assertEquals("Find a trade", m.current?.title)
    }

    @Test fun nudgeNamesTheNextLessonUntilDismissed() {
        val s = PresentSupport.session("Nudged", 6L)
        assertTrue(TutorialPresenter.nudge(s)!!.startsWith("Next lesson: Welcome"))
        s.tutorial.dismiss()
        assertNull(TutorialPresenter.nudge(s))
    }
}
