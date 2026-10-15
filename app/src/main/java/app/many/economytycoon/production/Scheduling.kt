package app.many.economytycoon.production

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.mulDiv

/** Chooses a recipe for an idle line when the facility runs under [SchedulePolicy.AUTO_MARGIN]. */
object Scheduler {
    /** Value created per tick of line time at the given prices; negative when the recipe destroys value. */
    fun marginPerTick(recipe: RecipeDef, content: ContentRepository, prices: PriceView, inventory: Inventory): Money {
        var out = 0L
        for (o in recipe.outputs) out += prices.price(o.good).valueOf(o.quantity).raw
        // A defective batch yields only a fraction; weigh the expected loss in.
        out -= mulDiv(out, recipe.baseDefectPpm * 700_000L, 1_000_000L * 1_000_000L)
        var inp = 0L
        for (i in recipe.inputs) {
            val unit = inventory.unitCost(i.good)
            val unitCost = if (unit.raw > 0) unit else prices.price(i.good).perUnit
            inp += unitCost.valueOf(i.quantity).raw
        }
        var labor = 0L
        for (l in recipe.labor) labor += content.role(l.role).baseWage.raw * l.headcount * recipe.durationTicks
        return Money((out - inp - labor) / recipe.durationTicks)
    }

    /** True when every input is currently on hand in the quantity one batch needs. */
    fun canStart(recipe: RecipeDef, inventory: Inventory): Boolean =
        recipe.inputs.all { inventory.available(it.good) >= it.quantity }

    /**
     * Picks the best recipe among [candidates] that can start now and earns a positive margin. Ties go to the
     * recipe with the smaller id so the choice is deterministic. Returns null to leave the line idle.
     */
    fun choose(candidates: List<RecipeDef>, content: ContentRepository, prices: PriceView, inventory: Inventory): RecipeDef? {
        var best: RecipeDef? = null
        var bestMargin = Money.ZERO
        for (r in candidates.sortedBy { it.id.key }) {
            if (!canStart(r, inventory)) continue
            val m = marginPerTick(r, content, prices, inventory)
            if (m.raw > bestMargin.raw) { best = r; bestMargin = m }
        }
        return best
    }

    /** Share of a recipe's inputs on hand, in ppm; used to rank recipes that could start soon. */
    fun readinessPpm(recipe: RecipeDef, inventory: Inventory): Long {
        if (recipe.inputs.isEmpty()) return 1_000_000L
        var min = 1_000_000L
        for (i in recipe.inputs) {
            val have = inventory.available(i.good)
            val share = if (i.quantity.raw == 0L) 1_000_000L else mulDiv(Quantity.min(have, i.quantity).raw, 1_000_000L, i.quantity.raw)
            if (share < min) min = share
        }
        return min
    }
}
