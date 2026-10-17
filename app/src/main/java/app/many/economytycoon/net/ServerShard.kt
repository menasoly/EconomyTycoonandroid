package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.Rules

/**
 * The authoritative host of one game. It owns the simulation, decides what every player's command is worth and
 * runs the clock: each tick it fixes the commands that will run, sends them to everyone and then applies them to
 * its own copy. Clients never change the game directly; they send signed requests and apply the commits they
 * receive, so all copies stay identical as long as the game is deterministic, and the server notices when they
 * do not.
 *
 * Nothing here blocks or reads a clock. The caller feeds it time in network milliseconds through [poll] and
 * [stepTick], which makes every behaviour, from grace periods to retransmission, reproducible in tests.
 */
class ServerShard(
    val config: ShardConfig,
    private val port: SimulationPort,
    private val transport: Transport,
    tokens: TokenSource,
) {
    val lobby = Lobby(config.lobby)
    val identities = IdentityBook(config.session, tokens)
    val antiCheat = AntiCheat(config.antiCheat)
    val chat = ChatModerator(config.chat)
    val chatHistory = ChatHistory(config.chat.historySize)
    val escrow = EscrowBook(config.escrow)
    val log = CommitLog()
    val seasonLedger = SeasonLedger()
    val season = SeasonTracker(seasonLedger, config.scoring, config.seasonLengthTicks)

    private val gate = CommandGate(config.session, config.gate)
    private val snapshots = SnapshotService(port, log)
    private val offer = ServerOffer(config.session, port.fingerprint, capabilities = config.capabilities, required = config.requiredCapabilities)

    private val byPlayer = java.util.TreeMap<PlayerId, ServerConn>()
    private val byEndpoint = java.util.TreeMap<EndpointId, ServerConn>()
    private val inbound = java.util.TreeMap<EndpointId, RateLimiter>()
    private val bannedEndpoints = java.util.TreeSet<EndpointId>()
    private val pending = java.util.TreeMap<Long, ArrayList<CommitEntry>>()
    private val hashes = java.util.TreeMap<Long, ByteArray>()
    private val reports = java.util.TreeMap<Long, java.util.TreeMap<PlayerId, ByteArray>>()
    private val accused = HashSet<Pair<Long, PlayerId>>()
    private val desyncReports = ArrayList<DesyncReport>()
    private val seats = ArrayList<SeatRecord>()
    private val checkpoints = java.util.TreeMap<Long, ByteArray>()
    private var metrics = ShardMetrics()
    private var lastNow = 0L

    val tick: Long get() = port.tick
    fun metrics(): ShardMetrics = metrics
    fun desyncReports(): List<DesyncReport> = desyncReports.toList()
    fun isConnected(player: PlayerId): Boolean = byPlayer.containsKey(player)

    // ---- the receive loop ----

    /** Handles everything that has arrived and does the periodic work: pings, timeouts, resends. */
    fun poll(now: Long) {
        lastNow = now
        for (d in transport.receive()) onDatagram(d, now)
        housekeeping(now)
    }

    private fun onDatagram(d: Datagram, now: Long) {
        metrics = metrics.copy(datagramsIn = metrics.datagramsIn + 1)
        if (d.from in bannedEndpoints) return
        val limiter = inbound.getOrPut(d.from) { RateLimiter(config.inboundBurst, config.inboundPerSecond) }
        if (!limiter.tryTake(now)) {
            metrics = metrics.copy(floodDropped = metrics.floodDropped + 1)
            byEndpoint[d.from]?.let { offend(it.player, Offense.RATE_LIMITED, "flood", now) }
            return
        }
        when (val decoded = MessageCodec.decode(d.bytes)) {
            is Decoded.Bad -> onBad(d.from, decoded, now)
            is Decoded.Ok -> onPacket(d.from, decoded.packet, now)
        }
    }

    private fun onBad(from: EndpointId, bad: Decoded.Bad, now: Long) {
        val conn = byEndpoint[from]
        if (bad.error in DAMAGE) {
            // a frame whose header or checksum fails could be line noise as easily as mischief, so it costs nothing
            metrics = metrics.copy(damaged = metrics.damaged + 1)
            if (bad.error == DecodeError.BAD_VERSION && conn == null) {
                transport.send(from, MessageCodec.encode(0, NetMessage.Error(NetError.UNSUPPORTED_VERSION, "this host speaks protocol ${NetLimits.MIN_PROTOCOL}..${NetLimits.PROTOCOL}")))
            }
            return
        }
        metrics = metrics.copy(malformed = metrics.malformed + 1)
        if (conn != null) offend(conn.player, if (bad.error == DecodeError.TOO_LARGE) Offense.OVERSIZED else Offense.MALFORMED, bad.error.name, now)
    }

    private fun onPacket(from: EndpointId, packet: Packet, now: Long) {
        val conn = byEndpoint[from]
        val msg = packet.message
        if (msg is NetMessage.Hello) { onHello(from, conn, packet.seq, msg, now); return }
        if (conn == null) {
            transport.send(from, MessageCodec.encode(0, NetMessage.Error(NetError.NOT_JOINED, "say hello first")))
            return
        }
        conn.stats.heard(now)
        when (conn.inbound.accept(packet.seq)) {
            SeqVerdict.FRESH -> Unit
            SeqVerdict.DUPLICATE -> {
                metrics = metrics.copy(duplicatesDropped = metrics.duplicatesDropped + 1)
                conn.replies[packet.seq]?.forEach { transport.send(conn.endpoint, it) }
                return
            }
            SeqVerdict.STALE, SeqVerdict.AHEAD -> return
        }
        dispatch(conn, packet.seq, msg, now)
    }

    private fun dispatch(c: ServerConn, seq: Long, msg: NetMessage, now: Long) {
        when (msg) {
            is NetMessage.Submit -> onSubmit(c, seq, msg, now)
            is NetMessage.HashReport -> onHashReport(c, msg, now)
            is NetMessage.SnapshotRequest -> onSnapshotRequest(c, seq, msg, now)
            is NetMessage.SnapshotAck -> c.sender?.onAck(msg, now)
            is NetMessage.Ping -> send(c, NetMessage.Pong(msg.nonce, msg.sentAt, port.tick))
            is NetMessage.Pong -> { c.stats.ponged(msg.nonce, now); c.reportedTick = maxOf(c.reportedTick, msg.serverTick.coerceAtMost(port.tick)) }
            is NetMessage.LobbyRequest -> onLobbyRequest(c, seq, msg, now)
            is NetMessage.Chat -> onChat(c, seq, msg, now)
            is NetMessage.TradeRequest -> onTrade(c, seq, msg, now)
            is NetMessage.Resend -> onResend(c, msg)
            is NetMessage.Bye -> disconnect(c, now, leave = true)
            else -> offend(c.player, Offense.MALFORMED, "unexpected ${msg.kind}", now)
        }
    }

    // ---- sending ----

    private fun send(c: ServerConn, m: NetMessage): ByteArray {
        val bytes = MessageCodec.encode(c.nextSeq(), m, c.protocol)
        transport.send(c.endpoint, bytes)
        return bytes
    }

    private fun reply(c: ServerConn, requestSeq: Long, vararg messages: NetMessage) {
        c.remember(requestSeq, messages.map { send(c, it) }, config.replyMemory)
    }

    private fun broadcast(m: NetMessage, include: (ServerConn) -> Boolean = { true }) {
        for (c in byPlayer.values) if (include(c)) send(c, m)
    }

    private fun refuse(c: ServerConn, seq: Long, error: NetError, detail: String) = reply(c, seq, NetMessage.Error(error, detail.take(NetLimits.MAX_DETAIL_CHARS)))

    // ---- joining ----

    private fun onHello(from: EndpointId, existing: ServerConn?, seq: Long, hello: NetMessage.Hello, now: Long) {
        val again = hello.resume
        if (existing != null && (again == null || again.player == existing.player && again.nonce == existing.helloNonce)) {
            // a repeated hello from a connected endpoint gets the same welcome again
            existing.stats.heard(now)
            existing.welcome?.let { transport.send(existing.endpoint, it) }
            return
        }
        fun fail(e: NetError, detail: String) { transport.send(from, MessageCodec.encode(0, NetMessage.Error(e, detail.take(NetLimits.MAX_DETAIL_CHARS)))) }
        val agreed = when (val n = Handshake.negotiate(hello, offer)) {
            is Negotiation.Agreed -> n
            is Negotiation.Refused -> { fail(n.error, n.detail); return }
        }
        val resume = hello.resume
        if (resume != null) { onResume(from, seq, hello, resume, agreed, now); return }
        if (lobby.phase == LobbyPhase.CLOSED) { fail(NetError.SESSION_UNKNOWN, "the session has closed"); return }
        val name = NameRules.clean(hello.name) ?: run { fail(NetError.BAD_NAME, "choose a different name"); return }
        if (lobby.members().size >= config.lobby.maxPlayers + config.lobby.maxSpectators || identities.size >= config.maxIdentities) { fail(NetError.SESSION_FULL, "no room"); return }
        if (lobby.members().any { it.name.equals(name, ignoreCase = true) }) { fail(NetError.NAME_TAKEN, "the name is taken"); return }
        val (record, token) = identities.enroll(name)
        val joined = lobby.join(record.id, name, now, hello.spectate)
        if (joined is Outcome.Rejected) {
            fail(lobbyError(joined.rejection), joined.rejection.message)
            return
        }
        val member = lobby.member(record.id) ?: return
        val needsSnapshot = lobby.phase == LobbyPhase.RUNNING
        val conn = bind(from, record.id, member.role, agreed, now)
        conn.inbound.restore(seq, 1L)
        sendWelcome(conn, token, needsSnapshot)
        pushLobby(now, force = true)
    }

    private fun onResume(from: EndpointId, seq: Long, hello: NetMessage.Hello, resume: ResumeInfo, agreed: Negotiation.Agreed, now: Long) {
        fun fail(e: NetError, detail: String) { transport.send(from, MessageCodec.encode(0, NetMessage.Error(e, detail))) }
        val member = lobby.member(resume.player)
        if (member == null) { fail(NetError.UNKNOWN_PLAYER, "you are not in this session"); return }
        if (identities.isBanned(resume.player) || lobby.isBanned(resume.player)) { fail(NetError.BANNED, "banned from this session"); return }
        if (!identities.acceptResume(resume.player, resume.nonce, resume.lastTick, resume.proof)) { fail(NetError.TOKEN_INVALID, "the proof does not match"); return }
        byPlayer[resume.player]?.let { old -> byEndpoint.remove(old.endpoint); byPlayer.remove(old.player) }
        if (member.presence != Presence.CONNECTED) lobby.returned(resume.player)
        val conn = bind(from, resume.player, member.role, agreed, now)
        conn.helloNonce = resume.nonce
        conn.inbound.restore(seq, 1L)
        val canCatchUp = lobby.phase != LobbyPhase.RUNNING || (resume.lastTick in 0..port.tick && port.tick - resume.lastTick <= MAX_CATCH_UP && resume.lastTick <= log.size)
        conn.reportedTick = resume.lastTick
        sendWelcome(conn, ByteArray(NetLimits.TOKEN_BYTES), needsSnapshot = !canCatchUp, resumeFrom = resume.lastTick)
        pushLobby(now, force = true)
    }

    private fun bind(from: EndpointId, player: PlayerId, role: Role, agreed: Negotiation.Agreed, now: Long): ServerConn {
        val conn = ServerConn(from, player, role, agreed.capabilities, agreed.protocol)
        conn.stats.heard(now)
        conn.joinedAt = now
        conn.reportedTick = port.tick
        byPlayer[player] = conn
        byEndpoint[from] = conn
        return conn
    }

    private fun sendWelcome(c: ServerConn, token: ByteArray, needsSnapshot: Boolean, resumeFrom: Long = -1L) {
        val tick = if (resumeFrom >= 0L) resumeFrom else port.tick
        val w = NetMessage.Welcome(
            c.protocol, c.caps, c.player, c.role, config.session, token, tick, config.commandDelay, config.hashInterval,
            needsSnapshot, config.seed, log.chainBefore(tick),
        )
        c.welcome = send(c, w)
    }

    private fun lobbyError(r: Rejection): NetError = when (r.rule) {
        Rules.LIMIT_EXCEEDED -> NetError.SESSION_FULL
        Rules.NAME_TAKEN -> NetError.NAME_TAKEN
        Rules.INVALID_ARGUMENT -> NetError.BAD_NAME
        Rules.REGULATION -> NetError.BANNED
        Rules.NOT_OWNER -> NetError.NOT_HOST
        Rules.WRONG_STATE -> NetError.WRONG_PHASE
        Rules.UNKNOWN_ENTITY -> NetError.UNKNOWN_PLAYER
        else -> NetError.WRONG_PHASE
    }

    // ---- lobby ----

    private fun onLobbyRequest(c: ServerConn, seq: Long, req: NetMessage.LobbyRequest, now: Long) {
        val me = c.player
        if (req.action == LobbyAction.ACK_STATE) { c.lobbyAcked = maxOf(c.lobbyAcked, req.arg); return }
        val result: Outcome<List<LobbyEvent>> = when (req.action) {
            LobbyAction.READY -> lobby.setReady(me, true)
            LobbyAction.UNREADY -> lobby.setReady(me, false)
            LobbyAction.LEAVE -> { val r = lobby.leave(me); disconnect(c, now, leave = true, alreadyLeft = true); r }
            LobbyAction.KICK -> lobby.kick(me, req.target).also { if (it is Outcome.Ok) afterKick(req.target, ban = true, now = now) }
            LobbyAction.START -> lobby.start(me)
            LobbyAction.SPECTATE -> lobby.setSpectating(me, true)
            LobbyAction.PLAY -> lobby.setSpectating(me, false)
            LobbyAction.TRANSFER_HOST -> lobby.transferHost(me, req.target)
            LobbyAction.ACK_STATE -> Outcome.ok(emptyList())
        }
        when (result) {
            is Outcome.Rejected -> refuse(c, seq, lobbyError(result.rejection), result.rejection.message)
            is Outcome.Ok -> {
                for (e in result.value) onLobbyEvent(e, now)
                lobby.member(me)?.let { c.role = it.role }
                if (byPlayer[me] === c) reply(c, seq, NetMessage.LobbyReply(seq, lobby.view().version))
                pushLobby(now, force = true)
            }
        }
    }

    private fun onLobbyEvent(e: LobbyEvent, now: Long) {
        when (e) {
            is LobbyEvent.Started -> startGame(e.plan, now)
            is LobbyEvent.Closed -> broadcast(NetMessage.Bye(ByeReason.SHUTDOWN))
            else -> Unit
        }
    }

    private fun afterKick(target: PlayerId, ban: Boolean, now: Long) {
        if (ban) identities.ban(target)
        byPlayer[target]?.let { c ->
            send(c, NetMessage.Bye(ByeReason.KICKED))
            if (ban) bannedEndpoints += c.endpoint
            unbind(c)
        }
        gate.forget(target)
    }

    private fun startGame(plan: StartPlan, now: Long) {
        for (r in Seating.seat(port, plan.seats)) {
            r.name?.let { seats += SeatRecord(r.player, it) }
            lobby.assignCompany(r.player, r.company)
        }
        season.begin(port.statements(0L))
        noteHash(port.tick, forceBroadcast = false)
        for (c in byPlayer.values) c.reportedTick = port.tick
    }

    private fun pushLobby(now: Long, force: Boolean) {
        val view = lobby.view()
        val msg = view.toMessage()
        for (c in byPlayer.values) {
            if (!force && c.lobbyAcked >= view.version) continue
            if (force || now - c.lobbySentAt >= config.lobbyResendMs) { send(c, msg); c.lobbySentAt = now }
        }
    }

    // ---- commands ----

    private fun onSubmit(c: ServerConn, seq: Long, s: NetMessage.Submit, now: Long) {
        val player = c.player
        if (c.role != Role.PLAYER) {
            reply(c, seq, NetMessage.Reject(s.seq, NetError.SPECTATOR, "spectator", port.tick))
            offend(player, Offense.SPECTATOR_COMMAND, "spectator submitted a command", now)
            return
        }
        if (lobby.phase != LobbyPhase.RUNNING) {
            reply(c, seq, NetMessage.Reject(s.seq, NetError.WRONG_PHASE, "not_running", port.tick))
            return
        }
        val key = identities.get(player)?.key ?: return
        val owned = port.companiesOf(player).toHashSet()
        when (val r = gate.evaluate(player, key, s, now, port.tick) { it in owned }) {
            is GateResult.Accepted -> {
                pending.getOrPut(s.targetTick) { ArrayList() } += r.entry
                metrics = metrics.copy(commandsAccepted = metrics.commandsAccepted + 1)
                reply(c, seq, r.reply)
            }
            is GateResult.Repeat -> r.reply?.let { reply(c, seq, it) }
            is GateResult.Refused -> {
                metrics = metrics.copy(commandsRefused = metrics.commandsRefused + 1)
                reply(c, seq, r.reply)
                offenseFor(r)?.let { offend(player, it, r.rule, now) }
            }
        }
    }

    private fun offenseFor(r: GateResult.Refused): Offense? = when (r.error) {
        NetError.BAD_SIGNATURE -> Offense.BAD_SIGNATURE
        NetError.RATE_LIMITED -> Offense.RATE_LIMITED
        NetError.STALE_SEQUENCE -> Offense.STALE_SEQUENCE
        NetError.SEQUENCE_AHEAD -> Offense.MALFORMED
        NetError.NOT_OWNER -> Offense.NOT_OWNER
        NetError.TOO_LARGE -> Offense.OVERSIZED
        NetError.INVALID_COMMAND -> if (r.rule == "impossible_value" || r.rule == "founding_not_allowed" || r.rule == "server_only") Offense.IMPOSSIBLE_COMMAND else Offense.MALFORMED
        else -> null
    }

    private fun offend(player: PlayerId, offense: Offense, detail: String, now: Long) {
        when (antiCheat.record(player, offense, port.tick, detail)) {
            Action.NONE -> Unit
            Action.WARN -> byPlayer[player]?.let { send(it, NetMessage.ChatBroadcast(PlayerId.SERVER, ChatKind.SYSTEM, 0, "Warning: unusual activity from your connection.", port.tick)) }
            Action.KICK -> expel(player, ban = false, now = now)
            Action.BAN -> expel(player, ban = true, now = now)
        }
    }

    /** Removes a participant on the server's own authority. */
    fun expel(player: PlayerId, ban: Boolean, now: Long = lastNow) {
        val result = lobby.expel(player, ban)
        if (result is Outcome.Ok) for (e in result.value) onLobbyEvent(e, now)
        afterKick(player, ban, now)
        pushLobby(now, force = true)
    }

    // ---- ticks ----

    /** Advances the game by one tick if the lobby is running and nobody is too far behind; null otherwise. */
    fun stepTick(now: Long): TickReport? {
        lastNow = now
        if (lobby.phase != LobbyPhase.RUNNING || seats.isEmpty() && lobby.players().isEmpty()) return null
        if (config.holdForSlowClients && laggards().isNotEmpty()) return null
        val t = port.tick
        escrow.beginTick(t)
        val transfers = if (port.supports(AssetKind.CASH)) escrow.drain() else emptyList()
        val entries = ordered(pending.remove(t).orEmpty(), t)
        val committed = log.append(t, entries, transfers)
        broadcast(committed.toMessage())
        metrics = metrics.copy(commitsSent = metrics.commitsSent + 1)

        val results = if (transfers.isEmpty()) emptyList() else port.applyTransfers(transfers)
        escrow.applied(results)
        for (e in entries) {
            val parsed = CommandPayload.parse(e.payload)
            if (parsed is ParsedCommand.Ok) port.enqueue(e.player, parsed.command)
        }
        for (a in port.advance()) {
            if (a.player in byPlayer) when (antiCheat.observe(a.player, a.accepted, t)) {
                Action.NONE -> Unit
                Action.WARN -> Unit
                Action.KICK -> expel(a.player, ban = false, now = now)
                Action.BAN -> expel(a.player, ban = true, now = now)
            }
        }
        gate.committed(t)
        antiCheat.advance(t)
        sendTradeNotices()
        val hash = noteHash(port.tick, forceBroadcast = false)
        season.advance(port.tick) { port.statements(season.clock.startTick) }?.let { announce(it) }
        return TickReport(t, entries.size, transfers.size, hash)
    }

    private fun announce(result: SeasonResult) {
        val winner = result.rows.firstOrNull()
        val text = if (winner == null) "Season ${result.season} finished." else "Season ${result.season} finished: ${winner.name} won with ${winner.total} points."
        val line = NetMessage.ChatBroadcast(PlayerId.SERVER, ChatKind.SYSTEM, 0, text.take(NetLimits.MAX_CHAT_CHARS), port.tick)
        chatHistory.add(ChatLine(port.tick, PlayerId.SERVER, ChatKind.SYSTEM, 0, line.text))
        broadcast(line)
    }

    private fun ordered(entries: List<CommitEntry>, tick: Long): List<CommitEntry> {
        if (entries.size < 2) return entries
        val seated = lobby.players().sortedBy { it.slot }.map { it.id }
        val n = maxOf(1, seated.size)
        fun rank(p: PlayerId): Long {
            val i = seated.indexOf(p)
            return if (i < 0) n + p.value else ((i + tick) % n)
        }
        return entries.sortedWith(compareBy<CommitEntry>({ rank(it.player) }, { it.player.value }, { it.seq }))
    }

    private fun noteHash(tick: Long, forceBroadcast: Boolean): ByteArray? {
        val due = tick % config.hashInterval == 0L || Capabilities.has(config.capabilities, Capabilities.EVERY_TICK_HASH) && false
        if (!due && !forceBroadcast) return null
        val h = port.stateHash()
        hashes[tick] = h
        checkpoints[tick] = h
        while (hashes.size > HASH_MEMORY) hashes.remove(hashes.firstKey())
        while (reports.size > HASH_MEMORY) reports.remove(reports.firstKey())
        broadcast(NetMessage.HashCheckpoint(tick, h)) { lobby.phase == LobbyPhase.RUNNING }
        return h
    }

    private fun laggards(): List<PlayerId> = byPlayer.values
        .filter { it.role == Role.PLAYER && port.tick - it.reportedTick > config.maxLagTicks }
        .map { it.player }

    // ---- hashes and desync ----

    private fun onHashReport(c: ServerConn, r: NetMessage.HashReport, now: Long) {
        if (r.tick > port.tick) { offend(c.player, Offense.MALFORMED, "hash for the future", now); return }
        c.reportedTick = maxOf(c.reportedTick, r.tick)
        reports.getOrPut(r.tick) { java.util.TreeMap() }[c.player] = r.hash
        val server = hashes[r.tick] ?: return
        if (!server.contentEquals(r.hash) && accused.add(r.tick to c.player)) {
            val analysis = DesyncAnalysis.analyse(r.tick, server, reports[r.tick].orEmpty())
            desyncReports += analysis
            while (desyncReports.size > MAX_DESYNC_REPORTS) desyncReports.removeAt(0)
            metrics = metrics.copy(desyncsFound = metrics.desyncsFound + 1)
            send(c, NetMessage.DesyncNotice(r.tick, server))
            if (c.player in analysis.culprits) offend(c.player, Offense.DESYNC, "state differs at tick ${r.tick}", now)
        }
    }

    // ---- snapshots and catching up ----

    private fun onSnapshotRequest(c: ServerConn, seq: Long, req: NetMessage.SnapshotRequest, now: Long) {
        if (lobby.phase != LobbyPhase.RUNNING) { refuse(c, seq, NetError.SNAPSHOT_UNAVAILABLE, "no game is running"); return }
        val resumed = if (req.snapshotId != 0L) snapshots.byId(req.snapshotId) else null
        if (resumed == null && c.lastSnapshotAt != Long.MIN_VALUE && now - c.lastSnapshotAt < config.snapshotMinIntervalMs) {
            refuse(c, seq, NetError.SERVER_BUSY, "wait a moment before asking again")
            return
        }
        val bundle = resumed ?: snapshots.current() ?: run { refuse(c, seq, NetError.SNAPSHOT_UNAVAILABLE, "the state could not be captured"); return }
        if (resumed == null) c.lastSnapshotAt = now
        val sender = SnapshotSender(bundle, config.snapshotWindow, config.snapshotRetransmitMs)
        sender.begin(if (resumed != null) req.resumeChunk else 0)
        c.sender = sender
        metrics = metrics.copy(snapshotsServed = metrics.snapshotsServed + 1)
        for (m in sender.pump(now)) send(c, m)
    }

    private fun onResend(c: ServerConn, r: NetMessage.Resend) {
        for (commit in log.range(r.fromTick, r.count.coerceIn(0, NetLimits.MAX_RESEND_TICKS))) send(c, commit.toMessage())
    }

    // ---- chat ----

    private fun onChat(c: ServerConn, seq: Long, m: NetMessage.Chat, now: Long) {
        if (!Capabilities.has(c.caps, Capabilities.CHAT)) { refuse(c, seq, NetError.MISSING_CAPABILITY, "chat was not negotiated"); return }
        when (val v = chat.check(c.player, m, now)) {
            is ChatVerdict.Allowed -> {
                chatHistory.add(ChatLine(port.tick, c.player, v.kind, v.emote, v.text))
                broadcast(NetMessage.ChatBroadcast(c.player, v.kind, v.emote, v.text, port.tick)) { Capabilities.has(it.caps, Capabilities.CHAT) }
            }
            is ChatVerdict.Blocked -> {
                refuse(c, seq, v.error, v.reason)
                if (v.strike) offend(c.player, Offense.CHAT_ABUSE, v.reason, now)
            }
        }
    }

    // ---- trade ----

    private val escrowView = object : EscrowView {
        override fun owns(player: PlayerId, company: Long) = port.companiesOf(player).contains(company)
        override fun supports(kind: AssetKind) = port.supports(kind)
        override fun available(company: Long, asset: TradeAsset) = port.available(company, asset)
    }

    private fun onTrade(c: ServerConn, seq: Long, r: NetMessage.TradeRequest, now: Long) {
        if (!Capabilities.has(c.caps, Capabilities.TRADE)) { refuse(c, seq, NetError.MISSING_CAPABILITY, "trading was not negotiated"); return }
        if (c.role != Role.PLAYER) { refuse(c, seq, NetError.SPECTATOR, "spectators cannot trade"); return }
        if (lobby.phase != LobbyPhase.RUNNING) { refuse(c, seq, NetError.WRONG_PHASE, "no game is running"); return }
        val outcome: Outcome<TradeId> = when (r.action) {
            TradeAction.OFFER -> {
                val mine = lobby.member(c.player)?.company ?: -1L
                val other = lobby.member(r.counterparty)
                if (other == null || other.role != Role.PLAYER || other.company < 0L) Outcome.reject(Rules.UNKNOWN_ENTITY, "no such player")
                else escrow.offer(escrowView, c.player, mine, other.id, other.company, r.give, r.want, port.tick, r.ttlTicks)
            }
            TradeAction.ACCEPT -> escrow.accept(escrowView, r.trade, c.player, port.tick).map { r.trade }
            TradeAction.DECLINE -> escrow.decline(r.trade, c.player).map { r.trade }
            TradeAction.CANCEL -> escrow.cancel(r.trade, c.player).map { r.trade }
        }
        when (outcome) {
            is Outcome.Rejected -> refuse(c, seq, NetError.TRADE_REFUSED, outcome.rejection.message)
            is Outcome.Ok -> {
                escrow.trade(outcome.value)?.let { reply(c, seq, it.notice()) }
                sendTradeNotices()
            }
        }
    }

    /** Tells both sides of every trade that changed; clients treat a notice as the latest state, so repeats are harmless. */
    private fun sendTradeNotices() {
        for (n in escrow.takeNotices()) {
            byPlayer[n.from]?.let { send(it, n) }
            byPlayer[n.to]?.let { send(it, n) }
        }
    }

    // ---- housekeeping ----

    private fun housekeeping(now: Long) {
        for (c in byPlayer.values.toList()) {
            if (now - c.stats.lastHeard > config.idleTimeoutMs) { disconnect(c, now, leave = false); continue }
            if (c.lastPingAt == Long.MIN_VALUE || now - c.lastPingAt >= config.pingIntervalMs) {
                c.lastPingAt = now
                val nonce = ++c.pingNonce
                c.stats.pinged(nonce, now)
                send(c, NetMessage.Ping(nonce, now))
            }
            c.sender?.let { s ->
                if (s.complete) c.sender = null else for (m in s.pump(now)) send(c, m)
            }
        }
        val events = lobby.expire(now)
        for (e in events) onLobbyEvent(e, now)
        pushLobby(now, force = events.isNotEmpty())
    }

    private fun disconnect(c: ServerConn, now: Long, leave: Boolean, alreadyLeft: Boolean = false) {
        unbind(c)
        if (leave) gate.forget(c.player)
        if (alreadyLeft) return
        val r = if (leave) lobby.leave(c.player) else lobby.dropped(c.player, now)
        if (r is Outcome.Ok) for (e in r.value) onLobbyEvent(e, now)
        pushLobby(now, force = true)
    }

    private fun unbind(c: ServerConn) {
        if (byPlayer[c.player] === c) byPlayer.remove(c.player)
        if (byEndpoint[c.endpoint] === c) byEndpoint.remove(c.endpoint)
    }

    // ---- views ----

    /** A snapshot of who is here and how their connection is doing. */
    fun status(now: Long = lastNow): ShardStatus {
        val rows = lobby.members().map { m ->
            val c = byPlayer[m.id]
            PlayerStatus(
                m.id, m.name, m.role, m.slot, m.company, m.presence, m.ready,
                c?.stats?.quality(now) ?: Quality.OFFLINE, c?.stats?.rttMs ?: -1L, c?.stats?.lossPpm() ?: 0,
                if (c == null || c.reportedTick < 0L) -1L else (port.tick - c.reportedTick).coerceAtLeast(0L),
                antiCheat.standing(m.id), antiCheat.score(m.id), chat.isMuted(m.id, now), m.id == lobby.hostId,
            )
        }
        return ShardStatus(port.tick, lobby.phase, lobby.hostId, rows, pending.values.sumOf { it.size }, escrow.open().size, desyncReports.size, season.clock)
    }

    /** The live season table over every company in the game. */
    fun leaderboard(): List<LeaderRow> = season.table(port.statements(season.clock.startTick))

    /** Everything needed to replay the game so far. */
    fun record(): SessionRecord {
        val copy = CommitLog()
        for (c in log.all()) copy.append(c.tick, c.entries, c.transfers)
        return SessionRecord(config.session, port.fingerprint, seats.toList(), copy, checkpoints.mapValues { it.value.copyOf() })
    }

    // ---- saving the host's own state ----

    /**
     * Writes everything the host knows apart from the game itself: who is enrolled and their keys, the lobby, open
     * trades, the season, the log of every commit and the hashes taken so far. The game is saved separately, by
     * its own snapshot. Connections are not saved; players come back through a resume.
     */
    fun writeState(w: ByteWriter) {
        w.writeVarInt(STATE_VERSION)
        identities.write(w); lobby.write(w); escrow.write(w); seasonLedger.write(w); season.write(w); log.write(w)
        w.writeVarInt(seats.size)
        for (s in seats) { w.writeVarLong(s.player.value); w.writeString(s.name) }
        w.writeVarInt(checkpoints.size)
        for ((t, h) in checkpoints) { w.writeVarLong(t); w.writeBytesRaw(h) }
    }

    /** Loads what [writeState] wrote; everyone who was connected is treated as dropped at [now] so they can resume. */
    fun readState(r: ByteReader, now: Long) {
        val version = r.readVarInt()
        if (version != STATE_VERSION) throw ByteReader.CorruptDataException("unsupported host state version $version")
        identities.read(r); lobby.read(r); escrow.read(r); seasonLedger.read(r); season.read(r); log.read(r)
        val seatCount = r.readVarInt()
        if (seatCount < 0 || seatCount > NetLimits.MAX_MEMBERS) throw ByteReader.CorruptDataException("implausible seat count $seatCount")
        seats.clear()
        repeat(seatCount) { seats += SeatRecord(PlayerId(r.readVarLong()), r.readString()) }
        val cps = r.readVarInt()
        if (cps < 0 || cps > MAX_CHECKPOINTS) throw ByteReader.CorruptDataException("implausible checkpoint count $cps")
        checkpoints.clear()
        repeat(cps) {
            val tick = r.readVarLong()
            if (r.remaining < NetLimits.HASH_BYTES) throw ByteReader.CorruptDataException("unexpected end of data")
            checkpoints[tick] = ByteArray(NetLimits.HASH_BYTES) { r.readByte().toByte() }
        }
        byPlayer.clear(); byEndpoint.clear(); pending.clear(); reports.clear(); accused.clear(); hashes.clear()
        if (checkpoints.isNotEmpty()) for ((t, h) in checkpoints.tailMap(checkpoints.lastKey() - HASH_MEMORY, true)) hashes[t] = h
        lastNow = now
        for (m in lobby.members().filter { it.presence == Presence.CONNECTED }) lobby.dropped(m.id, now)
    }

    /** A hash of [writeState], for checking that a restored host matches the one that was saved. */
    fun stateHash(): ByteArray = Hmac.sha256(ByteWriter().also { writeState(it) }.toByteArray())

    /** Tells everyone the host is going away. */
    fun shutdown() {
        broadcast(NetMessage.Bye(ByeReason.SHUTDOWN))
        lobby.close(PlayerId.SERVER, "server shut down")
    }

    companion object {
        private val DAMAGE = setOf(DecodeError.EMPTY, DecodeError.TOO_SHORT, DecodeError.BAD_MAGIC, DecodeError.BAD_VERSION, DecodeError.BAD_CHECKSUM)
        private const val STATE_VERSION = 1
        private const val MAX_CHECKPOINTS = 10_000_000
        private const val MAX_CATCH_UP = 512L
        private const val HASH_MEMORY = 64
        private const val MAX_DESYNC_REPORTS = 50
    }
}
