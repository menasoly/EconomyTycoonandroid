package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

/**
 * The pure laws that turn what people think of a brand into demand. They take plain numbers so the market code can
 * call them without knowing anything else about marketing. One million means no change.
 */
object MarketingEffects {
    /** Demand a brand draws, against an unknown brand: awareness brings buyers in, loyalty keeps the aware ones. */
    fun appealPpm(awarenessPpm: Long, loyaltyPpm: Long, params: MarketingParams): Long {
        val a = Ppm.clamp(awarenessPpm)
        val l = Ppm.clamp(loyaltyPpm)
        return Ppm.ONE + Ppm.mul(params.awarenessDemandLiftPpm, a) + Ppm.mul(params.loyaltyDemandLiftPpm, Ppm.mul(a, l))
    }

    fun appealPpm(stock: BrandStock?, params: MarketingParams): Long =
        if (stock == null) Ppm.ONE else appealPpm(stock.awarenessPpm, stock.loyaltyPpm, params)

    /** Growth of the whole category from advertising: the more of the market has heard of some brand, the more it buys. */
    fun categoryShiftPpm(awarenessOfBrands: List<Long>, params: MarketingParams): Long =
        Ppm.ONE + Ppm.mul(params.categoryExpansionPpm, Ppm.union(awarenessOfBrands.toLongArray()))

    /** How a brand's appeal compares with the average appeal of the brands it sells against (itself included). */
    fun relativeAppealPpm(own: Long, allSellers: List<Long>): Long {
        if (allSellers.isEmpty()) return Ppm.ONE
        val average = allSellers.sum() / allSellers.size
        return if (average <= 0L) Ppm.ONE else Ppm.ratio(own, average)
    }

    /** Splits a market between sellers in proportion to appeal. Weights sum to exactly one million. */
    fun shareWeights(appeals: List<Long>): List<Long> {
        if (appeals.isEmpty()) return emptyList()
        val total = appeals.sum()
        if (total <= 0L) return List(appeals.size) { i -> if (i == 0) Ppm.ONE - (Ppm.ONE / appeals.size) * (appeals.size - 1) else Ppm.ONE / appeals.size }
        val split = app.many.economytycoon.core.splitLargestRemainder(Ppm.ONE, appeals.toLongArray())
        return split.toList()
    }
}

/**
 * The marketing view the market layer asks for each day. [forBrand] is the multiplier on the demand one company's
 * product sees in a region (reputation, the discount in force, and the dip after one); [categoryShift] is the shift
 * of the whole region's demand ladder for a good, in the form `markets/Demand.kt` takes as `shiftPpm`.
 */
class DemandEffects(
    private val state: MarketingState,
    private val params: MarketingParams = MarketingParams(),
    private val promotions: PromotionService = PromotionService(state, params),
) {
    fun appeal(key: BrandKey): Long = MarketingEffects.appealPpm(state.stock(key), params)

    /** Appeal with the discount in force. Use when the market has not already lowered the price itself. */
    fun forBrand(key: BrandKey, tick: Tick): Long =
        Ppm.mul(appeal(key), promotions.multiplierPpm(key.company, key.good, key.region, tick))

    fun categoryShift(good: GoodId, region: RegionId): Long =
        MarketingEffects.categoryShiftPpm(state.stocksIn(good, region).map { it.second.awarenessPpm }, params)

    /** Relative pull of [company] against the other [sellers] of a good in a region. */
    fun relative(company: CompanyId, good: GoodId, region: RegionId, sellers: List<CompanyId>): Long {
        val appeals = sellers.map { appeal(BrandKey(it, good, region)) }
        return MarketingEffects.relativeAppealPpm(appeal(BrandKey(company, good, region)), appeals)
    }

    /** Weights, summing to one million, that split a market between [sellers] by appeal. */
    fun shares(good: GoodId, region: RegionId, sellers: List<CompanyId>): Map<CompanyId, Long> {
        val sorted = sellers.distinct().sorted()
        val weights = MarketingEffects.shareWeights(sorted.map { appeal(BrandKey(it, good, region)) })
        return sorted.indices.associate { sorted[it] to weights[it] }
    }

    /** Price premium the brand can charge before buyers walk away, ppm of the going price. */
    fun pricingPower(key: BrandKey): Long {
        val s = state.stock(key) ?: return 0L
        return Ppm.mul(Ppm.mul(s.awarenessPpm, s.loyaltyPpm), PRICING_POWER_CEILING)
    }

    private companion object {
        const val PRICING_POWER_CEILING = 150_000L
    }
}
