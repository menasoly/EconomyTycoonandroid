package app.many.economytycoon.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkTest {
    private val peers = listOf(100_000L, 200_000L, 300_000L, 400_000L)

    @Test fun tooFewPeersSayNothing() {
        assertNull(Benchmark.standing(Measure.NET_MARGIN, 150_000L, listOf(1L, 2L)))
    }

    @Test fun leadingOnAHigherIsBetterMeasure() {
        val s = Benchmark.standing(Measure.NET_MARGIN, 500_000L, peers)!!
        assertEquals(1_000_000L, s.rankPpm)
        assertEquals(1, s.quartile)
        assertTrue(s.ahead)
        assertEquals(400_000L, s.best)
        assertEquals(250_000L, s.peerMedian)
        assertEquals(4, s.peers)
    }

    @Test fun trailingOnAHigherIsBetterMeasure() {
        val s = Benchmark.standing(Measure.NET_MARGIN, 50_000L, peers)!!
        assertEquals(0L, s.rankPpm)
        assertEquals(4, s.quartile)
        assertFalse(s.ahead)
    }

    @Test fun lowerIsBetterFlipsTheRank() {
        val low = Benchmark.standing(Measure.DEBT_TO_EQUITY, 50_000L, peers)!!
        assertEquals(1_000_000L, low.rankPpm)
        assertEquals(100_000L, low.best)
        val high = Benchmark.standing(Measure.DEBT_TO_EQUITY, 900_000L, peers)!!
        assertEquals(0L, high.rankPpm)
    }

    @Test fun middleOfThePackIsQuartileTwoOrThree() {
        val s = Benchmark.standing(Measure.NET_MARGIN, 250_000L, peers)!!
        assertEquals(500_000L, s.rankPpm)
        assertEquals(2, s.quartile)
    }

    @Test fun compareOnlyCoversSharedMeasures() {
        val self = mapOf(Measure.NET_MARGIN to 90_000L, Measure.CURRENT to 2_000_000L)
        val others = listOf(
            mapOf(Measure.NET_MARGIN to 100_000L), mapOf(Measure.NET_MARGIN to 200_000L), mapOf(Measure.NET_MARGIN to 150_000L),
        )
        val out = Benchmark.compare(self, others)
        assertEquals(listOf(Measure.NET_MARGIN), out.map { it.measure })
    }

    @Test fun laggardsAndLeadersAreSortedAndLimited() {
        val standings = listOf(
            Standing(Measure.NET_MARGIN, 1, 1, 1, 100_000L, 5),
            Standing(Measure.CURRENT, 1, 1, 1, 900_000L, 5),
            Standing(Measure.CASH, 1, 1, 1, 0L, 5),
            Standing(Measure.GROSS_MARGIN, 1, 1, 1, 950_000L, 5),
            Standing(Measure.EQUITY_RATIO, 1, 1, 1, 500_000L, 5),
        )
        assertEquals(listOf(Measure.CASH, Measure.NET_MARGIN), Benchmark.laggards(standings).map { it.measure })
        assertEquals(listOf(Measure.GROSS_MARGIN, Measure.CURRENT), Benchmark.leaders(standings).map { it.measure })
        assertEquals(1, Benchmark.leaders(standings, 1).size)
    }
}
