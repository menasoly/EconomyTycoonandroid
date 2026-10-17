package app.many.economytycoon.content

import app.many.economytycoon.core.GoodId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionChainTest {
    private val repo get() = ExpansionFixture.repo

    /** Length of the longest recipe chain from a raw good to [good]. */
    private fun depth(good: GoodId, seen: Set<GoodId> = emptySet()): Int {
        if (good in seen) return 0
        val makers = repo.recipesProducing(good)
        if (makers.isEmpty() || ExpansionFixture.flows.isExtracted(good)) return 0
        return 1 + makers.minOf { r -> r.inputs.maxOfOrNull { depth(it.good, seen + good) } ?: 0 }
    }

    private fun ingredientsOf(good: GoodId, seen: MutableSet<GoodId> = HashSet()): Set<GoodId> {
        if (!seen.add(good)) return seen
        repo.recipesProducing(good).firstOrNull()?.inputs?.forEach { ingredientsOf(it.good, seen) }
        return seen
    }

    @Test fun aBusinessJetNeedsAtLeastFiveStagesOfWork() {
        assertTrue(depth(GoodId("business_jet")) >= 5)
    }

    @Test fun anMriScannerNeedsAtLeastFourStages() {
        assertTrue(depth(GoodId("mri_scanner")) >= 4)
    }

    @Test fun aSmartphoneNeedsAtLeastFourStages() {
        val phone = ExpansionFixture.expansion.flatMap { it.goods }.firstOrNull { it.id.key.contains("smartphone") } ?: return
        assertTrue(depth(phone.id) >= 4)
    }

    @Test fun theLaunchVehicleBuildsOnRocketEnginesAndTitanium() {
        val parts = ingredientsOf(GoodId("launch_vehicle")).map { it.key }
        assertTrue("rocket_engine" in parts)
        assertTrue(parts.any { it.contains("titanium") })
    }

    @Test fun theMriScannerBuildsOnSuperconductingWire() {
        assertTrue(ingredientsOf(GoodId("mri_scanner")).any { it.key == "superconducting_wire" })
    }

    @Test fun theBusinessJetBuildsOnWingsAndEngines() {
        val parts = ingredientsOf(GoodId("business_jet")).map { it.key }
        assertTrue("wing_box" in parts)
        assertTrue(parts.any { it.contains("engine") })
    }

    @Test fun everyExpansionGoodHasAtMostSixteenStages() {
        for (k in ExpansionFixture.allExpansionGoods) assertTrue(k, depth(GoodId(k)) <= 16)
    }

    @Test fun finishedGoodsAreDearerThanTheirParts() {
        for (k in listOf("business_jet", "regional_turboprop", "helicopter", "communications_satellite", "launch_vehicle", "mri_scanner")) {
            val g = repo.good(GoodId(k))
            for (r in repo.recipesProducing(g.id)) {
                val priciest = r.inputs.maxOf { repo.good(it.good).referencePrice.perUnit.raw }
                assertTrue(k, g.referencePrice.perUnit.raw > priciest)
            }
        }
    }

    @Test fun chainCostGrowsAlongTheChain() {
        val cc = ExpansionFixture.chain
        assertTrue(cc.perUnit(GoodId("business_jet")) > cc.perUnit(GoodId("wing_box")))
        assertTrue(cc.perUnit(GoodId("mri_scanner")) > cc.perUnit(GoodId("magnet_assembly")))
    }

    @Test fun ingredientsOfAGoodIncludeItself() {
        assertEquals(true, GoodId("rocket_engine") in ingredientsOf(GoodId("launch_vehicle")))
        assertEquals(true, GoodId("launch_vehicle") in ingredientsOf(GoodId("launch_vehicle")))
    }

    @Test fun serverHardwareNeedsChips() {
        assertTrue(ingredientsOf(GoodId("server_hardware")).any { it.key.startsWith("chip") })
    }

    @Test fun medicalEquipmentNeedsMetalAndElectronics() {
        val parts = ingredientsOf(GoodId("medical_equipment")).map { it.key }
        assertTrue(parts.size >= 4)
    }
}
