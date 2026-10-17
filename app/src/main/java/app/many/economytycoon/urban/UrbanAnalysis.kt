package app.many.economytycoon.urban

/** All the derived surfaces of a city at one moment. Nothing here is saved: it is recomputed from the plan. */
class CityAnalysis(
    val externalities: ExternalitySurfaces,
    val accessibility: Grid,
    val landValue: LandValueSurface,
    val factors: List<DistrictFactors>,
)

/** Builds the derived view of a city from its saved state. Pure and deterministic. */
object UrbanAnalysis {
    /** Cells of the districts that hold a stop of an active metro or rail line. */
    fun stations(plan: CityPlan): List<Cell> {
        val set = java.util.TreeSet<Cell>()
        for (l in plan.network.activeLines()) {
            if (l.mode == TransitMode.BUS) continue
            for (s in l.stops) set += plan.geometry.centre(s)
        }
        return set.toList()
    }

    fun analyse(plan: CityPlan, params: UrbanParams): CityAnalysis {
        val ext = Externalities.compute(plan.geometry, plan.districts, plan.budget.effects())
        val access = LandValueModel.accessibilityGrid(plan.geometry, plan.districts, stations(plan))
        val priceLevel = LandValueModel.priceLevelPpm(plan.inputs.averageWage, params)
        val land = LandValueModel.compute(plan.geometry, plan.districts, ext, access, priceLevel, params)
        val factors = plan.districts.map { Housing.districtFactors(plan.geometry, it.index, ext, access) }
        return CityAnalysis(ext, access, land, factors)
    }
}
