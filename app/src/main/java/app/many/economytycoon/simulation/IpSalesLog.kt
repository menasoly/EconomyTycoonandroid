package app.many.economytycoon.simulation

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick

/**
 * What each company sold, day by day, with the technologies behind the goods. The patent module asks what a licensee
 * sold using a technology in a country; this is where the answer comes from. It keeps a little over a year of days and
 * is saved with the patent module, so a restored game answers the same questions as the one that was saved.
 */
class IpSalesLog(private val retentionDays: Long = RETENTION_DAYS) {
    data class Key(val company: Long, val tech: String, val country: Long, val family: Int) : Comparable<Key> {
        override fun compareTo(other: Key): Int = compareValuesBy(this, other, { it.company }, { it.tech }, { it.country }, { it.family })
    }

    class Amount(var units: Long, var sales: Long)

    private val days = java.util.TreeMap<Long, java.util.TreeMap<Key, Amount>>()

    val isEmpty: Boolean get() = days.isEmpty()

    fun record(tick: Tick, company: CompanyId, tech: TechId, country: CountryId, family: IndustryFamily, units: Long, sales: Long) {
        if (units <= 0L && sales <= 0L) return
        val day = days.getOrPut(tick.value) { java.util.TreeMap() }
        val a = day.getOrPut(Key(company.value, tech.key, country.value, family.ordinal)) { Amount(0L, 0L) }
        a.units += units
        a.sales += sales
    }

    /** Forgets days older than the retention window. */
    fun prune(tick: Tick) {
        val oldest = tick.value - retentionDays
        while (days.isNotEmpty() && days.firstKey() < oldest) days.pollFirstEntry()
    }

    /** Every entry on the days in (fromExclusive, toInclusive], in day order. */
    fun between(fromExclusive: Tick, toInclusive: Tick, each: (Key, Amount) -> Unit) {
        if (toInclusive.value <= fromExclusive.value) return
        for ((_, entries) in days.subMap(fromExclusive.value, false, toInclusive.value, true)) for ((k, a) in entries) each(k, a)
    }

    fun clear() = days.clear()

    fun hashInto(h: CanonicalHasher) {
        h.putInt(days.size)
        for ((d, entries) in days) {
            h.putLong(d).putInt(entries.size)
            for ((k, a) in entries) h.putLong(k.company).putString(k.tech).putLong(k.country).putInt(k.family).putLong(a.units).putLong(a.sales)
        }
    }

    fun write(w: ByteWriter) {
        w.writeVarInt(days.size)
        for ((d, entries) in days) {
            w.writeVarLong(d)
            w.writeVarInt(entries.size)
            for ((k, a) in entries) {
                w.writeVarLong(k.company); w.writeString(k.tech); w.writeVarLong(k.country); w.writeVarInt(k.family)
                w.writeVarLong(a.units); w.writeVarLong(a.sales)
            }
        }
    }

    fun read(r: ByteReader) {
        days.clear()
        val n = r.readVarInt()
        if (n < 0 || n > MAX_DAYS) throw ByteReader.CorruptDataException("implausible sales history")
        repeat(n) {
            val d = r.readVarLong()
            val m = r.readVarInt()
            if (m < 0 || m > MAX_ENTRIES) throw ByteReader.CorruptDataException("implausible sales day")
            val entries = java.util.TreeMap<Key, Amount>()
            repeat(m) {
                val key = Key(r.readVarLong(), r.readString(), r.readVarLong(), r.readVarInt())
                entries[key] = Amount(r.readVarLong(), r.readVarLong())
            }
            days[d] = entries
        }
    }

    companion object {
        const val RETENTION_DAYS = 400L
        private const val MAX_DAYS = 10_000
        private const val MAX_ENTRIES = 1_000_000
    }
}
