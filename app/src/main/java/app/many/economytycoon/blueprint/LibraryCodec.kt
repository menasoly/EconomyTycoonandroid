package app.many.economytycoon.blueprint

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.persistence.SaveStore

/** What loading a saved library gave. */
data class LibraryLoad(
    val library: BlueprintLibrary,
    /** Entries stored in an older blueprint schema and converted on the way in. */
    val migrated: Int,
    /** Entries that could not be read and were left out. */
    val dropped: Int,
    /** True when the file as a whole was unreadable and an empty library was returned. */
    val unreadable: Boolean,
) {
    val isClean: Boolean get() = dropped == 0 && !unreadable
}

/**
 * Binary save format of a [BlueprintLibrary]. Each blueprint is stored as its own length-prefixed block, so one
 * damaged entry costs only itself. A new library version may append fields; readers stop at the end of what they know.
 */
object LibraryCodec {
    const val VERSION = 1

    fun toBytes(lib: BlueprintLibrary): ByteArray {
        val w = ByteWriter()
        w.writeVarInt(VERSION)
        w.writeVarLong(lib.nextIdValue())
        val folders = lib.explicitFolderList()
        w.writeVarInt(folders.size)
        folders.forEach { w.writeString(it) }
        val all = lib.all()
        w.writeVarInt(all.size)
        for (e in all) {
            w.writeVarLong(e.id.value); w.writeString(e.folder); w.writeBoolean(e.favourite); w.writeVarLong(e.addedDay)
            w.writeVarLong(e.copyOf?.value ?: -1L); w.writeVarInt(e.revision)
            w.writeVarInt(e.tags.size); e.tags.forEach { w.writeString(it) }
            w.writeBytes(BlueprintCodec.encode(e.blueprint))
        }
        return w.toByteArray()
    }

    /** Reads a saved library. Never throws: anything unreadable shrinks the result instead. */
    fun fromBytes(bytes: ByteArray?, limits: LibraryLimits = LibraryLimits()): LibraryLoad {
        val lib = BlueprintLibrary(limits)
        if (bytes == null) return LibraryLoad(lib, 0, 0, false)
        var migrated = 0
        var dropped = 0
        try {
            val r = ByteReader(bytes)
            val version = r.readVarInt()
            if (version != VERSION) return LibraryLoad(BlueprintLibrary(limits), 0, 0, true)
            val next = r.readVarLong()
            repeat(r.readVarInt().also { require(it in 0..limits.maxFolders * 4) { "implausible folder count" } }) {
                BlueprintLibrary.normalizeFolder(r.readString())?.takeIf { it.isNotEmpty() }?.let { lib.restoreFolder(it) }
            }
            val n = r.readVarInt()
            require(n in 0..limits.maxEntries) { "implausible entry count" }
            val ids = HashSet<Long>()
            repeat(n) {
                val id = r.readVarLong(); val folder = r.readString(); val fav = r.readBoolean(); val day = r.readVarLong()
                val copyOf = r.readVarLong().takeIf { it >= 0 }; val rev = r.readVarInt()
                val tags = List(r.readVarInt().also { require(it in 0..limits.maxTagsPerEntry) { "implausible tag count" } }) { r.readString() }
                val block = r.readBytes()
                when (val d = BlueprintCodec.decode(block)) {
                    is Decoded.Failed -> dropped++
                    is Decoded.Ok -> {
                        val path = BlueprintLibrary.normalizeFolder(folder)
                        val clean = BlueprintLibrary.normalizeTags(tags)
                        if (path == null || clean == null || id <= 0 || !ids.add(id)) dropped++
                        else {
                            if (d.schema < BlueprintCodec.SCHEMA) migrated++
                            lib.restore(LibraryEntry(BlueprintId(id), d.blueprint, path, clean, fav, day.coerceAtLeast(0L), copyOf?.let { BlueprintId(it) }, rev.coerceAtLeast(1)))
                        }
                    }
                }
            }
            lib.restoreNextId(next)
            return LibraryLoad(salvage(lib), migrated, dropped, false)
        } catch (e: ByteReader.CorruptDataException) {
            return LibraryLoad(salvage(lib), migrated, dropped, lib.size == 0)
        } catch (e: IllegalArgumentException) {
            return LibraryLoad(salvage(lib), migrated, dropped, lib.size == 0)
        }
    }

    /** Keeps what was read before the damage; copies pointing at entries that did not survive become originals. */
    private fun salvage(lib: BlueprintLibrary): BlueprintLibrary {
        val ids = lib.all().map { it.id }.toSet()
        for (e in lib.all()) if (e.copyOf != null && e.copyOf !in ids) lib.restore(e.copy(copyOf = null))
        return lib
    }
}

/** Keeps the player's library in a [SaveStore] under one key. */
class BlueprintStore(private val store: SaveStore, private val key: String = KEY, private val limits: LibraryLimits = LibraryLimits()) {
    fun load(): LibraryLoad = LibraryCodec.fromBytes(store.get(key), limits)
    fun save(library: BlueprintLibrary) = store.put(key, LibraryCodec.toBytes(library))
    fun reset() = store.remove(key)

    companion object { const val KEY = "blueprints.library" }
}
