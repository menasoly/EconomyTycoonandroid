package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillDistributionTest {
    private fun hash(d: SkillDistribution) = CanonicalHasher().also { d.hashInto(it) }.finish()

    @Test fun anEmptyGroupHasNoMean() {
        val d = SkillDistribution()
        assertTrue(d.isEmpty)
        assertEquals(null, d.meanOrNull())
        assertEquals(123L, d.mean(123L))
        assertEquals(0L, d.countAtLeast(0L))
        assertEquals(0L, d.percentile(500_000L))
    }

    @Test fun bucketsCoverTheScale() {
        assertEquals(0, SkillDistribution.bucketOf(0L))
        assertEquals(0, SkillDistribution.bucketOf(49_999L))
        assertEquals(1, SkillDistribution.bucketOf(50_000L))
        assertEquals(19, SkillDistribution.bucketOf(1_000_000L))
        assertEquals(19, SkillDistribution.bucketOf(5_000_000L))
        assertEquals(25_000L, SkillDistribution.midpoint(0))
        assertEquals(975_000L, SkillDistribution.midpoint(19))
    }

    @Test fun addingPeopleMovesTheMean() {
        val d = SkillDistribution()
        d.addAt(100_000L, 10L)
        assertEquals(125_000L, d.mean())
        d.addAt(500_000L, 10L)
        assertEquals(10L + 10L, d.total)
        assertEquals(325_000L, d.mean())
    }

    @Test fun countAtLeastSplitsTheStraddledBand() {
        val d = SkillDistribution.single(130_000L, 100L)
        assertEquals(100L, d.countAtLeast(100_000L))
        assertEquals(100L, d.countAtLeast(0L))
        assertEquals(60L, d.countAtLeast(120_000L))
        assertEquals(0L, d.countAtLeast(150_000L))
        assertEquals(0L, d.countAtLeast(2_000_000L))
        assertEquals(600_000L, d.shareAtLeast(120_000L))
    }

    @Test fun percentilesWalkTheBands() {
        val d = SkillDistribution()
        d.addAt(100_000L, 50L); d.addAt(700_000L, 50L)
        assertEquals(125_000L, d.percentile(250_000L))
        assertEquals(125_000L, d.percentile(500_000L))
        assertEquals(725_000L, d.percentile(600_000L))
        assertEquals(725_000L, d.percentile(1_000_000L))
    }

    @Test fun aroundBuildsABellOfTheRightSize() {
        val d = SkillDistribution.around(500_000L, 100_000L, 1_001L)
        assertEquals(1_001L, d.total)
        assertTrue(Math.abs(d.mean() - 500_000L) < 30_000L)
        assertTrue(d.count(SkillDistribution.bucketOf(500_000L)) > d.count(SkillDistribution.bucketOf(300_000L)))
        assertEquals(0L, d.count(0))
        assertTrue(SkillDistribution.around(500_000L, 100_000L, 0L).isEmpty)
    }

    @Test fun takingProportionallyKeepsTheShape() {
        val d = SkillDistribution()
        d.addAt(100_000L, 300L); d.addAt(800_000L, 100L)
        val out = d.take(100L)
        assertEquals(100L, out.total)
        assertEquals(300L, d.total)
        assertEquals(75L, out.count(2))
        assertEquals(25L, out.count(16))
    }

    @Test fun takingBestFirstRemovesTheTop() {
        val d = SkillDistribution()
        d.addAt(100_000L, 10L); d.addAt(500_000L, 10L); d.addAt(900_000L, 10L)
        val best = d.take(15L, TakePolicy.BEST_FIRST)
        assertEquals(10L, best.count(18))
        assertEquals(5L, best.count(10))
        assertEquals(15L, d.total)
        assertEquals(10L, d.count(2))
    }

    @Test fun takingWorstFirstRemovesTheBottom() {
        val d = SkillDistribution()
        d.addAt(100_000L, 10L); d.addAt(900_000L, 10L)
        val worst = d.take(12L, TakePolicy.WORST_FIRST)
        assertEquals(10L, worst.count(2))
        assertEquals(2L, worst.count(18))
        assertEquals(8L, d.count(18))
    }

    @Test fun takingWithAFloorLeavesTheWeakAlone() {
        val d = SkillDistribution()
        d.addAt(100_000L, 20L); d.addAt(700_000L, 5L)
        val out = d.take(10L, TakePolicy.PROPORTIONAL, minLevel = 600_000L)
        assertEquals(5L, out.total)
        assertEquals(20L, d.total)
        assertEquals(20L, d.countAtLeast(0L))
    }

    @Test fun takingMoreThanThereIsTakesEveryone() {
        val d = SkillDistribution.single(300_000L, 7L)
        assertEquals(7L, d.take(50L, TakePolicy.BEST_FIRST).total)
        assertTrue(d.isEmpty)
        assertTrue(d.take(3L).isEmpty)
        assertTrue(d.take(0L).isEmpty)
    }

    @Test fun shrinkToLeavesTheRequestedHeadcount() {
        val d = SkillDistribution.around(400_000L, 150_000L, 500L)
        val left = d.shrinkTo(320L)
        assertEquals(180L, left.total)
        assertEquals(320L, d.total)
        assertTrue(d.shrinkTo(900L).isEmpty)
    }

    @Test fun shiftMovesWholeBandsAtOnce() {
        val d = SkillDistribution.single(100_000L, 40L)
        d.shiftAll(100_000L)
        assertEquals(40L, d.count(4))
        d.shiftAll(-100_000L)
        assertEquals(40L, d.count(2))
    }

    @Test fun smallShiftsCarryTheFractionForward() {
        val d = SkillDistribution.single(200_000L, 100L)
        repeat(10) { d.shiftAll(5_000L) }
        // ten shifts of a tenth of a band move the group's mean by one band, though no one person moved twice at a time
        assertEquals(100L, d.total)
        assertTrue(Math.abs(d.mean() - 275_000L) <= 3_000L)
    }

    @Test fun shiftsNeverCreateOrLosePeople() {
        val d = SkillDistribution.around(500_000L, 200_000L, 997L)
        repeat(200) { d.shift { b -> if (b % 2 == 0) 7_321L else -3_111L } }
        assertEquals(997L, d.total)
    }

    @Test fun theEndsOfTheScaleHoldWhoeverArrives() {
        val d = SkillDistribution.single(960_000L, 10L)
        d.shiftAll(400_000L)
        assertEquals(10L, d.count(19))
        val e = SkillDistribution.single(10_000L, 10L)
        e.shiftAll(-400_000L)
        assertEquals(10L, e.count(0))
    }

    @Test fun nobodyMovesTwiceInOneShift() {
        val d = SkillDistribution()
        d.addAt(0L, 10L); d.addAt(60_000L, 10L)
        d.shiftAll(50_000L)
        assertEquals(10L, d.count(1))
        assertEquals(10L, d.count(2))
    }

    @Test fun addAllMergesGroups() {
        val a = SkillDistribution.single(100_000L, 5L)
        val b = SkillDistribution.single(900_000L, 3L)
        a.addAll(b)
        assertEquals(8L, a.total)
        assertEquals(3L, a.count(18))
    }

    @Test fun copiesAreIndependent() {
        val a = SkillDistribution.single(100_000L, 5L)
        val b = a.copy()
        b.addAt(100_000L, 5L)
        assertEquals(5L, a.total)
        assertEquals(10L, b.total)
        assertNotEquals(a, b)
    }

    @Test fun hashSeesCountsAndCarry() {
        val a = SkillDistribution.single(100_000L, 5L)
        val b = a.copy()
        assertEquals(hash(a), hash(b))
        b.shiftAll(10_000L)
        assertNotEquals(hash(a), hash(b))
    }

    @Test fun restoreRoundTripsAndValidates() {
        val a = SkillDistribution.around(500_000L, 100_000L, 100L)
        a.shiftAll(1_234L)
        val b = SkillDistribution.restore(IntRange(0, 19).map { a.count(it) }.toLongArray(), a.carries())
        assertEquals(a, b)
        assertFalse(a === b)
    }

    @Test(expected = IllegalArgumentException::class) fun restoreRefusesNegativeCounts() {
        SkillDistribution.restore(LongArray(20) { -1L }, LongArray(20))
    }

    @Test(expected = IllegalArgumentException::class) fun addingNegativePeopleIsRefused() {
        SkillDistribution().addAt(100_000L, -1L)
    }
}
