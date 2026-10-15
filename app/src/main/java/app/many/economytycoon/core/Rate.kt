package app.many.economytycoon.core

/** A rate in parts per million (1,000,000 ppm = 100%). */
@JvmInline
value class Rate(val ppm: Long) : Comparable<Rate> {
    operator fun plus(other: Rate) = Rate(Math.addExact(ppm, other.ppm))
    operator fun minus(other: Rate) = Rate(Math.subtractExact(ppm, other.ppm))
    operator fun unaryMinus() = Rate(Math.negateExact(ppm))
    override operator fun compareTo(other: Rate) = ppm.compareTo(other.ppm)

    fun coerceIn(min: Rate, max: Rate) = Rate(ppm.coerceIn(min.ppm, max.ppm))

    /** Combines two rates multiplicatively: (this x other) / 1.0 */
    fun compose(other: Rate): Rate = Rate(mulDiv(ppm, other.ppm, ONE_PPM, RoundingMode.HALF_EVEN))

    /** Basis points, truncated. */
    fun basisPoints(): Long = ppm / 100

    fun format(): String {
        val neg = ppm < 0
        val abs = Math.abs(ppm)
        val whole = abs / 10_000
        val frac = (abs % 10_000) / 100
        return (if (neg) "-" else "") + whole + "." + frac.toString().padStart(2, '0') + "%"
    }

    override fun toString() = format()

    companion object {
        const val ONE_PPM: Long = 1_000_000L
        val ZERO = Rate(0)
        val ONE = Rate(ONE_PPM)

        fun percent(p: Long) = Rate(Math.multiplyExact(p, 10_000L))
        fun basisPoints(bp: Long) = Rate(Math.multiplyExact(bp, 100L))
        fun ofPpm(ppm: Long) = Rate(ppm)

        /** The rate numerator/denominator expressed in ppm, rounded half-even. */
        fun ratio(numerator: Long, denominator: Long): Rate {
            require(denominator != 0L) { "denominator must not be zero" }
            return Rate(mulDiv(numerator, ONE_PPM, denominator, RoundingMode.HALF_EVEN))
        }
    }
}
