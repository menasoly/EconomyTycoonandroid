package app.many.economytycoon.net

/**
 * Takes snapshots of the running game and keeps the latest few, so several peers asking at the same tick share
 * one, and a peer that lost its connection can ask for the same snapshot again and carry on where it stopped.
 */
class SnapshotService(private val port: SimulationPort, private val log: CommitLog, private val keep: Int = 4) {
    private val bundles = LinkedHashMap<Long, SnapshotBundle>()
    private var nextId = 1L

    val taken: Int get() = (nextId - 1).toInt()

    /** A snapshot of the state right now; the same bundle is returned while the tick has not changed. */
    fun current(): SnapshotBundle? {
        val tick = port.tick
        bundles[tick]?.let { return it }
        val bytes = port.snapshot()
        if (bytes.isEmpty() || bytes.size > NetLimits.MAX_SNAPSHOT_BYTES) return null
        val b = SnapshotBundle(nextId++, tick, bytes, port.stateHash(), log.head)
        bundles[tick] = b
        while (bundles.size > keep) bundles.remove(bundles.keys.first())
        return b
    }

    fun byId(id: Long): SnapshotBundle? = bundles.values.firstOrNull { it.id == id }
}
