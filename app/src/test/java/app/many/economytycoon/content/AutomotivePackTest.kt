package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomotivePackTest : ExpansionPackContract("automotive") {
    private fun keys(items: Collection<String>) = items.toSet()

    @Test fun sizeMatchesTheSpecification() {
        assertEquals(34, pack.goods.size); assertEquals(34, pack.recipes.size); assertEquals(7, pack.facilityTypes.size); assertEquals(10, pack.technologies.size)
    }

    @Test fun fiveIndustriesCoverPartsVehiclesAndAftermarket() {
        assertEquals(keys(listOf("powertrain_and_safety", "passenger_vehicles", "commercial_vehicles", "two_wheelers", "automotive_aftermarket")), keys(pack.industries.map { it.id.key }))
        assertTrue(pack.industries.all { it.family == IndustryFamily.AUTOMOTIVE })
    }

    @Test fun weldersAreTheNewRole() {
        assertEquals(listOf("welder"), pack.roles.map { it.id.key })
    }

    @Test fun vehiclesAreBuiltFromPartsThePackMakes() {
        val own = ExpansionFixture.goodKeys(pack)
        val assembly = pack.recipes.filter { it.industry.key == "passenger_vehicles" }
        assertTrue(assembly.isNotEmpty())
        assertTrue(assembly.all { r -> r.inputs.count { it.good.key in own } >= 3 })
    }

    @Test fun weldingCellsAndPaintBoothsAreMachines() {
        assertEquals(keys(listOf("robotic_welding_cell", "paint_booth", "cnc_center")), keys(pack.machineTypes.map { it.id.key }))
    }

    @Test fun mostTechnologiesHaveEffects() {
        assertTrue(pack.technologies.count { it.effects.isNotEmpty() } >= 6)
    }
}
