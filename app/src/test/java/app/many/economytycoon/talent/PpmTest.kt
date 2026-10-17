package app.many.economytycoon.talent

import org.junit.Assert.assertEquals
import org.junit.Test

class PpmTest {
    @Test fun mulAndRatioAreInverseAtParity() {
        assertEquals(250_000L, Ppm.mul(500_000L, 500_000L))
        assertEquals(500_000L, Ppm.ratio(1L, 2L))
        assertEquals(0L, Ppm.ratio(5L, 0L))
        assertEquals(0L, Ppm.ratio(5L, -3L))
    }

    @Test fun clampKeepsValuesInRange() {
        assertEquals(0L, Ppm.clamp(-5L))
        assertEquals(Ppm.ONE, Ppm.clamp(2_000_000L))
        assertEquals(300_000L, Ppm.clamp(300_000L))
        assertEquals(5L, Ppm.clamp(9L, 1L, 5L))
    }

    @Test fun lerpBlendsAndClampsTheWeight() {
        assertEquals(150L, Ppm.lerp(100L, 200L, 500_000L))
        assertEquals(100L, Ppm.lerp(100L, 200L, -3L))
        assertEquals(200L, Ppm.lerp(100L, 200L, 9_000_000L))
    }

    @Test fun approachMovesAtLeastOneUnitAndNeverOvershoots() {
        assertEquals(1L, Ppm.approach(0L, 10L, 1L))
        assertEquals(10L, Ppm.approach(9L, 10L, 900_000L))
        assertEquals(-1L, Ppm.approach(0L, -10L, 0L))
        assertEquals(7L, Ppm.approach(7L, 7L, 500_000L))
        assertEquals(50L, Ppm.approach(0L, 100L, 500_000L))
    }

    @Test fun perDaysScalesAnAnnualRate() {
        assertEquals(10_000L, Ppm.perDays(120_000L, 30L))
        assertEquals(120_000L, Ppm.perDays(120_000L, 360L))
    }

    @Test fun smoothstepIsMonotoneWithFlatEnds() {
        assertEquals(0L, Ppm.smooth(0L, 100L, 200L))
        assertEquals(Ppm.ONE, Ppm.smooth(300L, 100L, 200L))
        assertEquals(500_000L, Ppm.smooth(150L, 100L, 200L))
        var last = -1L
        for (x in 100L..200L step 5L) { val v = Ppm.smooth(x, 100L, 200L); org.junit.Assert.assertTrue(v >= last); last = v }
    }
}
