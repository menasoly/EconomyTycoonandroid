package app.many.economytycoon.simulation

import app.many.economytycoon.carbon.AbatementEffect
import app.many.economytycoon.carbon.AbatementKind
import app.many.economytycoon.carbon.CarbonState
import app.many.economytycoon.carbon.EmissionFactorTable
import app.many.economytycoon.carbon.SourceKind
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.production.ProductionModifiers

/**
 * Production modifiers with the effect of finished efficiency retrofits added: a retrofitted facility burns less fuel
 * and draws less power, and nothing else changes. Everything else is passed on to [base]. A facility with no retrofit
 * (every facility, in a game with no carbon policy) gets exactly what [base] gives.
 */
class CarbonModifiers(
    private val base: ProductionModifiers,
    private val state: CarbonState,
    private val table: EmissionFactorTable,
    private val content: ContentRepository,
) : ProductionModifiers by base {

    private fun retrofits(facility: FacilityId): List<AbatementEffect> {
        if (state.isPristine) return emptyList()
        val record = state.inventory.facility(facility) ?: return emptyList()
        return record.effects.filter { it.kind == AbatementKind.EFFICIENCY_RETROFIT }
    }

    private fun savings(effects: List<AbatementEffect>, source: SourceKind, key: String): Rate {
        var surviving = Rate.ONE_PPM
        for (e in effects) surviving = mulDiv(surviving, e.survivingPpm(source, key), Rate.ONE_PPM, RoundingMode.HALF_EVEN)
        return Rate(Rate.ONE_PPM - surviving)
    }

    override fun facilityInputSavings(facility: FacilityId, good: GoodId): Rate {
        val effects = retrofits(facility)
        if (effects.isEmpty()) return Rate.ZERO
        val isFuel = table.fuels.containsKey(good) || content.goodOrNull(good)?.category == GoodCategory.ENERGY
        return if (isFuel) savings(effects, SourceKind.FUEL, good.key) else Rate.ZERO
    }

    override fun facilityEnergySavings(facility: FacilityId): Rate {
        val effects = retrofits(facility)
        return if (effects.isEmpty()) Rate.ZERO else savings(effects, SourceKind.ELECTRICITY, "grid")
    }
}
