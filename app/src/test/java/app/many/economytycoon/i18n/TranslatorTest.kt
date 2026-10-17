package app.many.economytycoon.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatorTest {
    @Test fun englishLooksUpPlainText() = assertEquals("Cancel", Translator(GameLocale.EN).t("action.cancel"))

    @Test fun otherLanguagesTranslate() {
        assertEquals("Cancelar", Translator(GameLocale.ES).t("action.cancel"))
        assertEquals("Annuler", Translator(GameLocale.FR).t("action.cancel"))
        assertEquals("Abbrechen", Translator(GameLocale.DE).t("action.cancel"))
    }

    @Test fun unknownKeyShowsTheKey() = assertEquals("no.such.key", Translator(GameLocale.DE).t("no.such.key"))

    @Test fun overridesWinOverCatalog() {
        val t = Translator(GameLocale.EN, mapOf("action.ok" to "Okay"))
        assertEquals("Okay", t.t("action.ok"))
        assertTrue(t.has("action.ok"))
    }

    @Test fun overrideForAnUnknownKeyIsUsed() = assertEquals("hi", Translator(GameLocale.FR, mapOf("custom" to "hi")).t("custom"))

    @Test fun argumentsFillIn() {
        assertEquals("Day 7", Translator.ENGLISH.t("clock.day", "day" to 7))
        assertEquals("Jour 7", Translator(GameLocale.FR).t("clock.day", "day" to 7))
    }

    @Test fun countsUsePluralForms() {
        val en = Translator(GameLocale.EN)
        assertEquals("No alerts", en.count("home.alerts", 0))
        assertEquals("1 alert", en.count("home.alerts", 1))
        assertEquals("12 alerts", en.count("home.alerts", 12))
        assertEquals("Aucune alerte", Translator(GameLocale.FR).count("home.alerts", 0))
        assertEquals("2 alertes", Translator(GameLocale.FR).count("home.alerts", 2))
    }

    @Test fun germanPluralOfDays() {
        val de = Translator(GameLocale.DE)
        assertEquals("In 1 Tag", de.count("time.inDays", 1))
        assertEquals("In 3 Tagen", de.count("time.inDays", 3))
    }

    @Test fun switchingLanguageKeepsOverrides() {
        val t = Translator(GameLocale.EN, mapOf("x" to "y")).withLocale(GameLocale.ES)
        assertEquals(GameLocale.ES, t.locale)
        assertEquals("y", t.t("x"))
    }

    @Test fun everyKeyResolvesInEveryLanguage() {
        for (l in GameLocale.values()) {
            val t = Translator(l)
            for (k in Catalogs.keys) assertTrue("$l $k", t.has(k))
        }
    }

    @Test fun noKeyResolvesToItself() {
        for (l in GameLocale.values()) for (k in Catalogs.keys) assertTrue("$l $k", k != Translator(l).t(k, "count" to 2, "day" to 1, "days" to 2, "amount" to "1", "date" to "d", "percent" to "1%", "rank" to 1, "company" to "c", "current" to 1, "total" to 2))
    }

    @Test fun localeCodesParse() {
        assertEquals(GameLocale.DE, GameLocale.fromCode("de"))
        assertEquals(GameLocale.FR, GameLocale.fromCode("fr-CA"))
        assertEquals(GameLocale.ES, GameLocale.fromCode("ES_mx"))
        assertEquals(GameLocale.EN, GameLocale.fromCode("pt"))
        assertEquals(GameLocale.EN, GameLocale.fromCode(null))
    }

    @Test fun deviceListPicksFirstSupported() {
        assertEquals(GameLocale.FR, GameLocale.best(listOf("pt-BR", "fr-CA", "de")))
        assertEquals(GameLocale.EN, GameLocale.best(listOf("ja", "ko")))
        assertEquals(GameLocale.EN, GameLocale.best(emptyList()))
    }

    @Test fun nativeNamesAreDistinct() {
        assertEquals(GameLocale.values().size, GameLocale.values().map { it.nativeName }.toSet().size)
        assertFalse(GameLocale.values().any { it.nativeName.isBlank() })
    }
}
