package app.many.economytycoon.session

import app.many.economytycoon.core.Money
import app.many.economytycoon.persistence.MemorySaveStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSettingsTest {
    @Test fun settingsRoundTripThroughBytes() {
        val s = GameSettings(GameSpeed.FAST, false, 90, CurrencyStyle.EURO, false, false, false, true, 120)
        assertEquals(s, GameSettings.fromBytes(s.toBytes()))
    }

    @Test fun missingOrDamagedBytesGiveDefaults() {
        assertEquals(GameSettings(), GameSettings.fromBytes(null))
        assertEquals(GameSettings(), GameSettings.fromBytes(byteArrayOf(9, 9, 9)))
        assertEquals(GameSettings(), GameSettings.fromBytes(ByteArray(0)))
    }

    @Test fun outOfRangeStoredValuesAreClamped() {
        fun stored(scale: Int, every: Int): ByteArray {
            val w = app.many.economytycoon.core.ByteWriter()
            w.writeVarInt(1); w.writeVarInt(1); w.writeBoolean(true); w.writeVarInt(every); w.writeVarInt(1)
            w.writeBoolean(true); w.writeBoolean(true); w.writeBoolean(true); w.writeBoolean(false); w.writeVarInt(scale)
            return w.toByteArray()
        }
        assertEquals(160, GameSettings.fromBytes(stored(900, 30)).uiScalePercent)
        assertEquals(80, GameSettings.fromBytes(stored(5, 30)).uiScalePercent)
        assertEquals(5, GameSettings.fromBytes(stored(100, 1)).autosaveEveryTicks)
        assertEquals(360, GameSettings.fromBytes(stored(100, 9_999)).autosaveEveryTicks)
    }

    @Test fun languageRoundTripsAndOldFilesDefaultToEnglish() {
        for (l in app.many.economytycoon.i18n.GameLocale.values()) assertEquals(l, GameSettings.fromBytes(GameSettings(language = l).toBytes()).language)
        val w = app.many.economytycoon.core.ByteWriter()
        w.writeVarInt(1); w.writeVarInt(1); w.writeBoolean(true); w.writeVarInt(30); w.writeVarInt(1)
        w.writeBoolean(true); w.writeBoolean(true); w.writeBoolean(true); w.writeBoolean(false); w.writeVarInt(100)
        assertEquals(app.many.economytycoon.i18n.GameLocale.EN, GameSettings.fromBytes(w.toByteArray()).language)
    }

    @Test fun moneyFollowsTheLanguage() {
        val s = GameSettings(currency = CurrencyStyle.EURO, compactNumbers = false, language = app.many.economytycoon.i18n.GameLocale.DE)
        assertEquals("1.234,00\u00A0€", s.money(Money.units(1_234)))
    }

    @Test fun invalidConstructionIsRefused() {
        var refused = 0
        try { GameSettings(autosaveEveryTicks = 1) } catch (e: IllegalArgumentException) { refused++ }
        try { GameSettings(uiScalePercent = 300) } catch (e: IllegalArgumentException) { refused++ }
        assertEquals(2, refused)
    }

    @Test fun moneyFollowsTheCurrencyAndCompactChoice() {
        assertEquals("$1.23K", GameSettings().money(Money.units(1_234)))
        assertEquals("€1,234.00", GameSettings(currency = CurrencyStyle.EURO, compactNumbers = false).money(Money.units(1_234)))
        assertEquals("1,234.00", GameSettings(currency = CurrencyStyle.PLAIN, compactNumbers = false).money(Money.units(1_234)))
    }

    @Test fun storePersistsAndFallsBack() {
        val store = MemorySaveStore(); val settings = SettingsStore(store)
        assertTrue(settings.load().autosave)
        settings.save(GameSettings(autosave = false))
        assertFalse(settings.load().autosave)
    }

    @Test fun speedsRunFasterInOrder() {
        val rates = GameSpeed.values().map { it.ticksPerSecond }
        assertEquals(rates.sorted(), rates)
        assertEquals(0, GameSpeed.PAUSED.ticksPerSecond)
    }
}
