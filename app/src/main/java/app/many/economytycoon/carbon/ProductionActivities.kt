package app.many.economytycoon.carbon

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.splitLargestRemainder
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.FacilityReport
import app.many.economytycoon.production.FacilityStatus

/**
 * Reads what the production engine reported for a facility and turns it into a [FacilityActivity]. The package never
 * touches the production service itself; it only looks at the day's report and at the facility it names.
 *
 * The report gives a total number of batches, not batches per recipe, so the total is shared evenly among the lines that
 * have a recipe and are switched on. That is exact for the common case of one recipe per facility and a fair split
 * otherwise.
 */
class ProductionActivities(
    private val content: ContentRepository,
    private val table: EmissionFactorTable,
    private val countryOfRegion: (RegionId) -> CountryId?,
    /** Reference price of a good; defaults to the content price. */
    private val priceOf: (GoodId) -> Money = { g -> content.goodOrNull(g)?.referencePrice?.perUnit ?: Money.ZERO },
) {
    private val electricity = GoodId(EmissionFactorTable.ELECTRICITY)

    /** The activity of [facility] for the day described by [report], or null if it is not a facility in a known country. */
    fun activity(report: FacilityReport, facility: Facility): FacilityActivity? {
        if (report.facility != facility.id) return null
        val country = countryOfRegion(facility.region) ?: return null
        val type = content.facilityTypeOrNull(facility.type) ?: return null
        val running = facility.status == FacilityStatus.OPERATING
        val consumed = java.util.TreeMap<GoodId, Quantity>(report.consumed)
        return FacilityActivity(
            facility = facility.id, company = facility.owner, country = country, type = facility.type, family = type.family,
            consumed = consumed, batchesByRecipe = batchesByRecipe(report.batches, facility),
            electricityUsed = report.energyUsed, electricityProduced = report.produced[electricity] ?: Quantity.ZERO, outputValue = report.producedValue, energySpend = energySpend(consumed, report.energyUsed),
            operating = running,
        )
    }

    private fun batchesByRecipe(batches: Long, facility: Facility): Map<RecipeId, Long> {
        if (batches <= 0L) return emptyMap()
        val recipes = facility.lines.filter { it.enabled }.mapNotNull { it.recipe }
        if (recipes.isEmpty()) return emptyMap()
        val parts = splitLargestRemainder(batches, LongArray(recipes.size) { 1L })
        val out = java.util.TreeMap<RecipeId, Long>()
        for ((i, r) in recipes.withIndex()) out[r] = (out[r] ?: 0L) + parts[i]
        return out
    }

    private fun energySpend(consumed: Map<GoodId, Quantity>, electricityUsed: Quantity): Money {
        var total = Money.ZERO
        for ((good, qty) in consumed) {
            val isEnergy = table.fuels.containsKey(good) || content.goodOrNull(good)?.category == GoodCategory.ENERGY
            if (isEnergy) total += priceOf(good).valueOf(qty)
        }
        if (electricityUsed.raw > 0L) total += priceOf(electricity).valueOf(electricityUsed)
        return total
    }

    /** Activities for every facility reported on, in facility id order. [lookup] finds the facility a report names. */
    fun activities(reports: List<FacilityReport>, lookup: (app.many.economytycoon.core.FacilityId) -> Facility?): List<FacilityActivity> =
        reports.sortedBy { it.facility }.mapNotNull { r -> lookup(r.facility)?.let { activity(r, it) } }
}
