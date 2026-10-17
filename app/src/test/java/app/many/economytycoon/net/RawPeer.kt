package app.many.economytycoon.net

import app.many.economytycoon.simulation.Command

/** A hand-driven participant for tests that need to send things a well-behaved client never would. */
class RawPeer(val rig: NetRig, val id: EndpointId, val name: String = "Raw Peer", val spectate: Boolean = false) {
    val transport: Transport = rig.net.endpoint(id)
    var frame = 0L
    var player = PlayerId(-1)
    var token = ByteArray(0)
    var key = ByteArray(0)
    var commandSeq = 0L
    val received = ArrayList<NetMessage>()

    fun send(m: NetMessage, seq: Long = ++frame) { transport.send(rig.serverId, MessageCodec.encode(seq, m)) }
    fun sendRaw(bytes: ByteArray) { transport.send(rig.serverId, bytes) }

    fun hello(content: String = "toy:1", resume: ResumeInfo? = null, protocol: Int = NetLimits.PROTOCOL) {
        send(NetMessage.Hello(protocol, NetLimits.MIN_PROTOCOL, Capabilities.ALL, name, NetFixtures.session, content, spectate, resume))
    }

    /** Pumps the rig and collects what the server sent to this peer. */
    fun pump(frames: Int = 10): List<NetMessage> {
        val got = ArrayList<NetMessage>()
        repeat(frames) {
            rig.frame()
            for (d in transport.receive()) {
                val dec = MessageCodec.decode(d.bytes)
                if (dec is Decoded.Ok) got += dec.packet.message
            }
        }
        received += got
        return got
    }

    fun join(): Boolean {
        hello()
        val w = pump().filterIsInstance<NetMessage.Welcome>().firstOrNull() ?: return false
        player = w.player
        token = w.token
        key = Signing.deriveKey(token, NetFixtures.session, player)
        return true
    }

    fun signedSubmit(seq: Long, target: Long, payload: ByteArray, key: ByteArray = this.key): NetMessage.Submit =
        NetMessage.Submit(seq, target, payload, Signing.commandMac(key, NetFixtures.session, player, seq, target, payload))

    fun submit(command: Command, target: Long, seq: Long = ++commandSeq) = send(signedSubmit(seq, target, CommandPayload.encode(command)))

    fun errors(): List<NetError> = received.filterIsInstance<NetMessage.Error>().map { it.error }
    fun rejects(): List<NetMessage.Reject> = received.filterIsInstance<NetMessage.Reject>()
}
