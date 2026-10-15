package app.many.economytycoon.i18n

import app.many.economytycoon.i18n.catalogs.DeStrings
import app.many.economytycoon.i18n.catalogs.EnStrings
import app.many.economytycoon.i18n.catalogs.EsStrings
import app.many.economytycoon.i18n.catalogs.FrStrings

/** The strings of every language, with English as the one that is always complete. */
object Catalogs {
    fun of(locale: GameLocale): Map<String, String> = when (locale) {
        GameLocale.EN -> EnStrings.entries
        GameLocale.ES -> EsStrings.entries
        GameLocale.FR -> FrStrings.entries
        GameLocale.DE -> DeStrings.entries
    }

    val keys: Set<String> get() = EnStrings.entries.keys
}

/**
 * Looks up text for one language. A key missing from the language falls back to English, and one missing
 * from English shows as the key itself, so a gap in a translation is visible but never fatal.
 */
class Translator(val locale: GameLocale, private val overrides: Map<String, String> = emptyMap()) {
    private val table = Catalogs.of(locale)
    private val english = Catalogs.of(GameLocale.EN)
    val format = LocaleFormat(locale)

    fun has(key: String): Boolean = key in overrides || key in table

    fun raw(key: String): String = overrides[key] ?: table[key] ?: english[key] ?: key

    fun t(key: String, args: Map<String, Any?> = emptyMap()): String {
        val pattern = raw(key)
        return if (args.isEmpty() && '{' !in pattern && '\'' !in pattern) pattern else MessageFormat.format(pattern, locale, args)
    }

    fun t(key: String, vararg args: Pair<String, Any?>): String = t(key, args.toMap())

    /** Text for a count, using the key's plural forms; the count is passed as `count`. */
    fun count(key: String, n: Long): String = t(key, mapOf("count" to n))

    fun withLocale(other: GameLocale): Translator = Translator(other, overrides)

    companion object {
        val ENGLISH = Translator(GameLocale.EN)
    }
}
