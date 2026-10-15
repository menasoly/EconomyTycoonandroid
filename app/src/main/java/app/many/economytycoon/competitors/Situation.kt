package app.many.economytycoon.competitors

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.Department
import app.many.economytycoon.companies.DividendPolicy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick

class LineView(
    val id: LineId,
    val recipe: RecipeId?,
    val enabled: Boolean,
    /** Code of the reason the line last stalled, as produced by the production engine. */
    val blocked: String,
    /** Share of the last period the line worked, ppm. */
    val utilizationPpm: Long,
)

class FacilityView(
    val id: FacilityId,
    val type: FacilityTypeId,
    val region: RegionId,
    val operating: Boolean,
    val lines: List<LineView>,
    val maxLines: Int,
    val stock: Map<GoodId, Quantity>,
    val capacity: Quantity,
    /** Profit the facility earned over the last review period. */
    val profit: Money,
    val ageTicks: Long,
)

class TargetView(
    val company: CompanyId,
    val fairPricePerShare: Money,
    val availableShares: Long,
    val totalShares: Long,
    val industries: Set<IndustryId>,
)

/** Everything an AI company sees about itself when it plans. */
class Situation(
    val company: CompanyId,
    val tick: Tick,
    val cash: Money,
    val debt: Money,
    /** Extra borrowing the bank would allow right now. */
    val creditAvailable: Money,
    val operatingCostPerTick: Money,
    val revenuePerTick: Money,
    val facilities: List<FacilityView>,
    val sellRules: Map<Pair<FacilityId, GoodId>, SellRule>,
    val buyRules: Map<Pair<FacilityId, GoodId>, BuyRule>,
    val budgets: Map<Department, Money>,
    val dividend: DividendPolicy,
    val staffTargets: Map<Pair<FacilityId, RoleId>, Long>,
    val researchSlotsFree: Int,
    val researchRanked: List<TechId>,
    val availableRecipes: Set<RecipeId>,
    val availableFacilityTypes: Set<FacilityTypeId>,
    val targets: List<TargetView>,
    val homeRegions: List<RegionId>,
    /** Construction projects already under way. */
    val pendingBuilds: Int = 0,
) {
    val operating: List<FacilityView> get() = facilities.filter { it.operating }
    val netCash: Money get() = cash - debt
}

/** Prices and conditions the AI reads from the world. */
interface MarketOutlook {
    fun price(region: RegionId, good: GoodId): Price
    /** Demand against supply for a good in a region: above 1,000,000 means shortage. */
    fun scarcityPpm(region: RegionId, good: GoodId): Long
    fun wage(role: RoleId, region: RegionId): Money
    fun unemploymentPpm(region: RegionId): Long
    /** Cost of one unit of energy where the plant would stand. */
    fun energyPrice(region: RegionId): Money
}
