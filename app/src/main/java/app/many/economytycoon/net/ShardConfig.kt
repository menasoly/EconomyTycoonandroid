package app.many.economytycoon.net

data class ShardConfig(
    val session: SessionId,
    val seed: Long = 0L,
    /** Ticks between a command being sent and being applied; covers the time commits need to reach everyone. */
    val commandDelay: Int = 3,
    /** State hashes are exchanged every this many ticks. */
    val hashInterval: Int = 10,
    val lobby: LobbyConfig = LobbyConfig(),
    val gate: GateConfig = GateConfig(),
    val chat: ChatConfig = ChatConfig(),
    val antiCheat: AntiCheatConfig = AntiCheatConfig(),
    val escrow: EscrowConfig = EscrowConfig(),
    val capabilities: Long = Capabilities.ALL,
    val requiredCapabilities: Long = 0L,
    /** Wait for players who fall too far behind instead of running ahead of them. */
    val holdForSlowClients: Boolean = false,
    val maxLagTicks: Int = 60,
    /** A connection silent for this long (network ms) is treated as dropped. */
    val idleTimeoutMs: Long = 10_000L,
    val pingIntervalMs: Long = 1_000L,
    val lobbyResendMs: Long = 300L,
    val snapshotMinIntervalMs: Long = 500L,
    val snapshotWindow: Int = 8,
    val snapshotRetransmitMs: Long = 200L,
    val inboundBurst: Int = 300,
    val inboundPerSecond: Int = 600,
    val replyMemory: Int = 64,
    val maxIdentities: Int = 256,
    val seasonLengthTicks: Long = 360L,
    val scoring: ScoreRules = ScoreRules(),
) {
    init {
        require(commandDelay in 0..1_000 && hashInterval in 1..100_000) { "timing values out of range" }
        require(gate.maxTickBytes <= 20 * 1024) { "a tick's commands must fit one frame" }
        require(gate.maxPerPlayerPerTick * lobby.maxPlayers <= NetLimits.MAX_COMMIT_ENTRIES) { "a commit could exceed the entry limit" }
        require(gate.maxAheadTicks >= commandDelay) { "commands could never be sent far enough ahead" }
    }
}

/** What the server knows about one participant, for the player list. */
data class PlayerStatus(
    val id: PlayerId,
    val name: String,
    val role: Role,
    val slot: Int,
    val company: Long,
    val presence: Presence,
    val ready: Boolean,
    val quality: Quality,
    val rttMs: Long,
    val lossPpm: Int,
    /** How many ticks the player's last report trails the server; -1 when they have not reported. */
    val lagTicks: Long,
    val standing: Standing,
    val suspicion: Int,
    val muted: Boolean,
    val host: Boolean,
)

data class ShardStatus(
    val tick: Long,
    val phase: LobbyPhase,
    val host: PlayerId,
    val players: List<PlayerStatus>,
    val pendingCommands: Int,
    val openTrades: Int,
    val desyncs: Int,
    val season: SeasonClock,
)

/** Counters of what the server has seen, for diagnostics. */
data class ShardMetrics(
    val datagramsIn: Int = 0,
    val damaged: Int = 0,
    val malformed: Int = 0,
    val floodDropped: Int = 0,
    val duplicatesDropped: Int = 0,
    val commitsSent: Int = 0,
    val commandsAccepted: Int = 0,
    val commandsRefused: Int = 0,
    val snapshotsServed: Int = 0,
    val desyncsFound: Int = 0,
)

/** What one tick of the server did. */
data class TickReport(val tick: Long, val commands: Int, val transfers: Int, val hash: ByteArray?)
