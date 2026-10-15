package app.many.economytycoon.session

import app.many.economytycoon.content.FacilityClass
import app.many.economytycoon.content.FacilityTypeDef
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.Economy

data class DepositOption(
    val id: DepositId,
    val good: String,
    val remaining: Quantity,
    val gradePpm: Long,
    val surveyed: Boolean,
    val ownedByYou: Boolean,
)

/** One facility type a company might build in a region, with everything needed to decide and to explain a "no". */
data class BuildOption(
    val type: FacilityTypeId,
    val name: String,
    val family: IndustryFamily,
    val facilityClass: FacilityClass,
    val cost: Money,
    val buildTicks: Int,
    val lines: Int,
    val upkeepPerTick: Money,
    val requiredTech: TechId?,
    val techName: String?,
    val needsDeposit: Boolean,
    val deposits: List<DepositOption>,
    val outputs: List<String>,
    val blockers: List<String>,
) {
    val canBuild: Boolean get() = blockers.isEmpty()
}

data class RecipeOption(
    val id: app.many.economytycoon.core.RecipeId,
    val name: String,
    val inputs: List<String>,
    val outputs: List<String>,
    val durationTicks: Int,
    val locked: Boolean,
    val techName: String?,
)

/** Works out what a company can build and run, and turns a chosen option into the matching command. */
class BuildPlanner(private val e: Economy) {

    fun options(company: CompanyId, region: RegionId): List<BuildOption> {
        val cash = e.companies.company(company)?.let { e.finance.ledger.cash(it.owner) } ?: Money.ZERO
        return e.content.allFacilityTypes.map { def -> option(company, region, def, cash) }
            .sortedWith(compareBy({ !it.canBuild }, { it.family.ordinal }, { it.cost.raw }, { it.name }))
    }

    private fun option(company: CompanyId, region: RegionId, def: FacilityTypeDef, cash: Money): BuildOption {
        val blockers = ArrayList<String>()
        val techName = def.requiredTech?.let { e.content.technologyOrNull(it)?.name }
        if (def.requiredTech != null && !e.research.isUnlocked(company, def.requiredTech!!)) blockers += "Needs the ${techName ?: def.requiredTech!!.key} technology"
        val deposits = def.extracts?.let { good -> depositsFor(company, region, good) }.orEmpty()
        if (def.extracts != null && deposits.isEmpty()) blockers += "No ${Formatting.title(def.extracts!!.key)} deposit available in this region"
        if (def.extracts != null && deposits.isNotEmpty() && deposits.none { it.ownedByYou }) blockers += "Claim a deposit first"
        if (cash < def.buildCost) blockers += "Costs ${Formatting.money(def.buildCost)}, you have ${Formatting.money(cash)}"
        val outputs = e.content.recipesForFacility(def.id).flatMap { r -> r.outputs.map { e.content.good(it.good).name } }.distinct().sorted()
        return BuildOption(
            def.id, def.name, def.family, def.facilityClass, def.buildCost, def.buildTicks, def.maxLines, def.upkeepPerTick,
            def.requiredTech, techName, def.extracts != null, deposits, outputs, blockers,
        )
    }

    fun depositsFor(company: CompanyId, region: RegionId, good: GoodId): List<DepositOption> =
        e.world.deposits.inRegion(region).filter { it.good == good && !it.exhausted && (it.owner == null || it.owner == company) }
            .sortedWith(compareByDescending<app.many.economytycoon.world.Deposit> { it.owner == company }.thenByDescending { it.gradePpm }.thenBy { it.id })
            .map { DepositOption(it.id, Formatting.title(it.good.key), it.remaining, it.gradePpm, it.surveyed, it.owner == company) }

    /** The command that builds [option] in [region], on the first owned deposit when one is needed. */
    fun buildCommand(company: CompanyId, option: BuildOption, region: RegionId, name: String = ""): Command.Build? {
        if (!option.canBuild) return null
        val deposit = if (option.needsDeposit) option.deposits.firstOrNull { it.ownedByYou }?.id ?: return null else null
        val city = e.world.citiesIn(region).maxByOrNull { e.world.population(it.id).total }?.id
        return Command.Build(company, option.type, region, city, null, deposit, name)
    }

    fun recipes(company: CompanyId, facilityType: FacilityTypeId): List<RecipeOption> =
        e.content.recipesForFacility(facilityType).map { r -> recipeOption(company, r) }.sortedWith(compareBy({ it.locked }, { it.name }))

    private fun recipeOption(company: CompanyId, r: RecipeDef): RecipeOption {
        val locked = r.requiredTech != null && !e.research.isUnlocked(company, r.requiredTech!!)
        return RecipeOption(
            r.id, r.name,
            r.inputs.map { Formatting.quantity(it.quantity, e.content.good(it.good).name) },
            r.outputs.map { Formatting.quantity(it.quantity, e.content.good(it.good).name) },
            r.durationTicks, locked, r.requiredTech?.let { e.content.technologyOrNull(it)?.name },
        )
    }
}
