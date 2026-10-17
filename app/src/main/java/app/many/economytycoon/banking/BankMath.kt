package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.core.splitLargestRemainder

/** Small fixed-point helpers shared by the banking engines. */
internal object BankMath {
    const val MONTH_TICKS = 30
    const val YEAR_TICKS = 360

    /** A cap that never binds. */
    val UNLIMITED = Money(Long.MAX_VALUE / 4L)

    /** One month of interest at an annual rate, rounded half-even. */
    fun monthlyInterest(balance: Money, ratePpm: Long): Money =
        if (balance.raw <= 0L || ratePpm == 0L) Money.ZERO else Money(mulDiv(balance.raw, ratePpm, Rate.ONE_PPM * 12L))

    /** One day of interest at an annual rate. */
    fun dailyInterest(balance: Money, ratePpm: Long): Money =
        if (balance.raw <= 0L || ratePpm == 0L) Money.ZERO else Money(mulDiv(balance.raw, ratePpm, Rate.ONE_PPM * YEAR_TICKS.toLong()))

    /** Splits [total] by [weights]; everything is zero when all the weights are zero. Parts always sum to the total. */
    fun split(total: Money, weights: LongArray): Array<Money> {
        if (weights.isEmpty()) return emptyArray()
        if (total.isZero || weights.all { it == 0L }) return Array(weights.size) { Money.ZERO }
        val parts = splitLargestRemainder(total.raw, weights)
        return Array(parts.size) { Money(parts[it]) }
    }

    /**
     * Splits [total] by [weights] without giving any part more than its cap. What a capped part cannot take is shared
     * among the others by the same weights. The parts sum to the total unless every cap is full, in which case they sum to
     * the sum of the caps.
     */
    fun splitCapped(total: Money, weights: LongArray, caps: Array<Money>): Array<Money> {
        require(weights.size == caps.size) { "one cap per weight" }
        val out = Array(weights.size) { Money.ZERO }
        var left = total
        val open = BooleanArray(weights.size) { weights[it] > 0L && caps[it].raw > 0L }
        while (left.raw > 0L && open.any { it }) {
            val w = LongArray(weights.size) { if (open[it]) weights[it] else 0L }
            val parts = split(left, w)
            var progressed = false
            for (i in out.indices) {
                if (!open[i]) continue
                val room = caps[i] - out[i]
                val give = Money.min(parts[i], room)
                if (give.raw > 0L) progressed = true
                out[i] += give
                left -= give
                if (out[i] >= caps[i]) open[i] = false
            }
            if (!progressed) break
        }
        return out
    }

    /** [n] over [d] as a rate, or [whenZero] when [d] is not positive. */
    fun ratio(n: Money, d: Money, whenZero: Rate = Rate.ZERO): Rate =
        if (d.raw <= 0L) whenZero else Rate(mulDiv(n.raw, Rate.ONE_PPM, d.raw))

    /** [amount] times [rate], rounded half-even, never negative for a non-negative amount. */
    fun times(amount: Money, rate: Rate): Money = amount.times(rate)

    fun floorTimes(amount: Money, rate: Rate): Money = Money(mulDiv(amount.raw, rate.ppm, Rate.ONE_PPM, RoundingMode.DOWN))

    fun clamp(r: Rate, lo: Rate, hi: Rate): Rate = r.coerceIn(lo, hi)

    /** Weighted blend of two rates, [weightB] out of [total]. */
    fun blend(a: Long, b: Long, weightB: Long, total: Long): Long =
        if (total <= 0L) a else a + mulDiv(b - a, weightB, total)

    fun basisPointsOf(r: Rate): Long = r.ppm / 100L
}
