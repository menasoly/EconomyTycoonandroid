package app.many.economytycoon.ui

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.ui.model.Parsed
import app.many.economytycoon.ui.model.Parsing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {
    @Test fun moneyAcceptsSeparatorsAndDecimals() {
        assertEquals(Money.units(1_234), Parsing.money("1,234").valueOrNull)
        assertEquals(Money.units(1_234) + Money.cents(50), Parsing.money(" 1 234.5 ").valueOrNull)
        assertEquals(Money(1), Parsing.money("0.0001").valueOrNull)
        assertEquals(Money.units(7), Parsing.money("7.").valueOrNull)
        assertEquals(Money.cents(50), Parsing.money(".5").valueOrNull)
    }

    @Test fun moneyRejectsNonsenseAndTooManyDecimals() {
        for (bad in listOf("abc", "1.2.3", "1e5", "$5", "--1", ".", "12.00001")) assertTrue(bad, Parsing.money(bad) is Parsed.Invalid)
        assertEquals("At most 4 decimal places.", Parsing.money("1.23456").error)
    }

    @Test fun negativesAreOptIn() {
        assertTrue(Parsing.money("-5") is Parsed.Invalid)
        assertEquals(-Money.units(5), Parsing.money("-5", allowNegative = true).valueOrNull)
        assertEquals(Money.units(5), Parsing.money("+5").valueOrNull)
    }

    @Test fun blankIsEmptyNotInvalid() {
        assertEquals(Parsed.Empty, Parsing.money("   "))
        assertEquals(Parsed.Empty, Parsing.quantity(""))
        assertEquals(Parsed.Empty, Parsing.wholeNumber(""))
        assertEquals("This field is required.", Parsing.required(Parsing.money("")).error)
        assertEquals(Money.units(1), Parsing.required(Parsing.money("1")).valueOrNull)
    }

    @Test fun quantitiesHaveThreeDecimals() {
        assertEquals(Quantity(12_500), Parsing.quantity("12.5").valueOrNull)
        assertEquals(Quantity(1), Parsing.quantity("0.001").valueOrNull)
        assertTrue(Parsing.quantity("0.0001") is Parsed.Invalid)
        assertTrue(Parsing.quantity("-1") is Parsed.Invalid)
    }

    @Test fun percentagesBecomeRates() {
        assertEquals(Rate.percent(12) + Rate(5_000), Parsing.percent("12.5").valueOrNull)
        assertEquals(Rate(10_000), Parsing.percent("1").valueOrNull)
        assertEquals(Rate(-25_000), Parsing.percent("-2.5", allowNegative = true).valueOrNull)
        assertTrue(Parsing.percent("1.234") is Parsed.Invalid)
    }

    @Test fun wholeNumbersRefuseDecimalsAndHugeValues() {
        assertEquals(42L, Parsing.wholeNumber("42").valueOrNull)
        assertEquals(1_000_000L, Parsing.wholeNumber("1,000,000").valueOrNull)
        assertTrue(Parsing.wholeNumber("4.2") is Parsed.Invalid)
        assertTrue(Parsing.wholeNumber("99999999999999999999") is Parsed.Invalid)
        assertNull(Parsing.wholeNumber("-3").valueOrNull)
        assertEquals(-3L, Parsing.wholeNumber("-3", allowNegative = true).valueOrNull)
    }

    @Test fun overflowingAmountsAreRefusedNotWrapped() {
        assertTrue(Parsing.money("999999999999999") is Parsed.Invalid)
        assertTrue(Parsing.money("9".repeat(30)) is Parsed.Invalid)
    }
}
