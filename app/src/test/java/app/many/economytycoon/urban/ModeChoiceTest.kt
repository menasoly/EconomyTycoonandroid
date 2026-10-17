package app.many.economytycoon.urban

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModeChoiceTest {
    private fun sum(s: ModeShares) = s.car + s.transit + s.walk

    @Test fun `the exponential table matches e to the minus a sixteenth steps`() {
        assertEquals(1_000_000L, ModeChoice.expNegPpm(0))
        assertEquals(367_879L, ModeChoice.expNegPpm(16))
        assertTrue(ModeChoice.expNegPpm(32) in 135_000L..135_700L)
        assertEquals(0L, ModeChoice.expNegPpm(16 * 20))
        try { ModeChoice.expNegPpm(-1); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun `the exponential only falls`() {
        var last = Long.MAX_VALUE
        for (k in 0..400L) {
            val v = ModeChoice.expNegPpm(k)
            assertTrue("k=$k", v <= last)
            last = v
        }
    }

    @Test fun `shares always add up to the whole`() {
        val cases = listOf(
            ModeCosts(600, 900, 3_000), ModeCosts(UNREACHABLE, 700, 500), ModeCosts(1_200, UNREACHABLE, 5_000), ModeCosts(UNREACHABLE, UNREACHABLE, 100),
            ModeCosts(UNREACHABLE, UNREACHABLE, UNREACHABLE), ModeCosts(100, 100, 100), ModeCosts(5, 99_999, 1),
        )
        for (c in cases) for (avail in listOf(0L, 250_000L, 1_000_000L)) assertEquals(Scale.PPM, sum(ModeChoice.shares(c, avail)))
    }

    @Test fun `a much faster mode takes almost everyone`() {
        val s = ModeChoice.shares(ModeCosts(5_000, 600, UNREACHABLE))
        assertTrue(s.transit > 900_000L)
    }

    @Test fun `equal costs split the trips by weight`() {
        val s = ModeChoice.shares(ModeCosts(600 - TravelTimes.PARKING_SECONDS, 600, UNREACHABLE))
        assertTrue(Math.abs(s.car - s.transit) <= 1L)
        assertEquals(0L, s.walk)
    }

    @Test fun `short trips are walked`() {
        val s = ModeChoice.shares(ModeCosts(300, 800, 200))
        assertTrue(s.walk > s.car && s.walk > s.transit)
    }

    @Test fun `long walks are not considered`() {
        val s = ModeChoice.shares(ModeCosts(1_000, 1_000, ModeChoice.MAX_WALK_SECONDS + 1))
        assertEquals(0L, s.walk)
    }

    @Test fun `with nothing else available people walk`() {
        assertEquals(ModeShares(0, 0, Scale.PPM), ModeChoice.shares(ModeCosts(UNREACHABLE, UNREACHABLE, UNREACHABLE)))
        assertEquals(ModeShares(0, 0, Scale.PPM), ModeChoice.shares(ModeCosts(UNREACHABLE, UNREACHABLE, 4_000)))
    }

    @Test fun `people without a car use what is left`() {
        val withCars = ModeChoice.shares(ModeCosts(500, 900, UNREACHABLE), Scale.PPM)
        val noCars = ModeChoice.shares(ModeCosts(500, 900, UNREACHABLE), 0L)
        assertTrue(noCars.car < withCars.car)
        assertEquals(0L, noCars.car)
        assertEquals(Scale.PPM, noCars.transit)
    }

    @Test fun `if the only mode is the car and nobody has one the car still wins`() {
        val s = ModeChoice.shares(ModeCosts(500, UNREACHABLE, UNREACHABLE), 0L)
        assertEquals(Scale.PPM, s.car)
    }

    @Test fun `faster transit gains share monotonically`() {
        var last = -1L
        for (t in listOf(2_400L, 1_800L, 1_200L, 900L, 600L)) {
            val s = ModeChoice.shares(ModeCosts(900, t, UNREACHABLE)).transit
            assertTrue(s >= last)
            last = s
        }
    }

    @Test fun `car availability falls as incomes fall and is bounded`() {
        assertEquals(Scale.PPM, ModeChoice.carAvailabilityPpm(0L))
        assertTrue(ModeChoice.carAvailabilityPpm(500_000L) < ModeChoice.carAvailabilityPpm(200_000L))
        assertEquals(400_000L, ModeChoice.carAvailabilityPpm(Scale.PPM))
        assertEquals(250_000L, ModeChoice.carAvailabilityPpm(5_000_000L))
    }

    @Test fun `shares can be looked up by mode`() {
        val s = ModeShares(500_000L, 300_000L, 200_000L)
        assertEquals(300_000L, s.of(TravelMode.TRANSIT))
        assertEquals(700L, ModeCosts(1, 700, 3).of(TravelMode.TRANSIT))
        try { ModeShares(1, 2, 3); fail() } catch (e: IllegalArgumentException) { }
    }
}
