package app.many.economytycoon.production

import app.many.economytycoon.content.LaborRequirement
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick

/** How many of the required workers a facility has, expressed as a productive share. Implemented by the workforce adapter. */
interface LaborSupply {
    /** Productive staffing of [required] roles at [facility]: 0 means nobody, 1,000,000 means fully staffed at normal skill. */
    fun staffingPpm(facility: Facility, required: List<LaborRequirement>, tick: Tick): Long

    /** True when the facility's workforce is on strike this tick. */
    fun onStrike(facility: Facility, tick: Tick): Boolean = false
}

/** Electricity and fuel supply. Implemented by the simulation layer against the energy market. */
interface EnergySupply {
    /** Takes up to [wanted] energy for a facility and returns the amount delivered. */
    fun draw(facility: Facility, wanted: Quantity): Quantity
}

/** Access to natural resource bodies for extraction facilities. */
interface ResourceAccess {
    /** Extracts up to [wanted] from the deposit and returns the amount actually obtained. */
    fun extract(deposit: DepositId, good: GoodId, wanted: Quantity): Quantity
}

/** Recipe-level modifiers from technology, policy and events. All values are neutral by default. */
interface ProductionModifiers {
    fun throughputBonus(owner: CompanyId, recipe: RecipeDef): Rate = Rate.ZERO
    fun yieldBonus(owner: CompanyId, recipe: RecipeDef): Rate = Rate.ZERO
    fun inputSavings(owner: CompanyId, recipe: RecipeDef): Rate = Rate.ZERO
    fun energySavings(owner: CompanyId, recipe: RecipeDef): Rate = Rate.ZERO
    fun qualityBonus(owner: CompanyId, recipe: RecipeDef): Rate = Rate.ZERO
    fun laborSavings(owner: CompanyId, recipe: RecipeDef): Rate = Rate.ZERO
    fun storageBonus(owner: CompanyId): Rate = Rate.ZERO

    /** Share of [good] that one particular facility saves on top of the owner-wide savings, for example after a retrofit of that plant. */
    fun facilityInputSavings(facility: app.many.economytycoon.core.FacilityId, good: app.many.economytycoon.core.GoodId): Rate = Rate.ZERO

    /** Share of the energy one particular facility saves on top of the owner-wide savings. */
    fun facilityEnergySavings(facility: app.many.economytycoon.core.FacilityId): Rate = Rate.ZERO
}

object NoModifiers : ProductionModifiers

/** Prices used for scheduling decisions and value allocation. */
fun interface PriceView {
    fun price(good: GoodId): Price
}

/** Charges a company for something a production command costs; false means it could not pay. */
fun interface Payer {
    fun charge(owner: CompanyId, amount: Money, memo: String): Boolean
}

/** Pays for everything; used where finance is not under test. */
object FreePayer : Payer {
    override fun charge(owner: CompanyId, amount: Money, memo: String) = true
}

/** Tunable constants for production. */
data class ProductionParams(
    val maxBatchesPerLinePerTick: Int = 4,
    /** Output share of a defective batch, ppm. */
    val defectOutputPpm: Long = 300_000,
    /** Pollution points per unit of waste held per tick, in ppm of the pollution limit. */
    val pollutionPerWasteUnitPpm: Long = 400,
    val pollutionDecayPpm: Long = 2_000,
    /** Facility condition lost per tick without upkeep, ppm. */
    val conditionDecayPpm: Long = 250,
    val disposalCostPerUnit: Money = Money.units(3),
    val demolishRefundPpm: Long = 300_000,
    val maxMachinesPerLine: Int = 4,
    val lineBuildCostPpm: Long = 150_000,
)
