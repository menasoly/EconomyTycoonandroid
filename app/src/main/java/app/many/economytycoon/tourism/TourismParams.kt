package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money

/** Tunable limits and rates of the tourism module. Rates are ppm, money is whole units unless the name says otherwise. */
data class TourismParams(
    val maxPropertiesPerCompany: Int = 40,
    val maxAttractionsPerCompany: Int = 20,
    val maxRoomsPerProperty: Int = 800,
    val maxRoomsPerExpansion: Int = 100,
    val maxOverbookPpm: Long = 300_000L,
    /** The most of a class's rooms that may be set aside for operators at once. */
    val maxAllotmentSharePpm: Long = 700_000L,
    val minNetRatePpm: Long = 300_000L,
    val maxContractDays: Int = 360,
    val attractionBuildDays: Int = 120,
    val maxActiveShocksPerDestination: Int = 6,
    /** Marketing spend that gets half way to a saturated effect. */
    val campaignHalfSaturation: Money = Money.units(150_000),
    val minCampaignSpend: Money = Money.units(1_000),
    /** Daily decay of the effect of marketing, ppm. */
    val marketingDecayPpm: Long = 15_000L,
    /** How strongly average room rates pull a destination's price level, ppm. */
    val priceLevelPassThroughPpm: Long = 500_000L,
    val expansionCostPpm: Long = 1_200_000L,
    val renovationShare: Long = 400_000L,
    /** Wear a night adds to a property at full occupancy, and the share of it the routine upkeep repairs, ppm. */
    val wearPerNightPpm: Long = 1_400L,
    val upkeepShare: Long = 700_000L,
    /** Cost of walking a guest as a multiple of the rate paid. */
    val walkCostMultiplePpm: Long = 1_600_000L,
    /** Share of visitors who also stay for a day without a room (day trippers), relative to guests, ppm. */
    val dayTripperPpm: Long = 200_000L,
    val keepEndedAllotmentDays: Int = 180,
    val keepShockDays: Int = 120,
)
