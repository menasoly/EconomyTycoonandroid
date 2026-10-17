package app.many.economytycoon.blueprint

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.persistence.SaveStore

/**
 * A blueprint waiting for its building to finish. Building takes days, so the configuration cannot be applied when the
 * player asks for it; this remembers what to apply once a new facility of [type] called [facilityName] shows up in [region].
 */
data class PendingApply(
    val id: Long,
    val blueprint: Blueprint,
    val region: RegionId,
    val facilityName: String,
    val startedDay: Long,
    /** Facilities that already existed when the build was ordered; they are never mistaken for the new one. */
    val knownFacilities: List<Long>,
    val staffing: Boolean = true,
    val trading: Boolean = true,
    val storage: Boolean = true,
    val schedule: Boolean = true,
) {
    val type: FacilityTypeId get() = blueprint.facilityType

    fun options() = ApplyOptions(staffing = staffing, trading = trading, storage = storage, schedule = schedule)
}

/** A pending apply together with the facility that fulfils it. */
data class ReadyApply(val pending: PendingApply, val facility: FacilityId)

/** The player's open build-then-configure requests. */
class PendingApplyBook(private val max: Int = MAX) {
    private val items = java.util.TreeMap<Long, PendingApply>()
    private var nextId = 1L

    val size: Int get() = items.size
    fun all(): List<PendingApply> = items.values.toList()
    fun get(id: Long): PendingApply? = items[id]

    /** Registers a request; null when the book is full. */
    fun add(blueprint: Blueprint, region: RegionId, facilityName: String, day: Long, known: Collection<FacilityId>, options: ApplyOptions = ApplyOptions()): PendingApply? {
        if (items.size >= max) return null
        val p = PendingApply(nextId++, blueprint, region, facilityName, day, known.map { it.value }.sorted(), options.staffing, options.trading, options.storage, options.schedule)
        items[p.id] = p
        return p
    }

    fun remove(id: Long): Boolean = items.remove(id) != null

    /** Drops requests older than [maxAgeDays]; returns them so the player can be told. */
    fun expire(day: Long, maxAgeDays: Long = EXPIRY_DAYS): List<PendingApply> {
        val old = items.values.filter { day - it.startedDay > maxAgeDays }
        old.forEach { items.remove(it.id) }
        return old
    }

    /**
     * Requests whose building has appeared. Each facility fulfils at most one request, the oldest first.
     */
    fun ready(facilities: List<FacilityChoice>): List<ReadyApply> {
        val taken = HashSet<Long>()
        val out = ArrayList<ReadyApply>()
        for (p in items.values) {
            val match = facilities.filter { it.type == p.type && it.name == p.facilityName && it.id.value !in p.knownFacilities && it.id.value !in taken }
                .minByOrNull { it.id } ?: continue
            taken += match.id.value
            out += ReadyApply(p, match.id)
        }
        return out
    }

    fun toBytes(): ByteArray {
        val w = ByteWriter()
        w.writeVarInt(VERSION); w.writeVarLong(nextId); w.writeVarInt(items.size)
        for (p in items.values) {
            w.writeVarLong(p.id); w.writeVarLong(p.region.value); w.writeString(p.facilityName); w.writeVarLong(p.startedDay)
            w.writeVarInt(p.knownFacilities.size); p.knownFacilities.forEach { w.writeVarLong(it) }
            w.writeBoolean(p.staffing); w.writeBoolean(p.trading); w.writeBoolean(p.storage); w.writeBoolean(p.schedule)
            w.writeBytes(BlueprintCodec.encode(p.blueprint))
        }
        return w.toByteArray()
    }

    companion object {
        const val VERSION = 1
        const val MAX = 10
        const val EXPIRY_DAYS = 365L
        const val KEY = "blueprints.pending"

        /** Reads a saved book; anything unreadable gives an empty one. */
        fun fromBytes(bytes: ByteArray?, max: Int = MAX): PendingApplyBook {
            val book = PendingApplyBook(max)
            if (bytes == null) return book
            try {
                val r = ByteReader(bytes)
                if (r.readVarInt() != VERSION) return PendingApplyBook(max)
                val next = r.readVarLong()
                val n = r.readVarInt()
                require(n in 0..max) { "implausible count" }
                repeat(n) {
                    val id = r.readVarLong(); val region = RegionId(r.readVarLong()); val name = r.readString(); val day = r.readVarLong()
                    val known = List(r.readVarInt().also { require(it in 0..100_000) { "implausible count" } }) { r.readVarLong() }
                    val flags = List(4) { r.readBoolean() }
                    val d = BlueprintCodec.decode(r.readBytes())
                    if (d is Decoded.Ok) book.items[id] = PendingApply(id, d.blueprint, region, name, day, known, flags[0], flags[1], flags[2], flags[3])
                }
                book.nextId = maxOf(next, (book.items.keys.maxOrNull() ?: 0L) + 1L)
                return book
            } catch (e: ByteReader.CorruptDataException) {
                return PendingApplyBook(max)
            } catch (e: IllegalArgumentException) {
                return PendingApplyBook(max)
            }
        }
    }

    fun hashInto(h: app.many.economytycoon.core.CanonicalHasher) {
        h.section("blueprint.pending").putLong(nextId).putInt(items.size)
        for (p in items.values) h.putLong(p.id).putString(p.blueprint.hash).putLong(p.region.value).putString(p.facilityName).putLong(p.startedDay).putInt(p.knownFacilities.size)
    }
}

/** Keeps pending requests in a [SaveStore]. */
class PendingApplyStore(private val store: SaveStore, private val key: String = PendingApplyBook.KEY) {
    fun load(): PendingApplyBook = PendingApplyBook.fromBytes(store.get(key))
    fun save(book: PendingApplyBook) = store.put(key, book.toBytes())
    fun reset() = store.remove(key)
}
