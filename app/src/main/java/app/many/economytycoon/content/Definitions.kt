package app.many.economytycoon.content

import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.VehicleTypeId

/** A good that can be produced, stored, shipped and traded. */
data class GoodDef(
    val id: GoodId,
    val name: String,
    val category: GoodCategory,
    val referencePrice: Price,
    val volatility: VolatilityClass = VolatilityClass.NORMAL,
    /** Share of stock lost per tick to spoilage, in ppm. Zero for durable goods. */
    val spoilagePpm: Long = 0,
    /** Transport weight per base unit; scales shipping cost. */
    val weight: Long = 1_000,
    /** Storage space per base unit. */
    val volume: Long = 1_000,
    val tradable: Boolean = true,
    val storable: Boolean = true,
) {
    init {
        require(name.isNotBlank()) { "good ${id.key} needs a name" }
        require(referencePrice.perUnit.raw >= 0) { "good ${id.key} has negative price" }
        require(spoilagePpm in 0..Rate.ONE_PPM) { "good ${id.key} spoilage out of range" }
    }
}

data class IndustryDef(
    val id: IndustryId,
    val name: String,
    val family: IndustryFamily,
    val description: String = "",
    /** Milestone key required to unlock this industry; null means available at start. */
    val unlock: String? = null,
)

data class GoodAmount(val good: GoodId, val quantity: Quantity) {
    init { require(quantity.raw > 0) { "amount for ${good.key} must be positive" } }
}

data class LaborRequirement(val role: RoleId, val headcount: Int) {
    init { require(headcount > 0) { "headcount must be positive" } }
}

/** Recipe: converts inputs and labor into outputs over a fixed number of ticks. */
data class RecipeDef(
    val id: RecipeId,
    val name: String,
    val industry: IndustryId,
    val facilityType: FacilityTypeId,
    val inputs: List<GoodAmount>,
    val outputs: List<GoodAmount>,
    val durationTicks: Int,
    val labor: List<LaborRequirement>,
    val energyPerBatch: Quantity = Quantity.ZERO,
    /** Waste produced per batch (disposal handled by the production engine). */
    val waste: List<GoodAmount> = emptyList(),
    val requiredTech: TechId? = null,
    val machines: List<MachineTypeId> = emptyList(),
    /** Nominal fraction of batches that come out defective before quality modifiers, in ppm. */
    val baseDefectPpm: Long = 20_000,
    /** True when an input is a catalyst that is not consumed and may also appear in outputs. */
    val catalytic: Boolean = false,
) {
    init {
        require(durationTicks > 0) { "recipe ${id.key} needs positive duration" }
        require(outputs.isNotEmpty()) { "recipe ${id.key} needs an output" }
        require(baseDefectPpm in 0..Rate.ONE_PPM) { "recipe ${id.key} defect rate out of range" }
    }
}

data class MachineTypeDef(
    val id: MachineTypeId,
    val name: String,
    val cost: Money,
    /** Throughput multiplier relative to manual operation, in ppm of 1.0 (1,000,000 = 1x). */
    val throughputPpm: Long = Rate.ONE_PPM,
    /** Wear added per production batch in ppm of full life. */
    val wearPerBatchPpm: Long = 500,
    val maintenanceCostPerTick: Money = Money.ZERO,
    val laborSavingsPpm: Long = 0,
    val requiredTech: TechId? = null,
)

data class FacilityTypeDef(
    val id: FacilityTypeId,
    val name: String,
    val facilityClass: FacilityClass,
    val family: IndustryFamily,
    val buildCost: Money,
    val buildTicks: Int,
    /** Production lines the facility can host. */
    val maxLines: Int,
    /** Storage capacity in volume units. */
    val storageCapacity: Quantity,
    val upkeepPerTick: Money,
    /** Electricity needed per active line per tick. */
    val baseEnergyPerLine: Quantity = Quantity.ZERO,
    val requiredTech: TechId? = null,
    /** Staff needed to keep the building operating regardless of lines. */
    val overheadStaff: List<LaborRequirement> = emptyList(),
    val buildInputs: List<GoodAmount> = emptyList(),
    /** Deposit good this facility extracts, if it is an extraction facility. */
    val extracts: GoodId? = null,
) {
    init {
        require(buildTicks > 0) { "facility ${id.key} needs positive build time" }
        require(maxLines >= 0) { "facility ${id.key} has negative lines" }
    }
}

data class RoleDef(
    val id: RoleId,
    val name: String,
    val skillBand: SkillBand,
    /** Reference wage per tick (a game day). */
    val baseWage: Money,
    val trainingTicks: Int = 30,
)

data class TechEffect(
    val kind: EffectKind,
    val magnitude: Rate,
    /** Industry the effect applies to; null means all industries. */
    val industry: IndustryId? = null,
    /** Specific recipe the effect applies to; null means every recipe in scope. */
    val recipe: RecipeId? = null,
)

data class TechDef(
    val id: TechId,
    val name: String,
    val category: TechCategory,
    val tier: Int,
    val researchCost: Money,
    val researchTicks: Int,
    val prerequisites: List<TechId> = emptyList(),
    val unlocksRecipes: List<RecipeId> = emptyList(),
    val unlocksFacilities: List<FacilityTypeId> = emptyList(),
    val unlocksMachines: List<MachineTypeId> = emptyList(),
    val effects: List<TechEffect> = emptyList(),
    /** Ticks after which the technology starts losing competitive value; zero means never. */
    val obsolescenceTicks: Int = 0,
)

data class VehicleTypeDef(
    val id: VehicleTypeId,
    val name: String,
    val mode: TransportMode,
    val capacity: Quantity,
    /** Distance units covered per tick. */
    val speed: Int,
    val cost: Money,
    val operatingCostPerDistance: Money,
    val requiredTech: TechId? = null,
)

/** How one income band consumes a consumer good. */
data class DemandProfileDef(
    val good: GoodId,
    /** Base consumption per capita per tick for each income band, in milli-units. */
    val perCapitaMilli: Map<IncomeBand, Long>,
    /** Price elasticity of demand as a positive number in ppm (1,000,000 = unit elastic). */
    val elasticityPpm: Long,
    /** Income elasticity in ppm. */
    val incomeElasticityPpm: Long = 800_000,
    /** Seasonal multiplier in ppm per season name; missing seasons default to 1.0. */
    val seasonality: Map<String, Long> = emptyMap(),
    val substitutes: List<GoodId> = emptyList(),
    /** Share of the population that never buys this good in ppm. */
    val nonConsumersPpm: Long = 0,
)

/** Global tuning parameters. Units are documented per field; none are hard-coded in engines. */
data class EconomyParams(
    val startingCapital: Money = Money.units(500_000),
    val minimumWagePerTick: Money = Money.units(40),
    val corporateTaxRate: Rate = Rate.percent(20),
    val salesTaxRate: Rate = Rate.percent(8),
    val baseInterestRate: Rate = Rate.percent(4),
    val inflationTarget: Rate = Rate.percent(2),
    val depreciationRatePerYear: Rate = Rate.percent(10),
    /** Per-tick cap on price moves unless the good's volatility class says otherwise. */
    val defaultPriceMovePpm: Long = 50_000,
    val priceFloorPpm: Long = 100_000,
    val priceCeilingPpm: Long = 10_000_000,
    val orderRateLimitPerTick: Int = 50,
    val demandLagTicks: Int = 1,
    val storageSpoilageEnabled: Boolean = true,
)
