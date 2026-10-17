package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

/** Things that happened in marketing, for the news feed and alerts. Named in the past tense. */
sealed class MarketingEvent {
    abstract val tick: Tick
    abstract val company: CompanyId

    data class CampaignStarted(val campaign: CampaignId, override val company: CompanyId, override val tick: Tick) : MarketingEvent()
    data class CampaignFinished(val campaign: CampaignId, override val company: CompanyId, val spent: Money, override val tick: Tick) : MarketingEvent()
    data class CampaignCancelled(val campaign: CampaignId, override val company: CompanyId, val refund: Money, val fee: Money, override val tick: Tick) : MarketingEvent()
    data class CampaignSuspended(val campaign: CampaignId, override val company: CompanyId, override val tick: Tick) : MarketingEvent()
    data class CampaignAutoEnded(val campaign: CampaignId, override val company: CompanyId, val refund: Money, override val tick: Tick) : MarketingEvent()
    data class CreativeWornOut(val campaign: CampaignId, override val company: CompanyId, val freshnessPpm: Long, override val tick: Tick) : MarketingEvent()
    data class PromotionStarted(val promotion: PromotionId, override val company: CompanyId, override val tick: Tick) : MarketingEvent()
    data class PromotionEnded(val promotion: PromotionId, override val company: CompanyId, override val tick: Tick) : MarketingEvent()
    data class TestDecided(val test: AbTestId, val campaign: CampaignId, override val company: CompanyId, val outcome: AbOutcome, override val tick: Tick) : MarketingEvent()
    data class BrandDamaged(val damage: DamageId, override val company: CompanyId, val kind: DamageKind, val awarenessLostPpm: Long, override val tick: Tick) : MarketingEvent()
}

/** Everything one day of marketing produced: payments for the finance layer to post and events to report. */
data class DayReport(
    val tick: Tick,
    val intents: List<app.many.economytycoon.finance.SettlementIntent>,
    val events: List<MarketingEvent>,
    val spendByCompany: Map<CompanyId, Money>,
)
