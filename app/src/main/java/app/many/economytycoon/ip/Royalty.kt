package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv

/** Royalty arithmetic, free of state. */
object Royalty {
    /** The running royalty for a period before any minimum or cap. */
    fun running(terms: RoyaltyTerms, usage: UsageReport): Money = when (terms.basis) {
        RoyaltyBasis.LUMP_SUM -> Money.ZERO
        RoyaltyBasis.PER_UNIT -> terms.perUnit.valueOf(usage.units)
        RoyaltyBasis.PERCENT -> usage.sales.times(terms.percent)
        RoyaltyBasis.HYBRID -> terms.perUnit.valueOf(usage.units) + usage.sales.times(terms.percent)
    }

    /** What is due for one period: the running royalty, held to the cap, but never below the minimum. */
    fun due(terms: RoyaltyTerms, usage: UsageReport): Money {
        if (!terms.isRecurring) return Money.ZERO
        var amount = running(terms, usage)
        val cap = terms.capPerPeriod
        if (cap != null && amount > cap) amount = cap
        return Money.max(amount, terms.minimumPerPeriod)
    }

    /** Royalty as a share of sales, for comparing deals of different shapes. Zero when nothing was sold. */
    fun effectiveRate(terms: RoyaltyTerms, usage: UsageReport): Rate =
        if (usage.sales.raw <= 0L) Rate.ZERO else Rate(mulDiv(due(terms, usage).raw, Rate.ONE_PPM, usage.sales.raw))

    /** Total the licensee is committed to pay at least, over [periods] periods. */
    fun committed(terms: RoyaltyTerms, periods: Int): Money =
        if (!terms.isRecurring) terms.lumpSum + terms.upfront else Money(terms.minimumPerPeriod.raw * periods) + terms.upfront

    /** What the licensee pays when the licence starts. */
    fun dueAtSigning(terms: RoyaltyTerms): Money = terms.upfront + if (terms.basis == RoyaltyBasis.LUMP_SUM) terms.lumpSum else Money.ZERO

    /**
     * A percent-of-sales rate a willing licensor and licensee could agree on: a quarter of the profit the licensee makes,
     * scaled by how much of the product the patent really accounts for (its breadth and novelty).
     */
    fun reasonablePercent(licensing: LicenseParams, profitMarginPpm: Long, breadthPpm: Long, noveltyPpm: Long): Rate {
        val share = mulDiv(profitMarginPpm.coerceAtLeast(0L), licensing.profitShareRulePpm, 1_000_000L)
        val contribution = (breadthPpm.coerceIn(0L, 1_000_000L) + noveltyPpm.coerceIn(0L, 1_000_000L)) / 2
        return Rate(mulDiv(share, contribution, 1_000_000L).coerceIn(1_000L, licensing.maxPercentPpm))
    }

    /** Per-unit equivalent of a percent rate at a typical unit price. */
    fun perUnitFor(percent: Rate, unitPrice: Money): Money = unitPrice.times(percent)

    fun scaleUsage(u: UsageReport, share: Rate): UsageReport = UsageReport(u.units.times(share), u.sales.times(share))
}
