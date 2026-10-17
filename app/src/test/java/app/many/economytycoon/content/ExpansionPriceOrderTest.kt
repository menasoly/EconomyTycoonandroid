package app.many.economytycoon.content

import app.many.economytycoon.core.GoodId
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prices that must stay in a sensible order whatever the generator is tuned to. Each pair is (dearer, cheaper). */
class ExpansionPriceOrderTest {
    private val repo get() = ExpansionFixture.repo

    private fun price(key: String) = repo.good(GoodId(key)).referencePrice.perUnit.raw

    private fun assertDearer(pairs: List<Pair<String, String>>) {
        for ((a, b) in pairs) assertTrue("$a (${price(a)}) should cost more than $b (${price(b)})", price(a) > price(b))
    }

    @Test fun aircraftOrder() = assertDearer(listOf(
        "business_jet" to "regional_turboprop", "regional_turboprop" to "helicopter", "helicopter" to "survey_uav",
        "launch_vehicle" to "communications_satellite", "communications_satellite" to "satellite_bus",
        "business_jet" to "turboprop_engine", "regional_turboprop" to "turboprop_engine", "helicopter" to "turboshaft_engine",
        "launch_vehicle" to "rocket_engine", "business_jet" to "wing_box", "business_jet" to "fuselage_section",
    ))

    @Test fun aircraftPartsAreDearerThanTheirMaterials() = assertDearer(listOf(
        "forged_turbine_discs" to "titanium_ore", "wing_box" to "forged_turbine_discs", "rotor_system" to "forged_turbine_discs",
        "solar_array" to "satcom_terminal", "satellite_bus" to "attitude_control_unit",
    ))

    @Test fun medicalOrder() = assertDearer(listOf(
        "mri_scanner" to "xray_unit", "xray_unit" to "ultrasound_scanner", "mri_scanner" to "magnet_assembly",
        "xray_unit" to "x_ray_tube", "mri_scanner" to "detector_array", "ultrasound_scanner" to "ultrasonic_transducer",
    ))

    @Test fun anAssembledPartIsDearerThanItsCheapestComponent() {
        for (p in ExpansionFixture.expansion) for (r in p.recipes) {
            if (r.outputs.size != 1) continue
            val out = r.outputs.single()
            val outPrice = price(out.good.key)
            val cheapest = r.inputs.minOfOrNull { price(it.good.key) } ?: continue
            assertTrue("${r.id.key}: ${out.good.key} costs $outPrice, cheapest input $cheapest", outPrice * 4L >= cheapest || out.quantity.raw > 8_000L)
        }
    }

    @Test fun rawDepositsAreTheCheapestOfTheirChains() {
        for (k in listOf("sulfur_ore", "potash_ore", "gypsum", "titanium_ore")) {
            for (r in repo.recipesConsuming(GoodId(k))) for (o in r.outputs) assertTrue("$k -> ${o.good.key}", price(o.good.key) >= price(k) / 2L)
        }
    }

    @Test fun refiningAddsValue() = assertDearer(listOf(
        "sulfur" to "sulfur_ore", "potash" to "potash_ore", "titanium_sponge" to "titanium_ore",
    ).filter { runCatching { price(it.first) }.isSuccess })

    @Test fun consumerElectronicsAreDearerThanTheirComponents() {
        val phone = ExpansionFixture.expansion.flatMap { it.goods }.filter { it.category == GoodCategory.CONSUMER && it.id.key.contains("phone") }
        for (g in phone) for (r in repo.recipesProducing(g.id)) {
            val maxInput = r.inputs.maxOf { price(it.good.key) }
            assertTrue(g.id.key, price(g.id.key) > maxInput / 2)
        }
    }

    @Test fun cheapestAndDearestGoodsDifferByAWideRatio() {
        val all = ExpansionFixture.allExpansionGoods.map { price(it) }
        assertTrue(all.max() / all.min().coerceAtLeast(1L) > 1_000L)
    }

    @Test fun noGoodIsFree() {
        assertTrue(ExpansionFixture.allExpansionGoods.all { price(it) > 0L })
    }

    @Test fun noGoodCostsMoreThanTwentyMillion() {
        assertTrue(ExpansionFixture.allExpansionGoods.all { price(it) <= 20_000_000L * 10_000L })
    }
}
