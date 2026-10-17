package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent

/** A split test that has been started and what it costs. */
data class TestLaunch(val test: AbTest, val intents: List<SettlementIntent>)

/**
 * Starts and cancels split tests of a campaign's creative. The test is played out by [MarketingEngine] on its last
 * day, so the verdict depends only on the game seed and the test id.
 */
class ExperimentService(private val service: CampaignService) {
    private val state: MarketingState get() = service.state
    private val params: MarketingParams get() = service.params

    fun start(campaign: CampaignId, challenger: CreativeTier, days: Int, tick: Tick, available: Money): Outcome<TestLaunch> {
        val c = state.campaign(campaign) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown campaign")
        if (c.status != CampaignStatus.ACTIVE && c.status != CampaignStatus.SCHEDULED) return Outcome.reject(Rules.WRONG_STATE, "only a running campaign can be tested")
        if (days < params.abMinDays || days > params.abMaxDays) {
            return Outcome.reject(Rules.INVALID_ARGUMENT, "a test runs between ${params.abMinDays} and ${params.abMaxDays} days")
        }
        if (days > c.daysRemaining) return Outcome.reject(Rules.INVALID_ARGUMENT, "the campaign ends before the test would")
        if (state.testFor(campaign)?.isRunning == true) return Outcome.reject(Rules.DUPLICATE, "a test is already running on this campaign")
        if (challenger.qualityPpm == c.creative.qualityPpm) return Outcome.reject(Rules.INVALID_ARGUMENT, "the alternative must differ from the current creative")
        val fee = c.dailyBudget * (challenger.feeDays + 1).toLong()
        if (fee > available) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "a split test costs $fee")
        val test = AbTest(state.ids.test(), campaign, c.creative.qualityPpm, challenger.qualityPpm, tick, days)
        state.testMap[test.id] = test
        c.spentTotal = c.spentTotal + fee
        return Outcome.ok(TestLaunch(test, listOfNotNull(service.ledger.splitTest(c, fee, tick))))
    }

    fun cancel(id: AbTestId, tick: Tick): Outcome<AbTest> {
        val t = state.test(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown test")
        if (!t.isRunning) return Outcome.reject(Rules.WRONG_STATE, "that test is over")
        t.outcome = AbOutcome.CANCELLED
        t.decidedAt = tick
        return Outcome.ok(t)
    }

    /** How long a test needs to separate the two creatives, from the campaign's daily reach. */
    fun daysNeeded(c: Campaign, challenger: CreativeTier, audiences: AudienceSource): Int? {
        val audience = audiences.audience(c.region, c.city) ?: return null
        val reach = ReachModel.reachPpm(c.channel.spec, c.dailyBudget, audience, c.segment, params)
        val perDay = audience.segmentSize(c.segment) * reach / Ppm.ONE
        if (perDay <= 0L) return null
        val need = AbTesting.impressionsNeeded(c.creative.qualityPpm, challenger.qualityPpm, params)
        if (need >= Long.MAX_VALUE / 4) return null
        return ((need * 2L + perDay - 1L) / perDay).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
