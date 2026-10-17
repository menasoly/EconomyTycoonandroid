package app.many.economytycoon.workshop

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.audit.AuditReport
import app.many.economytycoon.content.audit.AuditSeverity
import app.many.economytycoon.content.audit.ContentAudit
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.simulation.SimConfig

data class CompatFinding(val code: String, val severity: IssueSeverity, val path: String, val message: String) {
    override fun toString() = "${severity.name.padEnd(7)} $path  $code: $message"
}

class CompatReport(val findings: List<CompatFinding>) {
    val compatible: Boolean get() = findings.none { it.severity == IssueSeverity.ERROR }
    val errors: List<CompatFinding> get() = findings.filter { it.severity == IssueSeverity.ERROR }
    fun has(code: String) = findings.any { it.code == code }
    fun withCode(code: String) = findings.filter { it.code == code }
}

/**
 * Checks a draft against the content that is loaded: are the packs it was written for present in a compatible version,
 * can the starting sites run, can the goals be reached with what the content offers. A pack is compatible when it has
 * the same major version and at least the same minor version as the draft asks for.
 */
object PackCompat {
    /** The packs of [content] in the form drafts record them. */
    fun loadedPacks(content: ContentRepository): List<String> = content.versions.map { "${it.packId}:${it.major}.${it.minor}" }.sorted()

    /** The draft with the packs it requires set to those loaded now. */
    fun stamp(draft: ScenarioDraft, content: ContentRepository): ScenarioDraft = draft.copy(packs = loadedPacks(content)).normalised()

    fun check(draft: ScenarioDraft, content: ContentRepository, audit: AuditReport = ContentAudit.run(content)): CompatReport {
        val out = ArrayList<CompatFinding>()
        packs(draft, content, out)
        starters(draft, content, audit, out)
        goals(draft, content, out)
        return CompatReport(out.sortedWith(compareBy<CompatFinding> { it.severity.ordinal }.thenBy { it.path }.thenBy { it.code }))
    }

    private fun packs(d: ScenarioDraft, content: ContentRepository, out: MutableList<CompatFinding>) {
        val loaded = content.versions.associateBy { it.packId }
        for ((i, spec) in d.packs.withIndex()) {
            val path = "packs[$i]"
            val m = Regex("([a-z0-9_-]+):(\\d+)\\.(\\d+)").matchEntire(spec)
            if (m == null) { out += CompatFinding("pack.format", IssueSeverity.ERROR, path, "'$spec' is not written as 'id:major.minor'"); continue }
            val id = m.groupValues[1]; val major = m.groupValues[2].toInt(); val minor = m.groupValues[3].toInt()
            val have = loaded[id]
            when {
                have == null -> out += CompatFinding("pack.missing", IssueSeverity.ERROR, path, "the content pack '$id' is not loaded; loaded: ${loaded.keys.joinToString()}")
                have.major != major -> out += CompatFinding("pack.major", IssueSeverity.ERROR, path, "written for $id $major.$minor but $id ${have.major}.${have.minor} is loaded; a different major version changes the rules")
                have.minor < minor -> out += CompatFinding("pack.minor_old", IssueSeverity.ERROR, path, "written for $id $major.$minor but only $id ${have.major}.${have.minor} is loaded")
                have.minor > minor -> out += CompatFinding("pack.newer", IssueSeverity.NOTE, path, "$id ${have.major}.${have.minor} is loaded; the draft was written for $major.$minor and may play a little differently")
            }
        }
        if (d.packs.isEmpty()) out += CompatFinding("pack.unpinned", IssueSeverity.NOTE, "packs", "the draft does not say which content it was written for", )
        val named = d.packs.mapNotNull { Regex("([a-z0-9_-]+):.*").matchEntire(it)?.groupValues?.get(1) }.toSet()
        val extra = loaded.keys.filter { it !in named }
        if (d.packs.isNotEmpty() && extra.isNotEmpty()) out += CompatFinding("pack.extra", IssueSeverity.NOTE, "packs", "loaded content not named by the draft: ${extra.joinToString()}")
    }

    private fun starters(d: ScenarioDraft, content: ContentRepository, audit: AuditReport, out: MutableList<CompatFinding>) {
        val importable = SimConfig(seed = 0L).importAlso.toSet()
        for ((i, s) in d.company.starters.withIndex()) {
            val path = "company.starters[$i].recipe"
            val recipe = content.recipeOrNull(RecipeId(s.recipe))
            if (recipe == null) { out += CompatFinding("starter.unknown", IssueSeverity.ERROR, path, "no recipe '${s.recipe}' in the loaded content"); continue }
            val type = content.facilityType(recipe.facilityType)
            if (type.buildCost.wholeUnits() > d.company.capital) out += CompatFinding("starter.expensive", IssueSeverity.WARNING, path, "a ${type.name} costs ${type.buildCost.wholeUnits()} to build, more than the whole starting capital")
            for (input in recipe.inputs) {
                val good = input.good
                val made = content.recipesProducing(good).isNotEmpty() || content.allFacilityTypes.any { it.extracts == good } || good.key in importable
                if (!made) out += CompatFinding("starter.inputs", IssueSeverity.WARNING, path, "'${s.recipe}' needs ${good.key}, which nothing makes and the trade board does not import")
            }
            for (f in audit.findings) {
                if (f.subject == s.recipe && f.severity == AuditSeverity.ERROR) out += CompatFinding("starter.audit", IssueSeverity.WARNING, path, "the content audit reports for ${s.recipe}: ${f.message}")
                if (f.subject == type.id.key && f.severity == AuditSeverity.ERROR) out += CompatFinding("starter.audit", IssueSeverity.WARNING, path, "the content audit reports for ${type.id.key}: ${f.message}")
            }
        }
    }

    private fun goals(d: ScenarioDraft, content: ContentRepository, out: MutableList<CompatFinding>) {
        for ((i, g) in d.goals.withIndex()) {
            val path = "goals[$i].target"
            when (g.kind) {
                GoalKind.TECHNOLOGIES -> if (g.target > content.allTechnologies.size) out += CompatFinding("goal.technologies", IssueSeverity.ERROR, path, "${g.target} technologies asked for; the content has ${content.allTechnologies.size}")
                GoalKind.INDUSTRIES -> if (g.target > content.allIndustries.size) out += CompatFinding("goal.industries", IssueSeverity.ERROR, path, "${g.target} industries asked for; the content has ${content.allIndustries.size}")
                else -> Unit
            }
        }
    }
}
