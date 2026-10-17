package app.many.economytycoon.workshop

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.persistence.SaveStore
import java.util.zip.CRC32

/** Binary form of the library: a header, the entries, and a checksum that catches a cut-off or edited file. */
object LibraryCodec {
    const val VERSION = 1
    private val MAGIC = byteArrayOf(0x45, 0x54, 0x57, 0x4C) // "ETWL"

    fun toBytes(lib: WorkshopLibrary): ByteArray {
        val w = ByteWriter()
        w.writeVarInt(VERSION)
        w.writeVarLong(lib.nextIdValue()); w.writeVarLong(lib.clockValue())
        val entries = lib.all()
        w.writeVarInt(entries.size)
        for (e in entries) {
            w.writeVarLong(e.id); w.writeString(e.folder); w.writeString(e.note); w.writeBoolean(e.starred)
            w.writeVarLong(e.created); w.writeVarLong(e.updated)
            w.writeVarInt(e.tags.size); for (t in e.tags) w.writeString(t)
            DraftCodec.write(e.draft, w)
        }
        val payload = w.toByteArray()
        val out = ByteWriter()
        out.writeBytesRaw(MAGIC)
        out.writeBytes(payload)
        out.writeFixedLong(CRC32().also { it.update(payload) }.value)
        return out.toByteArray()
    }

    fun fromBytes(bytes: ByteArray, capacity: Int = WorkshopLibrary.MAX_ENTRIES): WorkshopLibrary {
        if (bytes.size < MAGIC.size + 9) throw ByteReader.CorruptDataException("library is too short")
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) throw ByteReader.CorruptDataException("not a library file")
        val r = ByteReader(bytes, MAGIC.size)
        val payload = r.readBytes()
        if (r.readFixedLong() != CRC32().also { it.update(payload) }.value) throw ByteReader.CorruptDataException("library checksum does not match")
        val p = ByteReader(payload)
        val version = p.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported library version $version")
        val next = p.readVarLong(); val clock = p.readVarLong()
        val n = p.readVarInt()
        if (n < 0 || n > capacity) throw ByteReader.CorruptDataException("bad entry count $n")
        val entries = List(n) {
            val id = p.readVarLong(); val folder = p.readString(); val note = p.readString(); val starred = p.readBoolean()
            val created = p.readVarLong(); val updated = p.readVarLong()
            val tagCount = p.readVarInt()
            if (tagCount < 0 || tagCount > WorkshopLibrary.MAX_TAGS) throw ByteReader.CorruptDataException("bad tag count $tagCount")
            val tags = List(tagCount) { p.readString() }
            LibraryEntry(id, DraftCodec.read(p), folder, tags, note, starred, created, updated)
        }
        if (entries.map { it.id }.distinct().size != entries.size) throw ByteReader.CorruptDataException("duplicate entry ids")
        return WorkshopLibrary(capacity).also { it.restore(entries, next, clock) }
    }
}

/** Keeps the library in a [SaveStore] under one key. A file that cannot be read gives an empty library, and is left alone until the next save. */
class LibraryStore(private val store: SaveStore, private val key: String = KEY) {
    fun load(): WorkshopLibrary {
        val bytes = store.get(key) ?: return WorkshopLibrary()
        return try { LibraryCodec.fromBytes(bytes) } catch (e: ByteReader.CorruptDataException) { WorkshopLibrary() }
    }

    /** Whether something is stored but cannot be read, so a screen can warn before it is overwritten. */
    fun damaged(): Boolean {
        val bytes = store.get(key) ?: return false
        return try { LibraryCodec.fromBytes(bytes); false } catch (e: ByteReader.CorruptDataException) { true }
    }

    fun save(library: WorkshopLibrary) = store.put(key, LibraryCodec.toBytes(library))
    fun reset() = store.remove(key)

    companion object { const val KEY = "workshop.library" }
}
