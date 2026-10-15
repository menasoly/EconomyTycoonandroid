package app.many.economytycoon.automation

import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.core.Money
import app.many.economytycoon.persistence.MemorySaveStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerSettingsTest {
    @Test fun startsWithEverythingOff() {
        val s = ManagerSettings()
        assertFalse(s.anyOn)
        for (k in ManagerKind.values()) assertFalse(s.isOn(k))
    }

    @Test fun switchingOneOnAndOffLeavesTheOthers() {
        val s = ManagerSettings().with(ManagerKind.WASTE, true).with(ManagerKind.SELLING, true).with(ManagerKind.WASTE, false)
        assertTrue(s.isOn(ManagerKind.SELLING))
        assertFalse(s.isOn(ManagerKind.WASTE))
        assertTrue(s.anyOn)
    }

    @Test fun everyManagerHasWordsAndACadence() {
        for (k in ManagerKind.values()) {
            assertTrue(k.label.isNotBlank()); assertTrue(k.summary.isNotBlank()); assertTrue(k.everyDays in 1..60)
        }
    }

    @Test fun roundTripsThroughBytes() {
        val s = ManagerSettings(setOf(ManagerKind.RESEARCH, ManagerKind.MAINTENANCE), Money.units(12_345), 600_000L, 35L, 50_000L, TechCategory.values().first())
        assertEquals(s, ManagerSettings.fromBytes(s.toBytes()))
    }

    @Test fun roundTripsWithNoFocus() {
        val s = ManagerSettings(setOf(ManagerKind.SELLING))
        val back = ManagerSettings.fromBytes(s.toBytes())
        assertNull(back.researchFocus)
        assertEquals(s, back)
    }

    @Test fun missingOrDamagedBytesGiveDefaults() {
        assertEquals(ManagerSettings(), ManagerSettings.fromBytes(null))
        assertEquals(ManagerSettings(), ManagerSettings.fromBytes(byteArrayOf(9, 9, 9)))
        assertEquals(ManagerSettings(), ManagerSettings.fromBytes(ByteArray(0)))
    }

    @Test fun outOfRangeLimitsAreRefused() {
        val bad = listOf<() -> Unit>(
            { ManagerSettings(reserve = Money(-1)) },
            { ManagerSettings(repairBelowPpm = 50_000L) },
            { ManagerSettings(repairBelowPpm = 1_000_001L) },
            { ManagerSettings(wasteUnits = 0L) },
            { ManagerSettings(minMarginPpm = -600_000L) },
        )
        for (b in bad) {
            var refused = false
            try { b() } catch (e: IllegalArgumentException) { refused = true }
            assertTrue(refused)
        }
    }

    @Test fun storeSavesLoadsAndResets() {
        val store = ManagerStore(MemorySaveStore())
        assertEquals(ManagerSettings(), store.load())
        val s = ManagerSettings(setOf(ManagerKind.STAFFING))
        store.save(s)
        assertEquals(s, store.load())
        store.reset()
        assertEquals(ManagerSettings(), store.load())
    }
}
