package app.many.economytycoon.planner

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.FacilityTypeDef
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.content.audit.RecipeEconomics
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.mulDiv

/**
 * Works out what it takes to make a product from scratch: which recipes to run, how many production lines and
 * buildings that needs, what has to be bought, and what the chain would cost and earn each day at reference prices.
 *
 * The plan is a design aid, not a promise. It assumes every line runs flat out, inputs arrive on time and prices stay
 * at their reference values.
 */
class ChainPlanner(
    private val content: ContentRepository,
    /** Recipes the company may use; the default allows all. */
    private val allowed: (RecipeDef) -> Boolean = { true },
) {
    private val ppm = 1_000_000L

    /**
     * Plans [perDay] units of [product] a day. Goods in [buy] are bought rather than made, and so are goods no
     * allowed recipe makes. Chains deeper than [maxDepth] stop and buy what is left.
     */
    fun plan(product: GoodId, perDay: Quantity, buy: Set<GoodId> = emptySet(), maxDepth: Int = DEFAULT_DEPTH): ChainPlan? {
        if (content.goodOrNull(product) == null || perDay.raw <= 0L) return null
        val root = node(product, perDay, buy, maxDepth, emptySet(), 0)
        return ChainPlan(product, perDay, root, totals(root, product, perDay))
    }

    /** The recipe chosen for a good: the cheapest per unit made, ties broken by id. Null if none is allowed. */
    fun recipeFor(good: GoodId): RecipeDef? {
        val makers = content.recipesProducing(good).filter { r -> allowed(r) && r.outputs.any { it.good == good } }
        return makers.minWithOrNull(compareBy<RecipeDef>({ unitCost(it, good) }, { it.id.key }))
    }

    private fun unitCost(r: RecipeDef, good: GoodId): Long {
        val econ = RecipeEconomics.of(content, r)
        val out = r.outputs.first { it.good == good }
        val worth = r.outputs.sumOf { RecipeEconomics.valueOf(content, it) }
        val mine = RecipeEconomics.valueOf(content, out)
        val share = if (worth <= 0L) econ.totalCost / r.outputs.size else mulDiv(econ.totalCost, mine, worth)
        return if (out.quantity.raw <= 0L) Long.MAX_VALUE else mulDiv(share, Quantity.SCALE, out.quantity.raw)
    }

    private fun yieldOf(r: RecipeDef, good: GoodId): Long {
        val out = r.outputs.first { it.good == good }
        return mulDiv(out.quantity.raw, ppm - r.baseDefectPpm, ppm).coerceAtLeast(1L)
    }

    private fun node(good: GoodId, perDay: Quantity, buy: Set<GoodId>, maxDepth: Int, path: Set<GoodId>, depth: Int): PlanNode {
        val def = content.good(good)
        val recipe = if (good in buy || good in path || depth >= maxDepth) null else recipeFor(good)
        if (recipe == null) return PlanNode(good, def.name, perDay, Sourcing.BUY, null, 0L, emptyList())
        val batches = mulDiv(perDay.raw, ppm, yieldOf(recipe, good))
        val inputs = if (recipe.catalytic) recipe.inputs.filter { inp -> recipe.outputs.none { it.good == inp.good } } else recipe.inputs
        val children = inputs.map { inp ->
            val need = Quantity(mulDiv(inp.quantity.raw, batches, ppm).coerceAtLeast(1L))
            node(inp.good, need, buy, maxDepth, path + good, depth + 1)
        }
        val by = recipe.outputs.filter { it.good != good }.map { it.good to Quantity(mulDiv(it.quantity.raw, batches, ppm)) }
        return PlanNode(good, def.name, perDay, Sourcing.MAKE, recipe, batches, children, by)
    }

    private fun totals(root: PlanNode, product: GoodId, perDay: Quantity): PlanTotals {
        val nodes = root.walk()
        // One recipe can appear several times if two branches need the same intermediate; its lines are shared.
        val linesByRecipe = LinkedHashMap<String, Long>()
        val batchesByRecipe = LinkedHashMap<String, Long>()
        val recipes = LinkedHashMap<String, RecipeDef>()
        for (n in nodes) {
            val r = n.recipe ?: continue
            recipes[r.id.key] = r
            batchesByRecipe[r.id.key] = (batchesByRecipe[r.id.key] ?: 0L) + n.batchesPerDayPpm
        }
        for ((key, batches) in batchesByRecipe) {
            val r = recipes.getValue(key)
            linesByRecipe[key] = (batches * r.durationTicks + ppm - 1) / ppm
        }

        val linesByType = LinkedHashMap<FacilityTypeId, Long>()
        for ((key, lines) in linesByRecipe) {
            val type = recipes.getValue(key).facilityType
            linesByType[type] = (linesByType[type] ?: 0L) + lines
        }
        val needs = ArrayList<FacilityNeed>()
        var capital = 0L; var upkeep = 0L; var overhead = 0L
        val staff = LinkedHashMap<RoleId, Long>()
        for ((type, lines) in linesByType.toSortedMap(compareBy { it.key })) {
            val def = content.facilityTypeOrNull(type) ?: continue
            val count = facilitiesFor(def, lines)
            val cost = def.buildCost.raw * count
            capital += cost
            upkeep += def.upkeepPerTick.raw * count
            for (o in def.overheadStaff) {
                overhead += content.role(o.role).baseWage.raw * o.headcount * count
                staff[o.role] = (staff[o.role] ?: 0L) + o.headcount.toLong() * count
            }
            needs += FacilityNeed(type, def.name, count, lines, Money(cost))
        }

        var labour = 0L; var energy = 0L; var energyUnits = 0L
        val price = RecipeEconomics.priceOf(content, RecipeEconomics.ELECTRICITY)
        for ((key, lines) in linesByRecipe) {
            val r = recipes.getValue(key)
            val batches = batchesByRecipe.getValue(key)
            for (l in r.labor) {
                labour += mulDiv(content.role(l.role).baseWage.raw * l.headcount * r.durationTicks, batches, ppm)
                staff[l.role] = (staff[l.role] ?: 0L) + l.headcount.toLong() * lines
            }
            val units = mulDiv(r.energyPerBatch.raw, batches, ppm)
            energyUnits += units
            energy += price.valueOf(Quantity(units)).raw
        }

        val bought = LinkedHashMap<GoodId, Long>()
        for (n in nodes) if (n.sourcing == Sourcing.BUY && n != root) bought[n.good] = (bought[n.good] ?: 0L) + n.perDay.raw
        if (root.sourcing == Sourcing.BUY) bought[root.good] = root.perDay.raw
        val purchases = bought.entries.sortedBy { it.key.key }.map { (g, q) ->
            Purchase(g, content.good(g).name, Quantity(q), RecipeEconomics.priceOf(content, g).valueOf(Quantity(q)))
        }
        val materials = purchases.sumOf { it.costPerDay.raw }
        val revenue = if (root.sourcing == Sourcing.BUY) 0L else RecipeEconomics.priceOf(content, product).valueOf(perDay).raw
        return PlanTotals(
            facilities = needs, purchases = purchases, staff = staff.entries.sortedBy { it.key.key }.map { it.key to it.value },
            capital = Money(capital), revenuePerDay = Money(revenue), materialsPerDay = Money(materials), labourPerDay = Money(labour),
            energyPerDay = Money(energy), upkeepPerDay = Money(upkeep), overheadPerDay = Money(overhead), energyUnitsPerDay = Quantity(energyUnits),
        )
    }

    /** Buildings needed to host [lines] lines when each holds at most the type's line limit. */
    private fun facilitiesFor(def: FacilityTypeDef, lines: Long): Int {
        val per = def.maxLines.coerceAtLeast(1)
        return ((lines + per - 1) / per).toInt().coerceAtLeast(1)
    }

    companion object { const val DEFAULT_DEPTH = 8 }
}
