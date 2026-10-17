package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChemicalsPackTest : ExpansionPackContract("chemicals") {
    private fun keys(items: Collection<String>) = items.toSet()

    @Test fun sizeMatchesTheSpecification() {
        assertEquals(34, pack.goods.size); assertEquals(35, pack.recipes.size); assertEquals(6, pack.facilityTypes.size); assertEquals(6, pack.technologies.size)
    }

    @Test fun industriesAreTheFourChemicalFamilies() {
        assertEquals(keys(listOf("inorganic_chemicals", "organic_chemicals", "engineering_polymers", "coatings_and_sealants")), keys(pack.industries.map { it.id.key }))
        assertTrue(pack.industries.all { it.family == IndustryFamily.CHEMICALS })
    }

    @Test fun sulfurAndPotashAreMinedFromNewDeposits() {
        assertEquals(keys(listOf("sulfur_ore", "potash_ore")), keys(pack.facilityTypes.mapNotNull { it.extracts?.key }))
        assertTrue(pack.goods.filter { it.category == GoodCategory.RAW }.map { it.id.key }.containsAll(listOf("sulfur_ore", "potash_ore")))
    }

    @Test fun chemistIsTheOnlyNewRole() {
        assertEquals(listOf("chemist"), pack.roles.map { it.id.key })
    }

    @Test fun reactorsAndExtrudersAreTheMachines() {
        assertEquals(keys(listOf("catalytic_reactor", "extrusion_line", "spray_dryer")), keys(pack.machineTypes.map { it.id.key }))
    }

    @Test fun otherPacksCanBuyChemicals() {
        val consumers = ExpansionFixture.expansion.filter { it.version.packId != "chemicals" }.flatMap { p -> p.recipes.flatMap { r -> r.inputs.map { it.good.key } } }.toSet()
        assertTrue(consumers.any { it in ExpansionFixture.goodKeys(pack) })
    }
}
