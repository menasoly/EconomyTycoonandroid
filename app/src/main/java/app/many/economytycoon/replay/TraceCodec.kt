package app.many.economytycoon.replay

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.persistence.CommandCodec
import app.many.economytycoon.persistence.SimStateCodec
import java.util.zip.CRC32

/** Writes a [Trace] as bytes. A short header and a trailing checksum catch files that are not traces or were cut off. */
object TraceCodec {
    const val VERSION = 1
    private val MAGIC = byteArrayOf(0x45, 0x54, 0x54, 0x52) // "ETTR"

    fun write(trace: Trace): ByteArray {
        val body = ByteWriter()
        body.writeVarInt(VERSION)
        SimStateCodec.writeConfig(trace.config, body)
        CommandCodec.writeLog(trace.log, body)
        body.writeVarInt(trace.checkpoints.size)
        for (c in trace.checkpoints) { body.writeVarLong(c.tick); body.writeString(c.hash) }
        body.writeVarInt(trace.content.size)
        for (p in trace.content) body.writeString(p)
        val payload = body.toByteArray()
        val out = ByteWriter()
        out.writeBytesRaw(MAGIC)
        out.writeBytes(payload)
        out.writeFixedLong(checksum(payload))
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): Trace {
        if (bytes.size < MAGIC.size + 9) throw ByteReader.CorruptDataException("trace is too short")
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) throw ByteReader.CorruptDataException("not a trace file")
        val r = ByteReader(bytes, MAGIC.size)
        val payload = r.readBytes()
        val stored = r.readFixedLong()
        if (stored != checksum(payload)) throw ByteReader.CorruptDataException("trace checksum does not match")
        val p = ByteReader(payload)
        val version = p.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported trace version $version")
        val config = SimStateCodec.readConfig(p)
        val log = CommandCodec.readLog(p)
        val n = p.readVarInt()
        if (n < 0 || n > MAX_CHECKPOINTS) throw ByteReader.CorruptDataException("bad checkpoint count $n")
        val checkpoints = List(n) { Checkpoint(p.readVarLong(), p.readString()) }
        val m = p.readVarInt()
        if (m < 0 || m > MAX_PACKS) throw ByteReader.CorruptDataException("bad content count $m")
        val content = List(m) { p.readString() }
        return Trace(config, log, checkpoints, content)
    }

    private fun checksum(bytes: ByteArray): Long = CRC32().also { it.update(bytes) }.value

    private const val MAX_CHECKPOINTS = 1_000_000
    private const val MAX_PACKS = 1_000
}
