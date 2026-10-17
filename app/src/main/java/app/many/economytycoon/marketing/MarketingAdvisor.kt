package app.many.economytycoon.marketing

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.advisor.Topic

/** What the advisor knows about one running campaign. Money is in whole units, ratios in ppm. */
data class CampaignFact(
    val id: Long,
    val label: String,
    val channel: Channel,
    val dailyUnits: Long,
    val reachPpm: Long,
    val freshnessPpm: Long,
    val daysRemaining: Int,
    val awarenessPpm: Long,
    val loyaltyPpm: Long,
    val running: Boolean,
    /** Return on spend from the latest review, ppm of spend (below zero loses money); null when not yet measured. */
    val roiPpm: Long?,
    val suspended: Boolean = false,
)

/** What the advisor knows about one product the company sells somewhere. */
data class BrandFact(
    val label: String,
    val awarenessPpm: Long,
    val loyaltyPpm: Long,
    val taintPpm: Long,
    val advertised: Boolean,
    val loyaltyChannel: Boolean,
    /** Share of the company's revenue this product earns there. */
    val revenueSharePpm: Long,
    val ownVoicePpm: Long,
    val loudestRivalVoicePpm: Long,
    val channelsUsed: Int,
    val dailyUnits: Long,
)

data class PromotionFact(val label: String, val marginAfterPpm: Long, val daysLeft: Int, val belowCost: Boolean)

/** Everything the marketing rules look at, as plain numbers. */
data class MarketingFacts(
    val tick: Long = 0L,
    val cashUnits: Long = 0L,
    val monthRevenueUnits: Long = 0L,
    val monthMarketingUnits: Long = 0L,
    val campaigns: List<CampaignFact> = emptyList(),
    val brands: List<BrandFact> = emptyList(),
    val promotions: List<PromotionFact> = emptyList(),
) {
    val dailyCommitmentUnits: Long get() = campaigns.filter { it.running }.sumOf { it.dailyUnits }
}

data class MarketingTuning(
    val saturatedAwarenessPpm: Long = 850_000L,
    val lowReachPpm: Long = 60_000L,
    val lowReachMinDailyUnits: Long = 200L,
    val wornFreshnessPpm: Long = 350_000L,
    val endingSoonDays: Int = 3,
    val losingRoiPpm: Long = -300_000L,
    val unadvertisedRevenueSharePpm: Long = 150_000L,
    val unknownAwarenessPpm: Long = 250_000L,
    val loyaltyGapAwarenessPpm: Long = 600_000L,
    val loyaltyGapPpm: Long = 250_000L,
    val outshoutedGapPpm: Long = 250_000L,
    val damagedTaintPpm: Long = 200_000L,
    val overspendShareOfRevenuePpm: Long = 150_000L,
    val cashCoverDays: Long = 10L,
    val thinPromotionMarginPpm: Long = 100_000L,
    val oneChannelMinDailyUnits: Long = 500L,
)

/** The marketing rules, in the shape of the main advisor: each returns suggestions, or nothing when all is well. */
object MarketingRules {
    private fun pct(ppm: Long) = "${ppm / 10_000L}%"
    private fun one(i: Insight?): List<Insight> = if (i == null) emptyList() else listOf(i)

    val saturated = MarketingRule { f, t ->
        f.campaigns.filter { it.running && it.awarenessPpm >= t.saturatedAwarenessPpm && it.channel.spec.awarenessWeightPpm > it.channel.spec.loyaltyWeightPpm }
            .map {
                Insight("mkt-saturated-${it.id}", Topic.MARKET, 55, "Awareness is saturated for ${it.label}",
                    "${pct(it.awarenessPpm)} of buyers already know the brand, so more ${it.channel.label.lowercase()} money buys almost nothing. Trim it, or spend on a loyalty programme instead.")
            }
    }

    val lowReach = MarketingRule { f, t ->
        f.campaigns.filter { it.running && it.dailyUnits >= t.lowReachMinDailyUnits && it.reachPpm < t.lowReachPpm }
            .map {
                Insight("mkt-low-reach-${it.id}", Topic.MARKET, 60, "Spend is wasted on a low-reach channel: ${it.label}",
                    "${it.channel.label} reaches only ${pct(it.reachPpm)} of the audience for ${it.dailyUnits} a day. Move the money to a channel with a better reach for the price.")
            }
    }

    val wornCreative = MarketingRule { f, t ->
        f.campaigns.filter { it.running && it.freshnessPpm < t.wornFreshnessPpm }
            .map {
                Insight("mkt-worn-${it.id}", Topic.MARKET, 50, "The creative for ${it.label} has worn out",
                    "People have seen it too often and it works at a fraction of its strength. A fresh creative costs a few days of budget and restores the full effect.")
            }
    }

    val endingSoon = MarketingRule { f, t ->
        f.campaigns.filter { it.running && it.daysRemaining in 1..t.endingSoonDays && (it.roiPpm ?: 0L) > 0L }
            .map {
                Insight("mkt-ending-${it.id}", Topic.MARKET, 35, "${it.label} ends in ${it.daysRemaining} days",
                    "It has been paying its way. Awareness fades within weeks of the money stopping, so extend it if you want to keep what it built.")
            }
    }

    val losing = MarketingRule { f, t ->
        f.campaigns.filter { it.running && (it.roiPpm ?: 0L) < t.losingRoiPpm }
            .map {
                Insight("mkt-losing-${it.id}", Topic.MARKET, 65, "${it.label} costs more than it earns",
                    "The extra profit it brought is well under what it spends. Cut the budget, change channel, or stop it.")
            }
    }

    val unadvertised = MarketingRule { f, t ->
        val b = f.brands.filter { !it.advertised && it.revenueSharePpm >= t.unadvertisedRevenueSharePpm && it.awarenessPpm < t.unknownAwarenessPpm }
            .maxByOrNull { it.revenueSharePpm }
        one(b?.let { Insight("mkt-unadvertised", Topic.MARKET, 45, "${it.label} is a big seller nobody has heard of", "It earns ${pct(it.revenueSharePpm)} of your revenue with only ${pct(it.awarenessPpm)} awareness. A modest campaign should pay back quickly.") })
    }

    val loyaltyGap = MarketingRule { f, t ->
        f.brands.filter { it.awarenessPpm >= t.loyaltyGapAwarenessPpm && it.loyaltyPpm < t.loyaltyGapPpm && !it.loyaltyChannel }
            .take(1).map {
                Insight("mkt-loyalty-gap", Topic.MARKET, 40, "${it.label} is known but not loved",
                    "Most buyers have heard of it but only ${pct(it.loyaltyPpm)} come back. A loyalty programme or sponsorship builds the repeat custom that awareness alone does not.")
            }
    }

    val outshouted = MarketingRule { f, t ->
        f.brands.filter { it.loudestRivalVoicePpm - it.ownVoicePpm >= t.outshoutedGapPpm && it.advertised }
            .map {
                Insight("mkt-outshouted-${it.label}", Topic.MARKET, 52, "A rival is outshouting you with ${it.label}",
                    "They hold ${pct(it.loudestRivalVoicePpm)} of the advertising pressure to your ${pct(it.ownVoicePpm)}. Every message you send is blunted until you match them or pick another place to compete.")
            }
    }

    val damaged = MarketingRule { f, t ->
        f.brands.filter { it.taintPpm >= t.damagedTaintPpm }.take(1).map {
            Insight("mkt-damaged", Topic.MARKET, 58, "The brand is still marked by bad news: ${it.label}",
                "Distrust of ${pct(it.taintPpm)} blunts new advertising until it fades. Rebuild quietly with steady, modest campaigns rather than a big push.")
        }
    }

    val overspend = MarketingRule { f, t ->
        one(if (f.monthRevenueUnits > 0L && f.monthMarketingUnits * Ppm.ONE / f.monthRevenueUnits >= t.overspendShareOfRevenuePpm)
            Insight("mkt-overspend", Topic.MARKET, 62, "Marketing takes a large share of revenue",
                "Marketing cost ${pct(f.monthMarketingUnits * Ppm.ONE / f.monthRevenueUnits)} of last month's revenue. Review which campaigns pay back and drop the rest.") else null)
    }

    val thinCash = MarketingRule { f, t ->
        val daily = f.dailyCommitmentUnits
        one(if (daily > 0L && f.cashUnits < daily * t.cashCoverDays)
            Insight("mkt-cash-cover", Topic.FINANCE, 72, "Cash will not cover the campaigns for long",
                "Campaigns commit $daily a day and the bank holds ${f.cashUnits}. Unpaid campaigns are suspended and lose their momentum, so pause the weakest ones.") else null)
    }

    val suspended = MarketingRule { f, _ ->
        val n = f.campaigns.count { it.suspended }
        one(if (n > 0) Insight("mkt-suspended", Topic.MARKET, 70, "$n campaign${if (n == 1) " was" else "s were"} suspended for non-payment",
            "Bills went unpaid, so the agency stopped the campaigns. Restore cash before booking them again.") else null)
    }

    val oneChannel = MarketingRule { f, t ->
        f.brands.filter { it.advertised && it.channelsUsed == 1 && it.dailyUnits >= t.oneChannelMinDailyUnits }.take(1).map {
            Insight("mkt-one-channel", Topic.MARKET, 30, "All the money for ${it.label} rides on one channel",
                "Each channel reaches only part of the audience and its reach flattens as you spend. A second channel adds people the first one misses.")
        }
    }

    val thinPromotion = MarketingRule { f, t ->
        f.promotions.filter { it.marginAfterPpm < t.thinPromotionMarginPpm }.take(1).map {
            Insight("mkt-promo-margin", Topic.MARKET, if (it.belowCost) 68 else 48, "The sale on ${it.label} leaves ${if (it.belowCost) "a loss on every unit" else "very little margin"}",
                "Margin during the promotion is ${pct(it.marginAfterPpm)} with ${it.daysLeft} days to run. It must lift volume a lot to pay for itself.")
        }
    }

    val all: List<MarketingRule> = listOf(
        saturated, lowReach, wornCreative, endingSoon, losing, unadvertised, loyaltyGap, outshouted, damaged, overspend,
        thinCash, suspended, oneChannel, thinPromotion,
    )
}

fun interface MarketingRule {
    fun check(f: MarketingFacts, t: MarketingTuning): List<Insight>
}

/** Runs the marketing rules and orders what they find, most pressing first. */
class MarketingAdvisor(private val rules: List<MarketingRule> = MarketingRules.all, private val tuning: MarketingTuning = MarketingTuning()) {
    fun advise(facts: MarketingFacts, limit: Int = Int.MAX_VALUE): List<Insight> =
        rules.flatMap { it.check(facts, tuning) }
            .sortedWith(compareByDescending<Insight> { it.priority }.thenBy { it.code })
            .take(limit)
}
