package app.many.economytycoon.content

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.TechId

data class ContentIssue(val code: String, val message: String, val fatal: Boolean = true) {
    override fun toString() = "[$code] $message"
}

/**
 * Validates a set of packs before they are merged into a repository: unique ids, resolved
 * references, positive amounts, acyclic recipes and technologies, and basic economic sanity.
 */
object ContentValidator {

    fun validate(packs: List<ContentPack>): List<ContentIssue> {
        val issues = ArrayList<ContentIssue>()
        checkDuplicates(packs, issues)

        val goods = packs.flatMap { it.goods }.map { it.id }.toSet()
        val aliases = packs.fold(emptyMap<String, String>()) { acc, p -> acc + p.goodAliases }
        val industries = packs.flatMap { it.industries }.map { it.id }.toSet()
        val roles = packs.flatMap { it.roles }.map { it.id }.toSet()
        val facilities = packs.flatMap { it.facilityTypes }.associateBy { it.id }
        val machines = packs.flatMap { it.machineTypes }.map { it.id }.toSet()
        val recipes = packs.flatMap { it.recipes }.associateBy { it.id }
        val techs = packs.flatMap { it.technologies }.associateBy { it.id }
        val goodDefs = packs.flatMap { it.goods }.associateBy { it.id }

        fun goodExists(id: GoodId) = id in goods || aliases.containsKey(id.key)

        for (r in recipes.values) {
            if (r.industry !in industries) issues += ContentIssue("recipe.industry", "recipe ${r.id} uses unknown industry ${r.industry}")
            if (r.facilityType !in facilities) issues += ContentIssue("recipe.facility", "recipe ${r.id} uses unknown facility ${r.facilityType}")
            for (a in r.inputs + r.outputs + r.waste) {
                if (!goodExists(a.good)) issues += ContentIssue("recipe.good", "recipe ${r.id} references unknown good ${a.good}")
            }
            for (l in r.labor) {
                if (l.role !in roles) issues += ContentIssue("recipe.role", "recipe ${r.id} requires unknown role ${l.role}")
            }
            for (m in r.machines) {
                if (m !in machines) issues += ContentIssue("recipe.machine", "recipe ${r.id} requires unknown machine $m")
            }
            r.requiredTech?.let { if (it !in techs) issues += ContentIssue("recipe.tech", "recipe ${r.id} requires unknown tech $it") }
            if (r.labor.isEmpty() && r.machines.isEmpty()) {
                issues += ContentIssue("recipe.unattended", "recipe ${r.id} has neither labor nor machines", fatal = false)
            }
        }

        for (f in facilities.values) {
            f.requiredTech?.let { if (it !in techs) issues += ContentIssue("facility.tech", "facility ${f.id} requires unknown tech $it") }
            f.extracts?.let { if (!goodExists(it)) issues += ContentIssue("facility.extracts", "facility ${f.id} extracts unknown good $it") }
            for (a in f.buildInputs) if (!goodExists(a.good)) issues += ContentIssue("facility.build", "facility ${f.id} needs unknown good ${a.good}")
            for (l in f.overheadStaff) if (l.role !in roles) issues += ContentIssue("facility.role", "facility ${f.id} needs unknown role ${l.role}")
        }

        for (t in techs.values) {
            for (p in t.prerequisites) if (p !in techs) issues += ContentIssue("tech.prereq", "tech ${t.id} requires unknown ${p}")
            for (r in t.unlocksRecipes) if (r !in recipes) issues += ContentIssue("tech.unlock", "tech ${t.id} unlocks unknown recipe $r")
            for (f in t.unlocksFacilities) if (f !in facilities) issues += ContentIssue("tech.unlock", "tech ${t.id} unlocks unknown facility $f")
            for (m in t.unlocksMachines) if (m !in machines) issues += ContentIssue("tech.unlock", "tech ${t.id} unlocks unknown machine $m")
            if (t.researchTicks <= 0) issues += ContentIssue("tech.duration", "tech ${t.id} has non-positive research time")
        }
        issues += techCycles(techs)
        issues += recipeCycles(recipes, goodDefs)

        for (p in packs.flatMap { it.demandProfiles }) {
            if (!goodExists(p.good)) issues += ContentIssue("demand.good", "demand profile for unknown good ${p.good}")
            for (s in p.substitutes) if (!goodExists(s)) issues += ContentIssue("demand.substitute", "demand profile ${p.good} has unknown substitute $s")
            if (p.elasticityPpm <= 0) issues += ContentIssue("demand.elasticity", "demand profile ${p.good} needs positive elasticity")
        }

        issues += aliasProblems(aliases, goods)
        issues += economicSanity(recipes.values, goodDefs)
        return issues
    }

    fun fatal(issues: List<ContentIssue>) = issues.filter { it.fatal }

    private fun checkDuplicates(packs: List<ContentPack>, out: MutableList<ContentIssue>) {
        fun <T> dup(kind: String, ids: List<T>) {
            val seen = HashSet<T>()
            for (id in ids) if (!seen.add(id)) out += ContentIssue("duplicate.$kind", "duplicate $kind id $id")
        }
        dup("industry", packs.flatMap { it.industries }.map { it.id })
        dup("good", packs.flatMap { it.goods }.map { it.id })
        dup("role", packs.flatMap { it.roles }.map { it.id })
        dup("facility", packs.flatMap { it.facilityTypes }.map { it.id })
        dup("machine", packs.flatMap { it.machineTypes }.map { it.id })
        dup("recipe", packs.flatMap { it.recipes }.map { it.id })
        dup("tech", packs.flatMap { it.technologies }.map { it.id })
        dup("vehicle", packs.flatMap { it.vehicleTypes }.map { it.id })
        dup("pack", packs.map { it.version.packId })
    }

    private fun techCycles(techs: Map<TechId, TechDef>): List<ContentIssue> {
        val issues = ArrayList<ContentIssue>()
        val state = HashMap<TechId, Int>() // 1 visiting, 2 done
        fun visit(id: TechId, path: List<TechId>) {
            when (state[id]) {
                2 -> return
                1 -> { issues += ContentIssue("tech.cycle", "technology cycle: ${(path + id).joinToString(" -> ")}"); return }
            }
            state[id] = 1
            for (p in techs[id]?.prerequisites.orEmpty()) if (p in techs) visit(p, path + id)
            state[id] = 2
        }
        for (id in techs.keys.sorted()) visit(id, emptyList())
        return issues
    }

    /**
     * A production dependency cycle exists when good A is needed to make B and B (transitively)
     * is needed to make A without any raw source. Catalytic recipes are ignored.
     */
    private fun recipeCycles(recipes: Map<RecipeId, RecipeDef>, goods: Map<GoodId, GoodDef>): List<ContentIssue> {
        val edges = HashMap<GoodId, MutableSet<GoodId>>() // input -> outputs
        for (r in recipes.values) {
            if (r.catalytic) continue
            for (i in r.inputs) for (o in r.outputs) {
                if (i.good != o.good) edges.getOrPut(i.good) { LinkedHashSet() }.add(o.good)
            }
        }
        val issues = ArrayList<ContentIssue>()
        val state = HashMap<GoodId, Int>()
        fun visit(g: GoodId, path: List<GoodId>) {
            when (state[g]) {
                2 -> return
                1 -> { issues += ContentIssue("recipe.cycle", "recipe cycle through goods: ${(path + g).joinToString(" -> ")}"); return }
            }
            state[g] = 1
            for (n in edges[g].orEmpty().sorted()) visit(n, path + g)
            state[g] = 2
        }
        for (g in edges.keys.sorted()) visit(g, emptyList())
        return issues
    }

    private fun aliasProblems(aliases: Map<String, String>, goods: Set<GoodId>): List<ContentIssue> {
        val issues = ArrayList<ContentIssue>()
        for ((from, to) in aliases) {
            if (GoodId(from) in goods) issues += ContentIssue("alias.shadow", "alias $from shadows an existing good", fatal = false)
            var current = to
            var hops = 0
            while (GoodId(current) !in goods && hops < 8) {
                current = aliases[current] ?: break
                hops++
            }
            if (GoodId(current) !in goods) issues += ContentIssue("alias.dangling", "alias $from -> $to does not resolve to a good")
        }
        return issues
    }

    /** Warns when a recipe's outputs are worth far less than its inputs at reference prices. */
    private fun economicSanity(recipes: Collection<RecipeDef>, goods: Map<GoodId, GoodDef>): List<ContentIssue> {
        val issues = ArrayList<ContentIssue>()
        for (r in recipes) {
            val inputValue = r.inputs.sumOf { a -> goods[a.good]?.referencePrice?.valueOf(a.quantity)?.raw ?: 0L }
            val outputValue = r.outputs.sumOf { a -> goods[a.good]?.referencePrice?.valueOf(a.quantity)?.raw ?: 0L }
            if (inputValue > 0 && outputValue * 100 < inputValue * 60) {
                issues += ContentIssue("recipe.value_loss", "recipe ${r.id} outputs are worth under 60% of inputs", fatal = false)
            }
            if (inputValue == 0L && outputValue == 0L && r.outputs.none { goods[it.good]?.category == GoodCategory.SERVICE }) {
                issues += ContentIssue("recipe.worthless", "recipe ${r.id} produces nothing of value", fatal = false)
            }
        }
        return issues
    }
}
