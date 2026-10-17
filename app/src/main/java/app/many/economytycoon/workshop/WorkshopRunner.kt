package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the slow parts of testing (a balance study, a sweep) off the caller's thread so the screen stays responsive.
 * The work goes to an injected [Executor]; tests pass one that runs the job at once. One job runs at a time and there
 * is no cancel. The screen reads [busy] and [message] and asks again after [completed] changes.
 */
class WorkshopRunner(private val session: WorkshopSession, private val executor: Executor = background()) {
    private val current = AtomicReference<String?>(null)

    @Volatile var message: String? = null
        private set

    /** How many jobs have finished. */
    @Volatile var completed: Int = 0
        private set

    /** What is running, for the progress line; null when idle. */
    val busy: String? get() = current.get()

    /** Tests the draft as it is now; edits made while the test runs do not change what is tested. */
    fun startStudy(spec: StudySpec): Boolean {
        val draft = session.draft; val at = session.generation
        return start("Testing the scenario") { (session.studyOf(draft, at, spec) as? Outcome.Rejected)?.rejection?.message }
    }

    fun startSweep(spec: SweepSpec): Boolean {
        val draft = session.draft; val at = session.generation
        return start("Sweeping ${spec.param.label.lowercase()}") { session.sweepOf(draft, at, spec); null }
    }

    private fun start(label: String, work: () -> String?): Boolean {
        if (!current.compareAndSet(null, label)) return false
        message = null
        try {
            executor.execute {
                var result: String? = null
                try {
                    result = work()
                } catch (e: RuntimeException) {
                    result = "The test stopped: ${e.message ?: e.javaClass.simpleName}"
                } finally {
                    message = result
                    completed++
                    current.set(null)
                }
            }
        } catch (e: RuntimeException) {
            message = "The test could not start: ${e.message ?: e.javaClass.simpleName}"
            current.set(null)
            return false
        }
        return true
    }

    companion object {
        /** A single worker thread that does not keep the process alive. */
        fun background(): Executor {
            val pool = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "workshop-runner").also { it.isDaemon = true } }
            return pool
        }
    }
}
