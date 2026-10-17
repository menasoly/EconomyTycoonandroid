package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstructionMaterialsPackTest : ExpansionPackContract("construction_materials") {
    private fun keys(items: Collection<String>) = items.toSet()

    @Test fun sizeMatchesTheSpecification() {
        assertEquals(26, pack.goods.size); assertEquals(26, pack.recipes.size); assertEquals(7, pack.facilityTypes.size); assertEquals(8, pack.technologies.size)
    }

    @Test fun gypsumIsTheNewDeposit() {
        assertEquals(listOf("gypsum"), pack.facilityTypes.mapNotNull { it.extracts?.key })
    }

    @Test fun masonsAreTheNewRole() {
        assertEquals(listOf("mason"), pack.roles.map { it.id.key })
    }

    @Test fun industriesCoverFinishesEnvelopeWoodAndPrecast() {
        assertEquals(keys(listOf("interior_finishes", "building_envelope", "engineered_wood", "structural_precast")), keys(pack.industries.map { it.id.key }))
        assertTrue(pack.industries.all { it.family == IndustryFamily.CONSTRUCTION })
    }

    @Test fun industrialGoodsHaveWeight() {
        val heavy = pack.goods.filter { it.category == GoodCategory.INDUSTRIAL }
        assertTrue(heavy.isNotEmpty())
        assertTrue(heavy.all { it.weight >= 1_000L })
    }

    @Test fun machinesAreKilnsPressesAndMoulds() {
        assertEquals(keys(listOf("tile_kiln_line", "panel_press", "precast_mold_line")), keys(pack.machineTypes.map { it.id.key }))
    }
}
