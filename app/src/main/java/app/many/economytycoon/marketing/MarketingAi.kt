package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** One product a computer-run company sells somewhere, as it sees it. */
data class AiPosition(val context: MarketContext, val actualRevenue: Money)

data class AiCompanyView(
    val company: CompanyId,
    val cash: Money,
    /** Revenue over the last 30 days. */
    val monthRevenue: Money,
    val positions: List<AiPosition>,
    /** Return on spend of each live campaign from the latest review, ppm of spend; absent when not measured. */
    val roiPpm: Map<CampaignId, Long> = emptyMap(),
)

/** What a computer-run company wants done. The executor turns each into the matching command. */
sealed class MarketingProposal {
    abstract val company: CompanyId

    data class Launch(val request: CampaignRequest, val reason: String) : MarketingProposal() {
        override val company: CompanyId get() = request.company
    }
    data class SetBudget(override val company: CompanyId, val campaign: CampaignId, val dailyBudget: Money, val reason: String) : MarketingProposal()
    data class Extend(override val company: CompanyId, val campaign: CampaignId, val days: Int, val reason: String) : MarketingProposal()
    data class Refresh(override val company: CompanyId, val campaign: CampaignId, val tier: CreativeTier, val reason: String) : MarketingProposal()
    data class Pause(override val company: CompanyId, val campaign: CampaignId, val reason: String) : MarketingProposal()
    data class Resume(override val company: CompanyId, val campaign: CampaignId, val reason: String) : MarketingProposal()
    data class Cancel(override val company: CompanyId, val campaign: CampaignId, val reason: String) : MarketingProposal()
}

data class MarketingAiTuning(
    /** How often each company reviews its marketing, in days. Companies are staggered so they do not all act on one day. */
    val reviewEvery: Int = 7,
    /** Share of monthly revenue a timid and a bold company will put into marketing, ppm. */
    val minSpendShareOfRevenuePpm: Long = 15_000L,
    val maxSpendShareOfRevenuePpm: Long = 70_000L,
    /** The budget never exceeds this many days of cash. */
    val cashDaysPerDailyBudget: Long = 20L,
    val campaignDays: Int = 30,
    val maxLaunchesPerReview: Int = 3,
    /** A campaign is resized when the plan differs from its budget by more than this. */
    val resizeBandPpm: Long = 250_000L,
    val brokeBelowDailyBudgetDays: Long = 6L,
    val worstFreshnessPpm: Long = 330_000L,
    val minRoiToExtendPpm: Long = 0L,
    val dropRoiPpm: Long = -400_000L,
    val damagedTaintPpm: Long = 300_000L,
    val counterResponsePpm: Long = 600_000L,
)

/**
 * The marketing mind of a computer-run company. It sets a daily budget from revenue and cash and its own boldness,
 * has the optimiser split it over the products it sells, and keeps what is running healthy: resizing, refreshing worn
 * creatives, extending what pays and dropping what does not. It reads the situation and returns proposals; it never
 * changes anything itself.
 */
class MarketingAi(
    private val params: MarketingParams = MarketingParams(),
    private val tuning: MarketingAiTuning = MarketingAiTuning(),
    private val worldSeed: Long = 0L,
) {
    private val optimiser = BudgetOptimiser(params)

    /** Boldness of a company, fixed for the whole game: 0 is timid, one million is reckless. */
    fun boldnessPpm(company: CompanyId): Long =
        DeterministicRandom(RandomProvider.deriveSeed(worldSeed, "marketing.ai", company.value)).nextLong(Ppm.ONE)

    fun isReviewDay(company: CompanyId, tick: Tick): Boolean = (tick.value + company.value) % tuning.reviewEvery.toLong() == 0L

    /** The daily budget the company is willing to spend on all marketing. */
    fun dailyBudget(view: AiCompanyView): Money {
        val bold = boldnessPpm(view.company)
        val share = tuning.minSpendShareOfRevenuePpm + mulDiv(tuning.maxSpendShareOfRevenuePpm - tuning.minSpendShareOfRevenuePpm, bold, Ppm.ONE)
        val fromRevenue = Money(mulDiv(view.monthRevenue.raw, share, Ppm.ONE) / 30L)
        val fromCash = Money(view.cash.raw / tuning.cashDaysPerDailyBudget)
        return Money.min(fromRevenue, fromCash).coerceAtLeast(Money.ZERO)
    }

    fun decide(tick: Tick, views: List<AiCompanyView>, state: MarketingState, audiences: AudienceSource): List<MarketingProposal> {
        val out = ArrayList<MarketingProposal>()
        for (view in views.sortedBy { it.company }) {
            if (!isReviewDay(view.company, tick)) continue
            out += decideFor(tick, view, state, audiences)
        }
        return out
    }

    fun decideFor(tick: Tick, view: AiCompanyView, state: MarketingState, audiences: AudienceSource): List<MarketingProposal> {
        val out = ArrayList<MarketingProposal>()
        val budget = dailyBudget(view)
        val live = state.liveCampaignsOf(view.company)
        val committed = Money.sum(live.filter { it.status == CampaignStatus.ACTIVE }.map { it.dailyBudget })

        out += maintain(tick, view, live, committed, budget)
        if (budget.raw <= 0L || view.positions.isEmpty()) return out
        val dropped = out.filterIsInstance<MarketingProposal.Cancel>().map { it.campaign }.toSet()
        val keep = live.filter { it.id !in dropped }
        val stillCommitted = Money.sum(keep.filter { it.status == CampaignStatus.ACTIVE }.map { it.dailyBudget })
        val room = budget - stillCommitted
        if (room.raw <= 0L) return out

        val damaged = view.positions.any { it.context.taintPpm >= tuning.damagedTaintPpm }
        val contexts = view.positions.map { it.context }
        val step = Money(maxOf(room.raw / 12L, Money.units(10).raw))
        val plan = optimiser.optimise(contexts, OptimiserConstraints(room, step = step, maxChannelSharePpm = 800_000L))
        var launches = 0
        for (a in plan.allocations) {
            if (launches >= tuning.maxLaunchesPerReview) break
            if (damaged && a.dailyBudget > Money(room.raw / 3L)) continue
            val taken = keep.any { it.good == a.market.good && it.region == a.market.region && it.channel == a.channel && it.city == null }
            if (taken) continue
            val ctx = contexts.first { it.good == a.market.good && it.region == a.market.region }
            val request = CampaignRequest(
                view.company, a.market.good, a.market.region, null, a.channel, ctx.segment,
                clampBudget(a.channel, a.dailyBudget), tuning.campaignDays, tierFor(view.company, a.dailyBudget),
            )
            out += MarketingProposal.Launch(request, "Planned return ${a.lastReturnPpm / 10_000L}% on the last step")
            launches++
        }
        if (launches < tuning.maxLaunchesPerReview) out += counters(view, keep, room, state, audiences, tuning.maxLaunchesPerReview - launches)
        return out
    }

    private fun clampBudget(ch: Channel, m: Money): Money = Money.min(Money.max(m, ch.spec.minDailySpend), ch.spec.maxDailySpend)

    private fun tierFor(company: CompanyId, daily: Money): CreativeTier = when {
        daily >= Money.units(2_000) && boldnessPpm(company) > 400_000L -> CreativeTier.STUDIO
        daily >= Money.units(200) -> CreativeTier.PROFESSIONAL
        else -> CreativeTier.BASIC
    }

    /** Upkeep of what is already booked. */
    private fun maintain(tick: Tick, view: AiCompanyView, live: List<Campaign>, committed: Money, budget: Money): List<MarketingProposal> {
        val out = ArrayList<MarketingProposal>()
        val broke = view.cash < committed * tuning.brokeBelowDailyBudgetDays
        var running = committed
        for (c in live) {
            val roi = view.roiPpm[c.id]
            val daily = c.dailyBudget
            when {
                roi != null && roi < tuning.dropRoiPpm && c.daysRun >= RoiAnalysis.MIN_DAYS ->
                    out += MarketingProposal.Cancel(view.company, c.id, "It loses money")
                broke && c.status == CampaignStatus.ACTIVE && running > budget -> {
                    out += MarketingProposal.Pause(view.company, c.id, "Cash is short for the bills ahead")
                    running = running - daily
                }
                c.status == CampaignStatus.PAUSED && !broke && view.cash > daily * 10L ->
                    out += MarketingProposal.Resume(view.company, c.id, "Cash is back")
                c.status == CampaignStatus.ACTIVE && c.creative.freshnessPpm < tuning.worstFreshnessPpm && view.cash > daily * 8L &&
                    (c.creative.lastRefreshed?.let { tick - it >= params.creativeRefreshCooldown.toLong() } ?: true) ->
                    out += MarketingProposal.Refresh(view.company, c.id, CreativeTier.PROFESSIONAL, "The creative has worn out")
                c.status == CampaignStatus.ACTIVE && c.daysRemaining in 1..3 && roi != null && roi >= tuning.minRoiToExtendPpm &&
                    view.cash > daily * tuning.campaignDays.toLong() ->
                    out += MarketingProposal.Extend(view.company, c.id, tuning.campaignDays, "It is paying back")
            }
        }
        val limit = budget + Money(mulDiv(budget.raw, tuning.resizeBandPpm, Ppm.ONE))
        if (running > limit) {
            val biggest = live.filter { it.status == CampaignStatus.ACTIVE }.maxWithOrNull(compareBy<Campaign> { it.dailyBudget }.thenByDescending { it.id })
            if (biggest != null) {
                val target = clampBudget(biggest.channel, biggest.dailyBudget - (running - budget))
                if (target < biggest.dailyBudget) out += MarketingProposal.SetBudget(view.company, biggest.id, target, "The plan has shrunk")
            }
        }
        return out
    }

    private fun counters(
        view: AiCompanyView, keep: List<Campaign>, room: Money, state: MarketingState, audiences: AudienceSource, allowed: Int,
    ): List<MarketingProposal> {
        val out = ArrayList<MarketingProposal>()
        for (p in view.positions) {
            if (out.size >= allowed) break
            val ctx = p.context
            val proposal = Competition.counter(state, view.company, ctx.good, ctx.region, ctx.audience, params, room, tuning.counterResponsePpm) ?: continue
            if (keep.any { it.good == ctx.good && it.region == ctx.region && it.channel == proposal.channel }) continue
            val budget = clampBudget(proposal.channel, proposal.dailyBudget)
            if (budget > room) continue
            out += MarketingProposal.Launch(
                CampaignRequest(view.company, ctx.good, ctx.region, null, proposal.channel, ctx.segment, budget, tuning.campaignDays, CreativeTier.PROFESSIONAL),
                proposal.reason,
            )
        }
        return out
    }
}
