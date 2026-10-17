package app.many.economytycoon.content.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class MarginStatsTest {
    @Test fun emptyInputGivesNone() {
        assertEquals(MarginStats.NONE, MarginStats.of(emptyList()))
        assertEquals(0, MarginStats.NONE.count)
    }

    @Test fun singleValueIsAllThree() {
        val s = MarginStats.of(listOf(150_000L))
        assertEquals(MarginStats(1, 150_000L, 150_000L, 150_000L), s)
    }

    @Test fun oddCountTakesTheMiddleValue() {
        val s = MarginStats.of(listOf(300_000L, 100_000L, 200_000L))
        assertEquals(100_000L, s.minPpm); assertEquals(200_000L, s.medianPpm); assertEquals(300_000L, s.maxPpm)
    }

    @Test fun evenCountTakesTheLowerMiddleValue() {
        val s = MarginStats.of(listOf(400_000L, 100_000L, 300_000L, 200_000L))
        assertEquals(200_000L, s.medianPpm)
        assertEquals(4, s.count)
    }

    @Test fun negativeMarginsAreKept() {
        val s = MarginStats.of(listOf(-50_000L, 10_000L))
        assertEquals(-50_000L, s.minPpm); assertEquals(-50_000L, s.medianPpm); assertEquals(10_000L, s.maxPpm)
    }

    @Test fun inputOrderDoesNotMatter() {
        assertEquals(MarginStats.of(listOf(1L, 5L, 3L, 9L, 7L)), MarginStats.of(listOf(9L, 7L, 5L, 3L, 1L)))
    }

    @Test fun duplicatesCountOnce() {
        val s = MarginStats.of(listOf(7L, 7L, 7L))
        assertEquals(3, s.count); assertEquals(7L, s.medianPpm)
    }
}
