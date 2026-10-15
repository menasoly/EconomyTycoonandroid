package app.many.economytycoon.replay

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.simulation.Simulation

/**
 * Advances a simulation while noting its state hash every [every] ticks. The checkpoint at the starting tick is
 * taken straight away so a replay can confirm it began from the same world.
 */
class TraceRecorder(private val sim: Simulation, private val content: ContentRepository, private val every: Int = 30) {
    init { require(every >= 1) { "checkpoints must be at least one tick apart" } }

    private val checkpoints = ArrayList<Checkpoint>()

    init { checkpoints += Checkpoint(sim.tick.value, sim.hash()) }

    /** Runs [ticks] more ticks, recording as it goes. */
    fun advance(ticks: Int) {
        repeat(ticks) {
            sim.advance()
            if (sim.tick.value % every == 0L) checkpoints += Checkpoint(sim.tick.value, sim.hash())
        }
    }

    /** The trace so far, always ending on a checkpoint at the current tick. */
    fun trace(): Trace {
        val now = sim.tick.value
        val cps = if (checkpoints.last().tick == now) checkpoints.toList() else checkpoints + Checkpoint(now, sim.hash())
        return Trace(sim.config, sim.commandLog(), cps, packs(content))
    }

    companion object {
        fun packs(content: ContentRepository): List<String> = content.versions.map { "${it.packId}:${it.major}.${it.minor}" }.sorted()
    }
}
