package app.many.economytycoon.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoundingTest {
    @Test fun divRoundModesOnPositiveValues() {
        assertEquals(2L, divRound(5, 2, RoundingMode.DOWN))
        assertEquals(3L, divRound(5, 2, RoundingMode.UP))
        assertEquals(2L, divRound(5, 2, RoundingMode.FLOOR))
        assertEquals(3L, divRound(5, 2, RoundingMode.CEIL))
        assertEquals(2L, divRound(5, 2, RoundingMode.HALF_EVEN))
        assertEquals(4L, divRound(7, 2, RoundingMode.HALF_EVEN))
        assertEquals(3L, divRound(5, 2, RoundingMode.HALF_UP))
    }

    @Test fun divRoundModesOnNegativeValues() {
        assertEquals(-2L, divRound(-5, 2, RoundingMode.DOWN))
        assertEquals(-3L, divRound(-5, 2, RoundingMode.UP))
        assertEquals(-3L, divRound(-5, 2, RoundingMode.FLOOR))
        assertEquals(-2L, divRound(-5, 2, RoundingMode.CEIL))
        assertEquals(-2L, divRound(-5, 2, RoundingMode.HALF_EVEN))
        assertEquals(-3L, divRound(-5, 2, RoundingMode.HALF_UP))
    }

    @Test fun exactDivisionIsUnchangedByMode() {
        for (mode in RoundingMode.values()) assertEquals(4L, divRound(8, 2, mode))
    }

    @Test fun divisionByZeroThrows() {
        assertThrows(ArithmeticException::class.java) { divRound(1, 0, RoundingMode.DOWN) }
    }

    @Test fun mulDivUsesWideIntermediate() {
        val big = 4_000_000_000_000_000_000L
        assertEquals(big / 2, mulDiv(big, 3, 6, RoundingMode.DOWN))
        assertEquals(3_000_000_000_000_000_000L, mulDiv(big, 3, 4, RoundingMode.DOWN))
    }

    @Test fun mulDivOverflowOfResultThrows() {
        assertThrows(ArithmeticException::class.java) { mulDiv(Long.MAX_VALUE, 4, 1, RoundingMode.DOWN) }
    }

    @Test fun mulDivRoundsTiesToEven() {
        assertEquals(2L, mulDiv(5, 1, 2, RoundingMode.HALF_EVEN))
        assertEquals(2L, mulDiv(3, 1, 2, RoundingMode.HALF_EVEN))
        val big = 3_000_000_000_000_000_001L
        assertEquals(1_500_000_000_000_000_000L, mulDiv(big, 1, 2, RoundingMode.HALF_EVEN))
    }

    @Test fun largestRemainderSumsExactly() {
        val parts = splitLargestRemainder(10, longArrayOf(1, 1, 1))
        assertEquals(10L, parts.sum())
        assertEquals(listOf(4L, 3L, 3L), parts.toList())
    }

    @Test fun largestRemainderFavorsLargestFraction() {
        val parts = splitLargestRemainder(5, longArrayOf(3, 2, 2))
        assertEquals(5L, parts.sum())
        // exact shares 2.14, 1.43, 1.43 -> floors 2,1,1 leftover 1 goes to a 0.43 remainder: index 1 (tie by index)
        assertEquals(listOf(2L, 2L, 1L), parts.toList())
    }

    @Test fun largestRemainderHandlesZeroWeights() {
        val parts = splitLargestRemainder(7, longArrayOf(0, 5, 0, 5))
        assertEquals(7L, parts.sum())
        assertEquals(0L, parts[0])
        assertEquals(0L, parts[2])
    }

    @Test fun largestRemainderNegativeTotalMirrorsPositive() {
        val pos = splitLargestRemainder(10, longArrayOf(2, 3, 5))
        val neg = splitLargestRemainder(-10, longArrayOf(2, 3, 5))
        assertEquals(pos.map { -it }, neg.toList())
    }

    @Test fun isqrtMatchesDefinition() {
        for (n in listOf(0L, 1, 2, 3, 4, 15, 16, 17, 1_000_000L, Long.MAX_VALUE / 4)) {
            val r = isqrt(n)
            assertEquals(true, r * r <= n && (r + 1) * (r + 1) > n)
        }
    }

    @Test fun emaMovesTowardsSample() {
        assertEquals(150L, ema(100, 200, Rate.percent(50)))
        assertEquals(100L, ema(100, 200, Rate.ZERO))
        assertEquals(200L, ema(100, 200, Rate.ONE))
    }
}
