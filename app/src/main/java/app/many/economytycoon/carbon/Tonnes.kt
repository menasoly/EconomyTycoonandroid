package app.many.economytycoon.carbon

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.divRound
import app.many.economytycoon.core.mulDiv

/**
 * A mass of carbon dioxide equivalent. One raw unit is a kilogram, so 1,000 raw units make a tonne. Arithmetic is
 * exact integer arithmetic and overflow throws instead of wrapping.
 */
@JvmInline
value class Tonnes(val raw: Long) : Comparable<Tonnes> {

    operator fun plus(other: Tonnes) = Tonnes(Math.addExact(raw, other.raw))
    operator fun minus(other: Tonnes) = Tonnes(Math.subtractExact(raw, other.raw))
    operator fun unaryMinus() = Tonnes(Math.negateExact(raw))
    operator fun times(factor: Long) = Tonnes(Math.multiplyExact(raw, factor))
    override operator fun compareTo(other: Tonnes) = raw.compareTo(other.raw)

    val isZero get() = raw == 0L
    val isPositive get() = raw > 0L
    val isNegative get() = raw < 0L

    fun coerceAtLeast(min: Tonnes) = if (raw < min.raw) min else this
    fun coerceAtMost(max: Tonnes) = if (raw > max.raw) max else this

    /** This mass times a rate; rounds toward zero unless told otherwise. */
    fun times(rate: Rate, mode: RoundingMode = RoundingMode.DOWN): Tonnes =
        Tonnes(mulDiv(raw, rate.ppm, Rate.ONE_PPM, mode))

    fun scale(numerator: Long, denominator: Long, mode: RoundingMode = RoundingMode.DOWN): Tonnes =
        Tonnes(mulDiv(raw, numerator, denominator, mode))

    /** Money value of this mass at [pricePerTonne]. */
    fun cost(pricePerTonne: Money, mode: RoundingMode = RoundingMode.HALF_EVEN): Money =
        Money(mulDiv(pricePerTonne.raw, raw, SCALE, mode))

    /** Whole tonnes, truncated toward zero. */
    fun wholeTonnes(): Long = raw / SCALE

    /** Three decimals of a tonne, e.g. `12.500`. */
    fun format(): String {
        val neg = raw < 0
        val abs = Math.abs(raw)
        return (if (neg) "-" else "") + (abs / SCALE) + "." + (abs % SCALE).toString().padStart(3, '0')
    }

    override fun toString() = format()

    companion object {
        const val SCALE: Long = 1_000L
        val ZERO = Tonnes(0)

        fun of(whole: Long) = Tonnes(Math.multiplyExact(whole, SCALE))
        fun ofKg(kilograms: Long) = Tonnes(kilograms)
        fun ofRaw(raw: Long) = Tonnes(raw)

        /** Mass of [grams] of gas, rounded half-even to a kilogram. */
        fun ofGrams(grams: Long) = Tonnes(divRound(grams, 1_000L, RoundingMode.HALF_EVEN))

        fun sum(values: Iterable<Tonnes>): Tonnes {
            var total = 0L
            for (v in values) total = Math.addExact(total, v.raw)
            return Tonnes(total)
        }

        fun min(a: Tonnes, b: Tonnes) = if (a.raw <= b.raw) a else b
        fun max(a: Tonnes, b: Tonnes) = if (a.raw >= b.raw) a else b
    }
}

/** Calendar helpers used by the compliance cycle. A compliance year is a calendar year of 360 ticks. */
object CarbonCalendar {
    const val DAYS_PER_YEAR = Calendar.DAYS_PER_YEAR
    const val DAYS_PER_MONTH = Calendar.DAYS_PER_MONTH

    fun yearOf(tick: Tick): Int = Calendar.START_YEAR + (tick.value / DAYS_PER_YEAR).toInt()

    fun dayOfYear(tick: Tick): Int = (tick.value % DAYS_PER_YEAR).toInt()

    fun monthOfYear(tick: Tick): Int = dayOfYear(tick) / DAYS_PER_MONTH

    fun startOfYear(year: Int): Tick = Tick((year - Calendar.START_YEAR).toLong() * DAYS_PER_YEAR)

    fun isYearStart(tick: Tick) = tick.value % DAYS_PER_YEAR == 0L
    fun isMonthStart(tick: Tick) = tick.value % DAYS_PER_MONTH == 0L
}
