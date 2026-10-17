package app.many.economytycoon.net

/** What one call to [MultiplayerNode.pump] did; [headlines] are for the news feed of the first client's person. */
data class PumpReport(val ticksStepped: Int, val clientTicks: Long, val hostTick: Long, val headlines: List<String> = emptyList())

/**
 * The part of a multiplayer session that runs on one device: the server shard when this device hosts, and the
 * clients the device plays through. The app owns a clock and calls [pump] often (every 20 ms is plenty) with a
 * monotonic time in milliseconds; nothing here reads a clock. In a multiplayer session nothing else may advance the
 * game: the host steps its shard here, and every client moves only by applying the host's commits.
 *
 * The transport is whatever the shard and clients were built with. [MemoryNetwork] is used for local tables and
 * tests (pass it as [memory] so its virtual clock follows [pump]). A device-to-device game needs a [Transport] over
 * a socket or relay; see docs/architecture/modules/net.md for what that adapter has to provide.
 */
class MultiplayerNode(
    val shard: ServerShard?,
    val clients: List<ClientSession>,
    /** Milliseconds of real time between two ticks of the hosted game. */
    val tickMs: Long = 200L,
    private val memory: MemoryNetwork? = null,
    /** Ticks the host may catch up in one call after a stall. */
    private val maxStepsPerPump: Int = 5,
) {
    init { require(tickMs > 0L && maxStepsPerPump > 0) { "timing values must be positive" } }

    private var nextTickAt = -1L
    private var last = 0L

    fun pump(nowMs: Long): PumpReport {
        val now = maxOf(nowMs, last)
        last = now
        memory?.let { if (now > it.now) it.advanceTo(now) }
        shard?.poll(now)
        for (c in clients) c.poll(now)
        var stepped = 0
        if (shard != null) {
            if (nextTickAt < 0L) nextTickAt = now
            while (nextTickAt <= now && stepped < maxStepsPerPump) {
                shard.stepTick(now) ?: break
                nextTickAt += tickMs
                stepped++
            }
            // not running yet (lobby), or the host stalled for long: do not owe the ticks that were skipped
            if (stepped == 0 && nextTickAt < now) nextTickAt = now
            if (stepped == maxStepsPerPump && nextTickAt <= now) nextTickAt = now + tickMs
        }
        val me = clients.firstOrNull()
        val names = { p: PlayerId -> me?.lobby()?.members?.firstOrNull { it.id == p }?.name ?: "Player ${p.value}" }
        val headlines = me?.drainEvents()?.mapNotNull { NetNews.headline(it, names) }.orEmpty()
        return PumpReport(stepped, me?.tick ?: -1L, shard?.tick ?: -1L, headlines)
    }
}

/** Builds the pieces of a table that lives inside one process: a host and any number of guests on a [MemoryNetwork]. */
class LocalTable(
    val config: ShardConfig,
    authority: SimulationPort,
    tokens: TokenSource,
    network: MemoryNetwork = MemoryNetwork(config.seed),
    val tickMs: Long = 200L,
) {
    val network: MemoryNetwork = network
    val serverEndpoint = EndpointId(1)
    val shard = ServerShard(config, authority, network.endpoint(serverEndpoint), tokens)
    private val guests = ArrayList<ClientSession>()
    private var nextEndpoint = 10

    /** Adds a person at this table who plays (or only watches) through [port]. Call before [node]; the node sees later joins too. */
    fun join(name: String, port: SimulationPort, spectate: Boolean = false): ClientSession {
        val client = ClientSession(ClientConfig(session = config.session, name = name, spectate = spectate), port, network.endpoint(EndpointId(nextEndpoint++)), serverEndpoint)
        guests += client
        return client
    }

    fun node(): MultiplayerNode = MultiplayerNode(shard, guests, tickMs, network)
}
