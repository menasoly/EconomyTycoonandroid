package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextilesPackTest : ExpansionPackContract("textiles") {
    private fun keys(items: Collection<String>) = items.toSet()

    @Test fun sizeMatchesTheSpecification() {
        assertEquals(29, pack.goods.size); assertEquals(29, pack.recipes.size); assertEquals(6, pack.facilityTypes.size); assertEquals(6, pack.technologies.size)
    }

    @Test fun industriesFollowTheFibreToHomeChain() {
        assertEquals(keys(listOf("fibers_and_yarns", "technical_textiles", "knitwear_and_sportswear", "home_textiles")), keys(pack.industries.map { it.id.key }))
        assertTrue(pack.industries.all { it.family == IndustryFamily.TEXTILES })
    }

    @Test fun textileTechniciansRunTheLooms() {
        assertEquals(listOf("textile_technician"), pack.roles.map { it.id.key })
        assertTrue(pack.recipes.any { r -> r.labor.any { it.role.key == "textile_technician" } })
    }

    @Test fun mostGoodsAreForConsumers() {
        assertTrue(pack.goods.count { it.category == GoodCategory.CONSUMER } >= 12)
    }

    @Test fun machinesAreSpinnersKnittersAndTufters() {
        assertEquals(keys(listOf("filament_spinner", "circular_knitter", "tufting_machine")), keys(pack.machineTypes.map { it.id.key }))
    }

    @Test fun noNewDepositIsNeeded() {
        assertTrue(pack.facilityTypes.none { it.extracts != null })
    }
}
