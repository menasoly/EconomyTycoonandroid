package app.many.economytycoon.talent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillDynamicsTest {
    private val p = SkillDynamicsParams()
    private val machining = TalentTestSupport.taxonomy.skill(DefaultTaxonomy.MACHINING)

    @Test fun theCeilingIsWhatTheJobAsksPlusAMargin() {
        assertEquals(700_000L, SkillDynamics.ceilingFor(550_000L, p))
        assertEquals(1_000_000L, SkillDynamics.ceilingFor(950_000L, p))
    }

    @Test fun learningStopsAtTheCeiling() {
        assertEquals(0L, SkillDynamics.learnDelta(machining, 700_000L, 700_000L, 30L, Ppm.ONE, 0L))
        assertEquals(0L, SkillDynamics.learnDelta(machining, 800_000L, 700_000L, 30L, Ppm.ONE, 0L))
        assertEquals(0L, SkillDynamics.learnDelta(machining, 100_000L, 0L, 30L, Ppm.ONE, 0L))
    }

    @Test fun learningSlowsAsPeopleImprove() {
        val low = SkillDynamics.learnDelta(machining, 100_000L, 700_000L, 30L, Ppm.ONE, 0L)
        val mid = SkillDynamics.learnDelta(machining, 400_000L, 700_000L, 30L, Ppm.ONE, 0L)
        val high = SkillDynamics.learnDelta(machining, 650_000L, 700_000L, 30L, Ppm.ONE, 0L)
        assertTrue(low > mid && mid > high && high > 0L)
    }

    @Test fun practiceAndMentorsScaleTheGain() {
        val base = SkillDynamics.learnDelta(machining, 300_000L, 700_000L, 30L, Ppm.ONE, 0L)
        val half = SkillDynamics.learnDelta(machining, 300_000L, 700_000L, 30L, 500_000L, 0L)
        val mentored = SkillDynamics.learnDelta(machining, 300_000L, 700_000L, 30L, Ppm.ONE, 500_000L)
        assertTrue(Math.abs(half * 2 - base) <= 1L)
        assertEquals(base + base / 2, mentored)
    }

    @Test fun aYearOfWorkRaisesAGroupTowardTheCeiling() {
        val d = SkillDistribution.around(300_000L, 100_000L, 400L)
        val before = d.mean()
        repeat(12) { SkillDynamics.learnByDoing(d, machining, 550_000L, 30L, Ppm.ONE, p) }
        assertTrue(d.mean() > before + 40_000L)
        assertTrue(d.mean() < SkillDynamics.ceilingFor(550_000L, p))
        assertEquals(400L, d.total)
    }

    @Test fun anExperiencedGroupLearnsLessThanANewOne() {
        val novices = SkillDistribution.single(200_000L, 100L)
        val veterans = SkillDistribution.single(650_000L, 100L)
        val gainNew = SkillDynamics.learnByDoing(novices, machining, 550_000L, 90L, Ppm.ONE, p)
        val gainOld = SkillDynamics.learnByDoing(veterans, machining, 550_000L, 90L, Ppm.ONE, p)
        assertTrue(gainNew > gainOld)
    }

    @Test fun unusedSkillsFadeButOnlyToTheFloor() {
        val d = SkillDistribution.single(500_000L, 100L)
        repeat(40) { SkillDynamics.forget(d, machining, 360L) }
        assertTrue(d.mean() <= machining.floorPpm + SkillDistribution.WIDTH)
        assertTrue(d.mean() >= machining.floorPpm - SkillDistribution.WIDTH)
        assertEquals(100L, d.total)
    }

    @Test fun nothingIsForgottenBelowTheFloor() {
        assertEquals(0L, SkillDynamics.forgetDelta(machining, machining.floorPpm, 360L))
        assertEquals(0L, SkillDynamics.forgetDelta(machining, 50_000L, 360L))
        assertTrue(SkillDynamics.forgetDelta(machining, 900_000L, 360L) < 0L)
    }

    @Test fun idlePeopleForgetFasterThanWorkers() {
        val a = SkillDynamics.forgetDelta(machining, 600_000L, 90L)
        val b = SkillDynamics.forgetDelta(machining, 600_000L, 90L, 1_500_000L)
        assertTrue(b < a)
    }

    @Test fun trainingLiftsOnlyTheTrainedShareAndStopsAtTheCap() {
        val d = SkillDistribution.single(300_000L, 100L)
        SkillDynamics.train(d, 100_000L, 500_000L, 800_000L)
        // half the gain for everyone, in expectation: the whole group moves up one band (50k)
        assertEquals(375_000L, d.mean())
        val e = SkillDistribution.single(790_000L, 10L)
        SkillDynamics.train(e, 400_000L, Ppm.ONE, 800_000L)
        assertTrue(e.mean() <= 825_000L)
        assertEquals(10L, e.total)
    }

    @Test fun zeroTrainingShareChangesNothing() {
        val d = SkillDistribution.around(400_000L, 100_000L, 50L)
        val h = d.copy()
        SkillDynamics.train(d, 100_000L, 0L, 800_000L)
        assertEquals(h, d)
    }

    @Test fun mentorBoostGrowsWithTheNumberOfExperts() {
        val few = SkillDistribution(); few.addAt(100_000L, 95L); few.addAt(650_000L, 5L)
        val many = SkillDistribution(); many.addAt(100_000L, 50L); many.addAt(650_000L, 50L)
        assertTrue(SkillDynamics.mentorBoost(many, 700_000L, p) > SkillDynamics.mentorBoost(few, 700_000L, p))
        assertEquals(0L, SkillDynamics.mentorBoost(SkillDistribution.single(100_000L, 10L), 700_000L, p))
    }
}
