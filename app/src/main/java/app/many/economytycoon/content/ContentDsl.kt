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
import kotlin.math.roundToLong

/** Converts a decimal literal to a Quantity at load time. Literals have at most three decimals. */
fun qty(amount: Double): Quantity = Quantity((amount * Quantity.SCALE).roundToLong())

fun money(amount: Double): Money = Money((amount * Money.SCALE).roundToLong())

fun priceOf(amount: Double): Price = Price(money(amount))

@DslMarker
annotation class ContentDsl

fun contentPack(packId: String, major: Int, minor: Int, block: PackBuilder.() -> Unit): ContentPack {
    val b = PackBuilder(ContentVersion(packId, major, minor))
    b.block()
    return b.build()
}

@ContentDsl
class PackBuilder(private val version: ContentVersion) {
    private val industries = ArrayList<IndustryDef>()
    private val goods = ArrayList<GoodDef>()
    private val roles = ArrayList<RoleDef>()
    private val facilities = ArrayList<FacilityTypeDef>()
    private val machines = ArrayList<MachineTypeDef>()
    private val recipes = ArrayList<RecipeDef>()
    private val techs = ArrayList<TechDef>()
    private val vehicles = ArrayList<VehicleTypeDef>()
    private val demand = ArrayList<DemandProfileDef>()
    private val aliases = LinkedHashMap<String, String>()

    fun industry(id: String, name: String, family: IndustryFamily, description: String = "", unlock: String? = null) {
        industries += IndustryDef(IndustryId(id), name, family, description, unlock)
    }

    fun good(
        id: String,
        name: String,
        category: GoodCategory,
        price: Double,
        volatility: VolatilityClass = VolatilityClass.NORMAL,
        spoilagePpm: Long = 0,
        weight: Long = 1_000,
        volume: Long = 1_000,
        tradable: Boolean = true,
        storable: Boolean = true,
    ) {
        goods += GoodDef(GoodId(id), name, category, priceOf(price), volatility, spoilagePpm, weight, volume, tradable, storable)
    }

    fun role(id: String, name: String, band: SkillBand, wage: Double, trainingTicks: Int = 30) {
        roles += RoleDef(RoleId(id), name, band, money(wage), trainingTicks)
    }

    fun machine(
        id: String,
        name: String,
        cost: Double,
        throughput: Double = 1.0,
        wearPerBatchPpm: Long = 500,
        maintenance: Double = 0.0,
        laborSavings: Double = 0.0,
        tech: String? = null,
    ) {
        machines += MachineTypeDef(
            MachineTypeId(id), name, money(cost), (throughput * Rate.ONE_PPM).roundToLong(), wearPerBatchPpm,
            money(maintenance), (laborSavings * Rate.ONE_PPM).roundToLong(), tech?.let { TechId(it) },
        )
    }

    fun facility(id: String, name: String, facilityClass: FacilityClass, family: IndustryFamily, block: FacilityBuilder.() -> Unit) {
        val b = FacilityBuilder(id, name, facilityClass, family)
        b.block()
        facilities += b.result()
    }

    fun recipe(id: String, name: String, industry: String, facility: String, ticks: Int, block: RecipeBuilder.() -> Unit) {
        val b = RecipeBuilder(id, name, industry, facility, ticks)
        b.block()
        recipes += b.result()
    }

    fun tech(id: String, name: String, category: TechCategory, tier: Int, cost: Double, ticks: Int, block: TechBuilder.() -> Unit = {}) {
        val b = TechBuilder(id, name, category, tier, cost, ticks)
        b.block()
        techs += b.result()
    }

    fun vehicle(
        id: String, name: String, mode: TransportMode, capacity: Double, speed: Int, cost: Double, opCost: Double, tech: String? = null,
    ) {
        vehicles += VehicleTypeDef(VehicleTypeId(id), name, mode, qty(capacity), speed, money(cost), money(opCost), tech?.let { TechId(it) })
    }

    fun demand(
        good: String,
        low: Long, middle: Long, high: Long,
        elasticity: Double,
        incomeElasticity: Double = 0.8,
        substitutes: List<String> = emptyList(),
        seasons: Map<Season, Double> = emptyMap(),
        nonConsumers: Double = 0.0,
    ) {
        demand += DemandProfileDef(
            good = GoodId(good),
            perCapitaMilli = mapOf(IncomeBand.LOW to low, IncomeBand.MIDDLE to middle, IncomeBand.HIGH to high),
            elasticityPpm = (elasticity * Rate.ONE_PPM).roundToLong(),
            incomeElasticityPpm = (incomeElasticity * Rate.ONE_PPM).roundToLong(),
            seasonality = seasons.entries.associate { it.key.name to (it.value * Rate.ONE_PPM).roundToLong() },
            substitutes = substitutes.map { GoodId(it) },
            nonConsumersPpm = (nonConsumers * Rate.ONE_PPM).roundToLong(),
        )
    }

    fun alias(oldId: String, newId: String) { aliases[oldId] = newId }

    fun build(): ContentPack = ContentPack(
        version, industries.toList(), goods.toList(), roles.toList(), facilities.toList(),
        machines.toList(), recipes.toList(), techs.toList(), vehicles.toList(), demand.toList(), aliases.toMap(),
    )
}

typealias Season = app.many.economytycoon.core.Season

@ContentDsl
class FacilityBuilder(
    private val id: String,
    private val name: String,
    private val facilityClass: FacilityClass,
    private val family: IndustryFamily,
) {
    var cost: Double = 100_000.0
    var buildTicks: Int = 30
    var lines: Int = 2
    var storage: Double = 10_000.0
    var upkeep: Double = 50.0
    var energyPerLine: Double = 0.0
    var tech: String? = null
    var extracts: String? = null
    private val staff = ArrayList<LaborRequirement>()
    private val inputs = ArrayList<GoodAmount>()

    fun staff(role: String, headcount: Int) { staff += LaborRequirement(RoleId(role), headcount) }
    fun buildInput(good: String, amount: Double) { inputs += GoodAmount(GoodId(good), qty(amount)) }

    fun result() = FacilityTypeDef(
        FacilityTypeId(id), name, facilityClass, family, money(cost), buildTicks, lines, qty(storage), money(upkeep),
        qty(energyPerLine), tech?.let { TechId(it) }, staff.toList(), inputs.toList(), extracts?.let { GoodId(it) },
    )
}

@ContentDsl
class RecipeBuilder(
    private val id: String,
    private val name: String,
    private val industry: String,
    private val facility: String,
    private val ticks: Int,
) {
    var energy: Double = 0.0
    var tech: String? = null
    var defectPpm: Long = 20_000
    var catalytic: Boolean = false
    private val inputs = ArrayList<GoodAmount>()
    private val outputs = ArrayList<GoodAmount>()
    private val waste = ArrayList<GoodAmount>()
    private val labor = ArrayList<LaborRequirement>()
    private val machines = ArrayList<MachineTypeId>()

    fun input(good: String, amount: Double) { inputs += GoodAmount(GoodId(good), qty(amount)) }
    fun output(good: String, amount: Double) { outputs += GoodAmount(GoodId(good), qty(amount)) }
    fun waste(good: String, amount: Double) { waste += GoodAmount(GoodId(good), qty(amount)) }
    fun labor(role: String, headcount: Int) { labor += LaborRequirement(RoleId(role), headcount) }
    fun machine(type: String) { machines += MachineTypeId(type) }

    fun result() = RecipeDef(
        RecipeId(id), name, IndustryId(industry), FacilityTypeId(facility), inputs.toList(), outputs.toList(), ticks,
        labor.toList(), qty(energy), waste.toList(), tech?.let { TechId(it) }, machines.toList(), defectPpm, catalytic,
    )
}

@ContentDsl
class TechBuilder(
    private val id: String,
    private val name: String,
    private val category: TechCategory,
    private val tier: Int,
    private val cost: Double,
    private val ticks: Int,
) {
    var obsolescenceTicks: Int = 0
    private val prereqs = ArrayList<TechId>()
    private val recipes = ArrayList<RecipeId>()
    private val facilities = ArrayList<FacilityTypeId>()
    private val machines = ArrayList<MachineTypeId>()
    private val effects = ArrayList<TechEffect>()

    fun requires(vararg tech: String) { prereqs += tech.map { TechId(it) } }
    fun unlocksRecipe(vararg recipe: String) { recipes += recipe.map { RecipeId(it) } }
    fun unlocksFacility(vararg facility: String) { facilities += facility.map { FacilityTypeId(it) } }
    fun unlocksMachine(vararg machine: String) { machines += machine.map { MachineTypeId(it) } }
    fun effect(kind: EffectKind, magnitude: Double, industry: String? = null, recipe: String? = null) {
        effects += TechEffect(kind, Rate((magnitude * Rate.ONE_PPM).roundToLong()), industry?.let { IndustryId(it) }, recipe?.let { RecipeId(it) })
    }

    fun result() = TechDef(
        TechId(id), name, category, tier, money(cost), ticks, prereqs.toList(), recipes.toList(),
        facilities.toList(), machines.toList(), effects.toList(), obsolescenceTicks,
    )
}
