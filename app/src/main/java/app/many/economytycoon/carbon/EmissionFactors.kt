package app.many.economytycoon.carbon

import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RecipeId

/** Where a factor came from; shown in reports so a player can see which figures are measured and which are assumed. */
enum class FactorSource(val label: String) {
    /** An entry for exactly this good, recipe or facility type. */
    TABLE("Table"),
    /** No entry; the category of the good supplied a default. */
    CATEGORY_DEFAULT("Category default"),
    /** No entry; the industry family of the recipe supplied a default. */
    FAMILY_DEFAULT("Industry default"),
    /** Nothing known: counted as zero rather than failing. */
    NONE("Unknown"),
}

/**
 * Greenhouse gas released when one base unit of a good is burned or consumed as a fuel or feedstock, in grams of CO2
 * equivalent. A [biogenic] fuel releases carbon that plants took from the air, so it is reported but not regulated.
 */
data class FuelFactor(val gramsPerUnit: Long, val biogenic: Boolean = false) {
    init { require(gramsPerUnit >= 0L) { "a factor cannot be negative" } }
}

/**
 * The emission factors of the game, keyed by content ids. Any id missing from the table gets a graceful default
 * instead of an error, because content packs are added over time and a factor must never be a reason to refuse a save.
 *
 *  * [fuels]: grams per unit consumed of a good that releases carbon when used.
 *  * [processGramsPerBatch]: grams per batch of a recipe whose chemistry itself releases carbon (calcination, smelting).
 *  * [familyProcessGramsPerBatch]: the default process release per batch for recipes with no entry, by industry family.
 *  * [overheadGramsPerDay]: grams a facility type releases per operating day regardless of what it makes (heating, site vehicles).
 *  * [electricityGramsPerUnit]: grams per unit of electricity drawn from the grid, used when no national figure is known.
 *  * [energyGoodGramsPerUnit]: grams per unit for goods in the energy category with no entry.
 */
class EmissionFactorTable(
    fuels: Map<GoodId, FuelFactor> = emptyMap(),
    processGramsPerBatch: Map<RecipeId, Long> = emptyMap(),
    familyProcessGramsPerBatch: Map<IndustryFamily, Long> = emptyMap(),
    overheadGramsPerDay: Map<FacilityTypeId, Long> = emptyMap(),
    val electricityGramsPerUnit: Long = 0L,
    val energyGoodGramsPerUnit: Long = 0L,
) {
    private val fuelMap = java.util.TreeMap(fuels)
    private val processMap = java.util.TreeMap(processGramsPerBatch)
    private val familyMap = java.util.TreeMap(familyProcessGramsPerBatch)
    private val overheadMap = java.util.TreeMap(overheadGramsPerDay)

    init {
        require(electricityGramsPerUnit >= 0L && energyGoodGramsPerUnit >= 0L) { "factors cannot be negative" }
        require(processMap.values.all { it >= 0L } && familyMap.values.all { it >= 0L } && overheadMap.values.all { it >= 0L }) { "factors cannot be negative" }
    }

    val fuels: Map<GoodId, FuelFactor> get() = fuelMap
    val processFactors: Map<RecipeId, Long> get() = processMap
    val familyDefaults: Map<IndustryFamily, Long> get() = familyMap
    val overheads: Map<FacilityTypeId, Long> get() = overheadMap

    /** The factor for burning or consuming [good]. [category] is the good's content category when it is known. */
    fun fuel(good: GoodId, category: GoodCategory?): Pair<FuelFactor, FactorSource> {
        fuelMap[good]?.let { return it to FactorSource.TABLE }
        if (category == GoodCategory.ENERGY && energyGoodGramsPerUnit > 0L && good.key != ELECTRICITY) {
            return FuelFactor(energyGoodGramsPerUnit) to FactorSource.CATEGORY_DEFAULT
        }
        return FuelFactor(0L) to FactorSource.NONE
    }

    /** Process release per batch of [recipe]; [family] is the industry family of the recipe's industry if known. */
    fun process(recipe: RecipeId, family: IndustryFamily?): Pair<Long, FactorSource> {
        processMap[recipe]?.let { return it to FactorSource.TABLE }
        val byFamily = family?.let { familyMap[it] }
        if (byFamily != null && byFamily > 0L) return byFamily to FactorSource.FAMILY_DEFAULT
        return 0L to FactorSource.NONE
    }

    fun process(recipe: RecipeDef, family: IndustryFamily?) = process(recipe.id, family)

    fun overhead(type: FacilityTypeId): Long = overheadMap[type] ?: 0L

    fun withFuel(good: GoodId, factor: FuelFactor): EmissionFactorTable = copy(fuels = fuelMap + (good to factor))

    fun withProcess(recipe: RecipeId, gramsPerBatch: Long): EmissionFactorTable = copy(processGramsPerBatch = processMap + (recipe to gramsPerBatch))

    fun withElectricity(gramsPerUnit: Long): EmissionFactorTable = copy(electricityGramsPerUnit = gramsPerUnit)

    fun copy(
        fuels: Map<GoodId, FuelFactor> = fuelMap,
        processGramsPerBatch: Map<RecipeId, Long> = processMap,
        familyProcessGramsPerBatch: Map<IndustryFamily, Long> = familyMap,
        overheadGramsPerDay: Map<FacilityTypeId, Long> = overheadMap,
        electricityGramsPerUnit: Long = this.electricityGramsPerUnit,
        energyGoodGramsPerUnit: Long = this.energyGoodGramsPerUnit,
    ) = EmissionFactorTable(fuels, processGramsPerBatch, familyProcessGramsPerBatch, overheadGramsPerDay, electricityGramsPerUnit, energyGoodGramsPerUnit)

    /** Every key the table names, for validation against content. */
    fun goodKeys(): List<GoodId> = fuelMap.keys.toList()
    fun recipeKeys(): List<RecipeId> = processMap.keys.toList()
    fun facilityKeys(): List<FacilityTypeId> = overheadMap.keys.toList()

    companion object {
        const val ELECTRICITY = "electricity"
    }
}

/**
 * Factors for the content that ships with the game. Figures are scaled to the game's economy rather than to physics:
 * a unit of coal is worth about 14 and a tonne of carbon starts near 20, so burning a unit of coal costs about a third
 * of its price in carbon at first and more as the price rises.
 */
object StandardFactors {
    private fun fuel(key: String, grams: Long, biogenic: Boolean = false) = GoodId(key) to FuelFactor(grams, biogenic)
    private fun process(key: String, grams: Long) = RecipeId(key) to grams

    val table: EmissionFactorTable by lazy {
        EmissionFactorTable(
            fuels = mapOf(
                fuel("coal", 200_000L),
                fuel("natural_gas", 70_000L),
                fuel("diesel", 80_000L),
                fuel("gasoline", 75_000L),
                fuel("kerosene", 75_000L),
                fuel("propane", 60_000L),
                fuel("biomass", 90_000L, biogenic = true),
                fuel("biodiesel", 30_000L, biogenic = true),
                fuel("ethanol", 30_000L, biogenic = true),
            ),
            processGramsPerBatch = mapOf(
                process("make_cement", 30_000_000L),
                process("burn_lime", 18_000_000L),
                process("make_pig_iron", 22_000_000L),
                process("make_steel", 4_000_000L),
                process("smelt_aluminum", 12_000_000L),
                process("make_alumina", 3_000_000L),
                process("smelt_copper", 4_000_000L),
                process("make_glass", 3_000_000L),
                process("refine_gasoline", 14_000_000L),
                process("crack_ethylene", 6_000_000L),
                process("make_ammonia", 6_000_000L),
                process("make_hydrogen", 5_000_000L),
                process("make_sulfuric_acid", 2_000_000L),
            ),
            familyProcessGramsPerBatch = mapOf(
                IndustryFamily.CHEMICALS to 800_000L,
                IndustryFamily.METALS to 1_200_000L,
                IndustryFamily.MINING to 300_000L,
                IndustryFamily.OIL_AND_GAS to 1_000_000L,
            ),
            overheadGramsPerDay = emptyMap(),
            electricityGramsPerUnit = 25_000L,
            energyGoodGramsPerUnit = 60_000L,
        )
    }
}
