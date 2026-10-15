package app.many.economytycoon.i18n

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.session.Formatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleFormatTest {
    private val en = LocaleFormat(GameLocale.EN)
    private val de = LocaleFormat(GameLocale.DE)
    private val fr = LocaleFormat(GameLocale.FR)
    private val es = LocaleFormat(GameLocale.ES)

    @Test fun englishMatchesTheBaseFormatter() {
        for (v in listOf(0L, 5L, 999L, 1_000L, 12_345L, 1_234_567L, -42L, -9_876_543L)) {
            assertEquals(Formatting.grouped(v), en.grouped(v))
            assertEquals(Formatting.money(Money.units(v), "$"), en.money(Money.units(v), "$"))
            assertEquals(Formatting.compactMoney(Money.units(v), "$"), en.compactMoney(Money.units(v), "$"))
        }
    }

    @Test fun englishPercentAndQuantityMatchTheBaseFormatter() {
        for (p in listOf(0L, 1L, 499L, 500L, 12_345L, 250_000L, -75_000L, 1_000_000L)) {
            assertEquals(Formatting.percentPpm(p), en.percentPpm(p))
            assertEquals(Formatting.signedPercentPpm(p), en.signedPercentPpm(p))
        }
        for (r in listOf(0L, 1L, 1_000L, 1_500L, 12_345_678L, -2_500L)) assertEquals(Formatting.quantity(Quantity(r)), en.quantity(Quantity(r)))
    }

    @Test fun germanUsesDotsAndCommas() {
        assertEquals("1.234.567", de.grouped(1_234_567))
        assertEquals("1.234,50 €", de.money(Money(12_345_000), "€"))
    }

    @Test fun frenchUsesNarrowSpaces() {
        assertEquals("1 234 567", fr.grouped(1_234_567))
        assertEquals("12,5 %", fr.percentPpm(125_000))
    }

    @Test fun spanishPutsTheSymbolAfter() {
        assertEquals("1.000,00 €", es.money(Money.units(1_000), "€"))
        assertEquals("-3,25 €", es.money(Money(-32_500), "€"))
    }

    @Test fun englishPutsTheSymbolBefore() {
        assertEquals("$1,000.00", en.money(Money.units(1_000), "$"))
        assertEquals("-$3.25", en.money(Money(-32_500), "$"))
    }

    @Test fun symbolIsOptional() {
        assertEquals("1.000,00", de.money(Money.units(1_000)))
        assertEquals("1,000.00", en.money(Money.units(1_000)))
    }

    @Test fun compactUsesLocaleSuffixes() {
        assertEquals("12,3 mil", es.compactMoney(Money.units(12_300)))
        assertEquals("4,56 Mio.", de.compactMoney(Money.units(4_560_000)))
        assertEquals("950", de.compactMoney(Money.units(950)))
        assertEquals("1,50 Md", fr.compactMoney(Money.units(1_500_000_000)))
    }

    @Test fun compactKeepsThreeSignificantDigits() {
        assertEquals("1.23K", en.compactMoney(Money.units(1_234)))
        assertEquals("12.3K", en.compactMoney(Money.units(12_345)))
        assertEquals("123K", en.compactMoney(Money.units(123_456)))
    }

    @Test fun negativeCompactKeepsTheSign() = assertEquals("-12.3K", en.compactMoney(Money.units(-12_345)))

    @Test fun quantitiesTrimTrailingZeros() {
        assertEquals("12,5", de.quantity(Quantity(12_500)))
        assertEquals("12", de.quantity(Quantity.units(12)))
        assertEquals("0,25", fr.quantity(Quantity(250)))
    }

    @Test fun signedPercentGetsAPlus() {
        assertEquals("+3,2 %", de.signedPercentPpm(32_000))
        assertEquals("0,0 %", de.signedPercentPpm(0))
        assertEquals("-1,0 %", de.signedPercentPpm(-10_000))
    }

    @Test fun datesUseLocaleMonthNames() {
        assertEquals("1 Jan 2000", en.date(Tick(0)))
        assertEquals("1. Jan. 2000", de.date(Tick(0)))
        assertEquals("1 ene 2000", es.date(Tick(0)))
        assertEquals("1 janv. 2000", fr.date(Tick(0)))
    }

    @Test fun monthYearSpellsTheMonthOut() {
        assertEquals("January 2000", en.monthYear(Tick(0)))
        assertEquals("enero de 2000", es.monthYear(Tick(0)))
        assertEquals("März 2000", de.monthYear(Tick(60)))
    }

    @Test fun everyLocaleHasTwelveMonthsAndFourSuffixes() {
        for (l in GameLocale.values()) {
            assertEquals(12, l.monthNames.size)
            assertEquals(12, l.monthAbbreviations.size)
            assertEquals(4, l.compactSuffixes.size)
        }
    }

    @Test fun everyMonthRendersForEveryLocale() {
        for (l in GameLocale.values()) for (m in 0 until 12) {
            val text = LocaleFormat(l).date(Tick(m * 30L))
            assertTrue(text, text.contains(l.monthAbbreviations[m]))
        }
    }

    @Test fun smallAmountsRoundHalfAwayFromZero() {
        assertEquals("0,01", de.money(Money(50)))
        assertEquals("-0,01", de.money(Money(-50)))
        assertEquals("0,00", de.money(Money(49)))
    }
}
