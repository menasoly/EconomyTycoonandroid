package app.many.economytycoon.net

/** A received packet and where it came from. */
class Datagram(val from: EndpointId, val bytes: ByteArray)

/**
 * The only thing the multiplayer layer needs from a network: unreliable, unordered delivery of byte packets.
 * Sockets, relays and in-process pipes all fit behind it, and nothing above it knows which one it is.
 * Implementations may lose, duplicate, reorder or damage packets; the protocol copes with all four.
 */
interface Transport {
    val local: EndpointId

    /** Hands a packet to the network. Never blocks and never throws for an unreachable peer. */
    fun send(to: EndpointId, bytes: ByteArray)

    /** Packets that have arrived since the last call, oldest first. */
    fun receive(): List<Datagram>
}
