package app.many.economytycoon.core

/** Per-unit price: money per one base unit of a good. */
@JvmInline
value class Price(val perUnit: Money) : Comparable<Price> {
    override operator fun compareTo(other: Price) = perUnit.compareTo(other.perUnit)

    fun valueOf(quantity: Quantity, mode: RoundingMode = RoundingMode.HALF_EVEN): Money =
        perUnit.valueOf(quantity, mode)

    /** Scales the price by a rate, rounding half-even. */
    fun adjust(rate: Rate): Price = Price(perUnit.times(rate))

    /** Price change relative to [reference] as a rate: (this - reference) / reference. */
    fun changeFrom(reference: Price): Rate {
        require(reference.perUnit.raw != 0L) { "reference price must not be zero" }
        return Rate.ratio(perUnit.raw - reference.perUnit.raw, reference.perUnit.raw)
    }

    override fun toString() = perUnit.format()

    companion object {
        val ZERO = Price(Money.ZERO)
        fun of(units: Long) = Price(Money.units(units))
        fun ofRaw(raw: Long) = Price(Money(raw))
    }
}
