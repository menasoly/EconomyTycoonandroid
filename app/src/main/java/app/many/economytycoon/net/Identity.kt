package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.DeterministicRandom

/**
 * Where session secrets come from. Production wiring supplies a source backed by the platform's secure random;
 * [SeededTokenSource] is reproducible and therefore only for tests, replays of a lobby and local play.
 */
interface TokenSource {
    fun next(size: Int): ByteArray
}

class SeededTokenSource(seed: Long) : TokenSource {
    private val random = DeterministicRandom(seed)
    override fun next(size: Int): ByteArray {
        val out = ByteArray(size)
        var i = 0
        while (i < size) {
            var word = random.nextLong()
            var k = 0
            while (k < 8 && i < size) { out[i++] = word.toByte(); word = word ushr 8; k++ }
        }
        return out
    }
}

/** What is signed and how. Every signature names the session and the player so it cannot be moved elsewhere. */
object Signing {
    private val KEY_LABEL = "econ-net/key/1".toByteArray(Charsets.UTF_8)

    /** The per-player signing key derived from the secret token handed out at join time. */
    fun deriveKey(token: ByteArray, session: SessionId, player: PlayerId): ByteArray {
        val w = ByteWriter()
        w.writeBytesRaw(KEY_LABEL); w.writeFixedLong(session.value); w.writeFixedLong(player.value)
        return Hmac.sign(token, w.toByteArray())
    }

    fun commandMac(key: ByteArray, session: SessionId, player: PlayerId, seq: Long, targetTick: Long, payload: ByteArray): ByteArray {
        val w = ByteWriter()
        w.writeByte(1); w.writeFixedLong(session.value); w.writeFixedLong(player.value)
        w.writeFixedLong(seq); w.writeFixedLong(targetTick); w.writeBytes(payload)
        return Hmac.tag(key, w.toByteArray())
    }

    fun resumeProof(key: ByteArray, session: SessionId, player: PlayerId, nonce: Long, lastTick: Long): ByteArray {
        val w = ByteWriter()
        w.writeByte(2); w.writeFixedLong(session.value); w.writeFixedLong(player.value)
        w.writeFixedLong(nonce); w.writeFixedLong(lastTick)
        return Hmac.tag(key, w.toByteArray())
    }
}

/** What the server remembers about one participant between connections. */
class PlayerRecord(val id: PlayerId, var name: String, val key: ByteArray) {
    /** Highest resume nonce seen; a resume must present a larger one, so a captured hello is useless. */
    var lastResumeNonce: Long = 0L
        internal set
}

/**
 * Issues player ids and secrets and checks resume proofs. Ids are handed out in join order starting at 1,
 * which keeps every ordering that depends on them reproducible.
 */
class IdentityBook(val session: SessionId, private val tokens: TokenSource) {
    private val records = java.util.TreeMap<PlayerId, PlayerRecord>()
    private val banned = java.util.TreeSet<PlayerId>()
    private var nextId = 1L

    /** A new participant and the secret token to give them once; only the derived key is kept. */
    fun enroll(name: String): Pair<PlayerRecord, ByteArray> {
        val id = PlayerId(nextId++)
        val token = tokens.next(NetLimits.TOKEN_BYTES)
        val record = PlayerRecord(id, name, Signing.deriveKey(token, session, id))
        records[id] = record
        return record to token
    }

    fun get(id: PlayerId): PlayerRecord? = records[id]
    fun all(): List<PlayerRecord> = records.values.toList()
    val size: Int get() = records.size

    fun ban(id: PlayerId) { banned += id }
    fun isBanned(id: PlayerId): Boolean = id in banned
    fun bannedIds(): List<PlayerId> = banned.toList()

    /** True when [proof] shows the caller holds the secret for [id] and [nonce] has not been used before. */
    fun acceptResume(id: PlayerId, nonce: Long, lastTick: Long, proof: ByteArray): Boolean {
        val r = records[id] ?: return false
        if (id in banned || nonce <= r.lastResumeNonce) return false
        if (!Hmac.constantTimeEquals(Signing.resumeProof(r.key, session, id, nonce, lastTick), proof)) return false
        r.lastResumeNonce = nonce
        return true
    }

    fun rename(id: PlayerId, name: String) { records[id]?.name = name }

    // ---- persistence ----

    fun write(w: ByteWriter) {
        w.writeVarInt(VERSION)
        w.writeVarLong(nextId)
        w.writeVarInt(records.size)
        for (r in records.values) { w.writeVarLong(r.id.value); w.writeString(r.name); w.writeBytes(r.key); w.writeVarLong(r.lastResumeNonce) }
        w.writeVarInt(banned.size)
        for (b in banned) w.writeVarLong(b.value)
    }

    fun read(r: ByteReader) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported identity book version $version")
        records.clear(); banned.clear()
        nextId = r.readVarLong()
        val n = r.readVarInt()
        if (n < 0 || n > MAX_RECORDS) throw ByteReader.CorruptDataException("implausible player count $n")
        repeat(n) {
            val id = PlayerId(r.readVarLong())
            val rec = PlayerRecord(id, r.readString(), r.readBytes())
            rec.lastResumeNonce = r.readVarLong()
            records[id] = rec
        }
        val b = r.readVarInt()
        if (b < 0 || b > MAX_RECORDS) throw ByteReader.CorruptDataException("implausible ban count $b")
        repeat(b) { banned += PlayerId(r.readVarLong()) }
    }

    companion object {
        const val VERSION = 1
        private const val MAX_RECORDS = 100_000
    }
}
