package app.many.economytycoon.carbon

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TonnesTest {
    @Test fun oneTonneIsAThousandKilograms() {
        assertEquals(1_000L, Tonnes.of(1).raw)
        assertEquals(Tonnes.of(3), Tonnes.ofKg(3_000))
        assertEquals(12L, Tonnes.of(12).wholeTonnes())
    }

    @Test fun arithmeticIsExact() {
        assertEquals(Tonnes.of(7), Tonnes.of(3) + Tonnes.of(4))
        assertEquals(Tonnes.of(1), Tonnes.of(4) - Tonnes.of(3))
        assertEquals(Tonnes.of(-2), -Tonnes.of(2))
        assertEquals(Tonnes.of(12), Tonnes.of(3) * 4)
    }

    @Test(expected = ArithmeticException::class) fun overflowThrowsInsteadOfWrapping() {
        Tonnes(Long.MAX_VALUE) + Tonnes.ofKg(1)
    }

    @Test fun gramsRoundHalfEvenToAKilogram() {
        assertEquals(Tonnes.ofKg(2), Tonnes.ofGrams(1_500))
        assertEquals(Tonnes.ofKg(2), Tonnes.ofGrams(2_500))
        assertEquals(Tonnes.ofKg(4), Tonnes.ofGrams(3_500))
        assertEquals(Tonnes.ofKg(0), Tonnes.ofGrams(499))
    }

    @Test fun timesRateRoundsDownByDefault() {
        assertEquals(Tonnes.ofKg(333), Tonnes.ofKg(1_000).times(Rate(333_333)))
        assertEquals(Tonnes.ofKg(334), Tonnes.ofKg(1_000).times(Rate(333_500), RoundingMode.UP))
    }

    @Test fun costIsPriceTimesMass() {
        assertEquals(Money.units(250), Tonnes.of(10).cost(Money.units(25)))
        assertEquals(Money.units(0), Tonnes.ZERO.cost(Money.units(25)))
        assertEquals(Money(Money.units(1).raw / 2), Tonnes.ofKg(500).cost(Money.units(1)))
    }

    @Test fun formatShowsThreeDecimals() {
        assertEquals("12.500", Tonnes.ofKg(12_500).format())
        assertEquals("-0.050", Tonnes.ofKg(-50).format())
        assertEquals("0.000", Tonnes.ZERO.toString())
    }

    @Test fun sumMinMaxAndClamps() {
        assertEquals(Tonnes.of(6), Tonnes.sum(listOf(Tonnes.of(1), Tonnes.of(2), Tonnes.of(3))))
        assertEquals(Tonnes.ZERO, Tonnes.sum(emptyList()))
        assertEquals(Tonnes.of(1), Tonnes.min(Tonnes.of(1), Tonnes.of(2)))
        assertEquals(Tonnes.of(2), Tonnes.max(Tonnes.of(1), Tonnes.of(2)))
        assertEquals(Tonnes.ZERO, Tonnes.of(-4).coerceAtLeast(Tonnes.ZERO))
        assertEquals(Tonnes.of(3), Tonnes.of(9).coerceAtMost(Tonnes.of(3)))
    }

    @Test fun signPredicates() {
        assertTrue(Tonnes.of(1).isPositive)
        assertTrue(Tonnes.of(-1).isNegative)
        assertTrue(Tonnes.ZERO.isZero)
        assertFalse(Tonnes.ZERO.isPositive)
    }

    @Test fun scaleTakesAFraction() {
        assertEquals(Tonnes.of(3), Tonnes.of(12).scale(1, 4))
    }

    @Test fun calendarSplitsTicksIntoYearsMonthsAndDays() {
        assertEquals(2000, CarbonCalendar.yearOf(Tick(0)))
        assertEquals(2000, CarbonCalendar.yearOf(Tick(359)))
        assertEquals(2001, CarbonCalendar.yearOf(Tick(360)))
        assertEquals(0, CarbonCalendar.dayOfYear(Tick(360)))
        assertEquals(11, CarbonCalendar.monthOfYear(Tick(359)))
        assertEquals(1, CarbonCalendar.monthOfYear(Tick(30)))
        assertTrue(CarbonCalendar.isYearStart(Tick(720)))
        assertFalse(CarbonCalendar.isYearStart(Tick(721)))
        assertTrue(CarbonCalendar.isMonthStart(Tick(90)))
        assertEquals(Tick(720), CarbonCalendar.startOfYear(2002))
    }
}
