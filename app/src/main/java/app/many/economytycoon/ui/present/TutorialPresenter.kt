package app.many.economytycoon.ui.present

import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.tutorial.Lesson
import app.many.economytycoon.tutorial.StepState
import app.many.economytycoon.tutorial.TutorialStep
import app.many.economytycoon.tutorial.TutorialView

data class LessonRow(val id: String, val title: String, val status: String, val tone: Tone, val progress: Double, val goal: String)

data class LessonGroup(val title: String, val summary: String, val rows: List<LessonRow>)

data class CurrentStep(val id: String, val title: String, val body: String, val goal: String, val progress: Double, val needsAcknowledgement: Boolean, val screen: String?)

data class TutorialModel(
    val headline: String,
    val progress: Double,
    val current: CurrentStep?,
    val groups: List<LessonGroup>,
    val finished: Boolean,
)

/** The tutorial as a current step to act on and a list of everything else. */
object TutorialPresenter {
    const val NOTHING = "Start or load a company to follow the tutorial."

    fun statusText(s: StepState.Status): String = when (s) {
        StepState.Status.DONE -> "Done"
        StepState.Status.SKIPPED -> "Skipped"
        StepState.Status.CURRENT -> "Now"
        StepState.Status.LOCKED -> "Later"
    }

    fun statusTone(s: StepState.Status): Tone = when (s) {
        StepState.Status.DONE -> Tone.GOOD
        StepState.Status.SKIPPED -> Tone.MUTED
        StepState.Status.CURRENT -> Tone.INFO
        StepState.Status.LOCKED -> Tone.NEUTRAL
    }

    fun headline(v: TutorialView): String = when {
        v.finished -> "You have been through every lesson."
        v.doneCount == 0 -> "Ready when you are."
        else -> "${v.doneCount} of ${v.total} lessons done."
    }

    fun current(step: TutorialStep, state: StepState?): CurrentStep = CurrentStep(
        id = step.id, title = step.title, body = step.body, goal = step.goal.describe(),
        progress = (state?.progressPpm ?: 0L) / 1_000_000.0,
        needsAcknowledgement = step.goal == app.many.economytycoon.tutorial.Goal.Acknowledge,
        screen = step.screen,
    )

    fun model(v: TutorialView): TutorialModel {
        val byLesson = v.steps.groupBy { it.step.lesson }
        val groups = Lesson.values().mapNotNull { lesson ->
            val states = byLesson[lesson] ?: return@mapNotNull null
            val done = states.count { it.status == StepState.Status.DONE || it.status == StepState.Status.SKIPPED }
            LessonGroup(
                title = lesson.label,
                summary = "$done of ${states.size} done",
                rows = states.map { LessonRow(it.step.id, it.step.title, statusText(it.status), statusTone(it.status), it.progressPpm / 1_000_000.0, it.step.goal.describe()) },
            )
        }
        val currentState = v.steps.firstOrNull { it.status == StepState.Status.CURRENT }
        return TutorialModel(headline(v), v.progressPpm / 1_000_000.0, v.current?.let { current(it, currentState) }, groups, v.finished)
    }

    fun build(session: GameSession): TutorialModel? = session.tutorialView()?.let { model(it) }

    /** A short reminder for the overview: what the next lesson is, or null when the guide is done or dismissed. */
    fun nudge(session: GameSession): String? {
        if (session.tutorial.progress.dismissed) return null
        val v = session.tutorialView() ?: return null
        val c = v.current ?: return null
        return "Next lesson: ${c.title}. " + Formatting.plural((v.total - v.doneCount).toLong(), "lesson") + " left."
    }
}
