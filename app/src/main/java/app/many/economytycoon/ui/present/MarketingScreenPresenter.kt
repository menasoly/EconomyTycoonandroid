package app.many.economytycoon.ui.present

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.marketing.CampaignId

/** The sections of the marketing screen. */
enum class MarketingTab(val label: String) {
    CAMPAIGNS("Campaigns"),
    CHANNELS("Channels"),
    PLAN("Budget plan"),
    RESULTS("Results"),
    ADVICE("Advice"),
}

data class MarketingAdviceItem(val code: String, val title: String, val detail: String, val tone: Tone)

data class MarketingAdviceModel(val items: List<MarketingAdviceItem>, val headline: String)

/** Turns the marketing advisor's findings into rows. */
object MarketingAdvicePresenter {
    fun headline(count: Int, top: Insight?): String = when {
        count == 0 -> "Your marketing looks healthy."
        count == 1 -> "One suggestion: ${top?.title}"
        else -> "$count suggestions. Most pressing: ${top?.title}"
    }

    fun build(insights: List<Insight>, limit: Int = 12): MarketingAdviceModel =
        MarketingAdviceModel(
            insights.take(limit).map { MarketingAdviceItem(it.code, it.title, it.detail, AdvisorPresenter.tone(it.priority)) },
            headline(insights.size, insights.firstOrNull()),
        )
}

/** Everything the marketing screen shows, built in one go by whoever owns the game session. */
data class MarketingScreenModel(
    val list: CampaignListModel,
    val details: Map<CampaignId, CampaignDetailModel>,
    val channels: ChannelComparisonModel?,
    val plan: OptimiserModel?,
    val results: RoiReportModel?,
    val advice: MarketingAdviceModel,
)

/** The bridge between the screen and the game: builds the model and carries out what the player asks for. */
interface MarketingScreenHost {
    fun build(): MarketingScreenModel?

    /** Carries out [action] and returns a short message for the player. */
    fun perform(action: CampaignAction): String

    /** Books the campaigns of the budget plan shown on the plan tab and returns a short message for the player. */
    fun applyPlan(): String = "Booking from the plan is not available here."
}
