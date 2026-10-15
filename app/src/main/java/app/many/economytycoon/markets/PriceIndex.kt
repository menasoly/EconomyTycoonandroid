package app.many.economytycoon.markets

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * Consumer and producer price indices. The consumer basket is weighted by what households buy at
 * reference prices; the producer basket weights raw and industrial goods equally by reference value.
 * An index of 1,000,000 ppm means prices sit at their reference levels.
 */
class PriceIndex(private val content: ContentRepository) {
    private val basket: List<Pair<GoodId, Long>> = content.allGoods
        .mapNotNull { g ->
            val profile = content.demandProfile(g.id) ?: return@mapNotNull null
            val weight = profile.perCapitaMilli.values.sum() * g.referencePrice.perUnit.raw
            if (weight > 0) g.id to weight else null
        }.sortedBy { it.first.key }

    private val producerBasket: List<Pair<GoodId, Long>> = content.allGoods
        .filter { it.category == GoodCategory.RAW || it.category == GoodCategory.INTERMEDIATE || it.category == GoodCategory.INDUSTRIAL }
        .map { it.id to it.referencePrice.perUnit.raw.coerceAtLeast(1L) }
        .sortedBy { it.first.key }

    private fun index(basketGoods: List<Pair<GoodId, Long>>, region: RegionId?, markets: MarketService): Long {
        var num = 0L; var den = 0L
        for ((good, weight) in basketGoods) {
            val ref = content.good(good).referencePrice.perUnit.raw
            if (ref <= 0) continue
            val price = if (region != null) markets.price(MarketKey(region, good)).perUnit.raw else markets.averagePrice(good).perUnit.raw
            // weight is already value at reference price; scale by the relative move
            num += mulDiv(weight, price, ref)
            den += weight
        }
        return if (den == 0L) 1_000_000L else mulDiv(num, 1_000_000L, den)
    }

    fun cpi(markets: MarketService, region: RegionId? = null): Long = index(basket, region, markets)

    fun ppi(markets: MarketService, region: RegionId? = null): Long = index(producerBasket, region, markets)
}

/** Monthly samples of the indices so inflation can be measured over a year. */
class IndexLog(private val capacity: Int = 24) {
    class Sample(val tick: Long, val cpi: Long, val ppi: Long)

    private val samples = ArrayList<Sample>()

    fun record(tick: Tick, cpi: Long, ppi: Long) {
        if (samples.isNotEmpty() && samples.last().tick == tick.value) return
        samples += Sample(tick.value, cpi, ppi)
        while (samples.size > capacity) samples.removeAt(0)
    }

    fun samples(): List<Sample> = samples.toList()
    fun latest(): Sample? = samples.lastOrNull()

    /** Year-on-year change of the consumer index, signed ppm; null until a full year of samples exists. */
    fun inflationPpm(now: Tick, ticksPerYear: Long = 360): Long? {
        val cur = samples.lastOrNull() ?: return null
        val past = samples.lastOrNull { it.tick <= now.value - ticksPerYear } ?: return null
        if (past.cpi == 0L) return null
        return mulDiv(cur.cpi - past.cpi, 1_000_000L, past.cpi)
    }

    /** Annualised change since the oldest retained sample, for early-game readouts. */
    fun trendPpm(ticksPerYear: Long = 360): Long? {
        if (samples.size < 2) return null
        val a = samples.first(); val b = samples.last()
        val span = b.tick - a.tick
        if (span <= 0 || a.cpi == 0L) return null
        return mulDiv(mulDiv(b.cpi - a.cpi, 1_000_000L, a.cpi), ticksPerYear, span)
    }

    internal fun restore(list: List<Sample>) { samples.clear(); samples.addAll(list) }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(samples.size)
        for (s in samples) h.putLong(s.tick).putLong(s.cpi).putLong(s.ppi)
    }
}
