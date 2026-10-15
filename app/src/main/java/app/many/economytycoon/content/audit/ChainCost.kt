package app.many.economytycoon.content.audit

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.GoodId

/**
 * The cheapest way to make each good at reference prices, followed back through its ingredients. Goods no
 * recipe makes cost their reference price (they are mined, grown or bought). A multi-output recipe splits its
 * cost between outputs by their reference value.
 */
class ChainCost(private val content: ContentRepository, private val maxDepth: Int = 12) {
    private val memo = HashMap<GoodId, Long>()
    private val economics = RecipeEconomics.all(content).associateBy { it.recipe.id.key }
    private val inProgress = HashSet<GoodId>()

    /** Cost in Money raw to make one whole unit of [good], or its reference price when nothing makes it. */
    fun perUnit(good: GoodId): Long = resolve(good, 0)

    private fun reference(good: GoodId): Long = RecipeEconomics.priceOf(content, good).raw

    private fun resolve(good: GoodId, depth: Int): Long {
        memo[good]?.let { return it }
        val makers = content.recipesProducing(good).filter { r -> r.outputs.any { it.good == good && it.quantity.raw > 0L } }
        if (makers.isEmpty() || depth >= maxDepth || !inProgress.add(good)) return reference(good)
        try {
            var best = Long.MAX_VALUE
            for (r in makers) {
                val econ = economics[r.id.key] ?: continue
                val ingredients = r.inputs.filter { inp -> !r.catalytic || r.outputs.none { it.good == inp.good } }
                    .sumOf { inp -> mulDiv(resolve(inp.good, depth + 1), inp.quantity.raw, 1_000L) }
                val total = ingredients + econ.laborCost + econ.energyCost
                val outputs = r.outputs
                val outputWorth = outputs.sumOf { mulDiv(reference(it.good), it.quantity.raw, 1_000L) }
                val mine = outputs.first { it.good == good }
                val myWorth = mulDiv(reference(good), mine.quantity.raw, 1_000L)
                val share = if (outputWorth <= 0L) total / outputs.size else mulDiv(total, myWorth, outputWorth)
                val unit = if (mine.quantity.raw <= 0L) Long.MAX_VALUE else mulDiv(share, 1_000L, mine.quantity.raw)
                if (unit < best) best = unit
            }
            val result = if (best == Long.MAX_VALUE) reference(good) else best
            memo[good] = result
            return result
        } finally {
            inProgress.remove(good)
        }
    }

    /** Reference price over chain cost in ppm: above one million means the market pays more than it costs. */
    fun priceToCostPpm(good: GoodId): Long {
        val cost = perUnit(good)
        val price = reference(good)
        return if (cost <= 0L) (if (price > 0L) RecipeEconomy.MAX_MARGIN_PPM else 1_000_000L) else mulDiv(price, 1_000_000L, cost).coerceAtMost(RecipeEconomy.MAX_MARGIN_PPM)
    }

    private fun mulDiv(a: Long, b: Long, c: Long): Long =
        java.math.BigInteger.valueOf(a).multiply(java.math.BigInteger.valueOf(b)).divide(java.math.BigInteger.valueOf(c)).let {
            if (it.bitLength() > 62) Long.MAX_VALUE / 4 else it.toLong()
        }
}
