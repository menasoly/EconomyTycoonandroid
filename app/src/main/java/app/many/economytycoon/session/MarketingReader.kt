package app.many.economytycoon.session

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.marketing.BrandKey
import app.many.economytycoon.marketing.BudgetOptimiser
import app.many.economytycoon.marketing.Campaign
import app.many.economytycoon.marketing.Channel
import app.many.economytycoon.marketing.ChannelReturn
import app.many.economytycoon.marketing.MarketContext
import app.many.economytycoon.marketing.MarketingNames
import app.many.economytycoon.marketing.MarketingOverview
import app.many.economytycoon.marketing.ModelledLift
import app.many.economytycoon.marketing.OptimiserConstraints
import app.many.economytycoon.marketing.OptimiserResult
import app.many.economytycoon.marketing.RoiAnalysis
import app.many.economytycoon.marketing.RoiReport
import app.many.economytycoon.simulation.Economy
import app.many.economytycoon.simulation.MarketingPositions

/**
 * Reads the live game into what the marketing screen shows: the company's campaigns and brands, what each campaign has
 * earned, the planners' proposals and the advisor's suggestions. Nothing here changes the game.
 */
class MarketingReader(private val e: Economy) {
    private val positions = MarketingPositions(e)

    val names: MarketingNames = object : MarketingNames {
        override fun good(id: GoodId): String = e.content.goodOrNull(id)?.name ?: id.key.replace('_', ' ')
        override fun region(id: RegionId): String = e.world.regions().firstOrNull { it.id == id }?.name ?: "region ${id.value}"
        override fun city(id: CityId): String = e.world.cityOrNull(id)?.name ?: "city ${id.value}"
    }

    private fun income(owner: LedgerOwner, from: Long, to: Long) = e.finance.statements.income(owner, Tick(from.coerceAtLeast(0L)), Tick(to.coerceAtLeast(0L)))

    /** Gross profit as a share of sales over the last month, or a typical margin before there are any sales. */
    private fun marginPpm(owner: LedgerOwner): Long {
        val now = e.tick.value
        val month = income(owner, now - 30L, now)
        return if (month.revenue.raw <= 0L) DEFAULT_MARGIN_PPM else mulDiv(month.grossProfit.raw, 1_000_000L, month.revenue.raw).coerceIn(0L, 1_000_000L)
    }

    /**
     * What a campaign has earned, judged by the company's sales: the daily average while it ran against the daily average
     * in the month before it began. Sales of the one product are not booked separately, so this is a company-level estimate.
     */
    fun roiOf(c: Campaign): RoiReport? {
        if (c.daysRun < 1) return null
        val owner = LedgerOwner.company(c.company)
        val start = c.startTick.value
        val from = (start - 30L).coerceAtLeast(0L)
        if (start - from <= 0L) return null
        val baseline = Money(income(owner, from, start).salesRevenue.raw / (start - from))
        val days = c.daysRun
        val during = income(owner, start, start + days).salesRevenue
        val revenue = List(days) { Money(during.raw / days) }
        val spend = List(days) { Money(c.netSpend.raw / days) }
        return RoiAnalysis.analyse(spend, revenue, baseline, marginPpm(owner))
    }

    fun overview(company: CompanyId): MarketingOverview = e.marketing.readModel(names, ::roiOf).overview(company, e.tick)

    /** The marketing advisor's suggestions for a company. */
    fun insights(company: CompanyId): List<Insight> {
        val c = e.companies.company(company) ?: return emptyList()
        // The rules only speak about campaigns and brands that exist, so a company that never advertised has nothing to read.
        if (e.marketing.state.campaignsOf(company).isEmpty() && e.marketing.state.stocksOf(company).isEmpty()) return emptyList()
        val now = e.tick.value
        val month = income(c.owner, now - 30L, now)
        val sold = positions.positions(company, e.tick)
        val total = sold.sumOf { it.context.baseRevenue.raw }
        val shares = sold.associate { BrandKey(company, it.context.good, it.context.region) to (if (total <= 0L) 0L else mulDiv(it.context.baseRevenue.raw, 1_000_000L, total)) }
        val facts = e.marketing.readModel(names, ::roiOf).facts(company, e.tick, e.finance.ledger.cash(c.owner), month.revenue, month.marketing) { shares[it] ?: 0L }
        return e.marketing.advisor.advise(facts)
    }

    /** The products the company sells to households, as the planners see them. */
    fun contexts(company: CompanyId): List<MarketContext> = positions.positions(company, e.tick).map { it.context }

    /** Daily money the plan works with: the marketing department budget, or a modest amount from sales when it is unset. */
    fun planBudget(company: CompanyId): Money {
        val c = e.companies.company(company) ?: return Money.ZERO
        val dept = e.companies.budgets(company).budget(app.many.economytycoon.companies.Department.MARKETING)
        if (dept.raw > 0L) return dept
        val now = e.tick.value
        val month = income(c.owner, now - 30L, now)
        return Money.max(Money.units(DEFAULT_PLAN_UNITS), Money(mulDiv(month.revenue.raw, PLAN_SHARE_PPM, 1_000_000L) / 30L))
    }

    fun optimise(company: CompanyId): OptimiserResult? {
        val contexts = contexts(company)
        if (contexts.isEmpty()) return null
        val budget = planBudget(company)
        val step = Money(maxOf(budget.raw / 12L, Money.units(10).raw))
        return BudgetOptimiser(e.marketing.params).optimise(contexts, OptimiserConstraints(budget, step = step, maxChannelSharePpm = 800_000L))
    }

    /** Each channel run alone at a typical spend, for the first product the company sells. */
    fun channelReturns(company: CompanyId): Pair<String, List<ChannelReturn>>? {
        val ctx = contexts(company).firstOrNull() ?: return null
        val spend = Money.max(Money.units(100), planBudget(company))
        return "${names.good(ctx.good)} in ${names.region(ctx.region)}" to ModelledLift.compareChannels(ctx, spend, e.marketing.params)
    }

    /** The review of the company's longest-running campaign that has results, with the spend split by channel. */
    fun results(company: CompanyId): Pair<RoiReport, Map<Channel, Money>>? {
        val mine = e.marketing.state.campaignsOf(company)
        val best = mine.filter { it.daysRun > 0 }.sortedWith(compareByDescending<Campaign> { it.daysRun }.thenBy { it.id }).firstNotNullOfOrNull { c -> roiOf(c) }
            ?: return null
        return best to RoiAnalysis.spendByChannel(mine)
    }

    private companion object {
        const val DEFAULT_MARGIN_PPM = 300_000L
        const val DEFAULT_PLAN_UNITS = 300L
        const val PLAN_SHARE_PPM = 30_000L
    }
}
