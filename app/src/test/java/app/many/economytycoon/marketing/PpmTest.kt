package app.many.economytycoon.marketing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PpmTest {
    @Test fun multiplyScalesByParts() {
        assertEquals(250_000L, Ppm.mul(500_000L, 500_000L))
        assertEquals(0L, Ppm.mul(0L, 900_000L))
        assertEquals(1_000_000L, Ppm.mul(1_000_000L, 1_000_000L))
    }

    @Test fun clampKeepsValuesInRange() {
        assertEquals(0L, Ppm.clamp(-5L))
        assertEquals(1_000_000L, Ppm.clamp(2_000_000L))
        assertEquals(42L, Ppm.clamp(42L))
    }

    @Test fun ratioOfZeroDenominatorIsZero() {
        assertEquals(0L, Ppm.ratio(5L, 0L))
        assertEquals(500_000L, Ppm.ratio(1L, 2L))
    }

    @Test fun unionIsAtLeastTheLargestPart() {
        val parts = longArrayOf(300_000L, 200_000L)
        val u = Ppm.union(parts)
        assertEquals(440_000L, u)
        assertTrue(u >= 300_000L)
    }

    @Test fun unionOfNothingIsZeroAndOfCertaintyIsOne() {
        assertEquals(0L, Ppm.union(longArrayOf()))
        assertEquals(1_000_000L, Ppm.union(longArrayOf(1_000_000L, 10L)))
    }

    @Test fun unionNeverExceedsOne() {
        assertTrue(Ppm.union(longArrayOf(900_000L, 900_000L, 900_000L)) <= 1_000_000L)
    }

    @Test fun decayShrinksGeometrically() {
        val after = Ppm.decay(1_000_000L, 100_000L, 2)
        assertEquals(810_000L, after)
        assertEquals(1_000_000L, Ppm.decay(1_000_000L, 100_000L, 0))
    }
}
