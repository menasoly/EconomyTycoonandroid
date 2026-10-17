package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Outcome
import java.util.zip.CRC32

data class SeatRecord(val player: PlayerId, val name: String)

/**
 * Everything needed to play a finished multiplayer game again: who sat where, the commit of every tick and the
 * state hashes the server noted along the way. Replaying it with the same content must give the same hashes.
 */
class SessionRecord(
    val session: SessionId,
    val fingerprint: String,
    val seats: List<SeatRecord>,
    val log: CommitLog,
    val checkpoints: Map<Long, ByteArray>,
) {
    val ticks: Int get() = log.size
}

object SessionRecordCodec {
    const val VERSION = 1
    private val MAGIC = byteArrayOf(0x45, 0x54, 0x4D, 0x50) // "ETMP"
    private const val MAX_SEATS = 64
    private const val MAX_CHECKPOINTS = 10_000_000

    fun write(record: SessionRecord): ByteArray {
        val body = ByteWriter()
        body.writeVarInt(VERSION)
        body.writeVarLong(record.session.value); body.writeString(record.fingerprint)
        body.writeVarInt(record.seats.size)
        for (s in record.seats) { body.writeVarLong(s.player.value); body.writeString(s.name) }
        record.log.write(body)
        val cps = record.checkpoints.toSortedMap()
        body.writeVarInt(cps.size)
        for ((tick, hash) in cps) { body.writeVarLong(tick); body.writeBytesRaw(hash) }
        val payload = body.toByteArray()
        val out = ByteWriter()
        out.writeBytesRaw(MAGIC); out.writeBytes(payload); out.writeFixedLong(CRC32().also { it.update(payload) }.value)
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): SessionRecord {
        if (bytes.size < MAGIC.size + 9) throw ByteReader.CorruptDataException("session record is too short")
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) throw ByteReader.CorruptDataException("not a session record")
        val r = ByteReader(bytes, MAGIC.size)
        val payload = r.readBytes()
        if (r.readFixedLong() != CRC32().also { it.update(payload) }.value) throw ByteReader.CorruptDataException("session record checksum does not match")
        if (r.remaining != 0) throw ByteReader.CorruptDataException("data after the session record")
        val p = ByteReader(payload)
        val version = p.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported session record version $version")
        val session = SessionId(p.readVarLong()); val fingerprint = p.readString()
        val seatCount = p.readVarInt()
        if (seatCount < 0 || seatCount > MAX_SEATS) throw ByteReader.CorruptDataException("implausible seat count $seatCount")
        val seats = List(seatCount) { SeatRecord(PlayerId(p.readVarLong()), p.readString()) }
        val log = CommitLog(); log.read(p)
        val n = p.readVarInt()
        if (n < 0 || n > MAX_CHECKPOINTS) throw ByteReader.CorruptDataException("implausible checkpoint count $n")
        val cps = java.util.TreeMap<Long, ByteArray>()
        repeat(n) {
            val tick = p.readVarLong()
            if (p.remaining < NetLimits.HASH_BYTES) throw ByteReader.CorruptDataException("unexpected end of data")
            cps[tick] = ByteArray(NetLimits.HASH_BYTES) { p.readByte().toByte() }
        }
        return SessionRecord(session, fingerprint, seats, log, cps)
    }
}

/** How a replay compared with the recorded hashes. */
sealed class ReplayVerdict {
    data class Identical(val checkpoints: Int, val ticks: Long) : ReplayVerdict()
    data class Diverged(val tick: Long, val expected: String, val actual: String) : ReplayVerdict()
    data class Refused(val reason: String) : ReplayVerdict()

    val ok: Boolean get() = this is Identical
}

/** A replay in progress: the state of the game after the commits applied so far. */
class ReplayRun internal constructor(private val record: SessionRecord, val port: SimulationPort) {
    var applied: Long = 0L
        private set

    val finished: Boolean get() = applied >= record.log.size

    /** Applies the next tick's commit. Returns false when the record is exhausted. */
    fun step(): Boolean {
        val c = record.log.get(applied) ?: return false
        if (c.transfers.isNotEmpty()) port.applyTransfers(c.transfers)
        for (e in c.entries) {
            val parsed = CommandPayload.parse(e.payload)
            if (parsed is ParsedCommand.Ok) port.enqueue(e.player, parsed.command)
        }
        port.advance()
        applied++
        return true
    }

    fun runTo(tick: Long) { while (applied < tick && step()) Unit }
}

/** Plays a recorded session again on a fresh game and checks it against the hashes taken live. */
class SessionReplay(private val factory: () -> SimulationPort) {
    /** Starts a replay with the players seated as they were. Null if the game cannot be set up the same way. */
    fun start(record: SessionRecord): Outcome<ReplayRun> {
        val port = factory()
        if (port.fingerprint != record.fingerprint) return Outcome.reject("content_mismatch", "recorded with ${record.fingerprint} but this game has ${port.fingerprint}")
        for (seat in record.seats) {
            val r = port.seat(seat.player, seat.name)
            if (r is Outcome.Rejected) return Outcome.reject("seat_failed", "${seat.name}: ${r.rejection.message}")
        }
        return Outcome.ok(ReplayRun(record, port))
    }

    fun verify(record: SessionRecord): ReplayVerdict {
        val run = when (val s = start(record)) {
            is Outcome.Ok -> s.value
            is Outcome.Rejected -> return ReplayVerdict.Refused(s.rejection.message)
        }
        if (record.checkpoints.isEmpty()) return ReplayVerdict.Refused("the record has no checkpoints")
        var matched = 0
        val startHash = record.checkpoints[0L]
        if (startHash != null) {
            if (!run.port.stateHash().contentEquals(startHash)) return ReplayVerdict.Diverged(0L, Hex.encode(startHash).take(12), Hex.encode(run.port.stateHash()).take(12))
            matched++
        }
        while (run.step()) {
            val expected = record.checkpoints[run.applied] ?: continue
            val actual = run.port.stateHash()
            if (!actual.contentEquals(expected)) return ReplayVerdict.Diverged(run.applied, Hex.encode(expected).take(12), Hex.encode(actual).take(12))
            matched++
        }
        return ReplayVerdict.Identical(matched, run.applied)
    }
}
