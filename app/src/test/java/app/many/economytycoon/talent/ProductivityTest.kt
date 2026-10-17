package app.many.economytycoon.talent

import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RoleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductivityTest {
    private val machinist = TalentTestSupport.taxonomy.profile(RoleId("machinist"))!!

    @Test fun meetingTheRequirementGivesPar() {
        assertEquals(Ppm.ONE, SkillMatch.factor(500_000L, 500_000L))
        assertEquals(Ppm.ONE, SkillMatch.factor(10L, 0L))
    }

    @Test fun deficitCostsMoreThanSurplusPays() {
        val short = SkillMatch.factor(250_000L, 500_000L)
        val over = SkillMatch.factor(750_000L, 500_000L)
        assertTrue(Ppm.ONE - short > over - Ppm.ONE)
        assertTrue(short >= SkillMatch.FLOOR_PPM)
        assertEquals(SkillMatch.FLOOR_PPM, SkillMatch.factor(0L, 500_000L))
    }

    @Test fun factorRisesMonotonicallyAndSaturates() {
        var last = 0L
        for (lvl in 0L..1_000_000L step 50_000L) {
            val f = SkillMatch.factor(lvl, 500_000L)
            assertTrue(f >= last); last = f
        }
        assertEquals(Ppm.ONE + SkillMatch.SURPLUS_BONUS_PPM, SkillMatch.factor(1_000_000L, 500_000L))
        assertEquals(SkillMatch.factor(1_000_000L, 500_000L), SkillMatch.factor(1_000_000L, 400_000L).coerceAtMost(SkillMatch.factor(1_000_000L, 500_000L)))
    }

    @Test fun aPersonAtRequirementIsAtPar() {
        val p = SkillMatch.person(machinist) { skill -> machinist.requirementFor(skill)!!.requiredPpm }
        assertEquals(Ppm.ONE, p.multiplierPpm)
        assertEquals(Ppm.ONE, p.matchPpm)
        assertNull(p.weakest)
    }

    @Test fun theWeakestSkillDragsTheBlend() {
        val strongOnly = SkillMatch.person(machinist) { s -> if (s == DefaultTaxonomy.MACHINING) 550_000L else 0L }
        val balanced = SkillMatch.person(machinist) { s -> if (s == DefaultTaxonomy.MACHINING) 550_000L else 400_000L }
        assertTrue(strongOnly.multiplierPpm < balanced.multiplierPpm)
        assertEquals(DefaultTaxonomy.GENERAL, strongOnly.weakest!!.skill)
        assertEquals(400_000L, strongOnly.weakest!!.shortfallPpm)
    }

    @Test fun matchWeighsSkillsByTheirWeight() {
        val onlyPrimary = SkillMatch.person(machinist) { s -> if (s == DefaultTaxonomy.MACHINING) 600_000L else 0L }
        assertEquals(700_000L, onlyPrimary.matchPpm)
    }

    @Test fun aCohortIsAveragedBandByBand() {
        val trained = SkillDistribution.single(575_000L, 100L)
        val mixed = SkillDistribution(); mixed.addAt(125_000L, 50L); mixed.addAt(975_000L, 50L)
        val general = SkillDistribution.single(425_000L, 100L)
        val a = SkillMatch.cohort(machinist) { s -> if (s == DefaultTaxonomy.MACHINING) trained else general }
        val b = SkillMatch.cohort(machinist) { s -> if (s == DefaultTaxonomy.MACHINING) mixed else general }
        // same mean, but the spread helps less than a flat group, because losses outweigh gains
        assertTrue(a.multiplierPpm > b.multiplierPpm)
    }

    @Test fun aMissingSkillCountsAsNobodyHavingIt() {
        val p = SkillMatch.cohort(machinist) { null }
        assertEquals(SkillMatch.FLOOR_PPM, p.multiplierPpm)
        assertEquals(0L, p.matchPpm)
        assertEquals(2, p.gaps.size)
    }

    @Test fun emptyGroupsGetTheFloor() {
        assertEquals(SkillMatch.FLOOR_PPM, SkillMatch.groupFactor(SkillDistribution(), 500_000L))
    }

    @Test fun theWorkforceEquivalentRoundTripsThroughTheSkillTerm() {
        assertEquals(0L, SkillMatch.equivalentSkillPpm(500_000L))
        assertEquals(250_000L, SkillMatch.equivalentSkillPpm(800_000L))
        assertEquals(500_000L, SkillMatch.equivalentSkillPpm(1_000_000L))
        assertEquals(Ppm.ONE, SkillMatch.equivalentSkillPpm(1_900_000L))
        val skill = 400_000L
        val factor = 600_000L + 800_000L * skill / Ppm.ONE
        assertEquals(skill, SkillMatch.equivalentSkillPpm(factor))
    }

    @Test fun dailyValueScalesWithHeadsAndWage() {
        val v = SkillMatch.dailyValueOfGain(100_000L, Money.units(100), 20L)
        assertEquals(Money.units(200), v)
        assertEquals(Money.ZERO, SkillMatch.dailyValueOfGain(0L, Money.units(100), 20L))
    }

    @Test fun unskilledRolesNeedOnlyGeneralWork() {
        val laborer = DefaultTaxonomy.profileOf("laborer", "Laborer", SkillBand.UNSKILLED, 55, 10)
        val p = SkillMatch.person(laborer) { 250_000L }
        assertEquals(Ppm.ONE, p.multiplierPpm)
    }
}
