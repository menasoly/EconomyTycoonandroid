package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.splitLargestRemainder

/** What the stability reserve does at the yearly review. */
data class ReserveDecision(val withhold: Tonnes, val release: Tonnes, val cancel: Tonnes) {
    val isIdle: Boolean get() = withhold.isZero && release.isZero && cancel.isZero
}

/**
 * The market stability reserve. When too many permits are in circulation it withholds a share of them from the
 * auctions and keeps them; when too few are, or the price spikes, it puts some back. Whatever the reserve holds beyond
 * its maximum is cancelled for good.
 */
object StabilityReserve {

    fun review(rule: ReserveRule, circulation: Tonnes, cap: Tonnes, stock: Tonnes, remainingAuctions: Tonnes): ReserveDecision {
        if (!cap.isPositive) return ReserveDecision(Tonnes.ZERO, Tonnes.ZERO, Tonnes.ZERO)
        val upper = cap.times(Rate(rule.upperPpm))
        val lower = cap.times(Rate(rule.lowerPpm))
        var withhold = Tonnes.ZERO
        var release = Tonnes.ZERO
        if (circulation > upper) {
            withhold = Tonnes.min(remainingAuctions, circulation.times(Rate(rule.intakePpm)))
        } else if (circulation < lower) {
            release = Tonnes.min(stock, cap.times(Rate(rule.releasePpm)))
        }
        val after = stock + withhold - release
        val limit = cap.times(Rate(rule.maxStockPpm))
        val cancel = if (after > limit) after - limit else Tonnes.ZERO
        return ReserveDecision(withhold, release, cancel)
    }

    /**
     * Extra permits released because the latest auction price ran away from the earlier ones. [prices] holds quarterly
     * auction prices, oldest first, with the latest last; at least four are needed to judge.
     */
    fun priceRelease(rule: ReserveRule, cap: Tonnes, stock: Tonnes, prices: List<Money>): Tonnes {
        if (prices.size < 5 || !cap.isPositive || !stock.isPositive) return Tonnes.ZERO
        val latest = prices.last()
        val earlier = prices.dropLast(1).takeLast(8)
        val average = Money.sum(earlier).raw / earlier.size
        if (average <= 0L) return Tonnes.ZERO
        val trigger = app.many.economytycoon.core.mulDiv(average, rule.priceTriggerPpm, Rate.ONE_PPM)
        if (latest.raw <= trigger) return Tonnes.ZERO
        return Tonnes.min(stock, cap.times(Rate(rule.priceReleasePpm)))
    }
}

/** Handing out free permits. */
object FreeAllocation {
    /**
     * Splits [pool] among companies in proportion to their [weights] (their baseline emissions). Companies are served in
     * id order, so the split is deterministic, and the parts add up to the pool exactly. With no weight at all nothing is
     * handed out.
     */
    fun distribute(pool: Tonnes, weights: Map<CompanyId, Long>): Map<CompanyId, Tonnes> {
        val positive = java.util.TreeMap<CompanyId, Long>()
        for ((c, w) in weights) if (w > 0L) positive[c] = w
        if (!pool.isPositive || positive.isEmpty()) return emptyMap()
        val ids = positive.keys.toList()
        val parts = splitLargestRemainder(pool.raw, LongArray(ids.size) { positive.getValue(ids[it]) })
        val out = java.util.TreeMap<CompanyId, Tonnes>()
        for ((i, c) in ids.withIndex()) if (parts[i] > 0L) out[c] = Tonnes(parts[i])
        return out
    }
}
