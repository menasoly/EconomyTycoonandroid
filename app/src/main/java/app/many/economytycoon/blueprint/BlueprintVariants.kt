package app.many.economytycoon.blueprint

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.content.audit.RecipeEconomics
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.mulDiv

/** Derives new blueprints from an existing one. Every variant is a new immutable blueprint; the source is untouched. */
class BlueprintVariants(private val content: ContentRepository) {

    /** Reverses the line order, so the line that ran first now runs last. Applying it twice gives back the original. */
    fun mirror(bp: Blueprint): Outcome<Blueprint> {
        if (bp.spec.lines.size < 2) return Outcome.reject(Rules.INVALID_ARGUMENT, "Mirroring needs at least two lines.")
        val lines = bp.spec.lines.reversed().mapIndexed { i, l -> l.copy(priority = i) }
        return Outcome.ok(derive(bp, bp.spec.copy(lines = lines), "mirrored"))
    }

    /** Recipes that run in the same kind of facility and make everything [recipe] makes, best earner first. */
    fun alternatives(recipe: RecipeId): List<RecipeDef> {
        val base = content.recipeOrNull(recipe) ?: return emptyList()
        val outs = base.outputs.map { it.good }.toSet()
        return content.recipesForFacility(base.facilityType)
            .filter { it.id != base.id && it.outputs.map { o -> o.good }.containsAll(outs) }
            .sortedWith(compareByDescending<RecipeDef> { RecipeEconomics.of(content, it).profitPerLineDay }.thenBy { it.id.key })
    }

    /**
     * Replaces recipes line by line. The machines the old recipe needed leave the line and the new recipe's
     * machines join it. Recipes must exist and belong to the same kind of facility.
     */
    fun swapRecipes(bp: Blueprint, mapping: Map<RecipeId, RecipeId>): Outcome<Blueprint> {
        if (mapping.isEmpty()) return Outcome.reject(Rules.INVALID_ARGUMENT, "Choose at least one recipe to swap.")
        for ((from, to) in mapping) {
            val target = content.recipeOrNull(to) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "Recipe '${to.key}' does not exist.")
            if (target.facilityType != bp.spec.facilityType) return Outcome.reject(Rules.INVALID_ARGUMENT, "${target.name} cannot run in this kind of facility.")
            if (bp.spec.lines.none { it.recipe == from }) return Outcome.reject(Rules.INVALID_ARGUMENT, "No line runs '${from.key}'.")
        }
        val lines = bp.spec.lines.map { l -> l.recipe?.let { mapping[it] }?.let { swapLine(l, it) } ?: l }
        if (lines == bp.spec.lines) return Outcome.reject(Rules.INVALID_ARGUMENT, "The swap changes nothing.")
        return Outcome.ok(derive(bp, bp.spec.copy(lines = lines), "swapped recipes"))
    }

    private fun swapLine(l: LineSpec, to: RecipeId): LineSpec {
        val old = l.recipe?.let { content.recipeOrNull(it) }
        val keep = l.machines - (old?.machines.orEmpty()).toSet()
        val need = content.recipeOrNull(to)?.machines.orEmpty()
        return l.copy(recipe = to, machines = (keep + need).distinct().sorted())
    }

    /**
     * Moves each line to the next better recipe: one making the same goods whose machines are faster versions
     * of the ones now required, with the technology available. Lines with no such recipe stay as they are.
     */
    fun upgradeTier(bp: Blueprint, research: ResearchView = ResearchView.ALL): Outcome<Blueprint> {
        var changed = false
        val lines = bp.spec.lines.map { l ->
            val best = l.recipe?.let { betterRecipe(it, research) }
            if (best == null) l else { changed = true; swapLine(l, best.id) }
        }
        if (!changed) return Outcome.reject(Rules.INVALID_ARGUMENT, "No line has a better recipe you can run yet.")
        return Outcome.ok(derive(bp, bp.spec.copy(lines = lines), "upgraded"))
    }

    private fun betterRecipe(recipe: RecipeId, research: ResearchView): RecipeDef? {
        val cur = content.recipeOrNull(recipe) ?: return null
        val curProfit = RecipeEconomics.of(content, cur).profitPerLineDay
        return alternatives(recipe).filter { alt ->
            alt.requiredTech.let { it == null || research.hasTech(it) } &&
                alt.machines.all { m -> content.machineTypeOrNull(m)?.requiredTech.let { it == null || research.hasTech(it) } } &&
                alt.machines.isNotEmpty() && alt.machines.all { m -> m in cur.machines || cur.machines.any { faster(m, it) } } &&
                alt.machines.any { it !in cur.machines } &&
                RecipeEconomics.of(content, alt).profitPerLineDay > curProfit
        }.minWithOrNull(compareBy<RecipeDef>({ cost(it) }, { it.id.key }))
    }

    private fun faster(a: MachineTypeId, than: MachineTypeId): Boolean {
        val x = content.machineTypeOrNull(a) ?: return false
        val y = content.machineTypeOrNull(than) ?: return false
        return x.throughputPpm > y.throughputPpm
    }

    private fun cost(r: RecipeDef) = r.machines.sumOf { content.machineTypeOrNull(it)?.cost?.raw ?: 0L }

    /**
     * Resizes the facility to [lineCount] lines. New lines copy the line that earns the most; staff targets and
     * trading quantities scale with the line count, rounded up so a bigger plant is never understaffed.
     */
    fun scaleLines(bp: Blueprint, lineCount: Int): Outcome<Blueprint> {
        val type = content.facilityTypeOrNull(bp.spec.facilityType) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "This facility type no longer exists.")
        val old = bp.spec.lines.size
        if (old == 0) return Outcome.reject(Rules.INVALID_ARGUMENT, "There is no line to scale from.")
        if (lineCount < 1 || lineCount > type.maxLines) return Outcome.reject(Rules.LIMIT_EXCEEDED, "${type.name} supports 1 to ${type.maxLines} lines.")
        if (lineCount == old) return Outcome.reject(Rules.INVALID_ARGUMENT, "The blueprint already has $old lines.")
        val lines = if (lineCount < old) bp.spec.lines.take(lineCount) else {
            val template = bestLine(bp.spec)
            bp.spec.lines + List(lineCount - old) { template }
        }.mapIndexed { i, l -> l.copy(priority = i) }
        val spec = bp.spec.copy(lines = lines).let { scaleVolumes(it, lineCount.toLong(), old.toLong()) }
        return Outcome.ok(derive(bp, spec, "$lineCount lines"))
    }

    /** Multiplies staffing targets by [factorPpm] (1,000,000 = unchanged), rounding up. */
    fun scaleStaff(bp: Blueprint, factorPpm: Long): Outcome<Blueprint> {
        if (factorPpm !in 100_000L..10_000_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "Choose a factor between 0.1 and 10.")
        if (bp.spec.staff.isEmpty()) return Outcome.reject(Rules.INVALID_ARGUMENT, "The blueprint has no staffing to scale.")
        val staff = bp.spec.staff.map { it.copy(target = ceilMul(it.target, factorPpm, 1_000_000L).coerceAtMost(BlueprintLimits.MAX_STAFF_TARGET)) }
        if (staff == bp.spec.staff) return Outcome.reject(Rules.INVALID_ARGUMENT, "The scaling changes nothing.")
        return Outcome.ok(derive(bp, bp.spec.copy(staff = staff), "staff x${factorPpm / 1_000_000}.${(factorPpm % 1_000_000 / 10_000).toString().padStart(2, '0')}"))
    }

    private fun scaleVolumes(spec: BlueprintSpec, num: Long, den: Long): BlueprintSpec = spec.copy(
        staff = spec.staff.map { it.copy(target = ceilMul(it.target, num, den).coerceAtMost(BlueprintLimits.MAX_STAFF_TARGET)) },
        sells = spec.sells.map { s -> s.copy(reserve = Quantity(ceilMul(s.reserve.raw, num, den)), maxPerOrder = s.maxPerOrder?.let { Quantity(ceilMul(it.raw, num, den)) }) },
        buys = spec.buys.map { b -> b.copy(target = Quantity(ceilMul(b.target.raw, num, den))) },
    )

    private fun bestLine(spec: BlueprintSpec): LineSpec = spec.lines.maxWithOrNull(compareBy<LineSpec>({ profit(it) }, { -it.priority })) ?: spec.lines.last()

    private fun profit(l: LineSpec): Long {
        val r = l.recipe?.let { content.recipeOrNull(it) } ?: return Long.MIN_VALUE
        return RecipeEconomics.of(content, r).profitPerLineDay
    }

    private fun ceilMul(v: Long, num: Long, den: Long): Long = (mulDiv(v, num, den) + if (mulDiv(v, num, den) * den < v * num) 1L else 0L)

    private fun derive(bp: Blueprint, spec: BlueprintSpec, tag: String): Blueprint {
        val suffix = " ($tag)"
        val base = bp.name.removeSuffix(suffix)
        val name = if (base.length + suffix.length > BlueprintLimits.MAX_NAME) base.take(BlueprintLimits.MAX_NAME - suffix.length).trimEnd() + suffix else base + suffix
        return bp.copy(spec = spec.normalized(), name = name)
    }
}
