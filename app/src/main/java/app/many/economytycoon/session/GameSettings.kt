package app.many.economytycoon.session

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter

/** How fast the clock runs while the game is playing, in ticks (game days) per real second. */
enum class GameSpeed(val ticksPerSecond: Int, val label: String) {
    PAUSED(0, "Paused"),
    NORMAL(1, "Normal"),
    FAST(3, "Fast"),
    FASTEST(8, "Fastest"),
}

enum class CurrencyStyle { PLAIN, DOLLAR, EURO, POUND }

/** Things a player sets once and expects to find again next time. */
data class GameSettings(
    val defaultSpeed: GameSpeed = GameSpeed.NORMAL,
    val autosave: Boolean = true,
    val autosaveEveryTicks: Int = 30,
    val currency: CurrencyStyle = CurrencyStyle.DOLLAR,
    val compactNumbers: Boolean = true,
    val confirmDestructive: Boolean = true,
    val pauseOnCritical: Boolean = true,
    val reduceMotion: Boolean = false,
    val uiScalePercent: Int = 100,
    val language: app.many.economytycoon.i18n.GameLocale = app.many.economytycoon.i18n.GameLocale.DEFAULT,
) {
    init {
        require(autosaveEveryTicks in 5..360) { "autosave interval out of range" }
        require(uiScalePercent in 80..160) { "ui scale out of range" }
    }

    val symbol: String get() = when (currency) {
        CurrencyStyle.PLAIN -> ""
        CurrencyStyle.DOLLAR -> "$"
        CurrencyStyle.EURO -> "€"
        CurrencyStyle.POUND -> "£"
    }

    /** Text and number conventions for the chosen language. */
    val format: app.many.economytycoon.i18n.LocaleFormat get() = app.many.economytycoon.i18n.LocaleFormat(language)

    fun money(m: app.many.economytycoon.core.Money): String =
        if (compactNumbers) format.compactMoney(m, symbol) else format.money(m, symbol)

    fun toBytes(): ByteArray {
        val w = ByteWriter()
        w.writeVarInt(VERSION)
        w.writeVarInt(defaultSpeed.ordinal)
        w.writeBoolean(autosave)
        w.writeVarInt(autosaveEveryTicks)
        w.writeVarInt(currency.ordinal)
        w.writeBoolean(compactNumbers)
        w.writeBoolean(confirmDestructive)
        w.writeBoolean(pauseOnCritical)
        w.writeBoolean(reduceMotion)
        w.writeVarInt(uiScalePercent)
        w.writeVarInt(language.ordinal)
        return w.toByteArray()
    }

    companion object {
        const val VERSION = 2
        private const val FIRST_VERSION = 1

        /** Reads stored settings; anything unreadable gives the defaults rather than blocking the app. */
        fun fromBytes(bytes: ByteArray?): GameSettings {
            if (bytes == null) return GameSettings()
            return try {
                val r = ByteReader(bytes)
                val version = r.readVarInt()
                if (version != VERSION && version != FIRST_VERSION) return GameSettings()
                GameSettings(
                    defaultSpeed = GameSpeed.values().getOrElse(r.readVarInt()) { GameSpeed.NORMAL },
                    autosave = r.readBoolean(),
                    autosaveEveryTicks = r.readVarInt().coerceIn(5, 360),
                    currency = CurrencyStyle.values().getOrElse(r.readVarInt()) { CurrencyStyle.DOLLAR },
                    compactNumbers = r.readBoolean(),
                    confirmDestructive = r.readBoolean(),
                    pauseOnCritical = r.readBoolean(),
                    reduceMotion = r.readBoolean(),
                    uiScalePercent = r.readVarInt().coerceIn(80, 160),
                    language = if (version >= 2) app.many.economytycoon.i18n.GameLocale.values().getOrElse(r.readVarInt()) { app.many.economytycoon.i18n.GameLocale.DEFAULT } else app.many.economytycoon.i18n.GameLocale.DEFAULT,
                )
            } catch (e: ByteReader.CorruptDataException) {
                GameSettings()
            }
        }
    }
}

/** Keeps settings in a [app.many.economytycoon.persistence.SaveStore] under one key. */
class SettingsStore(private val store: app.many.economytycoon.persistence.SaveStore, private val key: String = "settings.etprefs") {
    fun load(): GameSettings = GameSettings.fromBytes(store.get(key))
    fun save(settings: GameSettings) = store.put(key, settings.toBytes())
}
