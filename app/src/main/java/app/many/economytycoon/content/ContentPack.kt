package app.many.economytycoon.content

import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.VehicleTypeId

/** Version of a content pack. Saves record the versions they were created with. */
data class ContentVersion(val packId: String, val major: Int, val minor: Int) : Comparable<ContentVersion> {
    override fun compareTo(other: ContentVersion): Int =
        compareValuesBy(this, other, ContentVersion::packId, ContentVersion::major, ContentVersion::minor)

    override fun toString() = "$packId@$major.$minor"
}

/** A self-contained collection of definitions, typically one industry family. */
data class ContentPack(
    val version: ContentVersion,
    val industries: List<IndustryDef> = emptyList(),
    val goods: List<GoodDef> = emptyList(),
    val roles: List<RoleDef> = emptyList(),
    val facilityTypes: List<FacilityTypeDef> = emptyList(),
    val machineTypes: List<MachineTypeDef> = emptyList(),
    val recipes: List<RecipeDef> = emptyList(),
    val technologies: List<TechDef> = emptyList(),
    val vehicleTypes: List<VehicleTypeDef> = emptyList(),
    val demandProfiles: List<DemandProfileDef> = emptyList(),
    /** Aliases mapping a removed id to its replacement, so old saves keep loading. */
    val goodAliases: Map<String, String> = emptyMap(),
)

/**
 * Immutable, indexed view of all loaded content. Lookups throw for unknown ids because a
 * missing definition is a content defect, not a runtime condition.
 */
class ContentRepository private constructor(
    val versions: List<ContentVersion>,
    val params: EconomyParams,
    private val industries: Map<IndustryId, IndustryDef>,
    private val goods: Map<GoodId, GoodDef>,
    private val roles: Map<RoleId, RoleDef>,
    private val facilityTypes: Map<FacilityTypeId, FacilityTypeDef>,
    private val machineTypes: Map<MachineTypeId, MachineTypeDef>,
    private val recipes: Map<RecipeId, RecipeDef>,
    private val technologies: Map<TechId, TechDef>,
    private val vehicleTypes: Map<VehicleTypeId, VehicleTypeDef>,
    private val demandProfiles: Map<GoodId, DemandProfileDef>,
    private val goodAliases: Map<String, String>,
) {
    fun industry(id: IndustryId) = industries[id] ?: error("unknown industry $id")
    fun good(id: GoodId) = goods[resolveAlias(id)] ?: error("unknown good $id")
    fun goodOrNull(id: GoodId) = goods[resolveAlias(id)]
    fun role(id: RoleId) = roles[id] ?: error("unknown role $id")
    fun facilityType(id: FacilityTypeId) = facilityTypes[id] ?: error("unknown facility type $id")
    fun machineType(id: MachineTypeId) = machineTypes[id] ?: error("unknown machine type $id")
    fun machineTypeOrNull(id: MachineTypeId) = machineTypes[id]
    fun facilityTypeOrNull(id: FacilityTypeId) = facilityTypes[id]
    fun recipe(id: RecipeId) = recipes[id] ?: error("unknown recipe $id")
    fun recipeOrNull(id: RecipeId) = recipes[id]
    fun technology(id: TechId) = technologies[id] ?: error("unknown technology $id")
    fun technologyOrNull(id: TechId) = technologies[id]
    fun vehicleType(id: VehicleTypeId) = vehicleTypes[id] ?: error("unknown vehicle type $id")
    fun vehicleTypeOrNull(id: VehicleTypeId) = vehicleTypes[id]
    fun demandProfile(good: GoodId) = demandProfiles[resolveAlias(good)]

    val allIndustries: List<IndustryDef> get() = industries.values.sortedBy { it.id }
    val allGoods: List<GoodDef> get() = goods.values.sortedBy { it.id }
    val allRoles: List<RoleDef> get() = roles.values.sortedBy { it.id }
    val allFacilityTypes: List<FacilityTypeDef> get() = facilityTypes.values.sortedBy { it.id }
    val allMachineTypes: List<MachineTypeDef> get() = machineTypes.values.sortedBy { it.id }
    val allRecipes: List<RecipeDef> get() = recipes.values.sortedBy { it.id }
    val allTechnologies: List<TechDef> get() = technologies.values.sortedBy { it.id }
    val allVehicleTypes: List<VehicleTypeDef> get() = vehicleTypes.values.sortedBy { it.id }
    val allDemandProfiles: List<DemandProfileDef> get() = demandProfiles.values.sortedBy { it.good }

    fun recipesForFacility(type: FacilityTypeId): List<RecipeDef> = allRecipes.filter { it.facilityType == type }
    fun recipesProducing(good: GoodId): List<RecipeDef> =
        allRecipes.filter { r -> r.outputs.any { it.good == good } }
    fun recipesConsuming(good: GoodId): List<RecipeDef> =
        allRecipes.filter { r -> r.inputs.any { it.good == good } }
    fun goodsInCategory(category: GoodCategory): List<GoodDef> = allGoods.filter { it.category == category }
    fun industriesInFamily(family: IndustryFamily): List<IndustryDef> = allIndustries.filter { it.family == family }
    fun facilityTypesInFamily(family: IndustryFamily): List<FacilityTypeDef> =
        allFacilityTypes.filter { it.family == family }

    /** Technologies that must be researched, directly or transitively, before [id]. */
    fun prerequisitesOf(id: TechId): Set<TechId> {
        val out = LinkedHashSet<TechId>()
        fun visit(t: TechId) {
            for (p in technology(t).prerequisites) if (out.add(p)) visit(p)
        }
        visit(id)
        return out
    }

    private fun resolveAlias(id: GoodId): GoodId {
        var current = id
        var hops = 0
        while (current !in goods && hops < 8) {
            val next = goodAliases[current.key] ?: return current
            current = GoodId(next)
            hops++
        }
        return current
    }

    companion object {
        /** Merges packs; duplicate ids are a defect and reported by [ContentValidator]. */
        fun of(packs: List<ContentPack>, params: EconomyParams = EconomyParams()): ContentRepository {
            val ordered = packs.sortedBy { it.version }
            return ContentRepository(
                versions = ordered.map { it.version },
                params = params,
                industries = ordered.flatMap { it.industries }.associateBy { it.id },
                goods = ordered.flatMap { it.goods }.associateBy { it.id },
                roles = ordered.flatMap { it.roles }.associateBy { it.id },
                facilityTypes = ordered.flatMap { it.facilityTypes }.associateBy { it.id },
                machineTypes = ordered.flatMap { it.machineTypes }.associateBy { it.id },
                recipes = ordered.flatMap { it.recipes }.associateBy { it.id },
                technologies = ordered.flatMap { it.technologies }.associateBy { it.id },
                vehicleTypes = ordered.flatMap { it.vehicleTypes }.associateBy { it.id },
                demandProfiles = ordered.flatMap { it.demandProfiles }.associateBy { it.good },
                goodAliases = ordered.fold(emptyMap()) { acc, p -> acc + p.goodAliases },
            )
        }
    }
}
