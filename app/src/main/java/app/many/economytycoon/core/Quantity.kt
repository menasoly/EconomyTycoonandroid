package app.many.economytycoon.core

/**
 * Fixed-point quantity of goods. 1,000 raw units equal one base unit of the good.
 */
@JvmInline
value class Quantity(val raw: Long) : Comparable<Quantity> {

    operator fun plus(other: Quantity) = Quantity(Math.addExact(raw, other.raw))
    operator fun minus(other: Quantity) = Quantity(Math.subtractExact(raw, other.raw))
    operator fun times(factor: Long) = Quantity(Math.multiplyExact(raw, factor))
    override operator fun compareTo(other: Quantity) = raw.compareTo(other.raw)

    val isZero get() = raw == 0L
    val isPositive get() = raw > 0L
    val isNegative get() = raw < 0L

    fun times(rate: Rate, mode: RoundingMode = RoundingMode.DOWN): Quantity =
        Quantity(mulDiv(raw, rate.ppm, Rate.ONE_PPM, mode))

    fun scale(numerator: Long, denominator: Long, mode: RoundingMode = RoundingMode.DOWN): Quantity =
        Quantity(mulDiv(raw, numerator, denominator, mode))

    fun coerceAtLeast(min: Quantity) = if (raw < min.raw) min else this
    fun coerceAtMost(max: Quantity) = if (raw > max.raw) max else this

    /** Whole base units, truncated toward zero. */
    fun wholeUnits(): Long = raw / SCALE

    fun format(): String {
        val neg = raw < 0
        val abs = Math.abs(raw)
        return (if (neg) "-" else "") + (abs / SCALE) + "." + (abs % SCALE).toString().padStart(3, '0')
    }

    override fun toString() = format()

    companion object {
        const val SCALE: Long = 1_000L
        val ZERO = Quantity(0)

        fun units(whole: Long) = Quantity(Math.multiplyExact(whole, SCALE))
        fun ofRaw(raw: Long) = Quantity(raw)

        fun sum(values: Iterable<Quantity>): Quantity {
            var total = 0L
            for (v in values) total = Math.addExact(total, v.raw)
            return Quantity(total)
        }

        fun min(a: Quantity, b: Quantity) = if (a.raw <= b.raw) a else b
        fun max(a: Quantity, b: Quantity) = if (a.raw >= b.raw) a else b
    }
}
