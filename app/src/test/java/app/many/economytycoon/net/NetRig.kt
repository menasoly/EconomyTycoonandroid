package app.many.economytycoon.net

/**
 * A whole game in one process: a server shard, some clients and the in-memory network between them, all on a
 * virtual clock. Every run with the same seed behaves identically.
 */
class NetRig(
    val seed: Long = 1L,
    profile: LinkProfile = LinkProfile.PERFECT,
    val clientCount: Int = 2,
    shardConfig: (ShardConfig) -> ShardConfig = { it },
    clientConfig: (ClientConfig) -> ClientConfig = { it },
    income: Boolean = true,
    spectators: Int = 0,
) {
    val net = MemoryNetwork(seed, profile)
    val serverId = EndpointId(1)
    var serverSim = ToySimulation(income = income)
        private set
    private val incomeOn = income
    private val serverTransport = net.endpoint(serverId)
    val config: ShardConfig = shardConfig(
        ShardConfig(
            session = NetFixtures.session, seed = seed,
            lobby = LobbyConfig(maxPlayers = 4, graceMs = 20_000L),
            snapshotMinIntervalMs = 100L,
            idleTimeoutMs = 4_000L,
            seasonLengthTicks = 120L,
        ),
    )
    var shard = ServerShard(config, serverSim, serverTransport, SeededTokenSource(seed))
        private set
    val sims = ArrayList<ToySimulation>()
    val clients = ArrayList<ClientSession>()
    val endpoints = ArrayList<EndpointId>()
    var now = 0L
        private set

    init {
        for (i in 0 until clientCount + spectators) {
            val spectate = i >= clientCount
            val sim = ToySimulation(income = income)
            val id = EndpointId(10 + i)
            val cfg = clientConfig(ClientConfig(session = NetFixtures.session, name = if (spectate) "Watcher $i" else "Player $i", spectate = spectate))
            sims += sim
            endpoints += id
            clients += ClientSession(cfg, sim, net.endpoint(id), serverId)
        }
    }

    /** One slice of time: the network delivers, the server and each client react. */
    fun frame(ms: Long = FRAME_MS) {
        now += ms
        net.advanceTo(now)
        shard.poll(now)
        for (c in clients) c.poll(now)
    }

    fun frames(n: Int) { repeat(n) { frame() } }

    fun settle(ms: Long = 2_000L) { var t = 0L; while (t < ms) { frame(); t += FRAME_MS } }

    /** Joins everyone, readies them and starts the game from the first client, the host. */
    fun startGame() {
        for (c in clients) c.connect(now)
        waitFor(20_000L) { clients.all { it.state == ClientState.LOBBY && it.lobby() != null } }
        for (c in clients.filter { it.role == Role.PLAYER }) c.ready(now)
        waitFor(20_000L) { shard.lobby.players().all { it.ready } }
        val host = clients.first { it.player == shard.lobby.hostId }
        host.startGame(now)
        waitFor(20_000L) { clients.all { it.state == ClientState.RUNNING } }
    }

    fun waitFor(maxMs: Long, condition: () -> Boolean) {
        var t = 0L
        while (t < maxMs && !condition()) { frame(); t += FRAME_MS }
    }

    /** Runs [ticks] server ticks, [FRAMES_PER_TICK] frames each; [each] runs before every tick. */
    fun run(ticks: Int, each: (Int) -> Unit = {}) {
        for (i in 0 until ticks) {
            each(i)
            shard.stepTick(now)
            repeat(FRAMES_PER_TICK) { frame() }
        }
    }

    fun converged(): Boolean = clients.indices.all { clients[it].state == ClientState.RUNNING && sims[it].tick == serverSim.tick && sims[it].stateHash().contentEquals(serverSim.stateHash()) }

    /** Gives the network time to deliver everything still owed. */
    fun drain(maxMs: Long = 20_000L) {
        var t = 0L
        while (t < maxMs && !converged()) { frame(); t += FRAME_MS }
    }

    fun client(i: Int) = clients[i]

    /** Stops the host and starts a new one from its saved state and a snapshot of the game, as after a crash and restart. */
    fun restartServer(downMs: Long = 0L) {
        val saved = app.many.economytycoon.core.ByteWriter().also { shard.writeState(it) }.toByteArray()
        val game = serverSim.snapshot()
        now += downMs
        net.advanceTo(now)
        val sim = ToySimulation(income = incomeOn)
        sim.restore(game)
        serverSim = sim
        shard = ServerShard(config, sim, serverTransport, SeededTokenSource(seed + 1_000))
        shard.readState(app.many.economytycoon.core.ByteReader(saved), now)
    }

    companion object {
        const val FRAME_MS = 10L
        const val FRAMES_PER_TICK = 5
    }
}
