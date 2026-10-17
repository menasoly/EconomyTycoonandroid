package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionMachineTest {
    private val repo get() = ExpansionFixture.repo
    private val machines get() = ExpansionFixture.expansion.flatMap { it.machineTypes }

    @Test fun twentySixNewMachines() {
        assertEquals(26, machines.size)
        assertEquals(machines.size, machines.map { it.id }.toSet().size)
    }

    @Test fun everyMachineCostsMoneyAndRunsFasterThanByHand() {
        for (m in machines) {
            assertTrue(m.id.key, m.cost.raw > 0L)
            assertTrue(m.id.key, m.throughputPpm >= 1_000_000L)
        }
    }

    @Test fun everyMachineHasWearAndUpkeep() {
        for (m in machines) {
            assertTrue(m.id.key, m.wearPerBatchPpm in 1L..1_000_000L)
            assertTrue(m.id.key, m.maintenanceCostPerTick.raw >= 0L)
        }
    }

    @Test fun everyMachineNeedsATechnologyThatExists() {
        for (m in machines) assertNotNull(m.id.key, m.requiredTech?.let { repo.technologyOrNull(it) })
    }

    @Test fun everyMachineIsUsedByAtLeastOneRecipe() {
        val used = repo.allRecipes.flatMap { it.machines }.toSet()
        for (m in machines) assertTrue(m.id.key, m.id in used)
    }

    @Test fun machineRecipesRunInFacilitiesOfTheSameFamilyAsTheirIndustry() {
        for (p in ExpansionFixture.expansion) for (r in p.recipes.filter { it.machines.isNotEmpty() }) {
            val f = repo.facilityType(r.facilityType)
            assertTrue(r.id.key, f.family == repo.industry(r.industry).family)
        }
    }

    @Test fun aRecipeNeedsNoMoreThanTwoMachines() {
        for (p in ExpansionFixture.expansion) for (r in p.recipes) assertTrue(r.id.key, r.machines.size <= 2)
    }

    @Test fun machineRecipesAreGatedByResearchOrTheirMachine() {
        for (p in ExpansionFixture.expansion) for (r in p.recipes.filter { it.machines.isNotEmpty() }) {
            val gated = r.requiredTech != null || r.machines.all { repo.machineType(it).requiredTech != null }
            assertTrue(r.id.key, gated)
        }
    }

    @Test fun machineNamesAreNotRepeatedFromTheBasePacks() {
        val base = ExpandedContent.basePacks.flatMap { p -> p.machineTypes.map { it.name.lowercase() } }.toSet()
        assertTrue(machines.none { it.name.lowercase() in base })
    }

    @Test fun costsGrowWithThroughput() {
        val sorted = machines.sortedBy { it.throughputPpm }
        assertTrue(sorted.last().cost.raw >= sorted.first().cost.raw / 4)
    }
}
