package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionRoleTest {
    private val repo get() = ExpansionFixture.repo
    private val roles get() = ExpansionFixture.expansion.flatMap { it.roles }

    @Test fun thirteenNewRoles() {
        assertEquals(setOf("chemist", "biotechnologist", "lab_technician", "textile_technician", "mason", "assembler", "quality_inspector", "welder", "aerospace_technician", "clinical_engineer"), roles.map { it.id.key }.toSet())
    }

    @Test fun wagesGrowWithSkill() {
        val avg = roles.groupBy { it.skillBand }.mapValues { e -> e.value.sumOf { it.baseWage.raw } / e.value.size }
        val bands = avg.keys.sortedBy { it.ordinal }
        for (i in 1 until bands.size) assertTrue(bands[i].name, avg.getValue(bands[i]) >= avg.getValue(bands[i - 1]))
    }

    @Test fun everyRoleIsUsedByARecipe() {
        val used = repo.allRecipes.flatMap { r -> r.labor.map { it.role } }.toSet()
        for (r in roles) assertTrue(r.id.key, r.id in used)
    }

    @Test fun wagesArePositiveAndBelowTheDearestBaseRole() {
        val top = ExpandedContent.basePacks.flatMap { it.roles }.maxOf { it.baseWage.raw }
        for (r in roles) {
            assertTrue(r.id.key, r.baseWage.raw > 0L)
            assertTrue(r.id.key, r.baseWage.raw <= top * 2L)
        }
    }

    @Test fun trainingTakesTimeButNotYears() {
        for (r in roles) assertTrue(r.id.key, r.trainingTicks in 1..400)
    }

    @Test fun specialistRolesTrainLongerThanUnskilledOnes() {
        val unskilled = ExpandedContent.basePacks.flatMap { it.roles }.filter { it.skillBand == SkillBand.UNSKILLED }.map { it.trainingTicks }
        for (r in roles.filter { it.skillBand == SkillBand.SPECIALIST || it.skillBand == SkillBand.EXPERT }) assertTrue(r.id.key, r.trainingTicks >= unskilled.min())
    }

    @Test fun roleNamesAreUnique() {
        assertEquals(roles.size, roles.map { it.name.lowercase() }.toSet().size)
    }

    @Test fun recipesNeverStaffMoreThanSixtyPeople() {
        for (p in ExpansionFixture.expansion) for (r in p.recipes) assertTrue(r.id.key, r.labor.sumOf { it.headcount } <= 60)
    }
}
