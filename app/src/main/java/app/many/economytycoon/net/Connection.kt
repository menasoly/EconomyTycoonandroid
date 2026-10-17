package app.many.economytycoon.net

/** How good a link feels to the player, from round-trip time and lost pings. */
enum class Quality(val label: String) {
    EXCELLENT("Excellent"), GOOD("Good"), FAIR("Fair"), POOR("Poor"), OFFLINE("Offline")
}

/**
 * Round-trip and loss figures for one link, kept as integers. The round-trip time is smoothed (7/8 old, 1/8 new)
 * the way transport protocols do, so one slow packet does not swing the display.
 */
class LinkStats(private val offlineAfterMs: Long = 5_000L) {
    private val outcomes = ArrayDeque<Boolean>()
    private val outstanding = java.util.TreeMap<Long, Long>()

    var rttMs: Long = -1L
        private set
    var jitterMs: Long = 0L
        private set
    var lastHeard: Long = Long.MIN_VALUE
        private set

    fun heard(now: Long) { if (now > lastHeard) lastHeard = now }

    /** Notes that a ping went out. Pings that stay unanswered for longer than [timeoutMs] count as lost. */
    fun pinged(nonce: Long, now: Long, timeoutMs: Long = 3_000L) {
        val stale = outstanding.entries.filter { now - it.value > timeoutMs }.map { it.key }
        for (k in stale) { outstanding.remove(k); record(false) }
        outstanding[nonce] = now
        while (outstanding.size > MAX_OUTSTANDING) { outstanding.remove(outstanding.firstKey()); record(false) }
    }

    /** Notes an answer; returns the measured round trip, or null if the nonce was not outstanding. */
    fun ponged(nonce: Long, now: Long): Long? {
        val sentAt = outstanding.remove(nonce) ?: return null
        val rtt = (now - sentAt).coerceAtLeast(0L)
        if (rttMs < 0L) rttMs = rtt else {
            jitterMs = (jitterMs * 3 + Math.abs(rtt - rttMs)) / 4
            rttMs = (rttMs * 7 + rtt) / 8
        }
        record(true)
        heard(now)
        return rtt
    }

    private fun record(answered: Boolean) {
        outcomes.addLast(answered)
        while (outcomes.size > WINDOW) outcomes.removeFirst()
    }

    /** Share of recent pings that went unanswered, in parts per million. */
    fun lossPpm(): Int = if (outcomes.isEmpty()) 0 else (outcomes.count { !it } * 1_000_000L / outcomes.size).toInt()

    fun quality(now: Long): Quality {
        if (lastHeard == Long.MIN_VALUE || now - lastHeard > offlineAfterMs) return Quality.OFFLINE
        val loss = lossPpm()
        if (loss >= 300_000) return Quality.POOR
        if (rttMs < 0L) return Quality.FAIR
        return when {
            loss >= 300_000 || rttMs >= 400L -> Quality.POOR
            loss >= 100_000 || rttMs >= 200L -> Quality.FAIR
            loss > 0 || rttMs >= 80L -> Quality.GOOD
            else -> Quality.EXCELLENT
        }
    }

    private companion object {
        const val WINDOW = 16
        const val MAX_OUTSTANDING = 32
    }
}

/** What the server knows about one connected endpoint. */
internal class ServerConn(val endpoint: EndpointId, val player: PlayerId, var role: Role, val caps: Long, val protocol: Int) {
    val inbound = SeqWindow()
    val stats = LinkStats()
    val replies = LinkedHashMap<Long, List<ByteArray>>()
    var sendSeq = 0L
    var lobbyAcked = -1L
    var lobbySentAt = Long.MIN_VALUE / 4
    var pingNonce = 0L
    var lastPingAt = Long.MIN_VALUE
    var lastSnapshotAt = Long.MIN_VALUE
    var sender: SnapshotSender? = null
    var welcome: ByteArray? = null
    var reportedTick = -1L
    var joinedAt = 0L
    var helloNonce = 0L

    fun nextSeq(): Long = ++sendSeq

    fun remember(requestSeq: Long, frames: List<ByteArray>, limit: Int) {
        replies[requestSeq] = frames
        while (replies.size > limit) replies.remove(replies.keys.first())
    }
}
