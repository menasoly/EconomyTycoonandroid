package app.many.economytycoon.urban

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CongestionTest {
    @Test fun `an empty road runs at free flow`() {
        assertEquals(600L, Congestion.travelSeconds(600L, 0L, 1_000L))
        assertEquals(Scale.PPM, Congestion.delayFactorPpm(0L))
    }

    @Test fun `at capacity the standard curve adds the alpha share`() {
        assertEquals(Scale.PPM + Congestion.BPR_ALPHA_PPM, Congestion.delayFactorPpm(Scale.PPM))
        assertEquals(690L, Congestion.travelSeconds(600L, 1_000L, 1_000L))
    }

    @Test fun `delay grows steeply past capacity`() {
        val atCap = Congestion.travelSeconds(600L, 1_000L, 1_000L)
        val over = Congestion.travelSeconds(600L, 1_500L, 1_000L)
        val way = Congestion.travelSeconds(600L, 2_000L, 1_000L)
        assertTrue(over > atCap && way > over)
        assertEquals(600L * (1_000_000L + 150_000L * 16L) / 1_000_000L, way)
    }

    @Test fun `the curve is monotone`() {
        var last = 0L
        for (v in 0..40) {
            val t = Congestion.travelSeconds(1_000L, v * 100L, 1_000L)
            assertTrue(t >= last)
            last = t
        }
    }

    @Test fun `hopeless overload is capped and a missing road is jammed`() {
        assertEquals(Congestion.MAX_RATIO_PPM, Congestion.volumeCapacityPpm(1_000_000L, 10L))
        assertEquals(Congestion.MAX_RATIO_PPM, Congestion.volumeCapacityPpm(5L, 0L))
        assertEquals(0L, Congestion.volumeCapacityPpm(-3L, 100L))
        assertEquals(Congestion.delayFactorPpm(Congestion.MAX_RATIO_PPM), Congestion.delayFactorPpm(99_000_000L))
    }

    @Test fun `integer powers match repeated multiplication`() {
        assertEquals(Scale.PPM, Congestion.powPpm(500_000L, 0))
        assertEquals(500_000L, Congestion.powPpm(500_000L, 1))
        assertEquals(62_500L, Congestion.powPpm(500_000L, 4))
        assertEquals(16_000_000L, Congestion.powPpm(2_000_000L, 4))
        try { Congestion.powPpm(1L, -1); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun `levels of service step with the ratio`() {
        assertEquals(LevelOfService.A, LevelOfService.of(0L))
        assertEquals(LevelOfService.B, LevelOfService.of(400_000L))
        assertEquals(LevelOfService.C, LevelOfService.of(600_000L))
        assertEquals(LevelOfService.D, LevelOfService.of(800_000L))
        assertEquals(LevelOfService.E, LevelOfService.of(1_000_000L))
        assertEquals(LevelOfService.F, LevelOfService.of(1_000_001L))
    }

    @Test fun `crowding on transit is gentler than road delay at the same load`() {
        assertTrue(Congestion.crowdingFactorPpm(Scale.PPM) > Scale.PPM)
        assertEquals(Scale.PPM, Congestion.crowdingFactorPpm(0L))
        assertEquals(Congestion.delayFactorPpm(800_000L) - Scale.PPM, Congestion.delayPpm(800_000L))
    }
}
