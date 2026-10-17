package app.many.economytycoon.net

import java.util.zip.CRC32

/** A snapshot ready to be sent: the bytes and what a receiver needs to check them. */
class SnapshotBundle(
    val id: Long,
    val tick: Long,
    val bytes: ByteArray,
    val stateHash: ByteArray,
    val chain: Long,
    val chunkSize: Int = DEFAULT_CHUNK,
) {
    init {
        require(bytes.isNotEmpty() && bytes.size <= NetLimits.MAX_SNAPSHOT_BYTES) { "snapshot size out of range" }
        require(chunkSize in 1..NetLimits.MAX_CHUNK_BYTES) { "chunk size out of range" }
    }

    val chunkCount: Int = (bytes.size + chunkSize - 1) / chunkSize
    val digest: ByteArray = Hmac.sha256(bytes)

    fun header() = NetMessage.SnapshotHeader(id, tick, bytes.size, chunkSize, chunkCount, digest, stateHash, chain)

    fun chunk(index: Int): NetMessage.SnapshotChunk {
        require(index in 0 until chunkCount) { "no chunk $index" }
        val from = index * chunkSize
        val data = bytes.copyOfRange(from, minOf(bytes.size, from + chunkSize))
        return NetMessage.SnapshotChunk(id, index, data, crc(data))
    }

    companion object {
        const val DEFAULT_CHUNK = 8 * 1024
        fun crc(data: ByteArray): Long = CRC32().also { it.update(data) }.value
    }
}

/**
 * Sends one snapshot to one peer with a sliding window. The receiver acknowledges how many chunks it holds
 * in a row; anything it has not confirmed within [retransmitAfterMs] is sent again from that point, so loss,
 * duplication and reordering only cost time. A request can resume from chunk N after a dropped connection.
 */
class SnapshotSender(val bundle: SnapshotBundle, private val window: Int = 8, private val retransmitAfterMs: Long = 200L) {
    private var acked = 0
    private var sentUpTo = 0
    private var lastProgress = Long.MIN_VALUE
    private var headerSent = false

    var complete = false
        private set

    val chunksAcknowledged: Int get() = acked

    /** Starts (or restarts) from [fromChunk], as a request asked. */
    fun begin(fromChunk: Int) {
        val start = fromChunk.coerceIn(0, bundle.chunkCount)
        acked = start; sentUpTo = start; headerSent = false; complete = false; lastProgress = Long.MIN_VALUE
    }

    fun onAck(ack: NetMessage.SnapshotAck, now: Long) {
        if (ack.snapshotId != bundle.id || complete) return
        if (ack.complete) { complete = true; acked = bundle.chunkCount; return }
        if (ack.next > acked && ack.next <= bundle.chunkCount) {
            acked = ack.next
            if (sentUpTo < acked) sentUpTo = acked
            lastProgress = now
        }
    }

    /** The messages to send now: the header first, then as many chunks as the window allows. */
    fun pump(now: Long): List<NetMessage> {
        if (complete) return emptyList()
        val out = ArrayList<NetMessage>()
        if (!headerSent) { out += bundle.header(); headerSent = true; lastProgress = now }
        if (sentUpTo > acked && now - lastProgress >= retransmitAfterMs) {
            sentUpTo = acked
            lastProgress = now
            out += bundle.header()
        }
        while (sentUpTo < bundle.chunkCount && sentUpTo - acked < window) {
            out += bundle.chunk(sentUpTo)
            sentUpTo++
        }
        return out
    }
}

/** A snapshot that arrived whole and matched its digest. */
class ReceivedSnapshot(val id: Long, val tick: Long, val bytes: ByteArray, val stateHash: ByteArray, val chain: Long)

/** What came of one received piece. */
class ReceiveResult(val ack: NetMessage.SnapshotAck?, val completed: ReceivedSnapshot?, val error: NetError?) {
    companion object {
        val IGNORED = ReceiveResult(null, null, null)
    }
}

/**
 * Collects the chunks of one snapshot in any order, drops damaged or foreign ones, and hands back the whole
 * once every chunk is present and the digest matches. Progress survives a lost connection, so the next request
 * can say where to resume.
 */
class SnapshotReceiver {
    private var header: NetMessage.SnapshotHeader? = null
    private var chunks: Array<ByteArray?> = emptyArray()
    private var held = 0

    val snapshotId: Long get() = header?.snapshotId ?: 0L
    val total: Int get() = header?.chunkCount ?: 0
    val received: Int get() = held

    /** First chunk not yet held: where a resumed transfer should start. */
    fun firstMissing(): Int {
        for (i in chunks.indices) if (chunks[i] == null) return i
        return chunks.size
    }

    fun reset() { header = null; chunks = emptyArray(); held = 0 }

    fun onHeader(h: NetMessage.SnapshotHeader): ReceiveResult {
        val current = header
        if (current != null && current.snapshotId == h.snapshotId) return ReceiveResult(NetMessage.SnapshotAck(h.snapshotId, firstMissing(), false), null, null)
        header = h
        chunks = arrayOfNulls(h.chunkCount)
        held = 0
        return ReceiveResult(NetMessage.SnapshotAck(h.snapshotId, 0, false), null, null)
    }

    fun onChunk(c: NetMessage.SnapshotChunk): ReceiveResult {
        val h = header ?: return ReceiveResult.IGNORED
        if (c.snapshotId != h.snapshotId) return ReceiveResult.IGNORED
        if (c.index < 0 || c.index >= h.chunkCount) return ReceiveResult(null, null, NetError.BAD_FRAME)
        val expected = if (c.index == h.chunkCount - 1) h.totalBytes - h.chunkSize * (h.chunkCount - 1) else h.chunkSize
        if (c.data.size != expected) return ReceiveResult(null, null, NetError.BAD_FRAME)
        if (SnapshotBundle.crc(c.data) != c.crc) return ReceiveResult(null, null, NetError.BAD_CHECKSUM)
        if (chunks[c.index] == null) { chunks[c.index] = c.data; held++ }
        if (held < h.chunkCount) return ReceiveResult(NetMessage.SnapshotAck(h.snapshotId, firstMissing(), false), null, null)
        val whole = ByteArray(h.totalBytes)
        var at = 0
        for (piece in chunks.filterNotNull()) { piece.copyInto(whole, at); at += piece.size }
        if (!Hmac.constantTimeEquals(Hmac.sha256(whole), h.digest)) {
            reset()
            return ReceiveResult(null, null, NetError.BAD_CHECKSUM)
        }
        return ReceiveResult(NetMessage.SnapshotAck(h.snapshotId, h.chunkCount, true), ReceivedSnapshot(h.snapshotId, h.tick, whole, h.stateHash, h.chain), null)
    }
}
