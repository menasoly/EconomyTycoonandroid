package app.many.economytycoon.net

/** Players who reported the same state hash for a tick. */
class HashGroup(val hash: ByteArray, val players: List<PlayerId>) {
    val hex: String get() = Hex.encode(hash)
}

enum class DesyncCause {
    /** Every report agrees with the server. */
    NONE,

    /** Some players disagree with the server and with the majority. */
    CLIENTS,

    /** All reporting players agree with each other and not with the server. */
    SERVER,

    /** Nobody agrees with anybody, which points at the content or the build rather than one player. */
    UNKNOWN,
}

class DesyncReport(
    val tick: Long,
    val serverHash: ByteArray,
    val groups: List<HashGroup>,
    val culprits: List<PlayerId>,
    val cause: DesyncCause,
) {
    val serverShort: String get() = Hex.encode(serverHash).take(12)
}

/**
 * Works out who is out of step from the hashes players reported for one tick. The server's own hash is the
 * reference; players are grouped by what they reported, and the verdict says whether the fault lies with some
 * clients, with the server, or with nobody in particular. Players who reported nothing are not accused.
 */
object DesyncAnalysis {
    fun analyse(tick: Long, serverHash: ByteArray, reports: Map<PlayerId, ByteArray>): DesyncReport {
        val byHash = java.util.TreeMap<String, ArrayList<PlayerId>>()
        for ((p, h) in reports.toSortedMap()) byHash.getOrPut(Hex.encode(h)) { ArrayList() } += p
        val groups = byHash.entries
            .map { (hex, players) -> HashGroup(Hex.decode(hex) ?: ByteArray(0), players) }
            .sortedWith(compareByDescending<HashGroup> { it.players.size }.thenBy { it.hex })
        val serverHex = Hex.encode(serverHash)
        val diverged = groups.filter { it.hex != serverHex }.flatMap { it.players }.sorted()
        if (diverged.isEmpty()) return DesyncReport(tick, serverHash, groups, emptyList(), DesyncCause.NONE)
        val cause = when {
            reports.size >= 2 && groups.size == 1 -> DesyncCause.SERVER
            groups.size >= 3 && groups.all { it.players.size == 1 } -> DesyncCause.UNKNOWN
            else -> DesyncCause.CLIENTS
        }
        val culprits = if (cause == DesyncCause.SERVER) emptyList() else diverged
        return DesyncReport(tick, serverHash, groups, culprits, cause)
    }
}
