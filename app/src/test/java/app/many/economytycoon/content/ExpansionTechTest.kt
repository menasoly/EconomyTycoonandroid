package app.many.economytycoon.content

import app.many.economytycoon.core.TechId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionTechTest {
    private val repo get() = ExpansionFixture.repo
    private val techs get() = ExpansionFixture.expansion.flatMap { it.technologies }

    private fun depthOf(id: TechId, trail: Set<TechId> = emptySet()): Int {
        check(id !in trail) { "cycle at ${id.key}" }
        val t = repo.technology(id)
        return if (t.prerequisites.isEmpty()) 0 else 1 + t.prerequisites.maxOf { depthOf(it, trail + id) }
    }

    @Test fun prerequisitesNeverFormACycle() {
        for (t in techs) depthOf(t.id)
    }

    @Test fun everyPrerequisiteExists() {
        for (t in techs) for (p in t.prerequisites) assertNotNull("${t.id.key} -> ${p.key}", repo.technologyOrNull(p))
    }

    @Test fun aTechnologyIsNeverAheadOfItsPrerequisites() {
        val own = techs.map { it.id }.toSet()
        for (t in techs) for (p in t.prerequisites.filter { it in own }) assertTrue("${t.id.key} tier ${t.tier} vs ${p.key}", repo.technology(p).tier <= t.tier)
    }

    @Test fun researchCostAndTimeArePositive() {
        for (t in techs) {
            assertTrue(t.id.key, t.researchCost.raw > 0L)
            assertTrue(t.id.key, t.researchTicks > 0)
        }
    }

    @Test fun laterTiersCostMoreOnAverage() {
        val byTier = techs.groupBy { it.tier }.mapValues { e -> e.value.sumOf { it.researchCost.raw } / e.value.size }
        val tiers = byTier.keys.sorted()
        for (i in 1 until tiers.size) assertTrue("tier ${tiers[i]}", byTier.getValue(tiers[i]) >= byTier.getValue(tiers[i - 1]))
    }

    @Test fun everyTechnologyDoesSomething() {
        for (t in techs) {
            val does = t.unlocksRecipes.isNotEmpty() || t.unlocksFacilities.isNotEmpty() || t.unlocksMachines.isNotEmpty() || t.effects.isNotEmpty()
            assertTrue(t.id.key, does)
        }
    }

    @Test fun everyUnlockedThingExists() {
        for (t in techs) {
            t.unlocksRecipes.forEach { assertNotNull(it.key, repo.recipeOrNull(it)) }
            t.unlocksFacilities.forEach { assertNotNull(it.key, repo.facilityTypeOrNull(it)) }
            t.unlocksMachines.forEach { assertNotNull(it.key, repo.machineTypeOrNull(it)) }
        }
    }

    @Test fun everyGatedRecipeMachineAndFacilityHasAnUnlockingTechnology() {
        for (p in ExpansionFixture.expansion) {
            for (m in p.machineTypes) assertTrue(m.id.key, techs.any { m.id in it.unlocksMachines })
            for (f in p.facilityTypes.filter { it.requiredTech != null }) assertTrue(f.id.key, techs.any { f.id in it.unlocksFacilities })
        }
    }

    @Test fun aGatedRecipeIsNotReachableBeforeItsTechnology() {
        for (p in ExpansionFixture.expansion) for (r in p.recipes) {
            val t = r.requiredTech ?: continue
            assertTrue(r.id.key, repo.technology(t).unlocksRecipes.contains(r.id))
        }
    }

    @Test fun theDeepestChainIsAtLeastTwoLong() {
        assertTrue(techs.maxOf { depthOf(it.id) } >= 2)
    }

    @Test fun prerequisitesOfPrerequisitesAreIncludedInTheClosure() {
        for (t in techs) {
            val closure = repo.prerequisitesOf(t.id)
            for (p in t.prerequisites) assertTrue(closure.contains(p))
            assertTrue(!closure.contains(t.id))
        }
    }

    @Test fun researchGatesSomeOfTheRecipes() {
        val gated = ExpansionFixture.expansion.sumOf { p -> p.recipes.count { it.requiredTech != null } }
        assertTrue("gated $gated", gated * 20 >= ExpansionFixture.expansion.sumOf { it.recipes.size })
    }

    @Test fun technologyIdsAreUnique() {
        assertEquals(techs.size, techs.map { it.id }.toSet().size)
    }
}
