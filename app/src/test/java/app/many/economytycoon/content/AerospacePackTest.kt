package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AerospacePackTest : ExpansionPackContract("aerospace") {
    private fun keys(items: Collection<String>) = items.toSet()

    @Test fun sizeMatchesTheSpecification() {
        assertEquals(28, pack.goods.size); assertEquals(28, pack.recipes.size); assertEquals(8, pack.facilityTypes.size); assertEquals(10, pack.technologies.size)
    }

    @Test fun titaniumIsTheNewDeposit() {
        assertEquals(listOf("titanium_ore"), pack.facilityTypes.mapNotNull { it.extracts?.key })
    }

    @Test fun fiveIndustriesRunFromMaterialsToSpace() {
        assertEquals(keys(listOf("aero_materials", "aerostructures", "propulsion_and_avionics", "aircraft_assembly", "space_systems")), keys(pack.industries.map { it.id.key }))
        assertTrue(pack.industries.all { it.family == IndustryFamily.AEROSPACE })
    }

    @Test fun aircraftCostMoreThanAnythingElseInThePack() {
        val jet = repo.good(app.many.economytycoon.core.GoodId("business_jet")).referencePrice.perUnit.raw
        assertTrue(pack.goods.all { it.referencePrice.perUnit.raw <= jet })
    }

    @Test fun finishedAircraftAreOnlyBoughtBySomeone() {
        for (k in listOf("business_jet", "regional_turboprop", "helicopter", "launch_vehicle", "communications_satellite")) {
            val id = app.many.economytycoon.core.GoodId(k)
            assertTrue("$k", repo.demandProfile(id) != null || ExpansionFixture.flows.consumersOf(id).isNotEmpty())
        }
    }

    @Test fun paybackIsLongerThanAYearAndAHalf() {
        assertTrue(ExpansionFixture.paybacksOf(pack).mapNotNull { it.paybackDays }.max() >= 550L)
    }

    @Test fun melterAutoclaveAndMillAreMachines() {
        assertEquals(keys(listOf("vacuum_arc_remelter", "autoclave", "five_axis_mill", "assembly_jig")), keys(pack.machineTypes.map { it.id.key }))
    }
}
