package app.many.economytycoon.net

/** Things a participant can do wrong, weighted by how likely they are to mean harm rather than a bad connection. */
enum class Offense(val weight: Int) {
    MALFORMED(1),
    RATE_LIMITED(1),
    STALE_SEQUENCE(1),
    REPLAYED(3),
    OVERSIZED(4),
    BAD_SIGNATURE(10),
    NOT_OWNER(6),
    IMPOSSIBLE_COMMAND(8),
    ENGINE_REFUSALS(3),
    SPECTATOR_COMMAND(4),
    DESYNC(2),
    CHAT_ABUSE(2),
    TRADE_ABUSE(4),
}

/** What the server should do about a participant. */
enum class Action { NONE, WARN, KICK, BAN }

enum class Standing { GOOD, WATCHED, WARNED, KICKED, BANNED }

data class Incident(val tick: Long, val player: PlayerId, val offense: Offense, val detail: String)

data class AntiCheatConfig(
    val watchAt: Int = 10,
    val warnAt: Int = 25,
    val kickAt: Int = 60,
    val banAt: Int = 120,
    /** One point of suspicion is forgiven every this many ticks of good behaviour. */
    val forgiveEveryTicks: Int = 10,
    /** The share of a player's recent commands the engine may refuse before that counts as an offense, in ppm. */
    val refusalLimitPpm: Int = 700_000,
    val refusalWindow: Int = 20,
    val maxIncidents: Int = 200,
) {
    init { require(watchAt <= warnAt && warnAt <= kickAt && kickAt <= banAt) { "thresholds must rise" } }
}

/**
 * Keeps a running suspicion score per participant. Every offense adds its weight; good behaviour slowly forgives;
 * crossing a threshold changes the standing and, at the top, asks the server to act. Honest players on bad
 * connections produce small, rare offenses and never get near the thresholds; probing and forging do.
 */
class AntiCheat(val config: AntiCheatConfig = AntiCheatConfig()) {
    private class Record {
        var score = 0
        var lastTick = 0L
        var standing = Standing.GOOD
        val counts = java.util.EnumMap<Offense, Int>(Offense::class.java)
        val recent = ArrayDeque<Boolean>()
    }

    private val records = java.util.TreeMap<PlayerId, Record>()
    private val log = ArrayDeque<Incident>()

    private fun rec(p: PlayerId) = records.getOrPut(p) { Record() }

    fun score(p: PlayerId): Int = records[p]?.score ?: 0
    fun standing(p: PlayerId): Standing = records[p]?.standing ?: Standing.GOOD
    fun count(p: PlayerId, offense: Offense): Int = records[p]?.counts?.get(offense) ?: 0
    fun incidents(): List<Incident> = log.toList()
    fun incidentsOf(p: PlayerId): List<Incident> = log.filter { it.player == p }

    /** Notes an offense at [tick] and returns what the server should now do. */
    fun record(player: PlayerId, offense: Offense, tick: Long, detail: String = ""): Action {
        val r = rec(player)
        forgive(r, tick)
        r.score += offense.weight
        r.counts[offense] = (r.counts[offense] ?: 0) + 1
        log.addLast(Incident(tick, player, offense, detail.take(NetLimits.MAX_DETAIL_CHARS)))
        while (log.size > config.maxIncidents) log.removeFirst()
        return escalate(r)
    }

    private fun forgive(r: Record, tick: Long) {
        if (tick > r.lastTick && config.forgiveEveryTicks > 0) {
            val relief = ((tick - r.lastTick) / config.forgiveEveryTicks).toInt()
            if (relief > 0) { r.score = (r.score - relief).coerceAtLeast(0); r.lastTick += relief.toLong() * config.forgiveEveryTicks }
        } else if (tick > r.lastTick) r.lastTick = tick
        if (r.standing == Standing.WATCHED && r.score < config.watchAt) r.standing = Standing.GOOD
        if (r.standing == Standing.WARNED && r.score < config.warnAt) r.standing = Standing.WATCHED
    }

    private fun escalate(r: Record): Action {
        val s = r.score
        return when {
            s >= config.banAt -> { r.standing = Standing.BANNED; Action.BAN }
            s >= config.kickAt && r.standing != Standing.KICKED && r.standing != Standing.BANNED -> { r.standing = Standing.KICKED; Action.KICK }
            s >= config.warnAt && r.standing < Standing.WARNED -> { r.standing = Standing.WARNED; Action.WARN }
            s >= config.watchAt && r.standing < Standing.WATCHED -> { r.standing = Standing.WATCHED; Action.NONE }
            else -> Action.NONE
        }
    }

    /** Lets time pass so scores fall for everyone, including players who have been quiet. */
    fun advance(tick: Long) { for (r in records.values) { forgive(r, tick); if (r.standing == Standing.KICKED && r.score < config.kickAt) r.standing = Standing.WARNED } }

    /** Feeds in how the engine treated a player's commands. A long run of refusals suggests commands built to fail. */
    fun observe(player: PlayerId, accepted: Boolean, tick: Long): Action {
        val r = rec(player)
        r.recent.addLast(accepted)
        while (r.recent.size > config.refusalWindow) r.recent.removeFirst()
        if (r.recent.size < config.refusalWindow) return Action.NONE
        val refused = r.recent.count { !it }
        if (refused.toLong() * 1_000_000L / r.recent.size >= config.refusalLimitPpm) {
            r.recent.clear()
            return record(player, Offense.ENGINE_REFUSALS, tick, "the engine refused most recent commands")
        }
        return Action.NONE
    }

    fun forget(player: PlayerId) { records.remove(player) }

    fun players(): List<PlayerId> = records.keys.toList()
}
