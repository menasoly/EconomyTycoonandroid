package app.many.economytycoon.ui.present

import app.many.economytycoon.guide.GuideProgress
import app.many.economytycoon.guide.StepState
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.ui.nav.Route

data class GuideRow(
    val id: String,
    val title: String,
    val why: String,
    val how: String,
    val area: String,
    val state: StepState,
    val tone: Tone,
    val stateLabel: String,
    val route: Route?,
)

data class GuideModel(
    val headline: String,
    val summary: String,
    val ppm: Long,
    val rows: List<GuideRow>,
    val finished: Boolean,
)

/** The short card for the overview: what to do next, or nothing once the guide is complete. */
data class GuideNudge(val title: String, val hint: String, val position: String, val route: Route?)

object GuidePresenter {
    fun stateLabel(s: StepState): String = when (s) {
        StepState.DONE -> "Done"
        StepState.CURRENT -> "Next"
        StepState.LATER -> "Later"
    }

    fun stateTone(s: StepState): Tone = when (s) {
        StepState.DONE -> Tone.GOOD
        StepState.CURRENT -> Tone.INFO
        StepState.LATER -> Tone.MUTED
    }

    fun model(p: GuideProgress): GuideModel = GuideModel(
        headline = if (p.finished) "You know your way around" else "First steps",
        summary = if (p.finished) "Every step is done. The goals list has the longer challenges." else "${p.doneCount} of ${p.total} done",
        ppm = p.ppm,
        rows = p.statuses.map { st ->
            GuideRow(
                id = st.step.id, title = st.step.title, why = st.step.why, how = st.step.how, area = st.step.area.label,
                state = st.state, tone = stateTone(st.state), stateLabel = stateLabel(st.state), route = Route.parse(st.step.area.routeKey),
            )
        },
        finished = p.finished,
    )

    fun nudge(p: GuideProgress): GuideNudge? {
        val step = p.current ?: return null
        return GuideNudge(step.title, step.why, "Step ${p.doneCount + 1} of ${p.total}", Route.parse(step.area.routeKey))
    }

    fun build(session: GameSession): GuideModel? = session.guide()?.let { model(it) }

    fun nudge(session: GameSession): GuideNudge? = session.guide()?.let { nudge(it) }
}
