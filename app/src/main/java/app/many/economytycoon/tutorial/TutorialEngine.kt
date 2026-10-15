package app.many.economytycoon.tutorial

/** What the player should see: the step to work on now, and how the rest stand. */
data class TutorialView(
    val steps: List<StepState>,
    val current: TutorialStep?,
    val doneCount: Int,
    val total: Int,
    val finished: Boolean,
) {
    val progressPpm: Long get() = if (total == 0) 1_000_000L else doneCount * 1_000_000L / total
}

data class StepState(val step: TutorialStep, val status: Status, val progressPpm: Long) {
    enum class Status { DONE, SKIPPED, CURRENT, LOCKED }
}

/**
 * Works out the state of the tutorial from the game's facts. Steps complete in any order the game allows, but only
 * the first unfinished one is "current": the guide asks for one thing at a time.
 */
class TutorialEngine(private val script: List<TutorialStep> = TutorialScript.steps) {
    /** Marks every step whose goal is now met as complete. Completion is permanent: a step never un-completes. */
    fun update(progress: TutorialProgress, facts: TutorialFacts): TutorialProgress {
        if (progress.dismissed) return progress
        val done = progress.completed.toMutableSet()
        for (s in script) if (!progress.isDone(s.id) && s.goal.met(facts, s.id in progress.acknowledged)) done += s.id
        return if (done.size == progress.completed.size) progress else progress.copy(completed = done)
    }

    fun current(progress: TutorialProgress): TutorialStep? = script.firstOrNull { !progress.isDone(it.id) }

    fun acknowledge(progress: TutorialProgress, id: String): TutorialProgress =
        if (script.none { it.id == id }) progress else progress.copy(acknowledged = progress.acknowledged + id, completed = progress.completed + id)

    /** Skipping the current step lets the guide move on; it can be skipped only if it is not already done. */
    fun skip(progress: TutorialProgress, id: String): TutorialProgress =
        if (script.none { it.id == id } || progress.isDone(id)) progress else progress.copy(skipped = progress.skipped + id)

    fun skipCurrent(progress: TutorialProgress): TutorialProgress = current(progress)?.let { skip(progress, it.id) } ?: progress

    fun dismiss(progress: TutorialProgress) = progress.copy(dismissed = true)
    fun restart() = TutorialProgress()

    fun view(progress: TutorialProgress, facts: TutorialFacts): TutorialView {
        val current = current(progress)
        val states = script.map { s ->
            val status = when {
                s.id in progress.completed -> StepState.Status.DONE
                s.id in progress.skipped -> StepState.Status.SKIPPED
                s.id == current?.id -> StepState.Status.CURRENT
                else -> StepState.Status.LOCKED
            }
            StepState(s, status, if (status == StepState.Status.DONE) 1_000_000L else s.goal.progressPpm(facts, s.id in progress.acknowledged))
        }
        val done = states.count { it.status == StepState.Status.DONE || it.status == StepState.Status.SKIPPED }
        return TutorialView(states, current, done, script.size, current == null)
    }
}
