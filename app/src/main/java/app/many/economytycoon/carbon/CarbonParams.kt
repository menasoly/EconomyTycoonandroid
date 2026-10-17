package app.many.economytycoon.carbon

import app.many.economytycoon.core.Money

/** Tuning of the package. Nothing here is a rule of a regime; regimes carry their own rules. */
data class CarbonParams(
    /** Days into each quarter on which the permit auction is held. */
    val auctionDayOfQuarter: Int = 15,
    /** Weight of the newest day in the smoothed facility figures, ppm. */
    val smoothingPpm: Long = 100_000L,
    /** Weight of the newest month in the national grid intensity, ppm. */
    val gridSmoothingPpm: Long = 500_000L,
    val keepYears: Int = 6,
    val keepRecords: Int = 10,
    val keepAuctions: Int = 16,
    val keepTrades: Int = 60,
    val keepPrices: Int = 12,
    val minTrade: Tonnes = Tonnes.ofKg(1_000L),
    val maxOrdersPerCompany: Int = 6,
    val maxBidsPerCompany: Int = 4,
    val maxOffsetProjectsPerCompany: Int = 4,
    val maxLiveAbatementPerFacility: Int = 3,
    val minOffsetProject: Tonnes = Tonnes.of(100),
    val maxOffsetProject: Tonnes = Tonnes.of(200_000),
    /** Share of the money back when an offset project is rejected by the verifier. */
    val offsetRefundPpm: Long = 350_000L,
    /** Share of the money back when an abatement project is cancelled before it is finished. */
    val abatementRefundPpm: Long = 400_000L,
    /** Deviation of the open market's monthly credit supply from its norm, either way, ppm. */
    val marketSupplyJitterPpm: Long = 200_000L,
    /** Days of the year the AI sees as the horizon for appraising abatement. */
    val aiHorizonYears: Int = 5,
    /**
     * True when the production engine itself burns less in a facility with an efficiency retrofit, so the reported
     * emissions already fall and the measure must not be counted a second time when they are worked out.
     */
    val efficiencyRealisedInProduction: Boolean = false,
)

/** Spot credit prices and minimum holdings are tuned in [OffsetProfiles]; the thresholds an investor uses are in [AiCarbonTuning]. */
object CarbonDefaults {
    val STARTING_PRICE: Money = Money.units(20)
}
