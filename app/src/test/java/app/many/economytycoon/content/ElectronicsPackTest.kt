package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectronicsPackTest : ExpansionPackContract("electronics") {
    private fun keys(items: Collection<String>) = items.toSet()

    @Test fun sizeMatchesTheSpecification() {
        assertEquals(36, pack.goods.size); assertEquals(38, pack.recipes.size); assertEquals(6, pack.facilityTypes.size); assertEquals(8, pack.technologies.size)
    }

    @Test fun serverAndNetworkHardwareBecomeMakeable() {
        for (k in listOf("server_hardware", "network_equipment")) {
            assertTrue("$k is not made", ExpansionFixture.flows.producersOf(app.many.economytycoon.core.GoodId(k)).isNotEmpty())
            assertTrue(k in ExpansionFixture.goodKeys(pack) || ExpandedContent.basePacks.any { b -> b.goods.any { it.id.key == k } })
        }
    }

    @Test fun industriesCoverComponentsDisplaysComputingAndDevices() {
        assertEquals(keys(listOf("electronic_components", "displays_and_optics", "computing_hardware", "smart_devices")), keys(pack.industries.map { it.id.key }))
        assertTrue(pack.industries.all { it.family == IndustryFamily.ELECTRONICS })
    }

    @Test fun assemblersAndInspectorsAreNewRoles() {
        assertEquals(keys(listOf("assembler", "quality_inspector")), keys(pack.roles.map { it.id.key }))
    }

    @Test fun surfaceMountAndReflowAreMachines() {
        assertEquals(keys(listOf("smt_line", "reflow_oven", "optics_bonder")), keys(pack.machineTypes.map { it.id.key }))
    }

    @Test fun technologiesReachTierFour() {
        assertEquals(4, pack.technologies.maxOf { it.tier })
    }
}
