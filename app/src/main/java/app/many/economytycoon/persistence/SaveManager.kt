package app.many.economytycoon.persistence

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.simulation.Simulation

enum class SlotKind { MANUAL, AUTO }

/** A save as the load screen shows it. A slot whose file cannot be read is listed as damaged, not hidden. */
data class SaveSlot(
    val id: String,
    val label: String,
    val kind: SlotKind,
    val savedAtMillis: Long,
    val tick: Long,
    val seed: Long,
    val sizeBytes: Int,
    val damaged: Boolean = false,
    val hasBackup: Boolean = false,
)

class LoadedGame(val simulation: Simulation, val slot: String, val recoveredFromBackup: Boolean)

class SaveNotFoundException(slot: String) : RuntimeException("no save named '$slot'")

/**
 * Slots, autosaves and recovery on top of a [SaveStore]. Each slot is a snapshot file plus a small label
 * record. Overwriting a slot keeps the previous file as its backup, so a save that turns out to be bad does not
 * cost the last good one. Autosaves rotate through a fixed number of slots, oldest first.
 */
class SaveManager(
    private val store: SaveStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    val autosaveSlots: Int = 3,
    val autosaveEveryTicks: Long = 30L,
) {
    private var lastAutosaveTick = Long.MIN_VALUE

    fun save(slot: String, sim: Simulation, label: String, kind: SlotKind = SlotKind.MANUAL): SaveSlot {
        SaveKeys.check(slot)
        val bytes = SimulationSnapshot.save(sim)
        val info = SimulationSnapshot.peek(bytes)
        store.get(file(slot))?.let { previous -> store.put(backup(slot), previous) }
        store.put(file(slot), bytes)
        val now = clock()
        store.put(meta(slot), writeMeta(label.trim().take(MAX_LABEL), kind, now))
        return SaveSlot(slot, label.trim().take(MAX_LABEL), kind, now, info.tick, info.seed, bytes.size, hasBackup = store.contains(backup(slot)))
    }

    /** Writes an autosave if enough ticks have passed since the last one; returns it, or null when not due. */
    fun autosaveIfDue(sim: Simulation): SaveSlot? {
        val tick = sim.tick.value
        if (lastAutosaveTick != Long.MIN_VALUE && tick - lastAutosaveTick < autosaveEveryTicks) return null
        val target = nextAutosaveSlot()
        lastAutosaveTick = tick
        return save(target, sim, "Autosave, day ${tick + 1}", SlotKind.AUTO)
    }

    fun load(slot: String, content: ContentRepository, migrations: Migrations = Migrations()): LoadedGame {
        SaveKeys.check(slot)
        val primary = store.get(file(slot))
        val backup = store.get(backup(slot))
        if (primary == null && backup == null) throw SaveNotFoundException(slot)
        var failure: Exception? = null
        if (primary != null) {
            try { return LoadedGame(SimulationSnapshot.load(primary, content, migrations), slot, false) }
            catch (e: ByteReader.CorruptDataException) { failure = e }
        }
        if (backup != null) {
            try { return LoadedGame(SimulationSnapshot.load(backup, content, migrations), slot, true) }
            catch (e: ByteReader.CorruptDataException) { failure = failure ?: e }
        }
        throw failure ?: SaveNotFoundException(slot)
    }

    fun list(): List<SaveSlot> =
        store.keys().filter { it.endsWith(FILE_SUFFIX) }.map { it.removeSuffix(FILE_SUFFIX) }.map { describe(it) }
            .sortedWith(compareByDescending<SaveSlot> { it.savedAtMillis }.thenBy { it.id })

    fun delete(slot: String) {
        SaveKeys.check(slot)
        store.remove(file(slot)); store.remove(backup(slot)); store.remove(meta(slot))
    }

    fun rename(slot: String, label: String) {
        val m = readMeta(slot) ?: throw SaveNotFoundException(slot)
        store.put(meta(slot), writeMeta(label.trim().take(MAX_LABEL), m.kind, m.savedAt))
    }

    private fun nextAutosaveSlot(): String {
        val ring = List(autosaveSlots) { "auto-$it" }
        val free = ring.firstOrNull { !store.contains(file(it)) }
        if (free != null) return free
        return ring.minByOrNull { readMeta(it)?.savedAt ?: Long.MIN_VALUE } ?: ring.first()
    }

    private fun describe(slot: String): SaveSlot {
        val meta = readMeta(slot)
        val bytes = store.get(file(slot))
        val hasBackup = store.contains(backup(slot))
        val kind = meta?.kind ?: SlotKind.MANUAL
        val label = meta?.label ?: slot
        val savedAt = meta?.savedAt ?: 0L
        return try {
            val info = SimulationSnapshot.peek(bytes ?: ByteArray(0))
            SaveSlot(slot, label, kind, savedAt, info.tick, info.seed, bytes!!.size, hasBackup = hasBackup)
        } catch (e: ByteReader.CorruptDataException) {
            SaveSlot(slot, label, kind, savedAt, 0L, 0L, bytes?.size ?: 0, damaged = true, hasBackup = hasBackup)
        }
    }

    private class Meta(val label: String, val kind: SlotKind, val savedAt: Long)

    private fun writeMeta(label: String, kind: SlotKind, savedAt: Long): ByteArray =
        ByteWriter().also { it.writeVarInt(1); it.writeString(label); it.writeVarInt(kind.ordinal); it.writeFixedLong(savedAt) }.toByteArray()

    private fun readMeta(slot: String): Meta? {
        val bytes = store.get(meta(slot)) ?: return null
        return try {
            val r = ByteReader(bytes)
            if (r.readVarInt() != 1) return null
            Meta(r.readString(), SlotKind.values().getOrElse(r.readVarInt()) { SlotKind.MANUAL }, r.readFixedLong())
        } catch (e: ByteReader.CorruptDataException) { null }
    }

    private fun file(slot: String) = slot + FILE_SUFFIX
    private fun backup(slot: String) = slot + BACKUP_SUFFIX
    private fun meta(slot: String) = slot + META_SUFFIX

    companion object {
        const val FILE_SUFFIX = ".etsave"
        const val BACKUP_SUFFIX = ".etsave.bak"
        const val META_SUFFIX = ".etmeta"
        const val MAX_LABEL = 60
    }
}
