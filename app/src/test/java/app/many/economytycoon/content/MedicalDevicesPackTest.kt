package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalDevicesPackTest : ExpansionPackContract("medical_devices") {
    private fun keys(items: Collection<String>) = items.toSet()

    @Test fun sizeMatchesTheSpecification() {
        assertEquals(38, pack.goods.size); assertEquals(40, pack.recipes.size); assertEquals(7, pack.facilityTypes.size); assertEquals(9, pack.technologies.size)
    }

    @Test fun medicalEquipmentBecomesMakeable() {
        for (k in listOf("medical_equipment", "medical_equipment_kit")) {
            assertTrue("$k", ExpansionFixture.flows.producersOf(app.many.economytycoon.core.GoodId(k)).isNotEmpty())
        }
    }

    @Test fun fiveIndustriesAreHealthcare() {
        assertEquals(keys(listOf("medical_consumables", "implants_and_surgical", "patient_monitoring", "diagnostic_imaging", "hospital_equipment")), keys(pack.industries.map { it.id.key }))
        assertTrue(pack.industries.all { it.family == IndustryFamily.HEALTHCARE })
    }

    @Test fun clinicalEngineersAreTheNewRole() {
        assertEquals(listOf("clinical_engineer"), pack.roles.map { it.id.key })
    }

    @Test fun imagingScannersAreTheDearestGoods() {
        val mri = repo.good(app.many.economytycoon.core.GoodId("mri_scanner")).referencePrice.perUnit.raw
        assertTrue(pack.goods.filter { it.id.key != "mri_scanner" }.all { it.referencePrice.perUnit.raw <= mri })
    }

    @Test fun moldingSwissTurningAndCryostatsAreMachines() {
        assertEquals(keys(listOf("cleanroom_molding_line", "swiss_lathe", "cryostat_line")), keys(pack.machineTypes.map { it.id.key }))
    }
}
