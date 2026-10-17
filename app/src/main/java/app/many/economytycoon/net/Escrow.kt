package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.Rules

data class EscrowConfig(
    val maxOpenPerPlayer: Int = 5,
    val minTtlTicks: Int = 1,
    val maxTtlTicks: Int = 120,
    /** Times a payout that could not be delivered is tried again before it is left for the host to resolve. */
    val strandedRetries: Int = 3,
)

/** What the escrow needs to ask of the game to validate a trade. */
interface EscrowView {
    fun owns(player: PlayerId, company: Long): Boolean
    fun supports(kind: AssetKind): Boolean
    fun available(company: Long, asset: TradeAsset): Long
}

/** Where a trade is in its life. Locks take a tick to land, so there are in-between steps the players never see. */
internal enum class Stage(val tag: Int) {
    LOCKING_GIVE(0), OFFERED(1), LOCKING_WANT(2), READY(3), DONE(4);

    companion object { fun fromTag(tag: Int) = values().firstOrNull { it.tag == tag } }
}

class Trade internal constructor(
    val id: TradeId,
    val from: PlayerId,
    val fromCompany: Long,
    val to: PlayerId,
    val toCompany: Long,
    val give: List<TradeAsset>,
    val want: List<TradeAsset>,
    val createdAt: Long,
    val expiresAt: Long,
) {
    internal var stage: Stage = Stage.LOCKING_GIVE
    var state: TradeState = TradeState.OFFERED
        internal set

    fun notice() = NetMessage.TradeNotice(id, state, from, to, give, want, expiresAt)
}

/** A group of transfers that stand or fall together, with what to do once the game has applied them. */
internal class Step(val trade: TradeId, val kind: Kind, val transfers: List<Transfer>) {
    enum class Kind { LOCK_GIVE, LOCK_WANT, PAYOUT, REFUND, RETRY }
}

/**
 * Holds assets between two players while a trade is open. Everything that moves goes through the game as an
 * ordinary transfer with the escrow account on one side, so the game's own books show exactly what is held and
 * the totals can be checked against them: nothing is created, nothing is lost, and a trade either completes for
 * both parties or returns everything to its owner.
 *
 * Life of a trade: the giver offers and their assets are locked; the receiver accepts and theirs are locked; the
 * next tick both sides are paid out. An offer that nobody answers expires and unlocks. The book does not touch
 * the game directly: it queues transfers, [drain] hands them to the tick, and [applied] reports how they went.
 */
class EscrowBook(private val config: EscrowConfig = EscrowConfig()) {
    private val trades = java.util.TreeMap<TradeId, Trade>()
    private val queue = ArrayList<Step>()
    private val inFlight = ArrayList<Step>()
    private val held = java.util.TreeMap<Pair<Int, String>, Long>(compareBy<Pair<Int, String>>({ it.first }, { it.second }))
    private val reserved = HashMap<Triple<Long, Int, String>, Long>()
    private val stranded = ArrayList<Transfer>()
    private val strandedTries = ArrayList<Int>()
    private val notices = ArrayList<NetMessage.TradeNotice>()
    private var nextId = 1L

    fun trade(id: TradeId): Trade? = trades[id]
    fun all(): List<Trade> = trades.values.toList()
    fun open(): List<Trade> = trades.values.filter { it.state.open }
    fun openOf(player: PlayerId): List<Trade> = trades.values.filter { it.state.open && (it.from == player || it.to == player) }
    fun strandedTransfers(): List<Transfer> = stranded.toList()
    fun pendingTransfers(): Int = queue.sumOf { it.transfers.size }

    /** Notices produced since the last call, for sending to the parties. */
    fun takeNotices(): List<NetMessage.TradeNotice> { val n = notices.toList(); notices.clear(); return n }

    // ---- requests ----

    fun offer(
        view: EscrowView, from: PlayerId, fromCompany: Long, to: PlayerId, toCompany: Long,
        give: List<TradeAsset>, want: List<TradeAsset>, tick: Long, ttlTicks: Int,
    ): Outcome<TradeId> {
        if (from == to || fromCompany == toCompany) return Outcome.reject(Rules.INVALID_ARGUMENT, "a trade needs two different parties")
        if (fromCompany <= 0L || toCompany <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "unknown company")
        if (!view.owns(from, fromCompany)) return Outcome.reject(Rules.NOT_OWNER, "you do not run that company")
        if (!view.owns(to, toCompany)) return Outcome.reject(Rules.NOT_OWNER, "the other party does not run that company")
        if (give.isEmpty()) return Outcome.reject(Rules.INVALID_ARGUMENT, "offer something")
        if (ttlTicks < config.minTtlTicks || ttlTicks > config.maxTtlTicks) return Outcome.reject(Rules.INVALID_ARGUMENT, "the offer must last ${config.minTtlTicks}-${config.maxTtlTicks} ticks")
        checkAssets(view, give, "give")?.let { return Outcome.Rejected(it) }
        checkAssets(view, want, "want")?.let { return Outcome.Rejected(it) }
        if (openOf(from).count { it.from == from } >= config.maxOpenPerPlayer) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many open offers")
        availability(view, fromCompany, give)?.let { return Outcome.Rejected(it) }
        val t = Trade(TradeId(nextId++), from, fromCompany, to, toCompany, give.sorted(), want.sorted(), tick, tick + ttlTicks)
        trades[t.id] = t
        queueLock(t, Step.Kind.LOCK_GIVE, fromCompany, t.give)
        return Outcome.ok(t.id)
    }

    fun accept(view: EscrowView, id: TradeId, by: PlayerId, tick: Long): Outcome<Unit> {
        val t = trades[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such trade")
        if (by != t.to) return Outcome.reject(Rules.NOT_OWNER, "this offer is not for you")
        if (t.stage != Stage.OFFERED) return Outcome.reject(Rules.WRONG_STATE, "the offer is not open")
        if (tick >= t.expiresAt) return Outcome.reject(Rules.WRONG_STATE, "the offer has expired")
        if (!view.owns(t.to, t.toCompany)) return Outcome.reject(Rules.NOT_OWNER, "you no longer run that company")
        availability(view, t.toCompany, t.want)?.let { return Outcome.Rejected(it) }
        t.stage = Stage.LOCKING_WANT
        if (t.want.isEmpty()) { t.stage = Stage.READY; t.state = TradeState.ACCEPTED; notices += t.notice() }
        else queueLock(t, Step.Kind.LOCK_WANT, t.toCompany, t.want)
        return Outcome.ok(Unit)
    }

    fun decline(id: TradeId, by: PlayerId): Outcome<Unit> {
        val t = trades[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such trade")
        if (by != t.to) return Outcome.reject(Rules.NOT_OWNER, "this offer is not for you")
        return close(t, TradeState.DECLINED)
    }

    fun cancel(id: TradeId, by: PlayerId): Outcome<Unit> {
        val t = trades[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such trade")
        if (by != t.from) return Outcome.reject(Rules.NOT_OWNER, "this is not your offer")
        return close(t, TradeState.CANCELLED)
    }

    private fun close(t: Trade, state: TradeState): Outcome<Unit> {
        if (t.stage != Stage.OFFERED) return Outcome.reject(Rules.WRONG_STATE, "the offer can no longer be withdrawn")
        t.stage = Stage.DONE
        t.state = state
        queue += Step(t.id, Step.Kind.REFUND, t.give.map { Transfer(t.id, ESCROW, t.fromCompany, it) })
        notices += t.notice()
        return Outcome.ok(Unit)
    }

    // ---- ticks ----

    /** Expires stale offers and settles trades that are ready. Call once per tick before [drain]. */
    fun beginTick(tick: Long) {
        for (t in trades.values.sortedBy { it.id }) {
            if (t.stage == Stage.OFFERED && tick >= t.expiresAt) {
                t.stage = Stage.DONE; t.state = TradeState.EXPIRED
                queue += Step(t.id, Step.Kind.REFUND, t.give.map { Transfer(t.id, ESCROW, t.fromCompany, it) })
                notices += t.notice()
            } else if (t.stage == Stage.READY) {
                t.stage = Stage.DONE; t.state = TradeState.SETTLED
                val pay = t.give.map { Transfer(t.id, ESCROW, t.toCompany, it) } + t.want.map { Transfer(t.id, ESCROW, t.fromCompany, it) }
                queue += Step(t.id, Step.Kind.PAYOUT, pay)
                notices += t.notice()
            }
        }
        val retry = ArrayList<Transfer>()
        for (i in stranded.indices.reversed()) if (strandedTries[i] < config.strandedRetries) { retry += stranded[i]; strandedTries[i]++ }
        if (retry.isNotEmpty()) queue += Step(TradeId(0), Step.Kind.RETRY, retry.reversed())
    }

    /** The transfers to apply this tick: whole groups, oldest first, at most [limit] transfers in all. */
    fun drain(limit: Int = NetLimits.MAX_TRANSFERS): List<Transfer> {
        val out = ArrayList<Transfer>()
        var taken = 0
        for (step in queue) {
            if (out.size + step.transfers.size > limit) break
            out += step.transfers
            inFlight += step
            taken++
        }
        repeat(taken) { queue.removeAt(0) }
        return out
    }

    /** Reports how the game applied the transfers handed out by [drain], one result each in the same order. */
    fun applied(results: List<Boolean>) {
        require(results.size == inFlight.sumOf { it.transfers.size }) { "expected one result per drained transfer" }
        var at = 0
        val groups = ArrayList(inFlight)
        inFlight.clear()
        for (step in groups) {
            val ok = results.subList(at, at + step.transfers.size)
            at += step.transfers.size
            confirm(step, ok)
        }
    }

    private fun confirm(step: Step, ok: List<Boolean>) {
        val t = trades[step.trade]
        when (step.kind) {
            Step.Kind.LOCK_GIVE, Step.Kind.LOCK_WANT -> {
                for ((i, tr) in step.transfers.withIndex()) {
                    release(tr.from, tr.asset)
                    if (ok[i]) adjustHeld(tr.asset, tr.asset.amount)
                }
                if (t == null) return
                if (ok.all { it }) {
                    if (step.kind == Step.Kind.LOCK_GIVE) { t.stage = Stage.OFFERED; t.state = TradeState.OFFERED }
                    else { t.stage = Stage.READY; t.state = TradeState.ACCEPTED }
                    notices += t.notice()
                } else {
                    // Part of the lock did not land: send back what did, and give up on the trade.
                    val back = step.transfers.filterIndexed { i, _ -> ok[i] }.map { Transfer(t.id, ESCROW, it.from, it.asset) }
                    if (back.isNotEmpty()) queue += Step(t.id, Step.Kind.REFUND, back)
                    if (step.kind == Step.Kind.LOCK_WANT) {
                        queue += Step(t.id, Step.Kind.REFUND, t.give.map { Transfer(t.id, ESCROW, t.fromCompany, it) })
                    }
                    t.stage = Stage.DONE; t.state = TradeState.FAILED
                    notices += t.notice()
                }
            }
            Step.Kind.PAYOUT, Step.Kind.REFUND, Step.Kind.RETRY -> {
                for ((i, tr) in step.transfers.withIndex()) {
                    if (ok[i]) {
                        adjustHeld(tr.asset, -tr.asset.amount)
                        if (step.kind == Step.Kind.RETRY) removeStranded(tr)
                    } else if (step.kind != Step.Kind.RETRY) {
                        stranded += tr; strandedTries += 0
                    }
                }
            }
        }
    }

    private fun removeStranded(tr: Transfer) {
        val i = stranded.indexOf(tr)
        if (i >= 0) { stranded.removeAt(i); strandedTries.removeAt(i) }
    }

    // ---- bookkeeping ----

    private fun queueLock(t: Trade, kind: Step.Kind, company: Long, assets: List<TradeAsset>) {
        for (a in assets) reserved.merge(Triple(company, a.kind.tag, a.key), a.amount, Long::plus)
        queue += Step(t.id, kind, assets.map { Transfer(t.id, company, ESCROW, it) })
    }

    private fun release(company: Long, a: TradeAsset) {
        val key = Triple(company, a.kind.tag, a.key)
        val left = (reserved[key] ?: 0L) - a.amount
        if (left > 0L) reserved[key] = left else reserved.remove(key)
    }

    private fun adjustHeld(a: TradeAsset, delta: Long) {
        val key = a.kind.tag to a.key
        val v = (held[key] ?: 0L) + delta
        if (v == 0L) held.remove(key) else held[key] = v
    }

    private fun checkAssets(view: EscrowView, assets: List<TradeAsset>, side: String): Rejection? {
        if (assets.size > NetLimits.MAX_TRADE_ASSETS) return Rejection(Rules.LIMIT_EXCEEDED, "too many lines on the $side side")
        val seen = HashSet<Pair<Int, String>>()
        for (a in assets) {
            if (a.amount <= 0L) return Rejection(Rules.INVALID_QUANTITY, "amounts must be positive")
            if ((a.kind == AssetKind.CASH) != a.key.isEmpty()) return Rejection(Rules.INVALID_ARGUMENT, "cash has no good, goods need one")
            if (!seen.add(a.kind.tag to a.key)) return Rejection(Rules.DUPLICATE, "an asset appears twice")
            if (!view.supports(a.kind)) return Rejection(Rules.NOT_UNLOCKED, "${a.kind.name.lowercase()} cannot be traded between players here")
        }
        return null
    }

    private fun availability(view: EscrowView, company: Long, assets: List<TradeAsset>): Rejection? {
        for (a in assets) {
            val free = view.available(company, a) - (reserved[Triple(company, a.kind.tag, a.key)] ?: 0L)
            if (free < a.amount) {
                return if (a.kind == AssetKind.CASH) Rejection(Rules.INSUFFICIENT_FUNDS, "not enough cash free")
                else Rejection(Rules.INSUFFICIENT_STOCK, "not enough ${a.key} free")
            }
        }
        return null
    }

    /** Amount currently held in escrow per asset, as the book believes it. */
    fun heldTotals(): Map<Pair<AssetKind, String>, Long> = held.entries.associate { (k, v) -> (AssetKind.fromTag(k.first) ?: AssetKind.CASH) to k.second to v }

    /** Checks the book against itself: what is held equals what open trades locked plus what is stranded. */
    fun invariants(): List<String> {
        val out = ArrayList<String>()
        val expected = java.util.TreeMap<Pair<Int, String>, Long>(compareBy<Pair<Int, String>>({ it.first }, { it.second }))
        fun add(a: TradeAsset) { expected.merge(a.kind.tag to a.key, a.amount, Long::plus) }
        for (t in trades.values) {
            when (t.stage) {
                Stage.OFFERED -> t.give.forEach(::add)
                Stage.LOCKING_WANT -> t.give.forEach(::add)
                Stage.READY -> { t.give.forEach(::add); t.want.forEach(::add) }
                else -> Unit
            }
        }
        for (s in stranded) add(s.asset)
        // Steps in the queue or in flight have not landed yet; payouts and refunds still hold their assets.
        for (step in queue + inFlight) when (step.kind) {
            Step.Kind.PAYOUT, Step.Kind.REFUND -> step.transfers.forEach { add(it.asset) }
            else -> Unit
        }
        for ((k, v) in expected) if ((held[k] ?: 0L) != v) out += "ESC-01: ${k.second.ifEmpty { "cash" }} held ${held[k] ?: 0L} but trades account for $v"
        for ((k, v) in held) if (k !in expected && v != 0L) out += "ESC-01: ${k.second.ifEmpty { "cash" }} held $v with no trade behind it"
        for (v in held.values) if (v < 0L) out += "ESC-02: negative amount held"
        return out
    }

    // ---- persistence ----

    fun write(w: ByteWriter) {
        w.writeVarInt(VERSION)
        w.writeVarLong(nextId)
        w.writeVarInt(trades.size)
        for (t in trades.values) {
            w.writeVarLong(t.id.value); w.writeVarLong(t.from.value); w.writeVarLong(t.fromCompany); w.writeVarLong(t.to.value); w.writeVarLong(t.toCompany)
            writeAssets(w, t.give); writeAssets(w, t.want)
            w.writeVarLong(t.createdAt); w.writeVarLong(t.expiresAt); w.writeByte(t.stage.tag); w.writeByte(t.state.tag)
        }
        w.writeVarInt(held.size)
        for ((k, v) in held) { w.writeByte(k.first); w.writeString(k.second); w.writeVarLong(v) }
        w.writeVarInt(stranded.size)
        for (i in stranded.indices) { writeTransfer(w, stranded[i]); w.writeVarInt(strandedTries[i]) }
        w.writeVarInt(queue.size)
        for (s in queue) { w.writeVarLong(s.trade.value); w.writeByte(s.kind.ordinal); w.writeVarInt(s.transfers.size); s.transfers.forEach { writeTransfer(w, it) } }
    }

    fun read(r: ByteReader) {
        val v = r.readVarInt()
        if (v != VERSION) throw ByteReader.CorruptDataException("unsupported escrow version $v")
        trades.clear(); queue.clear(); inFlight.clear(); held.clear(); reserved.clear(); stranded.clear(); strandedTries.clear(); notices.clear()
        nextId = r.readVarLong()
        val n = r.readVarInt()
        if (n < 0 || n > MAX_TRADES) throw ByteReader.CorruptDataException("implausible trade count $n")
        repeat(n) {
            val id = TradeId(r.readVarLong())
            val t = Trade(id, PlayerId(r.readVarLong()), r.readVarLong(), PlayerId(r.readVarLong()), r.readVarLong(), readAssets(r), readAssets(r), r.readVarLong(), r.readVarLong())
            t.stage = Stage.fromTag(r.readByte()) ?: throw ByteReader.CorruptDataException("unknown trade stage")
            t.state = TradeState.fromTag(r.readByte()) ?: throw ByteReader.CorruptDataException("unknown trade state")
            trades[id] = t
        }
        repeat(boundedCount(r)) { held[r.readByte() to r.readString()] = r.readVarLong() }
        repeat(boundedCount(r)) { stranded += readTransfer(r); strandedTries += r.readVarInt() }
        repeat(boundedCount(r)) {
            val trade = TradeId(r.readVarLong())
            val kind = Step.Kind.values().getOrNull(r.readByte()) ?: throw ByteReader.CorruptDataException("unknown step kind")
            queue += Step(trade, kind, List(boundedCount(r)) { readTransfer(r) })
        }
        for (step in queue) if (step.kind == Step.Kind.LOCK_GIVE || step.kind == Step.Kind.LOCK_WANT) {
            for (tr in step.transfers) reserved.merge(Triple(tr.from, tr.asset.kind.tag, tr.asset.key), tr.asset.amount, Long::plus)
        }
    }

    private fun boundedCount(r: ByteReader): Int {
        val n = r.readVarInt()
        if (n < 0 || n > MAX_TRADES) throw ByteReader.CorruptDataException("implausible count $n")
        return n
    }

    private fun writeAssets(w: ByteWriter, list: List<TradeAsset>) {
        w.writeVarInt(list.size)
        for (a in list) { w.writeByte(a.kind.tag); w.writeString(a.key); w.writeVarLong(a.amount) }
    }

    private fun readAssets(r: ByteReader): List<TradeAsset> = List(boundedCount(r).also { if (it > NetLimits.MAX_TRADE_ASSETS) throw ByteReader.CorruptDataException("too many assets") }) {
        TradeAsset(AssetKind.fromTag(r.readByte()) ?: throw ByteReader.CorruptDataException("unknown asset kind"), r.readString(), r.readVarLong())
    }

    private fun writeTransfer(w: ByteWriter, t: Transfer) {
        w.writeVarLong(t.trade.value); w.writeVarLong(t.from); w.writeVarLong(t.to)
        w.writeByte(t.asset.kind.tag); w.writeString(t.asset.key); w.writeVarLong(t.asset.amount)
    }

    private fun readTransfer(r: ByteReader) = Transfer(
        TradeId(r.readVarLong()), r.readVarLong(), r.readVarLong(),
        TradeAsset(AssetKind.fromTag(r.readByte()) ?: throw ByteReader.CorruptDataException("unknown asset kind"), r.readString(), r.readVarLong()),
    )

    companion object {
        const val VERSION = 1
        const val ESCROW = RealSimulationPort.ESCROW
        private const val MAX_TRADES = 1_000_000
    }
}
