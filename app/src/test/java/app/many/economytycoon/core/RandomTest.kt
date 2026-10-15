package app.many.economytycoon.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomTest {
    @Test fun sameSeedGivesSameSequence() {
        val a = DeterministicRandom(42)
        val b = DeterministicRandom(42)
        repeat(100) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test fun knownVectorsAreStable() {
        // These values pin the SplitMix64 implementation; changing them breaks replay compatibility.
        val r = DeterministicRandom(0)
        assertEquals(-2152535657050944081L, r.nextLong())
        assertEquals(7960286522194355700L, r.nextLong())
    }

    @Test fun boundedValuesStayInRange() {
        val r = DeterministicRandom(7)
        repeat(1000) {
            val v = r.nextLong(10)
            assertTrue(v in 0..9)
        }
    }

    @Test fun rangeIsInclusive() {
        val r = DeterministicRandom(9)
        val seen = HashSet<Long>()
        repeat(2000) { seen.add(r.nextInRange(3, 6)) }
        assertEquals(setOf(3L, 4L, 5L, 6L), seen)
    }

    @Test fun streamsAreIndependentOfCreationOrder() {
        val p1 = RandomProvider(123)
        val a1 = p1.stream("market").nextLong()
        val b1 = p1.stream("production").nextLong()

        val p2 = RandomProvider(123)
        val b2 = p2.stream("production").nextLong()
        val a2 = p2.stream("market").nextLong()

        assertEquals(a1, a2)
        assertEquals(b1, b2)
        assertNotEquals(a1, b1)
    }

    @Test fun partitionsDiffer() {
        val p = RandomProvider(5)
        assertNotEquals(p.stream("ai", 1).nextLong(), p.stream("ai", 2).nextLong())
    }

    @Test fun snapshotAndRestoreContinueSequence() {
        val p = RandomProvider(77)
        p.stream("x").nextLong()
        p.stream("y", 3).nextLong()
        val snap = p.snapshot()
        val expectedX = p.stream("x").nextLong()
        val expectedY = p.stream("y", 3).nextLong()

        val q = RandomProvider(77)
        q.restore(snap)
        assertEquals(expectedX, q.stream("x").nextLong())
        assertEquals(expectedY, q.stream("y", 3).nextLong())
    }

    @Test fun chanceExtremes() {
        val r = DeterministicRandom(1)
        repeat(100) { assertTrue(!r.chance(Rate.ZERO)) }
        repeat(100) { assertTrue(r.chance(Rate.ONE)) }
    }

    @Test fun chanceApproximatesProbability() {
        val r = DeterministicRandom(2024)
        var hits = 0
        val trials = 20_000
        repeat(trials) { if (r.chance(Rate.percent(25))) hits++ }
        assertTrue("hits=$hits", hits in 4_600..5_400)
    }

    @Test fun shuffleKeepsElements() {
        val r = DeterministicRandom(3)
        val list = (1..20).toList()
        val s = r.shuffled(list)
        assertEquals(list.toSet(), s.toSet())
        assertEquals(20, s.size)
    }

    @Test fun weightedIndexRespectsZeroWeights() {
        val r = DeterministicRandom(11)
        repeat(500) {
            val i = r.weightedIndex(longArrayOf(0, 3, 0, 1))
            assertTrue(i == 1 || i == 3)
        }
    }

    @Test fun gaussianIsCenteredNearZero() {
        val r = DeterministicRandom(55)
        var sum = 0L
        val n = 10_000
        repeat(n) { sum += r.nextGaussianPpm() }
        assertTrue("mean=${sum / n}", Math.abs(sum / n) < 30_000)
    }
}
