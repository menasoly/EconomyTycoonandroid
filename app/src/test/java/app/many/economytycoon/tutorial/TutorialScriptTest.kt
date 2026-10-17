package app.many.economytycoon.tutorial

import app.many.economytycoon.ui.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialScriptTest {
    private val steps = TutorialScript.steps

    @Test fun idsAreUnique() = assertEquals(steps.size, steps.map { it.id }.toSet().size)

    @Test fun everyStepHasTextAndAGoal() {
        for (s in steps) {
            assertTrue(s.id, s.title.isNotBlank())
            assertTrue(s.id, s.body.length > 30)
            assertTrue(s.id, s.goal.describe().isNotBlank())
        }
    }

    @Test fun theGuideStartsWithAWelcome() {
        assertEquals("welcome", steps.first().id)
        assertEquals(Goal.Acknowledge, steps.first().goal)
    }

    @Test fun lessonsAreContiguousAndInOrder() {
        val order = steps.map { it.lesson }.distinct()
        assertEquals(Lesson.values().toList(), order)
        // A lesson never reappears after another has started.
        val seen = HashSet<Lesson>()
        var last: Lesson? = null
        for (s in steps) {
            if (s.lesson != last) { assertFalse(s.lesson.name, s.lesson in seen); seen += s.lesson }
            last = s.lesson
        }
    }

    @Test fun everyLessonHasSteps() = assertEquals(Lesson.values().toSet(), TutorialScript.byLesson().keys)

    private fun real(name: String) = Route.parse(name) != null || Route.parse("$name/1") != null

    @Test fun visitGoalsNameRealScreens() {
        for (s in steps) (s.goal as? Goal.Visit)?.let { assertTrue("${s.id}: ${it.route}", real(it.route)) }
    }

    @Test fun suggestedScreensAreRealRoutes() {
        for (s in steps) s.screen?.let { assertTrue("${s.id}: $it", real(it)) }
    }

    @Test fun lookupFindsStepsById() {
        assertEquals("build", TutorialScript.step("build")?.id)
        assertEquals(null, TutorialScript.step("nope"))
    }

    @Test fun idsListMatchesSteps() = assertEquals(steps.map { it.id }, TutorialScript.ids)

    @Test fun gaugeGoalsAreAchievable() {
        for (s in steps) (s.goal as? Goal.Reach)?.let { assertTrue(s.id, it.atLeast > 0L) }
    }
}
