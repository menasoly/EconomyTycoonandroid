package app.many.economytycoon.net

import app.many.economytycoon.core.DeterministicRandom
import java.util.PriorityQueue

/**
 * How one direction of a link misbehaves. Rates are parts per million; delays are milliseconds of network time,
 * and a spread between [minDelayMs] and [maxDelayMs] is what makes packets overtake each other.
 */
data class LinkProfile(
    val lossPpm: Int = 0,
    val duplicatePpm: Int = 0,
    val corruptPpm: Int = 0,
    val minDelayMs: Long = 0,
    val maxDelayMs: Long = 0,
) {
    init {
        require(lossPpm in 0..1_000_000 && duplicatePpm in 0..1_000_000 && corruptPpm in 0..1_000_000) { "rates are parts per million" }
        require(minDelayMs in 0..maxDelayMs) { "delay range is reversed" }
    }

    companion object {
        val PERFECT = LinkProfile()
        fun lossy(lossPpm: Int) = LinkProfile(lossPpm = lossPpm)
        fun jittery(minMs: Long, maxMs: Long) = LinkProfile(minDelayMs = minMs, maxDelayMs = maxMs)
        val HOSTILE = LinkProfile(lossPpm = 150_000, duplicatePpm = 100_000, corruptPpm = 30_000, minDelayMs = 0, maxDelayMs = 80)
    }
}

/** Counters of what the network did, for assertions and for the connection quality display. */
data class NetworkStats(val sent: Int, val delivered: Int, val dropped: Int, val duplicated: Int, val corrupted: Int, val cut: Int)

/**
 * A deterministic in-process network with its own clock. Every random choice comes from one seeded stream, so
 * the same seed and the same sequence of sends gives the same losses, delays and reorderings. It is the
 * transport for tests, for local hosting and for replaying a bad network.
 */
class MemoryNetwork(seed: Long, private val default: LinkProfile = LinkProfile.PERFECT) {
    private class Flight(val at: Long, val order: Long, val to: EndpointId, val datagram: Datagram)

    private val random = DeterministicRandom(seed)
    private val flights = PriorityQueue<Flight>(compareBy<Flight>({ it.at }, { it.order }))
    private val inboxes = java.util.TreeMap<EndpointId, ArrayList<Datagram>>()
    private val links = java.util.TreeMap<Pair<Int, Int>, LinkProfile>(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
    private val cuts = java.util.TreeSet<Pair<Int, Int>>(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
    private var order = 0L
    private var sent = 0; private var delivered = 0; private var dropped = 0; private var duplicated = 0; private var corrupted = 0; private var cut = 0

    /** Network time in milliseconds. */
    var now: Long = 0L
        private set

    fun endpoint(id: EndpointId): Transport {
        inboxes.getOrPut(id) { ArrayList() }
        return Port(id)
    }

    /** Overrides how packets travel from [from] to [to]; the other direction is unaffected. */
    fun setLink(from: EndpointId, to: EndpointId, profile: LinkProfile) { links[from.value to to.value] = profile }

    /** Applies [profile] in both directions between the two endpoints. */
    fun setBothWays(a: EndpointId, b: EndpointId, profile: LinkProfile) { setLink(a, b, profile); setLink(b, a, profile) }

    /** Cuts or restores the link in both directions. A cut link silently discards everything. */
    fun partition(a: EndpointId, b: EndpointId, isCut: Boolean) {
        if (isCut) { cuts += (a.value to b.value); cuts += (b.value to a.value) } else { cuts -= (a.value to b.value); cuts -= (b.value to a.value) }
    }

    /** Moves the clock to [time] and delivers everything due. */
    fun advanceTo(time: Long) {
        require(time >= now) { "network time cannot go backwards" }
        now = time
        while (true) {
            val next = flights.peek() ?: break
            if (next.at > now) break
            flights.poll()
            inboxes[next.to]?.add(next.datagram)
            delivered++
        }
    }

    fun advance(ms: Long) = advanceTo(now + ms)

    /** Packets still travelling. */
    fun inFlight(): Int = flights.size

    fun stats() = NetworkStats(sent, delivered, dropped, duplicated, corrupted, cut)

    private fun transmit(from: EndpointId, to: EndpointId, bytes: ByteArray) {
        sent++
        if ((from.value to to.value) in cuts || !inboxes.containsKey(to)) { cut++; return }
        val p = links[from.value to to.value] ?: default
        if (chance(p.lossPpm)) { dropped++; return }
        val copies = if (chance(p.duplicatePpm)) { duplicated++; 2 } else 1
        repeat(copies) {
            var payload = bytes.copyOf()
            if (payload.isNotEmpty() && chance(p.corruptPpm)) {
                corrupted++
                val i = random.nextInt(payload.size)
                payload[i] = (payload[i].toInt() xor (1 shl random.nextInt(8))).toByte()
            }
            val delay = if (p.maxDelayMs == p.minDelayMs) p.minDelayMs else random.nextInRange(p.minDelayMs, p.maxDelayMs)
            flights.add(Flight(now + delay, order++, to, Datagram(from, payload)))
        }
        if (flights.peek()?.at?.let { it <= now } == true) advanceTo(now)
    }

    private fun chance(ppm: Int): Boolean = ppm > 0 && (ppm >= 1_000_000 || random.nextInt(1_000_000) < ppm)

    private inner class Port(override val local: EndpointId) : Transport {
        override fun send(to: EndpointId, bytes: ByteArray) = transmit(local, to, bytes)
        override fun receive(): List<Datagram> {
            val box = inboxes[local] ?: return emptyList()
            if (box.isEmpty()) return emptyList()
            val out = ArrayList(box)
            box.clear()
            return out
        }
    }
}
