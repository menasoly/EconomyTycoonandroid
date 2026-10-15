package app.many.economytycoon.content.audit

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.GoodAmount
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money

/**
 * What one batch of a recipe costs and earns when everything trades at its reference price. Amounts are in
 * Money raw units (ten-thousandths) so nothing is lost to rounding.
 */
data class RecipeEconomy(
    val recipe: RecipeDef,
    val inputCost: Long,
    val laborCost: Long,
    val energyCost: Long,
    val outputValue: Long,
) {
    val totalCost: Long get() = inputCost + laborCost + energyCost
    val profitPerBatch: Long get() = outputValue - totalCost

    /** Profit as parts per million of cost; zero cost counts as a large positive margin when there is output. */
    val marginPpm: Long get() = when {
        totalCost > 0L -> profitPerBatch * 1_000_000L / totalCost
        outputValue > 0L -> MAX_MARGIN_PPM
        else -> 0L
    }.coerceIn(-MAX_MARGIN_PPM, MAX_MARGIN_PPM)

    /** What one production line earns in a day, since it runs one batch after another. */
    val profitPerLineDay: Long get() = profitPerBatch / recipe.durationTicks.coerceAtLeast(1)

    /** Share of the cost that is bought-in material, in ppm. */
    val materialSharePpm: Long get() = if (totalCost <= 0L) 0L else inputCost * 1_000_000L / totalCost

    companion object {
        const val MAX_MARGIN_PPM = 100_000_000L
    }
}

object RecipeEconomics {
    val ELECTRICITY = GoodId("electricity")

    fun priceOf(content: ContentRepository, good: GoodId): Money = content.goodOrNull(good)?.referencePrice?.perUnit ?: Money.ZERO

    fun valueOf(content: ContentRepository, a: GoodAmount): Long = priceOf(content, a.good).valueOf(a.quantity).raw

    fun of(content: ContentRepository, r: RecipeDef): RecipeEconomy {
        val inputs = if (r.catalytic) r.inputs.filter { inp -> r.outputs.none { it.good == inp.good } } else r.inputs
        val labor = r.labor.sumOf { l -> content.role(l.role).baseWage.raw * l.headcount * r.durationTicks }
        val energy = priceOf(content, ELECTRICITY).valueOf(r.energyPerBatch).raw
        return RecipeEconomy(r, inputs.sumOf { valueOf(content, it) }, labor, energy, r.outputs.sumOf { valueOf(content, it) })
    }

    fun all(content: ContentRepository): List<RecipeEconomy> = content.allRecipes.map { of(content, it) }
}
