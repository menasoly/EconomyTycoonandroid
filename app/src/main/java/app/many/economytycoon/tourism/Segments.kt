package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money

/** The four kinds of traveller the model distinguishes. The ordinal is part of the save format. */
enum class VisitorSegment(val label: String) {
    BUDGET("Budget"), FAMILY("Family"), BUSINESS("Business"), LUXURY("Luxury");

    val profile: SegmentProfile get() = SegmentProfiles.of(this)

    companion object {
        val COUNT = values().size
    }
}

/**
 * How one segment behaves. Weights are parts per million and say how much of the appeal of a destination comes from
 * each driver; they need not sum to anything. Lengths of stay are in tenths of a night, party size in tenths of a
 * person per room, so everything stays integral.
 */
class SegmentProfile(
    /** How strongly a price above the reference level pushes this segment away; 1,000,000 is a unit slope. */
    val priceSensitivityPpm: Long,
    /** The star level this segment books, 1 to 5. */
    val preferredStars: Int,
    val nightsTenths: Int,
    val partyTenths: Int,
    /** Spending outside the hotel per person and day, at the reference price level. */
    val dailySpend: Money,
    /** Preferred warmth on the climate scale, see [Seasonality.warmth]. */
    val idealWarmthPpm: Long,
    val climateWeight: Long,
    val infrastructureWeight: Long,
    val safetyWeight: Long,
    val cultureWeight: Long,
    val natureWeight: Long,
    val nightlifeWeight: Long,
    val eventWeight: Long,
    val reputationWeight: Long,
    /** How far from home this segment is willing to go; larger means distance deters less. */
    val distanceToleranceUnits: Int,
    /** Demand on a weekday and on a weekend day relative to the average, ppm. */
    val weekdayPpm: Long,
    val weekendPpm: Long,
    /** Share of room requests that arrive 0, 1, 2, ... [Seasonality.HORIZON] - 1 days before the stay, ppm summing to one. */
    val bookingCurve: LongArray,
    /** Probability that a booking is cancelled before the stay, ppm, on a flexible rate. */
    val cancelPpm: Long,
    val noShowPpm: Long,
    /** Shares of requests that ask for a standard, superior and suite room. */
    val classMixPpm: LongArray,
    /** Share of this segment's demand that sleeps with friends or in informal lodging and never books a property. */
    val informalPpm: Long,
) {
    val nights: Long get() = nightsTenths / 10L
}

object SegmentProfiles {
    private fun curve(vararg shares: Long): LongArray {
        require(shares.size == Seasonality.HORIZON) { "a booking curve has ${Seasonality.HORIZON} entries" }
        require(shares.sum() == 1_000_000L) { "a booking curve sums to one" }
        return shares
    }

    private val budget = SegmentProfile(
        priceSensitivityPpm = 1_400_000L, preferredStars = 2, nightsTenths = 35, partyTenths = 17, dailySpend = Money.units(30),
        idealWarmthPpm = 600_000L, climateWeight = 300_000L, infrastructureWeight = 100_000L, safetyWeight = 150_000L, cultureWeight = 150_000L,
        natureWeight = 150_000L, nightlifeWeight = 250_000L, eventWeight = 100_000L, reputationWeight = 100_000L,
        distanceToleranceUnits = 250, weekdayPpm = 900_000L, weekendPpm = 1_250_000L,
        bookingCurve = curve(40_000, 50_000, 60_000, 60_000, 70_000, 70_000, 80_000, 80_000, 80_000, 80_000, 80_000, 80_000, 80_000, 90_000),
        cancelPpm = 120_000L, noShowPpm = 30_000L, classMixPpm = longArrayOf(920_000L, 80_000L, 0L), informalPpm = 150_000L,
    )

    private val family = SegmentProfile(
        priceSensitivityPpm = 1_000_000L, preferredStars = 3, nightsTenths = 55, partyTenths = 34, dailySpend = Money.units(45),
        idealWarmthPpm = 700_000L, climateWeight = 400_000L, infrastructureWeight = 150_000L, safetyWeight = 300_000L, cultureWeight = 100_000L,
        natureWeight = 250_000L, nightlifeWeight = 0L, eventWeight = 150_000L, reputationWeight = 150_000L,
        distanceToleranceUnits = 350, weekdayPpm = 900_000L, weekendPpm = 1_250_000L,
        bookingCurve = curve(15_000, 20_000, 25_000, 30_000, 35_000, 40_000, 50_000, 55_000, 65_000, 75_000, 85_000, 100_000, 120_000, 285_000),
        cancelPpm = 100_000L, noShowPpm = 20_000L, classMixPpm = longArrayOf(600_000L, 340_000L, 60_000L), informalPpm = 100_000L,
    )

    private val business = SegmentProfile(
        priceSensitivityPpm = 450_000L, preferredStars = 4, nightsTenths = 20, partyTenths = 11, dailySpend = Money.units(70),
        idealWarmthPpm = 300_000L, climateWeight = 50_000L, infrastructureWeight = 350_000L, safetyWeight = 200_000L, cultureWeight = 30_000L,
        natureWeight = 0L, nightlifeWeight = 50_000L, eventWeight = 300_000L, reputationWeight = 200_000L,
        distanceToleranceUnits = 500, weekdayPpm = 1_250_000L, weekendPpm = 450_000L,
        bookingCurve = curve(150_000, 140_000, 120_000, 100_000, 90_000, 80_000, 70_000, 60_000, 50_000, 40_000, 35_000, 25_000, 20_000, 20_000),
        cancelPpm = 180_000L, noShowPpm = 40_000L, classMixPpm = longArrayOf(500_000L, 430_000L, 70_000L), informalPpm = 20_000L,
    )

    private val luxury = SegmentProfile(
        priceSensitivityPpm = 200_000L, preferredStars = 5, nightsTenths = 60, partyTenths = 20, dailySpend = Money.units(180),
        idealWarmthPpm = 500_000L, climateWeight = 350_000L, infrastructureWeight = 250_000L, safetyWeight = 300_000L, cultureWeight = 200_000L,
        natureWeight = 200_000L, nightlifeWeight = 100_000L, eventWeight = 150_000L, reputationWeight = 300_000L,
        distanceToleranceUnits = 800, weekdayPpm = 1_000_000L, weekendPpm = 1_000_000L,
        bookingCurve = curve(30_000, 30_000, 40_000, 40_000, 50_000, 50_000, 60_000, 60_000, 70_000, 80_000, 90_000, 100_000, 120_000, 180_000),
        cancelPpm = 140_000L, noShowPpm = 15_000L, classMixPpm = longArrayOf(100_000L, 400_000L, 500_000L), informalPpm = 30_000L,
    )

    fun of(segment: VisitorSegment): SegmentProfile = when (segment) {
        VisitorSegment.BUDGET -> budget
        VisitorSegment.FAMILY -> family
        VisitorSegment.BUSINESS -> business
        VisitorSegment.LUXURY -> luxury
    }
}

/** The sales channels a room can be booked through. The ordinal is part of the save format. */
enum class Channel(val label: String) {
    DIRECT("Direct"), AGENCY("Travel agency"), ONLINE("Online platform"), OPERATOR("Tour operator");

    companion object {
        val COUNT = values().size

        /** Share of each segment's requests that come through each channel, ppm in channel order. */
        fun mix(segment: VisitorSegment): LongArray = when (segment) {
            VisitorSegment.BUDGET -> longArrayOf(200_000L, 80_000L, 620_000L, 100_000L)
            VisitorSegment.FAMILY -> longArrayOf(180_000L, 190_000L, 330_000L, 300_000L)
            VisitorSegment.BUSINESS -> longArrayOf(460_000L, 220_000L, 270_000L, 50_000L)
            VisitorSegment.LUXURY -> longArrayOf(300_000L, 330_000L, 120_000L, 250_000L)
        }
    }
}
