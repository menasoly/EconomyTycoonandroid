package app.many.economytycoon.scenarios

import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.persistence.MemorySaveStore
import app.many.economytycoon.persistence.ProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerProfileTest {
    private val ids = ScenarioCatalog.all.map { it.id }

    @Test fun aFreshProfileOpensOnlyTheFirstScenario() {
        val p = PlayerProfile()
        assertEquals(listOf(ids[0]), p.unlocked().map { it.id })
        assertTrue(p.isUnlocked(ids[0])); assertFalse(p.isUnlocked(ids[1]))
        assertEquals(0, p.stars()); assertEquals(Medal.NONE, p.medal(ids[0])); assertNull(p.record(ids[0]))
    }

    @Test fun winningOpensTheNextOne() {
        val p = PlayerProfile()
        p.won(ids[0], Medal.BRONZE, 500)
        assertEquals(ids.take(2), p.unlocked().map { it.id })
        p.won(ids[1], Medal.GOLD, 900)
        assertEquals(ids.take(3), p.unlocked().map { it.id })
    }

    @Test fun lossesCountButDoNotUnlock() {
        val p = PlayerProfile()
        p.lost(ids[0]); p.lost(ids[0])
        assertEquals(2, p.record(ids[0])!!.losses)
        assertTrue(p.record(ids[0])!!.attempted)
        assertFalse(p.isUnlocked(ids[1]))
    }

    @Test fun theBestMedalAndFastestWinAreKept() {
        val p = PlayerProfile()
        p.won(ids[0], Medal.SILVER, 700)
        p.won(ids[0], Medal.BRONZE, 650)
        p.won(ids[0], Medal.GOLD, 800)
        val r = p.record(ids[0])!!
        assertEquals(Medal.GOLD, r.medal); assertEquals(650L, r.bestDays); assertEquals(3, r.wins)
        p.won(ids[0], Medal.SILVER, 900)
        assertEquals(Medal.GOLD, p.medal(ids[0]))
    }

    @Test fun aLossAfterAWinKeepsTheMedal() {
        val p = PlayerProfile()
        p.won(ids[0], Medal.SILVER, 700); p.lost(ids[0])
        val r = p.record(ids[0])!!
        assertEquals(Medal.SILVER, r.medal); assertEquals(700L, r.bestDays); assertEquals(1, r.wins); assertEquals(1, r.losses)
    }

    @Test fun starsCountMedalsByRank() {
        val p = PlayerProfile()
        p.won(ids[0], Medal.GOLD, 1); p.won(ids[1], Medal.SILVER, 1); p.won(ids[2], Medal.BRONZE, 1)
        assertEquals(6, p.stars())
        assertEquals(1, p.medalCount(Medal.GOLD)); assertEquals(1, p.medalCount(Medal.SILVER)); assertEquals(1, p.medalCount(Medal.BRONZE))
    }

    @Test fun theProfileRoundTripsThroughBytes() {
        val p = PlayerProfile()
        p.won(ids[0], Medal.GOLD, 400); p.lost(ids[0]); p.won(ids[1], Medal.BRONZE, 1_500); p.lost(ids[2])
        val back = ProfileStore.decode(ProfileStore.encode(p))
        assertEquals(p.all(), back.all())
    }

    @Test fun theStoreSavesAndReloads() {
        val store = ProfileStore(MemorySaveStore())
        assertTrue(store.load().all().isEmpty())
        val p = PlayerProfile(); p.won(ids[0], Medal.SILVER, 555)
        store.save(p)
        assertEquals(p.all(), store.load().all())
        store.reset()
        assertTrue(store.load().all().isEmpty())
    }

    @Test fun damagedDataReadsAsAFreshProfile() {
        val mem = MemorySaveStore()
        mem.put(ProfileStore.KEY, byteArrayOf(1, 9, 9, 9))
        assertTrue(ProfileStore(mem).load().all().isEmpty())
        mem.put(ProfileStore.KEY, ByteArray(0))
        assertTrue(ProfileStore(mem).load().all().isEmpty())
    }

    @Test fun anUnknownVersionIsTreatedAsDamaged() {
        val mem = MemorySaveStore()
        val w = ByteWriter(); w.writeVarInt(99)
        mem.put(ProfileStore.KEY, w.toByteArray())
        assertTrue(ProfileStore(mem).load().all().isEmpty())
    }

    @Test fun scenariosFromAnotherVersionAreIgnored() {
        val w = ByteWriter()
        w.writeVarInt(ProfileStore.VERSION); w.writeVarInt(2)
        w.writeString("retired-scenario"); w.writeVarInt(Medal.GOLD.ordinal); w.writeVarLong(10); w.writeVarInt(1); w.writeVarInt(0)
        w.writeString(ids[0]); w.writeVarInt(Medal.BRONZE.ordinal); w.writeVarLong(20); w.writeVarInt(1); w.writeVarInt(0)
        w.writeVarInt(0); w.writeVarInt(0)
        val p = ProfileStore.decode(w.toByteArray())
        assertEquals(listOf(ids[0]), p.all().keys.toList())
    }

    @Test fun statsAndAchievementsRoundTrip() {
        val p = PlayerProfile()
        p.stats.add(app.many.economytycoon.achievements.Stat.DAYS_PLAYED, 777); p.stats.add(app.many.economytycoon.achievements.Stat.TENDERS_WON, 3)
        p.achievements.unlock("first-million", 120); p.achievements.unlock("six-figures", 45)
        p.won(ids[0], Medal.SILVER, 600)
        val back = ProfileStore.decode(ProfileStore.encode(p))
        assertEquals(p.stats, back.stats)
        assertEquals(p.achievements, back.achievements)
        assertEquals(p.all(), back.all())
        assertEquals(120L, back.achievements.unlockedAt("first-million"))
    }

    @Test fun aVersionOneProfileStillLoads() {
        val w = ByteWriter()
        w.writeVarInt(1); w.writeVarInt(1)
        w.writeString(ids[0]); w.writeVarInt(Medal.GOLD.ordinal); w.writeVarLong(300); w.writeVarInt(2); w.writeVarInt(1)
        val p = ProfileStore.decode(w.toByteArray())
        assertEquals(Medal.GOLD, p.medal(ids[0]))
        assertEquals(0, p.achievements.count)
    }

    @Test fun unknownStatsAndAchievementsAreIgnored() {
        val w = ByteWriter()
        w.writeVarInt(ProfileStore.VERSION); w.writeVarInt(0)
        w.writeVarInt(2); w.writeString("RETIRED_STAT"); w.writeVarLong(9); w.writeString("DAYS_PLAYED"); w.writeVarLong(12)
        w.writeVarInt(2); w.writeString("retired-prize"); w.writeVarLong(3); w.writeString("six-figures"); w.writeVarLong(4)
        val p = ProfileStore.decode(w.toByteArray())
        assertEquals(12L, p.stats[app.many.economytycoon.achievements.Stat.DAYS_PLAYED])
        assertEquals(listOf("six-figures"), p.achievements.all().keys.toList())
    }
}
