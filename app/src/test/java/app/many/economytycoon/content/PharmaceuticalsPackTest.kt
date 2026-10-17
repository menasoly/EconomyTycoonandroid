package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PharmaceuticalsPackTest : ExpansionPackContract("pharmaceuticals") {
    private fun keys(items: Collection<String>) = items.toSet()

    @Test fun sizeMatchesTheSpecification() {
        assertEquals(27, pack.goods.size); assertEquals(27, pack.recipes.size); assertEquals(7, pack.facilityTypes.size); assertEquals(8, pack.technologies.size)
    }

    @Test fun industriesCoverBiologicsSpecialtyAndPackaging() {
        assertEquals(keys(listOf("biologics", "specialty_pharma", "pharma_packaging")), keys(pack.industries.map { it.id.key }))
        assertTrue(pack.industries.all { it.family == IndustryFamily.PHARMACEUTICALS })
    }

    @Test fun biotechnologistsAndLabTechniciansAreNewRoles() {
        assertEquals(keys(listOf("biotechnologist", "lab_technician")), keys(pack.roles.map { it.id.key }))
    }

    @Test fun biologicsEarnTheHighestMargins() {
        val byIndustry = ExpansionFixture.economicsOf(pack).groupBy { it.recipe.industry.key }.mapValues { e -> e.value.map { it.marginPpm }.sorted()[(e.value.size - 1) / 2] }
        assertTrue(byIndustry.getValue("biologics") > byIndustry.getValue("pharma_packaging"))
    }

    @Test fun sterileMachinesAreAllThere() {
        assertEquals(keys(listOf("bioreactor", "lyophilizer", "filling_isolator", "blister_line")), keys(pack.machineTypes.map { it.id.key }))
    }

    @Test fun medicinesAreSoldToConsumers() {
        assertTrue(pack.goods.count { it.category == GoodCategory.CONSUMER } >= 8)
    }
}
