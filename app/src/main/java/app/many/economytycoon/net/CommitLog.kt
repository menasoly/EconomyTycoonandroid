package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter

/** What ran on one tick: the commands in the order they were applied and the asset transfers settled before them. */
class CommittedTick(val tick: Long, val entries: List<CommitEntry>, val transfers: List<Transfer>, val chain: Long) {
    fun toMessage() = NetMessage.Commit(tick, entries, transfers, chain)

    override fun equals(other: Any?) = other is CommittedTick && tick == other.tick && entries == other.entries && transfers == other.transfers && chain == other.chain
    override fun hashCode() = tick.hashCode() * 31 + chain.hashCode()
}

/**
 * Each commit carries a value that depends on the one before it, so a participant who missed or reordered a
 * commit notices at the next one instead of drifting quietly.
 */
object CommitChain {
    fun next(previous: Long, tick: Long, entries: List<CommitEntry>, transfers: List<Transfer>): Long {
        val w = ByteWriter()
        w.writeFixedLong(previous); w.writeFixedLong(tick)
        w.writeVarInt(entries.size)
        for (e in entries) { w.writeVarLong(e.player.value); w.writeVarLong(e.seq); w.writeBytes(e.payload) }
        w.writeVarInt(transfers.size)
        for (t in transfers) {
            w.writeVarLong(t.trade.value); w.writeVarLong(t.from); w.writeVarLong(t.to)
            w.writeByte(t.asset.kind.tag); w.writeString(t.asset.key); w.writeVarLong(t.asset.amount)
        }
        val digest = Hmac.sha256(w.toByteArray())
        var out = 0L
        for (i in 0 until 8) out = (out shl 8) or (digest[i].toLong() and 0xFF)
        return out
    }
}

/**
 * The authoritative record of a session: every tick's commit in order. The server appends to it as ticks
 * run, answers resend requests from it and keeps it for the replay of a finished game.
 */
class CommitLog {
    private val ticks = ArrayList<CommittedTick>()

    /** The tick the next [append] must be for. */
    val nextTick: Long get() = ticks.size.toLong()
    val size: Int get() = ticks.size

    /** Chain value after the last commit; 0 for an empty log. */
    val head: Long get() = ticks.lastOrNull()?.chain ?: 0L

    fun append(tick: Long, entries: List<CommitEntry>, transfers: List<Transfer>): CommittedTick {
        require(tick == nextTick) { "expected commit for tick $nextTick but got $tick" }
        val c = CommittedTick(tick, entries.toList(), transfers.toList(), CommitChain.next(head, tick, entries, transfers))
        ticks += c
        return c
    }

    fun get(tick: Long): CommittedTick? = if (tick in 0 until ticks.size) ticks[tick.toInt()] else null

    /** Up to [count] consecutive commits starting at [from]. */
    fun range(from: Long, count: Int): List<CommittedTick> {
        if (from < 0 || from >= ticks.size) return emptyList()
        val end = minOf(ticks.size.toLong(), from + count).toInt()
        return ticks.subList(from.toInt(), end).toList()
    }

    fun all(): List<CommittedTick> = ticks.toList()

    fun chainBefore(tick: Long): Long = if (tick <= 0L) 0L else get(tick - 1)?.chain ?: 0L

    fun write(w: ByteWriter) {
        w.writeVarInt(VERSION)
        w.writeVarInt(ticks.size)
        for (c in ticks) {
            w.writeVarInt(c.entries.size)
            for (e in c.entries) { w.writeVarLong(e.player.value); w.writeVarLong(e.seq); w.writeBytes(e.payload) }
            w.writeVarInt(c.transfers.size)
            for (t in c.transfers) {
                w.writeVarLong(t.trade.value); w.writeVarLong(t.from); w.writeVarLong(t.to)
                w.writeByte(t.asset.kind.tag); w.writeString(t.asset.key); w.writeVarLong(t.asset.amount)
            }
            w.writeFixedLong(c.chain)
        }
    }

    /** Replaces the contents with a saved log, checking every chain value on the way in. */
    fun read(r: ByteReader) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported commit log version $version")
        val n = r.readVarInt()
        if (n < 0 || n > MAX_TICKS) throw ByteReader.CorruptDataException("implausible commit log length $n")
        val loaded = ArrayList<CommittedTick>(minOf(n, 4096))
        var prev = 0L
        for (tick in 0 until n) {
            val entryCount = r.readVarInt()
            if (entryCount < 0 || entryCount > NetLimits.MAX_COMMIT_ENTRIES) throw ByteReader.CorruptDataException("implausible entry count $entryCount")
            val entries = List(entryCount) { CommitEntry(PlayerId(r.readVarLong()), r.readVarLong(), r.readBytes()) }
            val transferCount = r.readVarInt()
            if (transferCount < 0 || transferCount > NetLimits.MAX_TRANSFERS) throw ByteReader.CorruptDataException("implausible transfer count $transferCount")
            val transfers = List(transferCount) {
                val trade = TradeId(r.readVarLong()); val from = r.readVarLong(); val to = r.readVarLong()
                val kind = AssetKind.fromTag(r.readByte()) ?: throw ByteReader.CorruptDataException("unknown asset kind")
                Transfer(trade, from, to, TradeAsset(kind, r.readString(), r.readVarLong()))
            }
            val chain = r.readFixedLong()
            if (chain != CommitChain.next(prev, tick.toLong(), entries, transfers)) throw ByteReader.CorruptDataException("commit chain broken at tick $tick")
            loaded += CommittedTick(tick.toLong(), entries, transfers, chain)
            prev = chain
        }
        ticks.clear(); ticks.addAll(loaded)
    }

    companion object {
        const val VERSION = 1
        private const val MAX_TICKS = 10_000_000
    }
}
