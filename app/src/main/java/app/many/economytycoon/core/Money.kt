package app.many.economytycoon.core

/**
 * Fixed-point money. One whole currency unit equals [SCALE] raw units, so the smallest
 * representable amount is 1/10,000 of a unit. All arithmetic is exact integer arithmetic.
 */
@JvmInline
value class Money(val raw: Long) : Comparable<Money> {

    operator fun plus(other: Money) = Money(Math.addExact(raw, other.raw))
    operator fun minus(other: Money) = Money(Math.subtractExact(raw, other.raw))
    operator fun unaryMinus() = Money(Math.negateExact(raw))
    operator fun times(factor: Long) = Money(Math.multiplyExact(raw, factor))
    override operator fun compareTo(other: Money) = raw.compareTo(other.raw)

    val isZero get() = raw == 0L
    val isPositive get() = raw > 0L
    val isNegative get() = raw < 0L

    fun abs() = if (raw < 0) Money(Math.negateExact(raw)) else this
    fun coerceAtLeast(min: Money) = if (raw < min.raw) min else this
    fun coerceAtMost(max: Money) = if (raw > max.raw) max else this

    /** Multiplies by a rate expressed in parts per million, rounding with [mode]. */
    fun times(rate: Rate, mode: RoundingMode = RoundingMode.HALF_EVEN): Money =
        Money(mulDiv(raw, rate.ppm, Rate.ONE_PPM, mode))

    /** Multiplies by [numerator]/[denominator] with a 128-bit intermediate. */
    fun scale(numerator: Long, denominator: Long, mode: RoundingMode = RoundingMode.HALF_EVEN): Money =
        Money(mulDiv(raw, numerator, denominator, mode))

    /** Money value of [quantity] units sold at this per-unit price. */
    fun valueOf(quantity: Quantity, mode: RoundingMode = RoundingMode.HALF_EVEN): Money =
        Money(mulDiv(raw, quantity.raw, Quantity.SCALE, mode))

    /** Splits this amount proportionally to [weights]; parts always sum exactly to the amount. */
    fun split(weights: LongArray): Array<Money> {
        val parts = splitLargestRemainder(raw, weights)
        return Array(parts.size) { Money(parts[it]) }
    }

    /** Whole currency units, truncated toward zero. */
    fun wholeUnits(): Long = raw / SCALE

    /** Decimal string with two fraction digits (rounded half-even) for display and logs. */
    fun format(): String {
        val rounded = divRound(raw, SCALE / 100, RoundingMode.HALF_EVEN)
        val neg = rounded < 0
        val abs = Math.abs(rounded)
        val units = abs / 100
        val cents = abs % 100
        return (if (neg) "-" else "") + units + "." + cents.toString().padStart(2, '0')
    }

    override fun toString() = format()

    companion object {
        const val SCALE: Long = 10_000L
        val ZERO = Money(0)

        fun units(whole: Long) = Money(Math.multiplyExact(whole, SCALE))
        fun cents(cents: Long) = Money(Math.multiplyExact(cents, SCALE / 100))
        fun ofRaw(raw: Long) = Money(raw)

        fun sum(values: Iterable<Money>): Money {
            var total = 0L
            for (v in values) total = Math.addExact(total, v.raw)
            return Money(total)
        }

        fun min(a: Money, b: Money) = if (a.raw <= b.raw) a else b
        fun max(a: Money, b: Money) = if (a.raw >= b.raw) a else b
    }
}
