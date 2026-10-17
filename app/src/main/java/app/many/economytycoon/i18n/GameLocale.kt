package app.many.economytycoon.i18n

/** How a language counts: which word form goes with a number. */
enum class PluralCategory { ZERO, ONE, OTHER }

/** The languages the game can be shown in, with the conventions each one writes numbers by. */
enum class GameLocale(
    val code: String,
    val nativeName: String,
    val decimalSeparator: Char,
    val groupSeparator: Char,
    /** Whether the currency symbol follows the number, as in `1.234,50 €`. */
    val symbolAfter: Boolean,
    /** Whether a space separates a percent sign from its number. */
    val spacedPercent: Boolean,
    val monthNames: List<String>,
    val monthAbbreviations: List<String>,
    val compactSuffixes: List<String>,
) {
    EN(
        "en", "English", '.', ',', false, false,
        listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
        listOf("K", "M", "B", "T"),
    ),
    ES(
        "es", "Español", ',', '.', true, true,
        listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"),
        listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"),
        listOf("mil", "M", "mM", "B"),
    ),
    FR(
        "fr", "Français", ',', ' ', true, true,
        listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"),
        listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."),
        listOf("k", "M", "Md", "Bn"),
    ),
    DE(
        "de", "Deutsch", ',', '.', true, true,
        listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"),
        listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez."),
        listOf("Tsd.", "Mio.", "Mrd.", "Bio."),
    ),
    ;

    /** Which plural form goes with [n] in this language. */
    fun plural(n: Long): PluralCategory = when (this) {
        EN, ES, DE -> if (n == 1L) PluralCategory.ONE else PluralCategory.OTHER
        // French counts zero and one together as the singular.
        FR -> if (n == 0L || n == 1L) PluralCategory.ONE else PluralCategory.OTHER
    }

    companion object {
        val DEFAULT = EN

        fun fromCode(code: String?): GameLocale {
            if (code == null) return DEFAULT
            val base = code.trim().lowercase().substringBefore('-').substringBefore('_')
            return values().firstOrNull { it.code == base } ?: DEFAULT
        }

        /** The best supported language for a device's ordered preferences, such as `["pt-BR", "fr-CA"]`. */
        fun best(preferences: List<String>): GameLocale {
            for (p in preferences) {
                val base = p.trim().lowercase().substringBefore('-').substringBefore('_')
                values().firstOrNull { it.code == base }?.let { return it }
            }
            return DEFAULT
        }
    }
}
