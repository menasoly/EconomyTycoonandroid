package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.marketing.CreativeTier
import app.many.economytycoon.marketing.OptimiserResult
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.simulation.Command

/**
 * Connects the marketing screen to a running game: builds what it shows from the selected company and turns what the
 * player taps into the same commands every other part of the game uses.
 */
class SessionMarketingHost(private val session: GameSession) : MarketingScreenHost {

    override fun build(): MarketingScreenModel? {
        val company = session.selectedCompany ?: return null
        val reader = session.marketing()
        val money: (Money) -> String = session.settings::money
        val overview = session.marketingOverview(company)
        val details = overview.campaigns.associate { it.id to MarketingPresenter.detail(it, money) }
        val channels = reader.channelReturns(company)?.let { (title, rows) -> ChannelComparisonPresenter.build(title, rows, money) }
        val plan = reader.optimise(company)?.let { OptimiserPresenter.build(it, reader.names, money) }
        val results = reader.results(company)?.let { (report, byChannel) -> RoiReportPresenter.build(report, byChannel, money) }
        return MarketingScreenModel(
            list = MarketingPresenter.list(overview, money),
            details = details,
            channels = channels,
            plan = plan,
            results = results,
            advice = MarketingAdvicePresenter.build(session.marketingInsights()),
        )
    }

    override fun perform(action: CampaignAction): String {
        val company = session.selectedCompany ?: return "Choose a company first."
        val command: Command = when (action) {
            is CampaignAction.Pause -> Command.PauseCampaign(company, action.campaign)
            is CampaignAction.Resume -> Command.ResumeCampaign(company, action.campaign)
            is CampaignAction.Extend -> Command.ExtendCampaign(company, action.campaign, action.days)
            is CampaignAction.Cancel -> Command.CancelCampaign(company, action.campaign)
            is CampaignAction.RefreshCreative -> Command.RefreshCreative(company, action.campaign, CreativeTier.PROFESSIONAL)
        }
        return when (val r = session.issueNow(command)) {
            is Outcome.Ok -> r.value.message.replaceFirstChar { it.uppercase() }.ifBlank { "Done." }
            is Outcome.Rejected -> r.rejection.message
        }
    }

    override fun applyPlan(): String {
        val company = session.selectedCompany ?: return "Choose a company first."
        val plan: OptimiserResult = session.marketing().optimise(company) ?: return "There is nothing to plan: no product of yours is sold to households."
        if (plan.allocations.isEmpty()) return "The plan found nothing worth booking."
        val contexts = session.marketing().contexts(company).associateBy { it.good to it.region }
        var booked = 0
        var refusal: String? = null
        for (a in plan.allocations) {
            val ctx = contexts[a.market.good to a.market.region] ?: continue
            val budget = Money.min(Money.max(a.dailyBudget, a.channel.spec.minDailySpend), a.channel.spec.maxDailySpend)
            val tier = if (budget >= Money.units(PROFESSIONAL_FROM_UNITS)) CreativeTier.PROFESSIONAL else CreativeTier.BASIC
            val command = Command.CreateCampaign(company, a.market.good, a.market.region, null, a.channel, ctx.segment, budget, PLAN_DAYS, tier, null)
            when (val r = session.issueNow(command)) {
                is Outcome.Ok -> booked++
                is Outcome.Rejected -> if (refusal == null) refusal = r.rejection.message
            }
        }
        return when {
            booked > 0 && refusal == null -> "Booked ${campaigns(booked)}."
            booked > 0 -> "Booked ${campaigns(booked)}; the others were refused: $refusal"
            else -> refusal ?: "Nothing could be booked."
        }
    }

    private fun campaigns(n: Int): String = app.many.economytycoon.session.Formatting.plural(n.toLong(), "campaign")

    private companion object {
        const val PLAN_DAYS = 30
        const val PROFESSIONAL_FROM_UNITS = 200L
    }
}
