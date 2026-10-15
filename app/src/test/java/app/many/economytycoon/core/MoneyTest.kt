package app.many.economytycoon.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test fun unitsAndCentsUseTheFixedScale() {
        assertEquals(10_000L, Money.units(1).raw)
        assertEquals(100L, Money.cents(1).raw)
        assertEquals(Money.units(2), Money.cents(150) + Money.cents(50))
    }

    @Test fun additionOverflowIsDetected() {
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE) + Money(1) }
    }

    @Test fun formatRoundsHalfEvenToTwoDecimals() {
        assertEquals("1.50", Money.cents(150).format())
        assertEquals("-0.05", Money.cents(-5).format())
        assertEquals("0.00", Money(49).format())
        // 0.005 ties to even (0.00); 0.015 ties to even (0.02)
        assertEquals("0.00", Money(50).format())
        assertEquals("0.02", Money(150).format())
    }

    @Test fun rateMultiplicationRoundsHalfEven() {
        val half = Rate.percent(50)
        assertEquals(Money(5), Money(10).times(half))
        // 3 * 50% = 1.5 -> ties to even 2
        assertEquals(Money(2), Money(3).times(half))
        // 5 * 50% = 2.5 -> ties to even 2
        assertEquals(Money(2), Money(5).times(half))
    }

    @Test fun valueOfQuantityScalesByQuantityUnit() {
        val price = Price.of(10)
        assertEquals(Money.units(25), price.valueOf(Quantity.units(2) + Quantity(500)))
    }

    @Test fun splitAlwaysSumsToTotal() {
        val total = Money(100)
        val parts = total.split(longArrayOf(1, 1, 1))
        assertEquals(100L, parts.sumOf { it.raw })
        assertEquals(listOf(34L, 33L, 33L), parts.map { it.raw })
    }

    @Test fun comparisonsAndSigns() {
        assertTrue(Money(5) > Money(4))
        assertTrue(Money(-1).isNegative)
        assertFalse(Money.ZERO.isPositive)
        assertEquals(Money(7), Money(-7).abs())
    }

    @Test fun sumIsExact() {
        assertEquals(Money(60), Money.sum(listOf(Money(10), Money(20), Money(30))))
    }
}
