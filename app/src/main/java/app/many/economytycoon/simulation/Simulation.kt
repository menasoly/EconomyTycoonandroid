package app.many.economytycoon.simulation

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick

/** Result of a submitted command once its tick has run. */
class CommandTicket(val sequence: Long, val actor: Actor, val command: Command)

/**
 * The simulation as the rest of the app sees it: build one from a config and content, submit commands,
 * and advance tick by tick. Everything that changes the economy goes through here, which is what makes
 * a run reproducible from its seed and its command log.
 */
class Simulation(val config: SimConfig, val content: ContentRepository, bootstrap: Boolean = true, restored: RestoredCore? = null) {
    val economy = Economy(config, content, restored)
    val executor = CommandExecutor(economy)
    val pipeline = TickPipeline(economy, executor)

    private val queue = ArrayList<QueuedCommand>()
    private var nextSequence = 1L
    private val log = ArrayList<LoggedCommand>()
    private val summaries = ArrayDeque<TickSummary>()

    init { if (bootstrap) Bootstrap(economy, executor).run() }

    val tick: Tick get() = economy.tick

    /** Queues a command for the next tick's command phase. */
    fun submit(actor: Actor, command: Command): CommandTicket {
        val q = QueuedCommand(nextSequence++, actor, command)
        queue += q
        return CommandTicket(q.sequence, actor, command)
    }

    /** Applies a command immediately, outside the tick, as the setup screens do. The outcome is logged. */
    fun execute(actor: Actor, command: Command): Outcome<CommandResult> {
        val r = executor.execute(actor, command)
        if (r is Outcome.Ok) log += LoggedCommand(nextSequence++, economy.tick.value, actor, command, immediate = true)
        return r
    }

    fun advance(): TickSummary {
        val batch = ArrayList(queue)
        queue.clear()
        val summary = pipeline.run(batch)
        log += pipeline.applied
        summaries.addLast(summary)
        while (summaries.size > MAX_SUMMARIES) summaries.removeFirst()
        return summary
    }

    fun advance(ticks: Int): List<TickSummary> = List(ticks) { advance() }

    internal fun queuedCommands(): List<QueuedCommand> = queue.toList()
    internal fun sequenceCounter(): Long = nextSequence

    /** Puts back what a saved game held outside the engines: the command log, waiting commands and counters. */
    internal fun restoreRuntime(entries: List<LoggedCommand>, waiting: List<QueuedCommand>, sequence: Long) {
        log.clear(); log.addAll(entries)
        queue.clear(); queue.addAll(waiting)
        nextSequence = sequence
        summaries.clear()
    }

    fun commandLog(): List<LoggedCommand> = log.toList()
    fun recentSummaries(): List<TickSummary> = summaries.toList()
    fun pendingCommands(): Int = queue.size
    fun hash(): String = economy.hash() + ":" + pipeline.events.let { h -> app.many.economytycoon.core.CanonicalHasher().also { h.hashInto(it) }.finish().toHex() }

    /** Rebuilds a run from its seed and log. The result has the same hash as the original at the same tick. */
    companion object {
        const val MAX_SUMMARIES = 120

        fun replay(config: SimConfig, content: ContentRepository, log: List<LoggedCommand>, untilTick: Long): Simulation {
            val sim = Simulation(config, content)
            val byTick = log.groupBy { it.tick }
            while (sim.tick.value < untilTick) {
                val now = sim.tick.value
                val entries = byTick[now].orEmpty().sortedBy { it.sequence }
                for (entry in entries) if (entry.immediate) sim.execute(entry.actor, entry.command) else sim.submit(entry.actor, entry.command)
                sim.advance()
            }
            return sim
        }
    }
}
