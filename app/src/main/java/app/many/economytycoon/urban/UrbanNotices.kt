package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent

enum class NoticeKind(val label: String, val important: Boolean) {
    PETITION_FILED("Petition filed", false),
    PETITION_HEARD("Hearing held", false),
    PETITION_APPROVED("Petition approved", true),
    PETITION_DENIED("Petition denied", true),
    PETITION_ENACTED("Zoning in force", true),
    LOBBYING_SCANDAL("Lobbying scandal", true),
    PROJECT_PHASE("Project phase done", false),
    PROJECT_STALLED("Project stalled", true),
    PROJECT_RESUMED("Project resumed", false),
    PROJECT_DONE("Project finished", true),
    CONCESSION_AWARDED("Concession awarded", true),
    CONCESSION_NO_BIDS("Concession drew no bids", true),
    CONCESSION_OPENED("Concession open", true),
    CONCESSION_ENDED("Concession handed back", false),
    EVENT("City event", true),
    ELECTION("Council election", true),
    BUDGET_CRISIS("Budget crisis", true),
    SERVICE_CUT("Services cut", true),
}

/** A line of news about a city, for the news feed and alerts. */
data class UrbanNotice(val city: CityId, val tick: Tick, val kind: NoticeKind, val text: String, val subject: Long = 0L)

/** What one update of the urban model produced. */
class UrbanStep(
    val notices: List<UrbanNotice>,
    /** Money movements for the ledger to settle. The urban model never posts to the ledger itself. */
    val intents: List<SettlementIntent>,
) {
    companion object {
        val EMPTY = UrbanStep(emptyList(), emptyList())
    }
}
