package app.many.economytycoon.marketing

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

/** Display names for the things marketing refers to by id. */
interface MarketingNames {
    fun good(id: GoodId): String
    fun region(id: RegionId): String
    fun city(id: CityId): String
}

/** Names built from the ids alone, for tools and tests that have no world to ask. */
object PlainNames : MarketingNames {
    override fun good(id: GoodId): String = id.key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    override fun region(id: RegionId): String = "Region ${id.value}"
    override fun city(id: CityId): String = "City ${id.value}"
}

data class TestView(
    val id: AbTestId,
    val outcome: AbOutcome,
    val daysLeft: Int,
    val qualityA: Long,
    val qualityB: Long,
    val zHundredths: Long,
    val impressions: Long,
)

data class CampaignView(
    val id: CampaignId,
    val company: CompanyId,
    val title: String,
    val place: String,
    val channel: Channel,
    val segment: TargetSegment,
    val status: CampaignStatus,
    val dailyBudget: Money,
    val billPerDay: Money,
    val daysBooked: Int,
    val daysRun: Int,
    val daysRemaining: Int,
    val spentTotal: Money,
    val refundedTotal: Money,
    val prepaid: Money,
    val reachPpm: Long,
    val impressions: Long,
    val freshnessPpm: Long,
    val creativeQualityPpm: Long,
    val creativeVersion: Int,
    val awarenessPpm: Long,
    val loyaltyPpm: Long,
    val missedPayments: Int,
    val test: TestView?,
    val roi: RoiReport?,
    val cancelRefund: Money,
    val cancelFee: Money,
    val canRefresh: Boolean,
    val good: GoodId,
    val region: RegionId,
) {
    val netSpend: Money get() = spentTotal - refundedTotal
    val isLive: Boolean get() = status.isLive
}

data class BrandView(
    val key: BrandKey,
    val title: String,
    val awarenessPpm: Long,
    val loyaltyPpm: Long,
    val taintPpm: Long,
    val appealPpm: Long,
    val liveCampaigns: Int,
    val dailySpend: Money,
)

data class PromotionView(
    val id: PromotionId,
    val title: String,
    val status: PromotionStatus,
    val discountPpm: Long,
    val salePrice: Money,
    val listPrice: Money,
    val marginAfterPpm: Long,
    val daysLeft: Int,
    val upliftPpm: Long,
    val belowCost: Boolean,
)

data class DamageView(val title: String, val kind: DamageKind, val severityPpm: Long, val tick: Tick, val awarenessLostPpm: Long)

data class MarketingOverview(
    val company: CompanyId,
    val tick: Tick,
    val campaigns: List<CampaignView>,
    val brands: List<BrandView>,
    val promotions: List<PromotionView>,
    val damages: List<DamageView>,
) {
    val live: List<CampaignView> get() = campaigns.filter { it.isLive }
    val dailyCommitment: Money get() = Money.sum(campaigns.filter { it.status == CampaignStatus.ACTIVE }.map { it.billPerDay })
    val totalNetSpend: Money get() = Money.sum(campaigns.map { it.netSpend })
}

/** Builds read-only views of the marketing state. Nothing here changes the state. */
class MarketingReadModel(
    private val state: MarketingState,
    private val service: CampaignService,
    private val names: MarketingNames = PlainNames,
    private val roiOf: (Campaign) -> RoiReport? = { null },
) {
    private val params: MarketingParams get() = service.params

    fun overview(company: CompanyId, tick: Tick): MarketingOverview {
        val campaigns = state.campaignsOf(company).sortedWith(compareBy<Campaign>({ !it.isLive }, { it.id })).map { campaign(it, tick) }
        val brands = state.stocksOf(company).map { (key, stock) -> brand(key, stock) }
        val promotions = state.promotions().filter { it.company == company }.map { promotion(it, tick) }
        val damages = state.damagesOf(company).map {
            DamageView(describe(it.good, it.region), it.kind, it.severityPpm, it.tick, it.awarenessLostPpm)
        }
        return MarketingOverview(company, tick, campaigns, brands, promotions, damages)
    }

    fun campaign(c: Campaign, tick: Tick): CampaignView {
        val stock = state.stock(c.key)
        val test = state.testFor(c.id)
        val (refund, fee) = service.cancellationTerms(c)
        val last = c.creative.lastRefreshed
        return CampaignView(
            id = c.id, company = c.company, title = "${c.channel.label}: ${names.good(c.good)}", place = placeOf(c),
            channel = c.channel, segment = c.segment, status = c.status, dailyBudget = c.dailyBudget, billPerDay = service.billFor(c),
            daysBooked = c.daysBooked, daysRun = c.daysRun, daysRemaining = c.daysRemaining, spentTotal = c.spentTotal,
            refundedTotal = c.refundedTotal, prepaid = c.prepaid, reachPpm = c.lastReachPpm, impressions = c.impressions,
            freshnessPpm = c.creative.freshnessPpm, creativeQualityPpm = c.creative.qualityPpm, creativeVersion = c.creative.version,
            awarenessPpm = stock?.awarenessPpm ?: 0L, loyaltyPpm = stock?.loyaltyPpm ?: 0L, missedPayments = c.missedPayments,
            test = test?.let { TestView(it.id, it.outcome, (it.endTick - tick).toInt().coerceAtLeast(0), it.qualityA, it.qualityB, it.zHundredths, it.impressions) },
            roi = roiOf(c), cancelRefund = if (c.isLive) refund else Money.ZERO, cancelFee = if (c.isLive) fee else Money.ZERO,
            canRefresh = c.isLive && (last == null || tick - last >= params.creativeRefreshCooldown.toLong()),
            good = c.good, region = c.region,
        )
    }

    private fun placeOf(c: Campaign): String = c.city?.let { names.city(it) + ", " + names.region(c.region) } ?: names.region(c.region)

    private fun describe(good: GoodId?, region: RegionId?): String =
        listOfNotNull(good?.let { names.good(it) } ?: "All products", region?.let { names.region(it) } ?: "everywhere").joinToString(" in ")

    fun brand(key: BrandKey, stock: BrandStock): BrandView {
        val live = state.campaignsFor(key).filter { it.status == CampaignStatus.ACTIVE }
        return BrandView(
            key, "${names.good(key.good)} in ${names.region(key.region)}", stock.awarenessPpm, stock.loyaltyPpm, stock.taintPpm,
            MarketingEffects.appealPpm(stock, params), live.size, Money.sum(live.map { it.dailyBudget }),
        )
    }

    fun promotion(p: Promotion, tick: Tick): PromotionView {
        val after = PromotionPlanner.marginPpm(p.salePrice, p.unitCost)
        val left = (p.endTick - tick).toInt().coerceAtLeast(0)
        return PromotionView(
            p.id, "${names.good(p.good)} in ${names.region(p.region)}", p.status, p.discountPpm, p.salePrice, p.listPrice, after,
            if (p.status == PromotionStatus.RUNNING || p.status == PromotionStatus.SCHEDULED) left else 0, p.expectedUpliftPpm, p.salePrice <= p.unitCost,
        )
    }

    /** The plain-number facts the marketing advisor reads, for one company. */
    fun facts(
        company: CompanyId, tick: Tick, cash: Money, monthRevenue: Money, monthMarketing: Money,
        revenueShare: (BrandKey) -> Long = { 0L },
    ): MarketingFacts {
        val ov = overview(company, tick)
        val campaigns = ov.campaigns.filter { it.status == CampaignStatus.ACTIVE || it.status == CampaignStatus.SUSPENDED || it.status == CampaignStatus.PAUSED }.map {
            CampaignFact(
                it.id.value, "${names.good(it.good)} in ${names.region(it.region)} (${it.channel.label})", it.channel, it.billPerDay.wholeUnits(),
                it.reachPpm, it.freshnessPpm, it.daysRemaining, it.awarenessPpm, it.loyaltyPpm, it.status == CampaignStatus.ACTIVE,
                it.roi?.roiPpm, it.status == CampaignStatus.SUSPENDED,
            )
        }
        val brands = ov.brands.map { b ->
            val live = state.campaignsFor(b.key).filter { it.status == CampaignStatus.ACTIVE }
            val voice = Competition.voice(state, b.key.good, b.key.region)
            val rival = Competition.loudestRival(voice, company)
            BrandFact(
                b.title, b.awarenessPpm, b.loyaltyPpm, b.taintPpm, live.isNotEmpty(),
                live.any { it.channel == Channel.LOYALTY_PROGRAMME || it.channel == Channel.SPONSORSHIP },
                revenueShare(b.key), voice.of(company)?.sharePpm ?: 0L, rival?.sharePpm ?: 0L, live.map { it.channel }.distinct().size,
                b.dailySpend.wholeUnits(),
            )
        }
        val promotions = ov.promotions.filter { it.status == PromotionStatus.RUNNING }.map { PromotionFact(it.title, it.marginAfterPpm, it.daysLeft, it.belowCost) }
        return MarketingFacts(tick.value, cash.wholeUnits(), monthRevenue.wholeUnits(), monthMarketing.wholeUnits(), campaigns, brands, promotions)
    }
}
