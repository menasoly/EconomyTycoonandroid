package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money

/** Every tunable number of the marketing model, so rules never hide constants. Rates are per day in ppm. */
data class MarketingParams(
    /** Share of the unaware that full exposure converts to aware each day. */
    val awarenessLearnPpm: Long = 55_000L,
    /** Share of awareness forgotten each day. */
    val awarenessDecayPpm: Long = 7_000L,
    val loyaltyLearnPpm: Long = 28_000L,
    val loyaltyDecayPpm: Long = 2_500L,
    /** Extra loyalty pull per day from delivered quality above or below expectations (1.0 = as expected). */
    val qualityLoyaltyPpm: Long = 4_000L,

    /** Demand a brand with total awareness gains over one with none. */
    val awarenessDemandLiftPpm: Long = 300_000L,
    /** Demand a fully loyal, fully known brand gains on top of that. */
    val loyaltyDemandLiftPpm: Long = 220_000L,
    /** How much the whole category grows when every buyer has heard of some brand. */
    val categoryExpansionPpm: Long = 120_000L,

    /** Floor of the wear-out multiplier: a creative nobody notices any more still works a little. */
    val wornEffectFloorPpm: Long = 300_000L,
    /** Strength of an exposure under the best and worst creative: [1 - spread, 1 + spread] around the middle. */
    val creativeSpreadPpm: Long = 500_000L,
    /** Cost of a media rate in a region with average income is one; richer regions pay more, by this share of the gap. */
    val incomeCostPassThroughPpm: Long = 400_000L,
    /** Strength of share of voice: effect multiplier is `floor + slope * share`. */
    val voiceFloorPpm: Long = 700_000L,
    val voiceSlopePpm: Long = 400_000L,

    val maxCampaignsPerCompany: Int = 24,
    val maxCampaignDays: Int = 360,
    val minCampaignDays: Int = 3,
    /** Campaigns booked for at least this many days get [longBookingDiscountPpm] off every day's bill. */
    val longBookingDays: Int = 60,
    val longBookingDiscountPpm: Long = 60_000L,
    /** Cancelling before this many days have run doubles the fee. */
    val earlyCancelDays: Int = 7,
    val creativeRefreshDays: Int = 3,
    val creativeRefreshCooldown: Int = 20,
    /** Days a campaign may stay paused before it is ended. */
    val maxPauseDays: Int = 45,
    /** Unpaid days in a row that suspend a campaign. */
    val missedPaymentsToSuspend: Int = 3,

    /** Brand-damage recovery: share of the lingering taint that fades each day. */
    val taintRecoveryPpm: Long = 12_000L,
    /** How strongly lingering taint blunts new exposure. */
    val taintBluntPpm: Long = 700_000L,

    val minPromotionDays: Int = 2,
    val maxPromotionDays: Int = 30,
    val maxDiscountPpm: Long = 500_000L,
    val minMarginAfterDiscountPpm: Long = 50_000L,
    /** Share of promotional volume that was only pulled forward and is lost again afterwards. */
    val pullForwardPpm: Long = 250_000L,
    val promotionCooldownDays: Int = 14,
    /** Loyalty lost per day of promotion at the full discount: shoppers learn to wait for a sale. */
    val promotionFatiguePpm: Long = 6_000L,

    val abMinDays: Int = 5,
    val abMaxDays: Int = 30,
    /** z-score (in hundredths) the gap must reach for a winner to be declared. */
    val abConfidenceHundredths: Long = 164L,
    /** Responses needed across both arms before a verdict means anything. */
    val abMinResponses: Long = 30L,

    val keepEndedDays: Int = 90,
    val keepEventsDays: Int = 180,
) {
    init {
        require(awarenessLearnPpm in 1..Ppm.ONE && awarenessDecayPpm in 0..Ppm.ONE) { "awareness rates out of range" }
        require(minCampaignDays in 1..maxCampaignDays) { "campaign length limits out of order" }
        require(maxDiscountPpm in 1..Ppm.ONE) { "discount ceiling out of range" }
    }

    /** The smallest sensible refresh bill for a campaign spending [daily] a day. */
    fun refreshCost(daily: Money): Money = daily * creativeRefreshDays.toLong()
}
