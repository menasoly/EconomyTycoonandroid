package app.many.economytycoon.content.catalog

import app.many.economytycoon.content.ExpandedContent
import app.many.economytycoon.content.IndustryFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogBuilderTest {
    private val ids = ExpandedContent.expansionPackIds.toSet()
    private val report: CatalogReport by lazy { CatalogBuilder.build(ExpandedContent.packs, ids) }
    private val everything: CatalogReport by lazy { CatalogBuilder.build(ExpandedContent.packs) }

    @Test fun oneEntryPerExpansionPack() {
        assertEquals(ExpandedContent.expansionPackIds, report.packs.map { it.packId })
    }

    @Test fun packTotalsMatchThePacks() {
        for (e in report.packs) {
            val p = ExpandedContent.expansionPack(e.packId)
            assertEquals(p.goods.size, e.goods); assertEquals(p.recipes.size, e.recipes)
            assertEquals(p.facilityTypes.size, e.facilityTypes); assertEquals(p.technologies.size, e.techs)
            assertEquals(p.machineTypes.size, e.machines); assertEquals(p.industries.size, e.industries)
            assertEquals(p.demandProfiles.size, e.demandProfiles)
        }
    }

    @Test fun grandTotalsAddUp() {
        assertEquals(252, report.totalGoods); assertEquals(257, report.totalRecipes)
        assertEquals(54, report.totalFacilityTypes); assertEquals(65, report.totalTechs)
    }

    @Test fun thirtyFourIndustries() {
        assertEquals(34, report.industries.size)
    }

    @Test fun industryRecipeCountsSumToThePackRecipeCount() {
        for (p in report.packs) assertEquals(p.packId, p.recipes, report.industriesOf(p.packId).sumOf { it.recipes })
    }

    @Test fun industryGoodsCoverThePackGoods() {
        for (p in report.packs) {
            val listed = report.industriesOf(p.packId).flatMap { it.goodKeys }.toSet()
            val own = ExpandedContent.expansionPack(p.packId).goods.map { it.id.key }.toSet()
            assertTrue(p.packId, listed.containsAll(own))
        }
    }

    @Test fun everyIndustryHasAMarginSpread() {
        for (e in report.industries) {
            assertTrue(e.key, e.margins.count == e.recipes)
            assertTrue(e.key, e.margins.minPpm <= e.margins.medianPpm && e.margins.medianPpm <= e.margins.maxPpm)
            assertTrue(e.key, e.margins.minPpm > 0L)
        }
    }

    @Test fun everyIndustryHasAPayback() {
        for (e in report.industries) {
            val best = e.bestPaybackDays; val worst = e.worstPaybackDays
            assertNotNull(e.key, best); assertNotNull(e.key, worst)
            if (best != null && worst != null) assertTrue(e.key, best in 1L..worst)
        }
    }

    @Test fun lookupByKey() {
        assertEquals("Aerospace", report.industry("aircraft_assembly")?.family?.displayName)
        assertNull(report.industry("nope"))
    }

    @Test fun lookupPackAndFamily() {
        assertEquals(4, report.pack("chemicals")?.industries)
        assertNull(report.pack("nope"))
        assertEquals(4, report.industriesIn(IndustryFamily.CHEMICALS).size)
        assertEquals(5, report.industriesIn(IndustryFamily.HEALTHCARE).size)
    }

    @Test fun listedKeysMatchTheCounts() {
        for (e in report.industries) {
            assertEquals(e.goods, e.goodKeys.size); assertEquals(e.recipes, e.recipeKeys.size)
            assertEquals(e.facilityTypes, e.facilityKeys.size); assertEquals(e.machines, e.machineKeys.size); assertEquals(e.techs, e.techKeys.size)
        }
    }

    @Test fun keysAreSortedAndUnique() {
        for (e in report.industries) {
            assertEquals(e.goodKeys.sorted().distinct(), e.goodKeys)
            assertEquals(e.recipeKeys.sorted().distinct(), e.recipeKeys)
        }
    }

    @Test fun limitingToOnePackLeavesOnlyItsIndustries() {
        val one = CatalogBuilder.build(ExpandedContent.packs, setOf("textiles"))
        assertEquals(listOf("textiles"), one.packs.map { it.packId })
        assertEquals(4, one.industries.size)
        assertTrue(one.industries.all { it.packId == "textiles" })
    }

    @Test fun anEmptyFilterIncludesTheBasePacksToo() {
        assertTrue(everything.packs.size > report.packs.size)
        assertTrue(everything.industries.size > report.industries.size)
    }

    @Test fun anUnknownPackNameGivesAnEmptyReport() {
        val none = CatalogBuilder.build(ExpandedContent.packs, setOf("nope"))
        assertTrue(none.packs.isEmpty() && none.industries.isEmpty())
        assertEquals(0, none.totalGoods)
    }

    @Test fun reportIsTheSameEachTime() {
        assertEquals(report, CatalogBuilder.build(ExpandedContent.packs, ids))
    }

    @Test fun everyRecipeBelongsToOneIndustryEntry() {
        val all = report.industries.flatMap { it.recipeKeys }
        assertEquals(all.size, all.toSet().size)
        assertEquals(ExpansionFixtureAccess.recipes, all.toSet())
    }
}

private object ExpansionFixtureAccess {
    val recipes: Set<String> get() = app.many.economytycoon.content.ExpansionFixture.allExpansionRecipes
}
