package app.many.economytycoon.replay

import app.many.economytycoon.simulation.LoggedCommand
import app.many.economytycoon.simulation.SimConfig

/** The state hash of a run at one tick. */
data class Checkpoint(val tick: Long, val hash: String)

/**
 * Everything needed to run a game again and check it comes out the same: the starting configuration, every
 * command with the tick it was issued on, and the state hash at regular points along the way.
 */
data class Trace(
    val config: SimConfig,
    val log: List<LoggedCommand>,
    val checkpoints: List<Checkpoint>,
    /** Content packs the run used, as "id:major.minor", so a replay against other content is refused early. */
    val content: List<String>,
) {
    val lastTick: Long get() = checkpoints.maxOfOrNull { it.tick } ?: 0L
    fun checkpointAt(tick: Long): Checkpoint? = checkpoints.firstOrNull { it.tick == tick }
}
