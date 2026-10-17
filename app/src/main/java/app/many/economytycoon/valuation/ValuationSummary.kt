package app.many.economytycoon.valuation

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.Valuation

enum class Verdict(val label: String) {
    UNDERVALUED("Priced below its worth"),
    FAIR("Fairly priced"),
    OVERVALUED("Priced above its worth"),
    NOT_LISTED("Not traded on the exchange"),
    WORTHLESS("Little value to compare against"),
}

/** All methods side by side, blended into one estimate with a range. */
data class ValuationSummary(
    val methods: List<MethodValue>,
    val equity: Money,
    val low: Money,
    val high: Money,
    val shares: Long,
    val marketPrice: Money?,
    /** How much to trust the estimate, in ppm: thin trading history and few usable methods both lower it. */
    val confidencePpm: Long,
) {
    val perShare: Money get() = Valuation.perShare(equity, shares)
    val lowPerShare: Money get() = Valuation.perShare(low, shares)
    val highPerShare: Money get() = Valuation.perShare(high, shares)
    val applicable: List<MethodValue> get() = methods.filter { it.applicable }

    /** Market price against the estimate, in ppm; positive when the market pays more than the estimate. */
    val premiumPpm: Long? get() {
        val price = marketPrice ?: return null
        if (perShare.raw <= 0L) return null
        return mulDiv(price.raw - perShare.raw, 1_000_000L, perShare.raw)
    }

    fun verdict(tolerancePpm: Long = TOLERANCE_PPM): Verdict {
        if (marketPrice == null) return Verdict.NOT_LISTED
        val p = premiumPpm ?: return Verdict.WORTHLESS
        return when {
            p > tolerancePpm -> Verdict.OVERVALUED
            p < -tolerancePpm -> Verdict.UNDERVALUED
            else -> Verdict.FAIR
        }
    }

    companion object {
        const val TOLERANCE_PPM = 150_000L
        private const val FULL_HISTORY_DAYS = 90L

        fun of(i: ValuationInput, a: ValuationAssumptions = ValuationAssumptions.DEFAULT, weights: Map<Method, Int> = emptyMap()): ValuationSummary {
            val methods = ValuationMethods.all(i, a)
            val usable = methods.filter { it.applicable }
            val w = { m: MethodValue -> (weights[m.method] ?: m.method.weight).toLong().coerceAtLeast(0L) }
            val total = usable.sumOf(w)
            val equity = if (total <= 0L) Money.ZERO else Money(usable.sumOf { mulDiv(it.equity.raw, w(it), total) })
            val low = usable.minOfOrNull { it.equity } ?: Money.ZERO
            val high = usable.maxOfOrNull { it.equity } ?: Money.ZERO
            return ValuationSummary(methods, equity, low, high, i.shares, i.marketPrice, confidence(i, usable.size, methods.size))
        }

        fun confidence(i: ValuationInput, usable: Int, total: Int): Long {
            if (total <= 0) return 0L
            val history = (i.historyDays.coerceIn(0L, FULL_HISTORY_DAYS)) * 1_000_000L / FULL_HISTORY_DAYS
            val coverage = usable.toLong() * 1_000_000L / total
            return mulDiv(history, coverage, 1_000_000L)
        }
    }
}
