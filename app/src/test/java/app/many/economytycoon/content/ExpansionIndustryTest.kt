package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionIndustryTest {
    private val repo get() = ExpansionFixture.repo
    private val industries get() = ExpansionFixture.expansion.flatMap { it.industries }

    @Test fun thirtyFourNewIndustries() {
        assertEquals(34, industries.size)
    }

    @Test fun everyIndustryHasADescription() {
        assertTrue(industries.all { it.description.isNotBlank() })
    }

    @Test fun everyIndustryIsAvailableFromTheStart() {
        assertTrue(industries.all { it.unlock == null })
    }

    @Test fun everyIndustryHasAtLeastTwoRecipes() {
        for (i in industries) assertTrue(i.id.key, repo.allRecipes.count { it.industry == i.id } >= 2)
    }

    @Test fun everyIndustryRunsInAFacilityOfItsFamily() {
        for (i in industries) {
            val families = repo.allRecipes.filter { it.industry == i.id }.map { repo.facilityType(it.facilityType).family }.toSet()
            assertTrue("${i.id.key} $families", i.family in families)
        }
    }

    @Test fun everyIndustryMakesAtLeastOneFinishedOrIntermediateGood() {
        for (i in industries) {
            val goods = repo.allRecipes.filter { it.industry == i.id }.flatMap { r -> r.outputs.map { it.good } }.distinct()
            assertTrue(i.id.key, goods.isNotEmpty())
        }
    }

    @Test fun everyIndustryMarginIsInTheBand() {
        for (i in industries) {
            val margins = repo.allRecipes.filter { it.industry == i.id }.map { ExpansionFixture.economyById.getValue(it.id.key).marginPpm }.sorted()
            val median = margins[(margins.size - 1) / 2]
            assertTrue("${i.id.key} median $median", median in 150_000L..400_000L)
        }
    }

    @Test fun everyIndustrySpreadIsNarrow() {
        for (i in industries) {
            val margins = repo.allRecipes.filter { it.industry == i.id }.map { ExpansionFixture.economyById.getValue(it.id.key).marginPpm }
            assertTrue("${i.id.key} spread", margins.max() - margins.min() <= 250_000L)
        }
    }

    @Test fun everyFamilyOfTheExpansionHasAFacility() {
        for (i in industries) assertTrue(i.id.key, repo.allFacilityTypes.any { it.family == i.family })
    }

    @Test fun industryNamesAreCapitalised() {
        assertTrue(industries.all { it.name.first().isUpperCase() })
    }

    @Test fun familiesUsedAreTheEightExpansionFamilies() {
        assertEquals(
            setOf(IndustryFamily.CHEMICALS, IndustryFamily.PHARMACEUTICALS, IndustryFamily.TEXTILES, IndustryFamily.CONSTRUCTION, IndustryFamily.ELECTRONICS, IndustryFamily.AUTOMOTIVE, IndustryFamily.AEROSPACE, IndustryFamily.HEALTHCARE),
            industries.map { it.family }.toSet(),
        )
    }

    @Test fun eachPackKeepsToOneFamily() {
        for (p in ExpansionFixture.expansion) assertEquals(p.version.packId, 1, p.industries.map { it.family }.toSet().size)
    }

    @Test fun eachIndustryOwnsAtLeastOneFacilityType() {
        for (i in industries) assertTrue(i.id.key, repo.allRecipes.any { it.industry == i.id })
    }

    @Test fun industryKeysAreNotReusedAsFacilityKeys() {
        val facilities = repo.allFacilityTypes.map { it.id.key }.toSet()
        assertTrue(industries.none { it.id.key in facilities })
    }

    @Test fun industryKeysAreNotReusedAsGoodKeys() {
        val goods = repo.allGoods.map { it.id.key }.toSet()
        assertTrue(industries.none { it.id.key in goods })
    }
}
