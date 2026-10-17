package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankMathTest {
    private fun m(u: Long) = Money.units(u)

    @Test fun monthlyInterestIsATwelfthOfTheAnnualRate() {
        assertEquals(m(100), BankMath.monthlyInterest(m(12_000), Rate.percent(10).ppm))
        assertEquals(Money.ZERO, BankMath.monthlyInterest(m(12_000), 0L))
        assertEquals(Money.ZERO, BankMath.monthlyInterest(Money.ZERO, Rate.percent(10).ppm))
        assertEquals(Money.ZERO, BankMath.monthlyInterest(m(-5), Rate.percent(10).ppm))
    }

    @Test fun twelveMonthsOfInterestIsOneYear() {
        val year = BankMath.monthlyInterest(m(1_200_000), Rate.percent(5).ppm).raw * 12
        assertEquals(m(60_000).raw, year)
    }

    @Test fun dailyInterestUsesTheGameYear() {
        assertEquals(m(10), BankMath.dailyInterest(m(360_000), Rate.percent(1).ppm))
    }

    @Test fun splitSumsExactly() {
        val parts = BankMath.split(Money(1_000_003), longArrayOf(1, 1, 1))
        assertEquals(1_000_003L, parts.sumOf { it.raw })
        assertTrue(parts.maxOf { it.raw } - parts.minOf { it.raw } <= 1L)
    }

    @Test fun splitHandlesZeroes() {
        assertTrue(BankMath.split(m(5), longArrayOf(0, 0)).all { it.isZero })
        assertTrue(BankMath.split(Money.ZERO, longArrayOf(3, 4)).all { it.isZero })
        assertEquals(0, BankMath.split(m(5), longArrayOf()).size)
        assertEquals(listOf(Money.ZERO, m(9)), BankMath.split(m(9), longArrayOf(0, 5)).toList())
    }

    @Test fun cappedSplitRespectsCapsAndRedistributes() {
        val parts = BankMath.splitCapped(m(100), longArrayOf(1, 1, 1), arrayOf(m(10), m(100), m(100)))
        assertEquals(m(10), parts[0])
        assertEquals(m(100), parts.fold(Money.ZERO) { a, b -> a + b })
        assertTrue(parts[1] > m(40))
    }

    @Test fun cappedSplitStopsWhenEverythingIsFull() {
        val parts = BankMath.splitCapped(m(100), longArrayOf(1, 1), arrayOf(m(10), m(20)))
        assertEquals(m(30), parts[0] + parts[1])
    }

    @Test fun cappedSplitIgnoresZeroWeightsAndZeroCaps() {
        val parts = BankMath.splitCapped(m(10), longArrayOf(0, 1, 1), arrayOf(m(5), Money.ZERO, m(50)))
        assertEquals(listOf(Money.ZERO, Money.ZERO, m(10)), parts.toList())
    }

    @Test(expected = IllegalArgumentException::class) fun cappedSplitNeedsMatchingSizes() {
        BankMath.splitCapped(m(1), longArrayOf(1), arrayOf())
    }

    @Test fun ratioAndFallback() {
        assertEquals(Rate.percent(25), BankMath.ratio(m(1), m(4)))
        assertEquals(Rate.ZERO, BankMath.ratio(m(1), Money.ZERO))
        assertEquals(Rate.ONE, BankMath.ratio(m(1), m(-3), Rate.ONE))
    }

    @Test fun blendMovesBetweenTheTwoValues() {
        assertEquals(1_000L, BankMath.blend(1_000L, 5_000L, 0L, 10L))
        assertEquals(5_000L, BankMath.blend(1_000L, 5_000L, 10L, 10L))
        assertEquals(3_000L, BankMath.blend(1_000L, 5_000L, 5L, 10L))
        assertEquals(1_000L, BankMath.blend(1_000L, 5_000L, 5L, 0L))
    }

    @Test fun floorTimesNeverRoundsUp() {
        assertEquals(3L, BankMath.floorTimes(Money(10), Rate.percent(35)).raw)
        assertEquals(Rate.percent(12).ppm / 100L, BankMath.basisPointsOf(Rate.percent(12)))
    }
}
