package app.many.economytycoon.persistence

import app.many.economytycoon.achievements.AchievementCatalog
import app.many.economytycoon.achievements.Stat
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.scenarios.Medal
import app.many.economytycoon.scenarios.PlayerProfile
import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.scenarios.ScenarioRecord

/** Saves the player's profile under one key. A damaged or missing profile reads as a fresh one. */
class ProfileStore(private val store: SaveStore, private val key: String = KEY) {
    fun load(): PlayerProfile {
        val bytes = store.get(key) ?: return PlayerProfile()
        return try { decode(bytes) } catch (e: ByteReader.CorruptDataException) { PlayerProfile() }
    }

    fun save(profile: PlayerProfile) { store.put(key, encode(profile)) }

    fun reset() { store.remove(key) }

    companion object {
        const val KEY = "profile.dat"
        const val VERSION = 2

        fun encode(p: PlayerProfile): ByteArray {
            val w = ByteWriter()
            w.writeVarInt(VERSION)
            val all = p.all()
            w.writeVarInt(all.size)
            for ((id, r) in all) {
                w.writeString(id); w.writeVarInt(r.medal.ordinal); w.writeVarLong(r.bestDays); w.writeVarInt(r.wins); w.writeVarInt(r.losses)
            }
            val stats = p.stats.nonZero()
            w.writeVarInt(stats.size)
            for ((stat, total) in stats) { w.writeString(stat.name); w.writeVarLong(total) }
            val earned = p.achievements.all()
            w.writeVarInt(earned.size)
            for ((id, day) in earned) { w.writeString(id); w.writeVarLong(day) }
            return w.toByteArray()
        }

        fun decode(bytes: ByteArray): PlayerProfile {
            val r = ByteReader(bytes)
            val version = r.readVarInt()
            if (version != 1 && version != VERSION) throw ByteReader.CorruptDataException("unsupported profile version $version")
            val p = PlayerProfile()
            repeat(r.readVarInt()) {
                val id = r.readString()
                val medalAt = r.readVarInt()
                val medal = Medal.values().getOrNull(medalAt) ?: throw ByteReader.CorruptDataException("unknown medal $medalAt")
                val rec = ScenarioRecord(medal, r.readVarLong(), r.readVarInt(), r.readVarInt())
                // Scenarios dropped from a later version are ignored rather than failing the whole profile.
                if (ScenarioCatalog.byId(id) != null) p.put(id, rec)
            }
            if (version >= 2) {
                repeat(r.readVarInt()) {
                    val name = r.readString(); val total = r.readVarLong()
                    // Stats dropped from a later version are ignored.
                    Stat.values().firstOrNull { it.name == name }?.let { p.stats.put(it, total) }
                }
                repeat(r.readVarInt()) {
                    val id = r.readString(); val day = r.readVarLong()
                    if (AchievementCatalog.byId(id) != null) p.achievements.put(id, day)
                }
            }
            return p
        }
    }
}
