package app.many.economytycoon.persistence

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import java.util.zip.CRC32

/** A named block of a save file. */
class Section(val name: String, val payload: ByteArray)

/**
 * The container every save is written in: a magic number, a format version, then named sections that each carry
 * their own checksum, and a checksum over the whole file. A damaged byte anywhere is found before any engine
 * state is touched, and the section that failed is named so the error is useful.
 */
object SectionedFile {
    const val MAGIC = 0x45545356_30303031L
    const val FORMAT = 1
    const val MAX_SECTIONS = 256

    fun write(sections: List<Section>): ByteArray {
        require(sections.map { it.name }.toSet().size == sections.size) { "duplicate section names" }
        require(sections.size <= MAX_SECTIONS) { "too many sections" }
        val w = ByteWriter()
        w.writeFixedLong(MAGIC)
        w.writeVarInt(FORMAT)
        w.writeVarInt(sections.size)
        for (s in sections) {
            w.writeString(s.name)
            w.writeBytes(s.payload)
            w.writeFixedLong(checksum(s.name, s.payload))
        }
        val body = w.toByteArray()
        val out = ByteWriter()
        out.writeBytesRaw(body)
        out.writeFixedLong(crc(body))
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): List<Section> {
        if (bytes.size < 16) throw ByteReader.CorruptDataException("file too short to be a save")
        val bodyLength = bytes.size - 8
        val stored = ByteReader(bytes, bodyLength).readFixedLong()
        val body = bytes.copyOfRange(0, bodyLength)
        if (crc(body) != stored) throw ByteReader.CorruptDataException("save file checksum mismatch")
        val r = ByteReader(body)
        if (r.readFixedLong() != MAGIC) throw ByteReader.CorruptDataException("not an EconomyTycoon save")
        val format = r.readVarInt()
        if (format != FORMAT) throw ByteReader.CorruptDataException("unsupported save container format $format")
        val count = r.readVarInt()
        if (count < 0 || count > MAX_SECTIONS) throw ByteReader.CorruptDataException("implausible section count $count")
        val out = ArrayList<Section>(count)
        val seen = HashSet<String>()
        repeat(count) {
            val name = r.readString()
            val payload = r.readBytes()
            if (r.readFixedLong() != checksum(name, payload)) throw ByteReader.CorruptDataException("section '$name' is damaged")
            if (!seen.add(name)) throw ByteReader.CorruptDataException("section '$name' appears twice")
            out += Section(name, payload)
        }
        if (r.remaining != 0) throw ByteReader.CorruptDataException("unexpected data after the last section")
        return out
    }

    private fun crc(b: ByteArray): Long = CRC32().also { it.update(b, 0, b.size) }.value

    private fun checksum(name: String, payload: ByteArray): Long {
        val c = CRC32()
        val n = name.toByteArray(Charsets.UTF_8)
        c.update(n, 0, n.size)
        c.update(payload, 0, payload.size)
        return c.value
    }
}
