package app.many.economytycoon.i18n

/** One thing wrong with a language's text. */
data class CatalogIssue(val locale: GameLocale, val key: String, val kind: Kind, val detail: String) {
    enum class Kind { MISSING, EXTRA, BAD_SYNTAX, ARGUMENT_MISMATCH, BLANK, UNTRANSLATED }
}

/** Checks that every language carries the same messages with the same arguments. */
object CatalogAudit {
    fun check(locale: GameLocale, entries: Map<String, String> = Catalogs.of(locale), base: Map<String, String> = Catalogs.of(GameLocale.EN)): List<CatalogIssue> {
        val issues = ArrayList<CatalogIssue>()
        for (key in base.keys.sorted()) {
            val text = entries[key]
            if (text == null) { issues += CatalogIssue(locale, key, CatalogIssue.Kind.MISSING, "no text"); continue }
            if (text.isBlank()) { issues += CatalogIssue(locale, key, CatalogIssue.Kind.BLANK, "empty text"); continue }
            if (!MessageFormat.isValid(text)) { issues += CatalogIssue(locale, key, CatalogIssue.Kind.BAD_SYNTAX, text); continue }
            val want = MessageFormat.argumentNames(base.getValue(key)).toSet()
            val have = MessageFormat.argumentNames(text).toSet()
            if (want != have) issues += CatalogIssue(locale, key, CatalogIssue.Kind.ARGUMENT_MISMATCH, "expected $want, found $have")
        }
        for (key in entries.keys.sorted()) if (key !in base) issues += CatalogIssue(locale, key, CatalogIssue.Kind.EXTRA, "not in English")
        return issues
    }

    /** Keys whose text is identical to English, which usually means they were not translated. Short words are exempt. */
    fun untranslated(locale: GameLocale): List<String> {
        if (locale == GameLocale.EN) return emptyList()
        val en = Catalogs.of(GameLocale.EN)
        val own = Catalogs.of(locale)
        return en.keys.sorted().filter { k -> own[k] == en[k] && en.getValue(k).length > 12 }
    }

    fun coveragePpm(locale: GameLocale): Long {
        val total = Catalogs.keys.size.toLong()
        if (total == 0L) return 1_000_000L
        val have = Catalogs.keys.count { it in Catalogs.of(locale) }.toLong()
        return have * 1_000_000L / total
    }

    fun all(): List<CatalogIssue> = GameLocale.values().flatMap { check(it) }
}
