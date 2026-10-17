package app.many.economytycoon.net

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Command

/** Where a client is in its life with one server. */
enum class ClientState { IDLE, CONNECTING, LOBBY, SYNCING, RUNNING, RECONNECTING, CLOSED, FAILED }

/** Timing and limits for a client. All times are network milliseconds supplied by the caller. */
data class ClientConfig(
    val session: SessionId,
    val name: String,
    val spectate: Boolean = false,
    val capabilities: Long = Capabilities.ALL,
    val helloRetryMs: Long = 300L,
    val maxHelloAttempts: Int = 20,
    val resendAfterMs: Long = 250L,
    val submitRetryMs: Long = 300L,
    val maxSubmitAttempts: Int = 6,
    val maxInFlight: Int = 32,
    val pingIntervalMs: Long = 1_000L,
    val silenceTimeoutMs: Long = 6_000L,
    val reconnectWindowMs: Long = 30_000L,
    val snapshotStallMs: Long = 600L,
    val maxSnapshotAttempts: Int = 5,
    val maxApplyPerPoll: Int = 64,
    val bufferAhead: Int = 2_048,
    val lagResyncTicks: Long = 600L,
    val maxExtraDelay: Int = 20,
    val chatLines: Int = 100,
) {
    init { require(helloRetryMs > 0 && resendAfterMs > 0 && submitRetryMs > 0 && maxApplyPerPoll > 0) { "timing values must be positive" } }
}

/** What a client needs to keep to come back to the same seat after a restart. */
class Credentials(val player: PlayerId, val token: ByteArray, val nonce: Long)

/** Things that happened since the caller last looked. */
sealed class ClientEvent {
    data class Joined(val player: PlayerId, val role: Role) : ClientEvent()
    data class Refused(val error: NetError, val detail: String) : ClientEvent()
    data class LobbyChanged(val version: Long) : ClientEvent()
    data class GameStarted(val tick: Long) : ClientEvent()
    data class Accepted(val seq: Long, val targetTick: Long) : ClientEvent()
    data class Rejected(val seq: Long, val error: NetError, val rule: String) : ClientEvent()
    data class Expired(val seq: Long) : ClientEvent()
    data class Desync(val tick: Long, val why: String) : ClientEvent()
    data class Resynced(val tick: Long) : ClientEvent()
    data class Chat(val message: NetMessage.ChatBroadcast) : ClientEvent()
    data class Trade(val notice: NetMessage.TradeNotice) : ClientEvent()
    data class ServerError(val error: NetError, val detail: String) : ClientEvent()
    data class Disconnected(val why: String) : ClientEvent()
    data class Reconnected(val tick: Long) : ClientEvent()
    data class Closed(val reason: ByeReason) : ClientEvent()
}

/** A read model for screens. */
data class ClientView(
    val state: ClientState,
    val player: PlayerId,
    val role: Role,
    val tick: Long,
    val serverTick: Long,
    val lagTicks: Long,
    val lobby: LobbyView?,
    val quality: Quality,
    val rttMs: Long,
    val lossPpm: Int,
    val inFlight: Int,
    val desyncs: Int,
    val resyncs: Int,
    val snapshotReceived: Int,
    val snapshotTotal: Int,
    val openTrades: Int,
)

/**
 * The game as one participant sees it. There is no prediction: the client holds its own copy of the simulation
 * and moves it forward only by applying the server's commits, in order, with every commit checked against the
 * chain. A command sent now runs a few ticks later, after the server has fixed it into a commit. Hashes of the
 * state are compared with the server's at regular ticks, and if they ever differ, or the chain breaks, the
 * client throws its copy away and takes a snapshot.
 */
class ClientSession(
    val config: ClientConfig,
    private val port: SimulationPort,
    private val transport: Transport,
    private val server: EndpointId,
    credentials: Credentials? = null,
) {
    var state = ClientState.IDLE
        private set
    var player = credentials?.player ?: PlayerId(-1)
        private set
    var role = if (config.spectate) Role.SPECTATOR else Role.PLAYER
        private set

    private var token: ByteArray = credentials?.token ?: ByteArray(0)
    private var key: ByteArray = if (credentials != null) Signing.deriveKey(credentials.token, config.session, credentials.player) else ByteArray(0)
    private var nonce = credentials?.nonce ?: 0L
    private var commandDelay = 3
    private var hashInterval = 10
    private var extraDelay = 0
    private var seated = false
    private var chainPrev = 0L
    private var serverTick = 0L
    private var frameSeq = 0L
    private var commandSeq = 0L

    private val buffer = java.util.TreeMap<Long, NetMessage.Commit>()
    private val myHashes = java.util.TreeMap<Long, ByteArray>()
    private val checkpoints = java.util.TreeMap<Long, ByteArray>()
    private val events = ArrayList<ClientEvent>()
    private val receiver = SnapshotReceiver()
    private val inFlight = java.util.TreeMap<Long, InFlight>()
    private val trades = java.util.TreeMap<TradeId, NetMessage.TradeNotice>()
    private val stats = LinkStats()
    val chat = ChatHistory(config.chatLines)

    private var lobbyView: LobbyView? = null
    private var pendingLobby: PendingLobby? = null
    private var helloBytes: ByteArray? = null
    private var helloAttempts = 0
    private var helloAt = 0L
    private var resuming = false
    private var reconnectSince = 0L
    private var lastHeard = 0L
    private var lastProgress = 0L
    private var lastResendAt = Long.MIN_VALUE / 4
    private var lastPingAt = Long.MIN_VALUE
    private var pingNonce = 0L
    private var snapshotAttempts = 0
    private var snapshotAskedAt = 0L
    private var snapshotProgressAt = 0L
    private var snapshotReason = SnapshotReason.JOIN
    private var desyncCount = 0
    private var resyncCount = 0

    private class InFlight(val payload: ByteArray, var target: Long, var bytes: ByteArray, var sentAt: Long, var attempts: Int)
    private class PendingLobby(val bytes: ByteArray, val frame: Long, var sentAt: Long)

    val tick: Long get() = port.tick
    val estimatedServerTick: Long get() = maxOf(serverTick, port.tick)

    // ---- the public surface ----

    /** Starts joining, or comes back to the seat held by the credentials given at construction. */
    fun connect(now: Long) {
        if (state != ClientState.IDLE) return
        lastHeard = now
        if (token.isNotEmpty()) { state = ClientState.RECONNECTING; reconnectSince = now; sendResumeHello(now) }
        else { state = ClientState.CONNECTING; sendFreshHello(now) }
    }

    fun credentials(): Credentials? = if (token.isEmpty() || player.value < 0L) null else Credentials(player, token.copyOf(), nonce)

    fun drainEvents(): List<ClientEvent> { val out = events.toList(); events.clear(); return out }

    fun lobby(): LobbyView? = lobbyView
    fun trades(): List<NetMessage.TradeNotice> = trades.values.toList()
    fun quality(now: Long): Quality = stats.quality(now)

    fun view(now: Long): ClientView = ClientView(
        state, player, role, port.tick, estimatedServerTick, (estimatedServerTick - port.tick).coerceAtLeast(0L), lobbyView,
        stats.quality(now), stats.rttMs, stats.lossPpm(), inFlight.size, desyncCount, resyncCount,
        receiver.received, receiver.total, trades.values.count { it.state.open },
    )

    /** Does all the work of a moment: reads the network, applies commits, then retries whatever is overdue. */
    fun poll(now: Long) {
        if (state == ClientState.IDLE || state == ClientState.CLOSED || state == ClientState.FAILED) {
            transport.receive()
            return
        }
        for (d in transport.receive()) {
            if (d.from != server) continue
            val decoded = MessageCodec.decode(d.bytes)
            if (decoded is Decoded.Ok) { lastHeard = now; stats.heard(now); onMessage(decoded.packet.message, now) }
        }
        applyCommits(now)
        timers(now)
    }

    /** Queues a command to run a few ticks from now; returns its number, or null when commands cannot be sent. */
    fun submit(command: Command, now: Long): Long? {
        if (state != ClientState.RUNNING || role != Role.PLAYER || inFlight.size >= config.maxInFlight) return null
        val payload = CommandPayload.encode(command)
        if (payload.size > NetLimits.MAX_COMMAND_BYTES) return null
        val seq = ++commandSeq
        val entry = InFlight(payload, 0L, ByteArray(0), now, 1)
        inFlight[seq] = entry
        sendSubmit(seq, entry, estimatedServerTick + commandDelay + extraDelay, now)
        return seq
    }

    fun requestLobby(action: LobbyAction, now: Long, target: PlayerId = PlayerId.SERVER, arg: Long = 0L) {
        if (state == ClientState.IDLE || state == ClientState.CLOSED || state == ClientState.FAILED) return
        val bytes = send(NetMessage.LobbyRequest(action, target, arg))
        if (action == LobbyAction.LEAVE) { state = ClientState.CLOSED; return }
        if (action != LobbyAction.ACK_STATE) pendingLobby = PendingLobby(bytes, frameSeq, now)
    }

    fun ready(now: Long, value: Boolean = true) = requestLobby(if (value) LobbyAction.READY else LobbyAction.UNREADY, now)
    fun startGame(now: Long) = requestLobby(LobbyAction.START, now)
    fun leave(now: Long) = requestLobby(LobbyAction.LEAVE, now)

    fun say(text: String) { if (live()) send(NetMessage.Chat(ChatKind.TEXT, 0, text)) }
    fun emote(id: Int) { if (live()) send(NetMessage.Chat(ChatKind.EMOTE, id, "")) }

    fun offerTrade(counterparty: PlayerId, give: List<TradeAsset>, want: List<TradeAsset>, ttlTicks: Int) {
        if (live()) send(NetMessage.TradeRequest(TradeAction.OFFER, TradeId(0), counterparty, give, want, ttlTicks))
    }

    fun answerTrade(trade: TradeId, action: TradeAction) {
        if (live()) send(NetMessage.TradeRequest(action, trade, PlayerId.SERVER, emptyList(), emptyList(), 0))
    }

    private fun live() = state == ClientState.RUNNING || state == ClientState.LOBBY

    // ---- sending ----

    private fun send(m: NetMessage): ByteArray {
        val bytes = MessageCodec.encode(++frameSeq, m)
        transport.send(server, bytes)
        return bytes
    }

    private fun sendFreshHello(now: Long) {
        val hello = NetMessage.Hello(NetLimits.PROTOCOL, NetLimits.MIN_PROTOCOL, config.capabilities, config.name, config.session, port.fingerprint, config.spectate, null)
        resuming = false
        helloBytes = send(hello); helloAt = now; helloAttempts++
    }

    private fun sendResumeHello(now: Long) {
        nonce++
        val proof = Signing.resumeProof(key, config.session, player, nonce, port.tick)
        val hello = NetMessage.Hello(NetLimits.PROTOCOL, NetLimits.MIN_PROTOCOL, config.capabilities, config.name, config.session, port.fingerprint, role == Role.SPECTATOR, ResumeInfo(player, nonce, port.tick, proof))
        resuming = true
        helloBytes = send(hello); helloAt = now; helloAttempts++
    }

    private fun sendSubmit(seq: Long, entry: InFlight, target: Long, now: Long) {
        entry.target = target
        val mac = Signing.commandMac(key, config.session, player, seq, target, entry.payload)
        entry.bytes = send(NetMessage.Submit(seq, target, entry.payload, mac))
        entry.sentAt = now
    }

    // ---- receiving ----

    private fun onMessage(m: NetMessage, now: Long) {
        when (m) {
            is NetMessage.Welcome -> onWelcome(m, now)
            is NetMessage.Error -> onError(m, now)
            is NetMessage.Bye -> { state = ClientState.CLOSED; events += ClientEvent.Closed(m.reason) }
            is NetMessage.Ack -> inFlight.remove(m.seq)?.let { events += ClientEvent.Accepted(m.seq, m.targetTick) }
            is NetMessage.Reject -> onReject(m, now)
            is NetMessage.Commit -> onCommit(m)
            is NetMessage.HashCheckpoint -> onCheckpoint(m, now)
            is NetMessage.DesyncNotice -> onDesyncNotice(m, now)
            is NetMessage.SnapshotHeader -> onSnapshotHeader(m, now)
            is NetMessage.SnapshotChunk -> onSnapshotChunk(m, now)
            is NetMessage.Ping -> send(NetMessage.Pong(m.nonce, m.sentAt, port.tick))
            is NetMessage.Pong -> { stats.ponged(m.nonce, now); serverTick = maxOf(serverTick, m.serverTick) }
            is NetMessage.LobbyReply -> if (pendingLobby?.frame == m.requestSeq) pendingLobby = null
            is NetMessage.LobbyState -> onLobbyState(m, now)
            is NetMessage.ChatBroadcast -> {
                chat.add(ChatLine(m.tick, m.from, m.chatKind, m.emote, m.text))
                events += ClientEvent.Chat(m)
            }
            is NetMessage.TradeNotice -> onTradeNotice(m)
            else -> Unit
        }
    }

    private fun onWelcome(w: NetMessage.Welcome, now: Long) {
        if (state != ClientState.CONNECTING && state != ClientState.RECONNECTING) return
        if (w.session != config.session) return
        val wasReconnect = state == ClientState.RECONNECTING
        player = w.player
        role = w.role
        if (w.token.any { it.toInt() != 0 }) token = w.token.copyOf()
        key = Signing.deriveKey(token, config.session, player)
        commandDelay = w.commandDelay
        hashInterval = w.hashInterval
        serverTick = maxOf(serverTick, w.tick)
        helloBytes = null
        lastProgress = now
        if (!wasReconnect) events += ClientEvent.Joined(player, role)
        if (wasReconnect && !seated && w.tick > 0L && !w.needsSnapshot) { seated = true; chainPrev = w.chain }
        if (w.needsSnapshot || (seated && (w.tick != port.tick || w.chain != chainPrev))) {
            startSnapshot(if (wasReconnect) SnapshotReason.RECONNECT else SnapshotReason.JOIN, now)
            if (wasReconnect) events += ClientEvent.Reconnected(port.tick)
            return
        }
        state = if (seated) ClientState.RUNNING else ClientState.LOBBY
        if (wasReconnect) events += ClientEvent.Reconnected(port.tick)
        if (state == ClientState.RUNNING) requestCommits(now)
    }

    private fun onError(e: NetMessage.Error, now: Long) {
        if (e.error == NetError.NOT_JOINED && token.isNotEmpty() && state != ClientState.RECONNECTING) {
            beginReconnect(now, "the server no longer knows this connection")
            return
        }
        if (e.error.fatal && (state == ClientState.CONNECTING || state == ClientState.RECONNECTING)) {
            state = ClientState.FAILED
            events += ClientEvent.Refused(e.error, e.detail)
            return
        }
        pendingLobby = null
        events += ClientEvent.ServerError(e.error, e.detail)
    }

    private fun onReject(r: NetMessage.Reject, now: Long) {
        val entry = inFlight[r.seq] ?: return
        serverTick = maxOf(serverTick, r.serverTick)
        if (r.error == NetError.TOO_LATE && entry.attempts < config.maxSubmitAttempts) {
            inFlight.remove(r.seq)
            if (extraDelay < config.maxExtraDelay) extraDelay++
            val seq = ++commandSeq
            entry.attempts++
            inFlight[seq] = entry
            sendSubmit(seq, entry, maxOf(r.serverTick, estimatedServerTick) + commandDelay + extraDelay, now)
            return
        }
        inFlight.remove(r.seq)
        events += ClientEvent.Rejected(r.seq, r.error, r.rule)
    }

    private fun onTradeNotice(n: NetMessage.TradeNotice) {
        trades[n.trade] = n
        while (trades.size > MAX_TRADES) {
            val gone = trades.entries.firstOrNull { !it.value.state.open }?.key ?: trades.firstKey()
            trades.remove(gone)
        }
        events += ClientEvent.Trade(n)
    }

    private fun onLobbyState(s: NetMessage.LobbyState, now: Long) {
        send(NetMessage.LobbyRequest(LobbyAction.ACK_STATE, PlayerId.SERVER, s.version))
        val current = lobbyView
        if (current != null && s.version <= current.version) return
        lobbyView = LobbyView(s.version, s.phase, s.host, s.members)
        s.members.firstOrNull { it.id == player }?.let { role = it.role }
        events += ClientEvent.LobbyChanged(s.version)
        when (s.phase) {
            LobbyPhase.CLOSED -> { state = ClientState.CLOSED; events += ClientEvent.Closed(ByeReason.SHUTDOWN) }
            LobbyPhase.RUNNING -> if (state == ClientState.LOBBY && !seated) beginGame(s.members, now)
            LobbyPhase.OPEN -> Unit
        }
    }

    private fun beginGame(members: List<MemberInfo>, now: Long) {
        Seating.seat(port, Seating.fromMembers(members))
        seated = true
        chainPrev = 0L
        state = ClientState.RUNNING
        lastProgress = now
        events += ClientEvent.GameStarted(port.tick)
        afterTick(now)
    }

    // ---- commits ----

    private fun onCommit(c: NetMessage.Commit) {
        serverTick = maxOf(serverTick, c.tick + 1)
        if (c.tick < port.tick || c.tick >= port.tick + config.bufferAhead) return
        buffer.putIfAbsent(c.tick, c)
    }

    private fun applyCommits(now: Long) {
        if (state != ClientState.RUNNING) return
        var n = 0
        while (n < config.maxApplyPerPoll) {
            val c = buffer[port.tick] ?: break
            val expected = CommitChain.next(chainPrev, c.tick, c.entries, c.transfers)
            if (expected != c.chain) {
                buffer.remove(c.tick)
                beginResync(c.tick, "the commit chain broke", SnapshotReason.DESYNC, now)
                return
            }
            buffer.remove(c.tick)
            if (c.transfers.isNotEmpty()) port.applyTransfers(c.transfers)
            for (e in c.entries) {
                val parsed = CommandPayload.parse(e.payload)
                if (parsed is ParsedCommand.Ok) port.enqueue(e.player, parsed.command)
            }
            port.advance()
            chainPrev = c.chain
            lastProgress = now
            n++
            afterTick(now)
            if (state != ClientState.RUNNING) return
        }
        while (buffer.isNotEmpty() && buffer.firstKey() < port.tick) buffer.pollFirstEntry()
    }

    private fun afterTick(now: Long) {
        val t = port.tick
        if (t % hashInterval != 0L) return
        val h = port.stateHash()
        myHashes[t] = h
        while (myHashes.size > HASH_MEMORY) myHashes.pollFirstEntry()
        send(NetMessage.HashReport(t, h))
        val theirs = checkpoints[t] ?: return
        if (!theirs.contentEquals(h)) beginResync(t, "state differs from the host's", SnapshotReason.DESYNC, now)
    }

    private fun onCheckpoint(c: NetMessage.HashCheckpoint, now: Long) {
        serverTick = maxOf(serverTick, c.tick)
        checkpoints[c.tick] = c.hash
        while (checkpoints.size > HASH_MEMORY) checkpoints.pollFirstEntry()
        val mine = myHashes[c.tick] ?: return
        if (state == ClientState.RUNNING && !mine.contentEquals(c.hash)) beginResync(c.tick, "state differs from the host's", SnapshotReason.DESYNC, now)
    }

    private fun onDesyncNotice(n: NetMessage.DesyncNotice, now: Long) {
        if (state != ClientState.RUNNING) return
        val mine = myHashes[n.tick]
        if (mine != null && mine.contentEquals(n.serverHash)) return
        beginResync(n.tick, "the host reported a mismatch", SnapshotReason.DESYNC, now)
    }

    private fun requestCommits(now: Long) {
        val need = when {
            buffer.isNotEmpty() && buffer.firstKey() > port.tick -> buffer.firstKey() - port.tick
            else -> estimatedServerTick - port.tick
        }.coerceIn(1L, NetLimits.MAX_RESEND_TICKS.toLong()).toInt()
        send(NetMessage.Resend(port.tick, need))
        lastResendAt = now
    }

    // ---- snapshots ----

    private fun beginResync(tick: Long, why: String, reason: SnapshotReason, now: Long) {
        if (state == ClientState.SYNCING) return
        desyncCount++
        events += ClientEvent.Desync(tick, why)
        startSnapshot(reason, now)
    }

    private fun startSnapshot(reason: SnapshotReason, now: Long) {
        state = ClientState.SYNCING
        snapshotReason = reason
        snapshotAttempts = 0
        receiver.reset()
        askSnapshot(now)
    }

    private fun askSnapshot(now: Long) {
        val resume = receiver.received > 0
        send(NetMessage.SnapshotRequest(snapshotReason, if (resume) receiver.snapshotId else 0L, if (resume) receiver.firstMissing() else 0))
        snapshotAskedAt = now
        snapshotProgressAt = now
    }

    private fun onSnapshotHeader(h: NetMessage.SnapshotHeader, now: Long) {
        if (state != ClientState.SYNCING) return
        val r = receiver.onHeader(h)
        snapshotProgressAt = now
        r.ack?.let { send(it) }
    }

    private fun onSnapshotChunk(c: NetMessage.SnapshotChunk, now: Long) {
        if (state != ClientState.SYNCING) return
        val r = receiver.onChunk(c)
        if (r.ack != null) { send(r.ack); snapshotProgressAt = now }
        r.completed?.let { finishSnapshot(it, now) }
        if (r.error == NetError.BAD_CHECKSUM && receiver.snapshotId == 0L) retrySnapshot(now)
    }

    private fun finishSnapshot(s: ReceivedSnapshot, now: Long) {
        receiver.reset()
        val restored = port.restore(s.bytes)
        if (restored is Outcome.Rejected || !port.stateHash().contentEquals(s.stateHash) || port.tick != s.tick) {
            retrySnapshot(now)
            return
        }
        chainPrev = s.chain
        seated = true
        myHashes.clear()
        while (checkpoints.isNotEmpty() && checkpoints.firstKey() < port.tick) checkpoints.pollFirstEntry()
        while (buffer.isNotEmpty() && buffer.firstKey() < port.tick) buffer.pollFirstEntry()
        serverTick = maxOf(serverTick, port.tick)
        state = ClientState.RUNNING
        lastProgress = now
        resyncCount++
        events += ClientEvent.Resynced(port.tick)
        requestCommits(now)
        afterTick(now)
        applyCommits(now)
    }

    private fun retrySnapshot(now: Long) {
        receiver.reset()
        if (++snapshotAttempts > config.maxSnapshotAttempts) {
            state = ClientState.FAILED
            events += ClientEvent.Refused(NetError.SNAPSHOT_UNAVAILABLE, "could not obtain a usable snapshot")
            return
        }
        askSnapshot(now)
    }

    // ---- timers ----

    private fun beginReconnect(now: Long, why: String) {
        if (state == ClientState.RECONNECTING || token.isEmpty()) {
            if (token.isEmpty()) { state = ClientState.FAILED; events += ClientEvent.Disconnected(why) }
            return
        }
        state = ClientState.RECONNECTING
        reconnectSince = now
        helloAttempts = 0
        receiver.reset()
        events += ClientEvent.Disconnected(why)
        sendResumeHello(now)
    }

    private fun timers(now: Long) {
        when (state) {
            ClientState.CONNECTING -> if (now - helloAt >= config.helloRetryMs) {
                if (helloAttempts >= config.maxHelloAttempts) { state = ClientState.FAILED; events += ClientEvent.Refused(NetError.SESSION_UNKNOWN, "no answer from the host") }
                else helloBytes?.let { transport.send(server, it); helloAt = now; helloAttempts++ }
            }
            ClientState.RECONNECTING -> if (now - helloAt >= config.helloRetryMs) {
                if (now - reconnectSince > config.reconnectWindowMs) { state = ClientState.FAILED; events += ClientEvent.Refused(NetError.SESSION_UNKNOWN, "could not reconnect in time") }
                else sendResumeHello(now)
            }
            ClientState.LOBBY, ClientState.RUNNING, ClientState.SYNCING -> live(now)
            else -> Unit
        }
    }

    private fun live(now: Long) {
        if (now - lastHeard > config.silenceTimeoutMs) { beginReconnect(now, "no word from the host"); return }
        if (lastPingAt == Long.MIN_VALUE || now - lastPingAt >= config.pingIntervalMs) {
            lastPingAt = now
            val n = ++pingNonce
            stats.pinged(n, now)
            send(NetMessage.Ping(n, now))
        }
        pendingLobby?.let { p -> if (now - p.sentAt >= config.resendAfterMs) { transport.send(server, p.bytes); p.sentAt = now } }
        when (state) {
            ClientState.RUNNING -> {
                val behind = estimatedServerTick - port.tick
                val gap = buffer.isNotEmpty() && buffer.firstKey() > port.tick
                if (behind > config.lagResyncTicks) { beginResync(port.tick, "too far behind to catch up", SnapshotReason.LAG, now); return }
                if ((gap || behind > 0L && now - lastProgress >= config.resendAfterMs) && now - lastResendAt >= config.resendAfterMs) requestCommits(now)
                retrySubmits(now)
            }
            ClientState.SYNCING -> {
                if (now - snapshotProgressAt >= config.snapshotStallMs) {
                    if (receiver.snapshotId == 0L) retrySnapshot(now)
                    else { if (++snapshotAttempts > config.maxSnapshotAttempts) { state = ClientState.FAILED; events += ClientEvent.Refused(NetError.SNAPSHOT_UNAVAILABLE, "the snapshot stalled") } else askSnapshot(now) }
                }
            }
            else -> Unit
        }
    }

    private fun retrySubmits(now: Long) {
        for ((seq, e) in inFlight.entries.toList()) {
            if (now - e.sentAt < config.submitRetryMs) continue
            if (e.attempts >= config.maxSubmitAttempts) { inFlight.remove(seq); events += ClientEvent.Expired(seq); continue }
            e.attempts++
            e.sentAt = now
            transport.send(server, e.bytes)
        }
    }

    companion object {
        private const val HASH_MEMORY = 64
        private const val MAX_TRADES = 50
    }
}
