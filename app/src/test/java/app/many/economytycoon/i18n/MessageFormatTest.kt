package app.many.economytycoon.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFormatTest {
    private fun en(p: String, vararg a: Pair<String, Any?>) = MessageFormat.format(p, GameLocale.EN, a.toMap())

    @Test fun plainTextPassesThrough() = assertEquals("hello world", en("hello world"))

    @Test fun argumentsAreInserted() = assertEquals("Day 12", en("Day {day}", "day" to 12))

    @Test fun missingArgumentsStayVisible() = assertEquals("Day {day}", en("Day {day}"))

    @Test fun pluralPicksOneAndOther() {
        val p = "{n, plural, one {# day} other {# days}}"
        assertEquals("1 day", en(p, "n" to 1))
        assertEquals("2 days", en(p, "n" to 2))
        assertEquals("0 days", en(p, "n" to 0))
    }

    @Test fun exactMatchBeatsCategory() {
        val p = "{n, plural, =0 {none} one {# item} other {# items}}"
        assertEquals("none", en(p, "n" to 0))
        assertEquals("1 item", en(p, "n" to 1))
        assertEquals("5 items", en(p, "n" to 5))
    }

    @Test fun hashGroupsLargeNumbers() = assertEquals("1,234,567 units", en("{n, plural, one {# unit} other {# units}}", "n" to 1_234_567))

    @Test fun hashFollowsTheLocale() {
        val p = "{n, plural, one {# Tag} other {# Tage}}"
        assertEquals("1.234 Tage", MessageFormat.format(p, GameLocale.DE, mapOf("n" to 1234)))
    }

    @Test fun selectPicksByWord() {
        val p = "{kind, select, loan {A loan} bond {A bond} other {Something}}"
        assertEquals("A loan", en(p, "kind" to "loan"))
        assertEquals("A bond", en(p, "kind" to "bond"))
        assertEquals("Something", en(p, "kind" to "grant"))
    }

    @Test fun selectFallsBackWhenArgumentIsMissing() = assertEquals("Something", en("{k, select, a {A} other {Something}}"))

    @Test fun quotedBracesAreLiteral() = assertEquals("use {name} here", en("use '{'name'}' here"))

    @Test fun doubledQuoteIsOneQuote() = assertEquals("it's", en("it''s"))

    @Test fun loneApostropheIsKept() = assertEquals("Aujourd'hui", en("Aujourd'hui"))

    @Test fun nestedArgumentsInsideForms() {
        val p = "{n, plural, one {{who} has # coin} other {{who} has # coins}}"
        assertEquals("Ann has 1 coin", en(p, "n" to 1, "who" to "Ann"))
        assertEquals("Ann has 3 coins", en(p, "n" to 3, "who" to "Ann"))
    }

    @Test fun frenchCountsZeroAsSingular() {
        val p = "{n, plural, one {# jour} other {# jours}}"
        assertEquals("0 jour", MessageFormat.format(p, GameLocale.FR, mapOf("n" to 0)))
        assertEquals("1 jour", MessageFormat.format(p, GameLocale.FR, mapOf("n" to 1)))
        assertEquals("2 jours", MessageFormat.format(p, GameLocale.FR, mapOf("n" to 2)))
    }

    @Test fun badPatternsShowAsWritten() {
        for (p in listOf("{unterminated", "oops}", "{n, plural, one {x}}", "{n, wibble, other {x}}", "{}")) {
            assertFalse(p, MessageFormat.isValid(p))
            assertEquals(p, en(p, "n" to 1))
        }
    }

    @Test fun validPatternsAreValid() {
        for (p in listOf("", "plain", "{a}", "{n, plural, other {x}}", "'{'")) assertTrue(p, MessageFormat.isValid(p))
    }

    @Test fun argumentNamesInFirstUseOrder() {
        assertEquals(listOf("b", "a", "n"), MessageFormat.argumentNames("{b} then {a} then {n, plural, one {# x} other {# y}}"))
        assertEquals(emptyList<String>(), MessageFormat.argumentNames("none"))
    }

    @Test fun nonNumericPluralArgumentUsesOther() {
        assertEquals("many", en("{n, plural, one {one} other {many}}", "n" to "x"))
    }

    @Test fun nullArgumentRendersAsPlaceholder() = assertEquals("{x}", en("{x}", "x" to null))
}
