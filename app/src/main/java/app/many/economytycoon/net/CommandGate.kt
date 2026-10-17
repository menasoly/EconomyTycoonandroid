package app.many.economytycoon.net

import app.many.economytycoon.simulation.Command

data class GateConfig(
    /** How many ticks past the next commit a command may be aimed at. */
    val maxAheadTicks: Int = 64,
    val maxPerPlayerPerTick: Int = 8,
    /** Total command bytes one tick may carry, so a commit always fits a frame. */
    val maxTickBytes: Int = 20 * 1024,
    val burst: Int = 40,
    val perSecond: Int = 30,
    val policy: CommandPolicy = CommandPolicy(),
    /** How many past verdicts are kept to answer a repeated request with the same reply. */
    val verdictMemory: Int = 128,
) {
    init {
        require(maxAheadTicks >= 1 && maxPerPlayerPerTick >= 1 && maxTickBytes >= NetLimits.MAX_COMMAND_BYTES) { "gate limits are too small" }
    }
}

sealed class GateResult {
    /** The command passed every check and now belongs to its target tick. */
    class Accepted(val command: Command, val entry: CommitEntry, val reply: NetMessage.Ack) : GateResult()

    /** The command was refused. [consumed] says whether its sequence number is now used up. */
    class Refused(val error: NetError, val rule: String, val consumed: Boolean, val reply: NetMessage.Reject) : GateResult()

    /** The sequence number was seen before; [reply] repeats what was said then, when that is remembered. */
    class Repeat(val reply: NetMessage?) : GateResult()
}

/**
 * Every check a submitted command goes through before it can reach the simulation, in a fixed order: signature,
 * message rate, replay window, timing, parse, policy, plausibility, ownership and per-tick budgets. Cheap checks
 * come first so hostile traffic costs as little as possible. The gate holds no game state; ownership is asked
 * of the caller.
 */
class CommandGate(private val session: SessionId, private val config: GateConfig = GateConfig()) {
    private class PerPlayer(config: GateConfig) {
        val window = SeqWindow()
        val limiter = RateLimiter(config.burst, config.perSecond)
        val verdicts = LinkedHashMap<Long, NetMessage>()
        val counts = java.util.TreeMap<Long, Int>()
    }

    private val players = java.util.TreeMap<PlayerId, PerPlayer>()
    private val tickBytes = java.util.TreeMap<Long, Int>()

    private fun of(p: PlayerId) = players.getOrPut(p) { PerPlayer(config) }

    fun evaluate(
        player: PlayerId,
        key: ByteArray,
        submit: NetMessage.Submit,
        now: Long,
        nextCommitTick: Long,
        owns: (Long) -> Boolean,
    ): GateResult {
        val s = of(player)
        val expected = Signing.commandMac(key, session, player, submit.seq, submit.targetTick, submit.payload)
        if (!Hmac.constantTimeEquals(expected, submit.mac)) return refuse(s, submit, NetError.BAD_SIGNATURE, "bad_signature", consumed = false)
        if (!s.limiter.tryTake(now)) return refuse(s, submit, NetError.RATE_LIMITED, "rate_limited", consumed = false)
        when (s.window.accept(submit.seq)) {
            SeqVerdict.FRESH -> Unit
            SeqVerdict.DUPLICATE -> return GateResult.Repeat(s.verdicts[submit.seq])
            SeqVerdict.STALE -> return GateResult.Refused(NetError.STALE_SEQUENCE, "stale_sequence", false, reject(submit, NetError.STALE_SEQUENCE, "stale_sequence"))
            SeqVerdict.AHEAD -> return GateResult.Refused(NetError.SEQUENCE_AHEAD, "sequence_ahead", false, reject(submit, NetError.SEQUENCE_AHEAD, "sequence_ahead"))
        }
        if (submit.targetTick < nextCommitTick) return refuse(s, submit, NetError.TOO_LATE, "too_late", consumed = true, tick = nextCommitTick)
        if (submit.targetTick > nextCommitTick + config.maxAheadTicks) return refuse(s, submit, NetError.TOO_EARLY, "too_early", consumed = true, tick = nextCommitTick)
        val parsed = CommandPayload.parse(submit.payload)
        val command = when (parsed) {
            is ParsedCommand.Ok -> parsed.command
            is ParsedCommand.Bad -> return refuse(s, submit, NetError.INVALID_COMMAND, "unreadable_command", consumed = true, tick = nextCommitTick)
        }
        CommandSanity.check(command, config.policy)?.let { rule -> return refuse(s, submit, NetError.INVALID_COMMAND, rule, consumed = true, tick = nextCommitTick) }
        val company = command.company
        if (company == null || !owns(company.value)) return refuse(s, submit, NetError.NOT_OWNER, "not_owner", consumed = true, tick = nextCommitTick)
        val mine = s.counts[submit.targetTick] ?: 0
        val bytes = tickBytes[submit.targetTick] ?: 0
        if (mine >= config.maxPerPlayerPerTick || bytes + submit.payload.size > config.maxTickBytes) {
            return refuse(s, submit, NetError.RATE_LIMITED, "tick_budget", consumed = true, tick = nextCommitTick)
        }
        s.counts[submit.targetTick] = mine + 1
        tickBytes[submit.targetTick] = bytes + submit.payload.size
        val ack = NetMessage.Ack(submit.seq, submit.targetTick)
        remember(s, submit.seq, ack)
        return GateResult.Accepted(command, CommitEntry(player, submit.seq, submit.payload), ack)
    }

    /** Forgets the budgets of ticks that have been committed. */
    fun committed(tick: Long) {
        for (s in players.values) s.counts.headMap(tick, true).clear()
        tickBytes.headMap(tick, true).clear()
    }

    fun forget(player: PlayerId) { players.remove(player) }

    private fun reject(submit: NetMessage.Submit, error: NetError, rule: String, tick: Long = 0L) = NetMessage.Reject(submit.seq, error, rule, tick)

    private fun refuse(s: PerPlayer, submit: NetMessage.Submit, error: NetError, rule: String, consumed: Boolean, tick: Long = 0L): GateResult.Refused {
        val reply = reject(submit, error, rule, tick)
        if (consumed) remember(s, submit.seq, reply)
        return GateResult.Refused(error, rule, consumed, reply)
    }

    private fun remember(s: PerPlayer, seq: Long, reply: NetMessage) {
        s.verdicts[seq] = reply
        while (s.verdicts.size > config.verdictMemory) s.verdicts.remove(s.verdicts.keys.first())
    }
}
