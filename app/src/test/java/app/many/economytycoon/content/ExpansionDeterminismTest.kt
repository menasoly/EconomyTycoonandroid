package app.many.economytycoon.content

import app.many.economytycoon.content.catalog.CatalogBuilder
import app.many.economytycoon.content.catalog.CatalogFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionDeterminismTest {
    private fun fingerprint(repo: ContentRepository): Long {
        var h = 1125899906842597L
        fun mix(s: String) { for (c in s) h = 31L * h + c.code }
        repo.allGoods.forEach { mix(it.toString()) }
        repo.allRecipes.forEach { mix(it.toString()) }
        repo.allFacilityTypes.forEach { mix(it.toString()) }
        repo.allTechnologies.forEach { mix(it.toString()) }
        repo.allIndustries.forEach { mix(it.toString()) }
        repo.allMachineTypes.forEach { mix(it.toString()) }
        repo.allDemandProfiles.forEach { mix(it.toString()) }
        return h
    }

    @Test fun twoRepositoriesFromTheSamePacksAreIdentical() {
        val a = ContentRepository.of(ExpandedContent.packs)
        val b = ContentRepository.of(ExpandedContent.packs)
        assertEquals(fingerprint(a), fingerprint(b))
    }

    @Test fun theOrderPacksAreListedInDoesNotChangeTheRepository() {
        val forward = ContentRepository.of(ExpandedContent.packs)
        val backward = ContentRepository.of(ExpandedContent.packs.reversed())
        assertEquals(fingerprint(forward), fingerprint(backward))
    }

    @Test fun listingsComeBackSorted() {
        val repo = ExpandedContent.repository
        assertEquals(repo.allGoods.map { it.id }.sorted(), repo.allGoods.map { it.id })
        assertEquals(repo.allRecipes.map { it.id }.sorted(), repo.allRecipes.map { it.id })
        assertEquals(repo.allTechnologies.map { it.id }.sorted(), repo.allTechnologies.map { it.id })
    }

    @Test fun expansionPacksAreTheSameObjectsEveryTime() {
        assertTrue(ExpandedContent.expansionPacks === ExpandedContent.expansionPacks)
        assertTrue(ExpandedContent.packs === ExpandedContent.packs)
    }

    @Test fun expansionChangesTheFingerprint() {
        assertNotEquals(fingerprint(ShippedContent.repository), fingerprint(ExpandedContent.repository))
    }

    @Test fun auditIsRepeatable() {
        val a = app.many.economytycoon.content.audit.ContentAudit.run(ExpandedContent.repository).findings.map { it.toString() }
        val b = app.many.economytycoon.content.audit.ContentAudit.run(ExpandedContent.repository).findings.map { it.toString() }
        assertEquals(a, b)
    }

    @Test fun economicsAreRepeatable() {
        val a = app.many.economytycoon.content.audit.RecipeEconomics.all(ExpandedContent.repository).map { it.marginPpm }
        val b = app.many.economytycoon.content.audit.RecipeEconomics.all(ExpandedContent.repository).map { it.marginPpm }
        assertEquals(a, b)
    }

    @Test fun catalogTextIsRepeatable() {
        val a = CatalogFormat.text(CatalogBuilder.build(ExpandedContent.packs, ExpandedContent.expansionPackIds.toSet()))
        val b = CatalogFormat.text(CatalogBuilder.build(ExpandedContent.packs, ExpandedContent.expansionPackIds.toSet()))
        assertEquals(a, b)
    }

    @Test fun everyPackSerialisesToTheSameTextTwice() {
        for (p in ExpandedContent.expansionPacks) assertEquals(p.goods.toString(), p.goods.toString())
    }
}
