package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.MarketingProposal

/**
 * Computer-run companies review their advertising on their own weekly day. The planner proposes; each proposal is turned
 * into the command a player would send, so the executor checks cash, limits and ownership the same way.
 */
class AiMarketing(private val e: Economy, private val executor: CommandExecutor) {
    class Report(var proposals: Int = 0, var applied: Int = 0, var rejected: Int = 0)

    private val positions = MarketingPositions(e)

    fun run(tick: Tick): Report {
        val report = Report()
        val due = e.companies.all()
            .filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE && e.marketing.ai.isReviewDay(it.id, tick) }
            .sortedBy { it.id }
        if (due.isEmpty()) return report
        val sellers = positions.sellerCounts()
        val views = due.mapNotNull { positions.view(it.id, tick, sellers) }
        for (p in e.marketing.ai.decide(tick, views, e.marketing.state, e.marketingRunner.audiences)) {
            report.proposals++
            if (executor.execute(Actor.Ai(p.company), command(p)) is Outcome.Ok) report.applied++ else report.rejected++
        }
        return report
    }

    fun command(p: MarketingProposal): Command = when (p) {
        is MarketingProposal.Launch -> p.request.let {
            Command.CreateCampaign(it.company, it.good, it.region, it.city, it.channel, it.segment, it.dailyBudget, it.days, it.tier, it.startTick)
        }
        is MarketingProposal.SetBudget -> Command.SetCampaignBudget(p.company, p.campaign, p.dailyBudget)
        is MarketingProposal.Extend -> Command.ExtendCampaign(p.company, p.campaign, p.days)
        is MarketingProposal.Refresh -> Command.RefreshCreative(p.company, p.campaign, p.tier)
        is MarketingProposal.Pause -> Command.PauseCampaign(p.company, p.campaign)
        is MarketingProposal.Resume -> Command.ResumeCampaign(p.company, p.campaign)
        is MarketingProposal.Cancel -> Command.CancelCampaign(p.company, p.campaign)
    }
}
