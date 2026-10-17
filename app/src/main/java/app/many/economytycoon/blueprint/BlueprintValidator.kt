package app.many.economytycoon.blueprint

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.FacilityClass
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.world.Region

enum class IssueSeverity { ERROR, WARNING, INFO }

/** One finding about a blueprint. [path] says where in the blueprint it was found, e.g. `lines[1].recipe`. */
data class BlueprintIssue(val severity: IssueSeverity, val code: String, val message: String, val path: String = "")

object IssueCodes {
    const val UNKNOWN_FACILITY = "unknown_facility"
    const val UNKNOWN_RECIPE = "unknown_recipe"
    const val UNKNOWN_MACHINE = "unknown_machine"
    const val UNKNOWN_ROLE = "unknown_role"
    const val UNKNOWN_GOOD = "unknown_good"
    const val TECH_MISSING = "tech_missing"
    const val TOO_MANY_LINES = "too_many_lines"
    const val TOO_MANY_MACHINES = "too_many_machines"
    const val RECIPE_WRONG_FACILITY = "recipe_wrong_facility"
    const val MACHINE_MISSING_FOR_RECIPE = "machine_missing_for_recipe"
    const val STAFF_SHORT = "staff_short"
    const val STAFF_UNUSED = "staff_unused"
    const val SELL_NOT_PRODUCED = "sell_not_produced"
    const val BUY_NOT_USED = "buy_not_used"
    const val NO_RECIPES = "no_recipes"
    const val ALL_DISABLED = "all_disabled"
    const val DEPOSIT_REQUIRED = "deposit_required"
    const val POOR_SOIL = "poor_soil"
    const val NOT_COASTAL = "not_coastal"
    const val SITE_UNCHECKED = "site_unchecked"
    const val EXPANSIONS_NEED_STORAGE = "expansions_need_storage"
    const val NOT_STORABLE = "not_storable"
}

/** Whether the company has researched a technology. */
fun interface ResearchView {
    fun hasTech(tech: TechId): Boolean

    companion object {
        val ALL = ResearchView { true }
        fun of(known: Set<TechId>) = ResearchView { it in known }
    }
}

/** What the validator needs to know about a build site: its region and the deposits the company can use there. */
data class SiteInfo(val region: Region?, val depositGoods: Set<GoodId> = emptySet())

/** Tunables of the site rules; the game rules themselves are not duplicated here. */
data class ValidationParams(
    val maxMachinesPerLine: Int = 4,
    val minFarmFertilityPpm: Long = 250_000L,
    val portsNeedCoast: Boolean = true,
)

data class ValidationReport(val issues: List<BlueprintIssue>) {
    val errors: List<BlueprintIssue> get() = issues.filter { it.severity == IssueSeverity.ERROR }
    val warnings: List<BlueprintIssue> get() = issues.filter { it.severity == IssueSeverity.WARNING }
    val infos: List<BlueprintIssue> get() = issues.filter { it.severity == IssueSeverity.INFO }

    /** True when nothing blocks applying the whole blueprint. */
    val isClean: Boolean get() = errors.isEmpty()
    fun has(code: String) = issues.any { it.code == code }
    fun count(severity: IssueSeverity) = issues.count { it.severity == severity }
}

/** Checks a blueprint against the current content, the company's research and the chosen site. */
class BlueprintValidator(
    private val content: ContentRepository,
    private val params: ValidationParams = ValidationParams(),
) {
    fun validate(spec: BlueprintSpec, research: ResearchView = ResearchView.ALL, site: SiteInfo? = null): ValidationReport {
        val out = ArrayList<BlueprintIssue>()
        fun err(code: String, path: String, msg: String) { out += BlueprintIssue(IssueSeverity.ERROR, code, msg, path) }
        fun warn(code: String, path: String, msg: String) { out += BlueprintIssue(IssueSeverity.WARNING, code, msg, path) }
        fun info(code: String, path: String, msg: String) { out += BlueprintIssue(IssueSeverity.INFO, code, msg, path) }

        val type = content.facilityTypeOrNull(spec.facilityType)
        if (type == null) {
            err(IssueCodes.UNKNOWN_FACILITY, "facilityType", "Facility type '${spec.facilityType.key}' no longer exists in the game.")
            return ValidationReport(out)
        }
        techIssue(type.requiredTech, type.name, research)?.let { err(IssueCodes.TECH_MISSING, "facilityType", it) }
        if (spec.lines.size > type.maxLines) {
            err(IssueCodes.TOO_MANY_LINES, "lines", "${type.name} supports ${type.maxLines} lines but the blueprint has ${spec.lines.size}.")
        }
        if (spec.storageExpansions > 0 && type.storageCapacity.raw <= 0L) {
            warn(IssueCodes.EXPANSIONS_NEED_STORAGE, "storageExpansions", "${type.name} has no storage to expand.")
        }

        var needed = type.overheadStaff.associate { it.role to it.headcount.toLong() }.toMutableMap()
        val produced = HashSet<GoodId>()
        val consumed = HashSet<GoodId>()
        for ((i, line) in spec.lines.withIndex()) {
            val at = "lines[$i]"
            if (line.machines.size > params.maxMachinesPerLine) {
                err(IssueCodes.TOO_MANY_MACHINES, "$at.machines", "Line ${i + 1} has ${line.machines.size} machines; a line holds ${params.maxMachinesPerLine}.")
            }
            for ((j, m) in line.machines.withIndex()) {
                val def = content.machineTypeOrNull(m)
                if (def == null) { err(IssueCodes.UNKNOWN_MACHINE, "$at.machines[$j]", "Machine '${m.key}' no longer exists in the game."); continue }
                techIssue(def.requiredTech, def.name, research)?.let { err(IssueCodes.TECH_MISSING, "$at.machines[$j]", it) }
            }
            val rid = line.recipe ?: continue
            val recipe = content.recipeOrNull(rid)
            if (recipe == null) { err(IssueCodes.UNKNOWN_RECIPE, "$at.recipe", "Recipe '${rid.key}' no longer exists in the game."); continue }
            if (recipe.facilityType != spec.facilityType) {
                err(IssueCodes.RECIPE_WRONG_FACILITY, "$at.recipe", "${recipe.name} cannot run in ${type.name}.")
            }
            techIssue(recipe.requiredTech, recipe.name, research)?.let { err(IssueCodes.TECH_MISSING, "$at.recipe", it) }
            for (m in recipe.machines) {
                if (m !in line.machines) {
                    val name = content.machineTypeOrNull(m)?.name ?: m.key
                    warn(IssueCodes.MACHINE_MISSING_FOR_RECIPE, "$at.recipe", "${recipe.name} on line ${i + 1} needs a $name; the line will stall without it.")
                }
            }
            if (line.enabled) {
                for (l in recipe.labor) needed[l.role] = (needed[l.role] ?: 0L) + l.headcount
                recipe.outputs.forEach { produced += it.good }
                recipe.inputs.forEach { consumed += it.good }
            }
        }
        if (spec.lines.none { it.recipe != null }) warn(IssueCodes.NO_RECIPES, "lines", "No line runs a recipe, so the facility would sit idle.")
        else if (spec.lines.none { it.enabled && it.recipe != null }) info(IssueCodes.ALL_DISABLED, "lines", "Every line with a recipe is switched off.")

        val staffed = spec.staff.associate { it.role to it.target }
        for ((i, s) in spec.staff.withIndex()) {
            if (content.allRoles.none { it.id == s.role }) { err(IssueCodes.UNKNOWN_ROLE, "staff[$i]", "Role '${s.role.key}' no longer exists in the game."); continue }
            if (s.role !in needed.keys && s.target > 0) info(IssueCodes.STAFF_UNUSED, "staff[$i]", "Nobody here needs ${roleName(s)} under this set-up.")
        }
        if (spec.staff.isNotEmpty()) {
            for ((role, need) in needed.toSortedMap()) {
                val have = staffed[role] ?: 0L
                if (have < need && content.allRoles.any { it.id == role }) {
                    warn(IssueCodes.STAFF_SHORT, "staff", "The lines need $need ${content.role(role).name} but the target is $have.")
                }
            }
        }

        for ((i, s) in spec.sells.withIndex()) {
            val good = content.goodOrNull(s.good)
            if (good == null) { err(IssueCodes.UNKNOWN_GOOD, "sells[$i]", "Good '${s.good.key}' no longer exists in the game."); continue }
            if (!good.tradable) warn(IssueCodes.NOT_STORABLE, "sells[$i]", "${good.name} cannot be traded.")
            else if (s.good !in produced) warn(IssueCodes.SELL_NOT_PRODUCED, "sells[$i]", "Nothing in this set-up produces ${good.name}.")
        }
        for ((i, b) in spec.buys.withIndex()) {
            val good = content.goodOrNull(b.good)
            if (good == null) { err(IssueCodes.UNKNOWN_GOOD, "buys[$i]", "Good '${b.good.key}' no longer exists in the game."); continue }
            if (b.good !in consumed) info(IssueCodes.BUY_NOT_USED, "buys[$i]", "No recipe here uses ${good.name}.")
        }
        siteIssues(type.facilityClass, type.extracts, type.name, site, out)
        return ValidationReport(out.sortedWith(compareBy({ it.severity.ordinal }, { it.path }, { it.code })))
    }

    private fun roleName(s: StaffSpec) = content.allRoles.firstOrNull { it.id == s.role }?.name ?: s.role.key

    private fun siteIssues(cls: FacilityClass, extracts: GoodId?, typeName: String, site: SiteInfo?, out: MutableList<BlueprintIssue>) {
        if (site == null) { out += BlueprintIssue(IssueSeverity.INFO, IssueCodes.SITE_UNCHECKED, "No site chosen yet; location rules were not checked.", "site"); return }
        if (extracts != null && extracts !in site.depositGoods) {
            val g = content.goodOrNull(extracts)?.name ?: extracts.key
            out += BlueprintIssue(IssueSeverity.ERROR, IssueCodes.DEPOSIT_REQUIRED, "$typeName needs a $g deposit that you hold in this region.", "site")
        }
        val region = site.region ?: return
        if (cls == FacilityClass.FARM && region.fertilityPpm < params.minFarmFertilityPpm) {
            out += BlueprintIssue(IssueSeverity.WARNING, IssueCodes.POOR_SOIL, "${region.name} has poor soil for a farm (fertility ${region.fertilityPpm / 10_000}%).", "site")
        }
        if (params.portsNeedCoast && cls == FacilityClass.PORT && !region.coastal) {
            out += BlueprintIssue(IssueSeverity.ERROR, IssueCodes.NOT_COASTAL, "A port must be built on the coast; ${region.name} is inland.", "site")
        }
    }

    /** The technologies still to research before [tech] can be used, prerequisites first. */
    fun missingTechChain(tech: TechId, research: ResearchView): List<TechId> {
        if (research.hasTech(tech)) return emptyList()
        val def = content.technologyOrNull(tech) ?: return listOf(tech)
        val chain = content.prerequisitesOf(tech).filter { !research.hasTech(it) }
            .sortedWith(compareBy({ content.technologyOrNull(it)?.tier ?: 0 }, { it.key }))
        return chain + def.id
    }

    private fun techIssue(tech: TechId?, what: String, research: ResearchView): String? {
        if (tech == null || research.hasTech(tech)) return null
        val chain = missingTechChain(tech, research).joinToString(" > ") { content.technologyOrNull(it)?.name ?: it.key }
        return "$what needs technology you have not researched: $chain."
    }
}
