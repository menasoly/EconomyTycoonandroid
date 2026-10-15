package app.many.economytycoon.replay

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.simulation.Simulation

/** How a replay compared with the trace it was checked against. */
sealed class Verdict {
    /** Every checkpoint matched. */
    data class Identical(val checkpoints: Int, val ticks: Long) : Verdict()

    /** A checkpoint hash differed: the run is not repeatable from this point. [lastGood] is the checkpoint before it. */
    data class Diverged(val tick: Long, val expected: String, val actual: String, val lastGood: Long?) : Verdict()

    /** The trace cannot be replayed with the content at hand. */
    data class Refused(val reason: String) : Verdict()

    val ok: Boolean get() = this is Identical

    fun describe(): String = when (this) {
        is Identical -> "replay matches at all $checkpoints checkpoints over $ticks ticks"
        is Diverged -> "replay diverged at tick $tick (last matching checkpoint: ${lastGood ?: "none"})"
        is Refused -> "replay refused: $reason"
    }
}

/**
 * Plays a trace again from its seed and commands and compares the state hash at each checkpoint. If two runs
 * of the same inputs ever differ, something depends on more than the seed and the commands, which is a defect.
 */
class ReplayVerifier(private val content: ContentRepository) {
    fun verify(trace: Trace): Verdict {
        if (trace.checkpoints.isEmpty()) return Verdict.Refused("the trace has no checkpoints")
        val have = TraceRecorder.packs(content)
        if (trace.content.isNotEmpty() && trace.content != have) {
            return Verdict.Refused("it was recorded with content ${trace.content.joinToString()} but this is ${have.joinToString()}")
        }
        val sim = Simulation(trace.config, content)
        val byTick = trace.log.groupBy { it.tick }
        val remaining = trace.checkpoints.sortedBy { it.tick }
        var lastGood: Long? = null
        var matched = 0
        for (cp in remaining) {
            while (sim.tick.value < cp.tick) step(sim, byTick)
            if (sim.tick.value != cp.tick) return Verdict.Refused("checkpoint at tick ${cp.tick} lies before the start tick ${sim.tick.value}")
            val actual = sim.hash()
            if (actual != cp.hash) return Verdict.Diverged(cp.tick, cp.hash, actual, lastGood)
            lastGood = cp.tick
            matched++
        }
        return Verdict.Identical(matched, trace.lastTick)
    }

    /** Replays [trace] and returns the finished simulation, for inspecting what the run ended with. */
    fun replay(trace: Trace, untilTick: Long = trace.lastTick): Simulation {
        val sim = Simulation(trace.config, content)
        val byTick = trace.log.groupBy { it.tick }
        while (sim.tick.value < untilTick) step(sim, byTick)
        return sim
    }

    private fun step(sim: Simulation, byTick: Map<Long, List<app.many.economytycoon.simulation.LoggedCommand>>) {
        val entries = byTick[sim.tick.value].orEmpty().sortedBy { it.sequence }
        for (entry in entries) if (entry.immediate) sim.execute(entry.actor, entry.command) else sim.submit(entry.actor, entry.command)
        sim.advance()
    }

    /**
     * Narrows a divergence between two live simulations built from the same inputs to the first tick that differs,
     * by running both forward one tick at a time. Null when they agree for [ticks] ticks.
     */
    fun firstDifference(a: Simulation, b: Simulation, ticks: Int): Long? {
        if (a.hash() != b.hash()) return a.tick.value
        repeat(ticks) {
            a.advance(); b.advance()
            if (a.hash() != b.hash()) return a.tick.value
        }
        return null
    }
}
