package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedContentTest {
    @Test fun validatorFindsNothingInTheCombinedPacks() {
        val issues = ContentValidator.validate(ExpandedContent.packs)
        assertTrue("issues: $issues", issues.isEmpty())
    }

    @Test fun noFatalIssueAndNoWarning() {
        val issues = ContentValidator.validate(ExpandedContent.packs)
        assertTrue(ContentValidator.fatal(issues).isEmpty())
        assertTrue(issues.none { !it.fatal })
    }

    @Test fun eightExpansionPacksInDependencyOrder() {
        assertEquals(
            listOf("chemicals", "pharmaceuticals", "textiles", "construction_materials", "electronics", "automotive", "aerospace", "medical_devices"),
            ExpandedContent.expansionPackIds,
        )
    }

    @Test fun combinedPacksStartWithTheShippedOnes() {
        val shipped = ShippedContent.packs.map { it.version.packId }
        assertEquals(shipped, ExpandedContent.packs.map { it.version.packId }.take(shipped.size))
    }

    @Test fun everyExpansionPackIsIncludedExactlyOnce() {
        val ids = ExpandedContent.packs.map { it.version.packId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.containsAll(ExpandedContent.expansionPackIds))
    }

    @Test fun basePacksAreTheShippedPacksOnly() {
        assertEquals(ShippedContent.packs.map { it.version.packId }.filter { it !in ExpandedContent.expansionPackIds }, ExpandedContent.basePacks.map { it.version.packId })
    }

    @Test fun lookingUpAPackByIdWorks() {
        assertEquals("aerospace", ExpandedContent.expansionPack("aerospace").version.packId)
    }

    @Test(expected = IllegalStateException::class) fun lookingUpAnUnknownPackFails() {
        ExpandedContent.expansionPack("no_such_pack")
    }

    @Test fun repositoryHoldsEveryPackedDefinition() {
        val repo = ExpandedContent.repository
        assertEquals(ExpandedContent.packs.sumOf { it.goods.size }, repo.allGoods.size)
        assertEquals(ExpandedContent.packs.sumOf { it.recipes.size }, repo.allRecipes.size)
        assertEquals(ExpandedContent.packs.sumOf { it.facilityTypes.size }, repo.allFacilityTypes.size)
        assertEquals(ExpandedContent.packs.sumOf { it.technologies.size }, repo.allTechnologies.size)
        assertEquals(ExpandedContent.packs.sumOf { it.industries.size }, repo.allIndustries.size)
    }

    @Test fun expansionAddsTheSpecifiedAmountOfContent() {
        val e = ExpandedContent.expansionPacks
        assertEquals(252, e.sumOf { it.goods.size })
        assertEquals(257, e.sumOf { it.recipes.size })
        assertEquals(54, e.sumOf { it.facilityTypes.size })
        assertEquals(65, e.sumOf { it.technologies.size })
        assertEquals(34, e.sumOf { it.industries.size })
        assertEquals(26, e.sumOf { it.machineTypes.size })
    }

    @Test fun theFourNewDepositsAreAllPresent() {
        val extracted = ExpandedContent.expansionPacks.flatMap { p -> p.facilityTypes.mapNotNull { it.extracts?.key } }.toSet()
        assertEquals(setOf("sulfur_ore", "potash_ore", "gypsum", "titanium_ore"), extracted)
    }

    @Test fun theExpansionIsLargerThanAnyBasePack() {
        val biggestBase = ExpandedContent.basePacks.maxOf { it.recipes.size }
        assertTrue(ExpandedContent.expansionPacks.all { it.recipes.size >= biggestBase / 4 })
    }

    @Test fun expansionDoesNotTouchTheShippedGoodList() {
        val shipped = ContentRepository.of(ExpandedContent.basePacks).allGoods.map { it.id }.toSet()
        assertTrue(ExpandedContent.repository.allGoods.map { it.id }.containsAll(shipped))
        assertFalse(ExpandedContent.repository.allGoods.size == shipped.size)
    }
}
