package app.many.economytycoon.net

/** Optional features two peers may agree to use. A bit set on both sides is switched on for the connection. */
object Capabilities {
    const val SNAPSHOT_RESUME = 1L shl 0
    const val CHAT = 1L shl 1
    const val TRADE = 1L shl 2
    const val SPECTATE = 1L shl 3
    const val EVERY_TICK_HASH = 1L shl 4

    const val ALL = SNAPSHOT_RESUME or CHAT or TRADE or SPECTATE or EVERY_TICK_HASH
    const val KNOWN_MASK = ALL

    fun has(set: Long, bit: Long) = set and bit == bit
    fun names(set: Long): List<String> = buildList {
        if (has(set, SNAPSHOT_RESUME)) add("snapshot-resume")
        if (has(set, CHAT)) add("chat")
        if (has(set, TRADE)) add("trade")
        if (has(set, SPECTATE)) add("spectate")
        if (has(set, EVERY_TICK_HASH)) add("every-tick-hash")
    }
}

enum class LobbyPhase(val tag: Int) {
    OPEN(0), RUNNING(1), CLOSED(2);

    companion object { fun fromTag(tag: Int) = values().firstOrNull { it.tag == tag } }
}

/** How a member's connection stands. A member in GRACE keeps their slot until the grace period runs out. */
enum class Presence(val tag: Int) {
    CONNECTED(0), GRACE(1), GONE(2);

    companion object { fun fromTag(tag: Int) = values().firstOrNull { it.tag == tag } }
}

enum class LobbyAction(val tag: Int) {
    READY(0), UNREADY(1), LEAVE(2), KICK(3), START(4), SPECTATE(5), PLAY(6), TRANSFER_HOST(7), ACK_STATE(8);

    companion object { fun fromTag(tag: Int) = values().firstOrNull { it.tag == tag } }
}

enum class TradeAction(val tag: Int) {
    OFFER(0), ACCEPT(1), DECLINE(2), CANCEL(3);

    companion object { fun fromTag(tag: Int) = values().firstOrNull { it.tag == tag } }
}

enum class TradeState(val tag: Int) {
    OFFERED(0), ACCEPTED(1), SETTLED(2), DECLINED(3), CANCELLED(4), EXPIRED(5), FAILED(6);

    val open: Boolean get() = this == OFFERED || this == ACCEPTED

    companion object { fun fromTag(tag: Int) = values().firstOrNull { it.tag == tag } }
}

enum class ChatKind(val tag: Int) {
    TEXT(0), EMOTE(1), SYSTEM(2);

    companion object { fun fromTag(tag: Int) = values().firstOrNull { it.tag == tag } }
}

enum class SnapshotReason(val tag: Int) {
    JOIN(0), DESYNC(1), RECONNECT(2), LAG(3);

    companion object { fun fromTag(tag: Int) = values().firstOrNull { it.tag == tag } }
}

enum class ByeReason(val tag: Int) {
    LEAVE(0), KICKED(1), SHUTDOWN(2), TIMEOUT(3), BANNED(4);

    companion object { fun fromTag(tag: Int) = values().firstOrNull { it.tag == tag } }
}

/** Identifies a message type on the wire. Tags never change meaning and are never reused. */
enum class MessageKind(val tag: Int) {
    HELLO(1), WELCOME(2), ERROR(3), BYE(4),
    SUBMIT(10), ACK(11), REJECT(12), COMMIT(13), RESEND(14),
    HASH_REPORT(20), HASH_CHECKPOINT(21), DESYNC_NOTICE(22),
    SNAPSHOT_REQUEST(30), SNAPSHOT_HEADER(31), SNAPSHOT_CHUNK(32), SNAPSHOT_ACK(33),
    PING(40), PONG(41),
    LOBBY_REQUEST(50), LOBBY_STATE(51), LOBBY_REPLY(52),
    CHAT(60), CHAT_BROADCAST(61),
    TRADE_REQUEST(70), TRADE_NOTICE(71);

    companion object {
        private val byTag = values().associateBy { it.tag }
        fun fromTag(tag: Int): MessageKind? = byTag[tag]
    }
}

/** A command as it travels: the encoded simulation command, who sent it and in what order. */
class CommitEntry(val player: PlayerId, val seq: Long, val payload: ByteArray) {
    override fun equals(other: Any?) = other is CommitEntry && player == other.player && seq == other.seq && payload.contentEquals(other.payload)
    override fun hashCode() = (player.value.hashCode() * 31 + seq.hashCode()) * 31 + payload.contentHashCode()
    override fun toString() = "CommitEntry($player #$seq ${payload.size}B)"
}

/** A row of the lobby as sent to every member. */
data class MemberInfo(
    val id: PlayerId,
    val name: String,
    val role: Role,
    /** Seat number for players, -1 for spectators. */
    val slot: Int,
    val ready: Boolean,
    val presence: Presence,
)

class ResumeInfo(val player: PlayerId, val nonce: Long, val lastTick: Long, val proof: ByteArray) {
    override fun equals(other: Any?) = other is ResumeInfo && player == other.player && nonce == other.nonce && lastTick == other.lastTick && proof.contentEquals(other.proof)
    override fun hashCode() = (player.value.hashCode() * 31 + nonce.hashCode()) * 31 + proof.contentHashCode()
}

/** Everything that crosses the wire. Each subclass is one message kind with an exact layout in [MessageCodec]. */
sealed class NetMessage {
    abstract val kind: MessageKind

    // ---- connection ----

    class Hello(
        val protocol: Int,
        val minProtocol: Int,
        val capabilities: Long,
        val name: String,
        val session: SessionId,
        val content: String,
        val spectate: Boolean,
        val resume: ResumeInfo?,
    ) : NetMessage() {
        override val kind get() = MessageKind.HELLO
        override fun equals(other: Any?) = other is Hello && protocol == other.protocol && minProtocol == other.minProtocol &&
            capabilities == other.capabilities && name == other.name && session == other.session && content == other.content &&
            spectate == other.spectate && resume == other.resume
        override fun hashCode() = name.hashCode() * 31 + session.hashCode()
    }

    class Welcome(
        val protocol: Int,
        val capabilities: Long,
        val player: PlayerId,
        val role: Role,
        val session: SessionId,
        val token: ByteArray,
        val tick: Long,
        val commandDelay: Int,
        val hashInterval: Int,
        val needsSnapshot: Boolean,
        val seed: Long,
        /** Commit chain value of the tick before [tick]. */
        val chain: Long,
    ) : NetMessage() {
        override val kind get() = MessageKind.WELCOME
        override fun equals(other: Any?) = other is Welcome && protocol == other.protocol && capabilities == other.capabilities &&
            player == other.player && role == other.role && session == other.session && token.contentEquals(other.token) &&
            tick == other.tick && commandDelay == other.commandDelay && hashInterval == other.hashInterval &&
            needsSnapshot == other.needsSnapshot && seed == other.seed && chain == other.chain
        override fun hashCode() = player.hashCode() * 31 + session.hashCode()
    }

    data class Error(val error: NetError, val detail: String) : NetMessage() {
        override val kind get() = MessageKind.ERROR
    }

    data class Bye(val reason: ByeReason) : NetMessage() {
        override val kind get() = MessageKind.BYE
    }

    // ---- commands ----

    class Submit(val seq: Long, val targetTick: Long, val payload: ByteArray, val mac: ByteArray) : NetMessage() {
        override val kind get() = MessageKind.SUBMIT
        override fun equals(other: Any?) = other is Submit && seq == other.seq && targetTick == other.targetTick &&
            payload.contentEquals(other.payload) && mac.contentEquals(other.mac)
        override fun hashCode() = seq.hashCode() * 31 + payload.contentHashCode()
    }

    data class Ack(val seq: Long, val targetTick: Long) : NetMessage() {
        override val kind get() = MessageKind.ACK
    }

    data class Reject(val seq: Long, val error: NetError, val rule: String, val serverTick: Long) : NetMessage() {
        override val kind get() = MessageKind.REJECT
    }

    /** The commands that run on [tick], in the order every participant must apply them, plus settled trades. */
    data class Commit(val tick: Long, val entries: List<CommitEntry>, val transfers: List<Transfer>, val chain: Long) : NetMessage() {
        override val kind get() = MessageKind.COMMIT
    }

    data class Resend(val fromTick: Long, val count: Int) : NetMessage() {
        override val kind get() = MessageKind.RESEND
    }

    // ---- state hashes ----

    class HashReport(val tick: Long, val hash: ByteArray) : NetMessage() {
        override val kind get() = MessageKind.HASH_REPORT
        override fun equals(other: Any?) = other is HashReport && tick == other.tick && hash.contentEquals(other.hash)
        override fun hashCode() = tick.hashCode() * 31 + hash.contentHashCode()
    }

    class HashCheckpoint(val tick: Long, val hash: ByteArray) : NetMessage() {
        override val kind get() = MessageKind.HASH_CHECKPOINT
        override fun equals(other: Any?) = other is HashCheckpoint && tick == other.tick && hash.contentEquals(other.hash)
        override fun hashCode() = tick.hashCode() * 31 + hash.contentHashCode()
    }

    class DesyncNotice(val tick: Long, val serverHash: ByteArray) : NetMessage() {
        override val kind get() = MessageKind.DESYNC_NOTICE
        override fun equals(other: Any?) = other is DesyncNotice && tick == other.tick && serverHash.contentEquals(other.serverHash)
        override fun hashCode() = tick.hashCode() * 31 + serverHash.contentHashCode()
    }

    // ---- snapshots ----

    data class SnapshotRequest(val reason: SnapshotReason, val snapshotId: Long, val resumeChunk: Int) : NetMessage() {
        override val kind get() = MessageKind.SNAPSHOT_REQUEST
    }

    class SnapshotHeader(
        val snapshotId: Long,
        val tick: Long,
        val totalBytes: Int,
        val chunkSize: Int,
        val chunkCount: Int,
        val digest: ByteArray,
        val stateHash: ByteArray,
        /** Commit chain value of the tick before [tick], so the receiver can keep verifying the chain. */
        val chain: Long,
    ) : NetMessage() {
        override val kind get() = MessageKind.SNAPSHOT_HEADER
        override fun equals(other: Any?) = other is SnapshotHeader && snapshotId == other.snapshotId && tick == other.tick &&
            totalBytes == other.totalBytes && chunkSize == other.chunkSize && chunkCount == other.chunkCount &&
            digest.contentEquals(other.digest) && stateHash.contentEquals(other.stateHash) && chain == other.chain
        override fun hashCode() = snapshotId.hashCode() * 31 + digest.contentHashCode()
    }

    class SnapshotChunk(val snapshotId: Long, val index: Int, val data: ByteArray, val crc: Long) : NetMessage() {
        override val kind get() = MessageKind.SNAPSHOT_CHUNK
        override fun equals(other: Any?) = other is SnapshotChunk && snapshotId == other.snapshotId && index == other.index &&
            data.contentEquals(other.data) && crc == other.crc
        override fun hashCode() = snapshotId.hashCode() * 31 + index
    }

    data class SnapshotAck(val snapshotId: Long, val next: Int, val complete: Boolean) : NetMessage() {
        override val kind get() = MessageKind.SNAPSHOT_ACK
    }

    // ---- liveness ----

    data class Ping(val nonce: Long, val sentAt: Long) : NetMessage() {
        override val kind get() = MessageKind.PING
    }

    data class Pong(val nonce: Long, val sentAt: Long, val serverTick: Long) : NetMessage() {
        override val kind get() = MessageKind.PONG
    }

    // ---- lobby ----

    data class LobbyRequest(val action: LobbyAction, val target: PlayerId, val arg: Long) : NetMessage() {
        override val kind get() = MessageKind.LOBBY_REQUEST
    }

    /** Tells a member their lobby request was carried out, so they can stop repeating it. */
    data class LobbyReply(val requestSeq: Long, val version: Long) : NetMessage() {
        override val kind get() = MessageKind.LOBBY_REPLY
    }

    data class LobbyState(val version: Long, val phase: LobbyPhase, val host: PlayerId, val members: List<MemberInfo>) : NetMessage() {
        override val kind get() = MessageKind.LOBBY_STATE
    }

    // ---- chat ----

    data class Chat(val chatKind: ChatKind, val emote: Int, val text: String) : NetMessage() {
        override val kind get() = MessageKind.CHAT
    }

    data class ChatBroadcast(val from: PlayerId, val chatKind: ChatKind, val emote: Int, val text: String, val tick: Long) : NetMessage() {
        override val kind get() = MessageKind.CHAT_BROADCAST
    }

    // ---- trade ----

    data class TradeRequest(
        val action: TradeAction,
        val trade: TradeId,
        val counterparty: PlayerId,
        val give: List<TradeAsset>,
        val want: List<TradeAsset>,
        val ttlTicks: Int,
    ) : NetMessage() {
        override val kind get() = MessageKind.TRADE_REQUEST
    }

    data class TradeNotice(
        val trade: TradeId,
        val state: TradeState,
        val from: PlayerId,
        val to: PlayerId,
        val give: List<TradeAsset>,
        val want: List<TradeAsset>,
        val expiresAt: Long,
    ) : NetMessage() {
        override val kind get() = MessageKind.TRADE_NOTICE
    }
}
