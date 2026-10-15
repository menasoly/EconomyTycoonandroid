package app.many.economytycoon.ui.present

import app.many.economytycoon.session.Alert
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.NewsKind
import app.many.economytycoon.session.ObjectiveProgress
import app.many.economytycoon.session.Objectives
import app.many.economytycoon.session.Severity
import app.many.economytycoon.ui.nav.Route

data class AlertItem(val key: String, val title: String, val detail: String, val tone: Tone, val severity: Severity, val route: Route?)

data class NewsLine(val whenText: String, val kind: NewsKind, val kindLabel: String, val headline: String, val tone: Tone)

data class GoalItem(val id: String, val title: String, val description: String, val progress: Double, val progressText: String, val done: Boolean)

data class GoalGroup(val name: String, val goals: List<GoalItem>, val doneCount: Int)

object FeedPresenter {
    /** The screen an alert is about, so tapping it goes there. */
    fun routeFor(alert: Alert): Route? = when (alert.code.substringBefore('.')) {
        "facility" -> if (alert.subject > 0) Route.FacilityDetail(alert.subject) else Route.Facilities
        "cash", "loan", "distress", "insolvency" -> Route.Finance
        "wages", "strike", "morale", "vacancies" -> Route.People
        "research" -> Route.Research
        "offer" -> Route.Rivals
        "contract" -> Route.Contracts
        "portfolio" -> Route.Exchange
        "insurance" -> Route.Insurance
        "tender" -> Route.Tenders
        "law" -> Route.Government
        "bond" -> Route.Bonds
        "union" -> Route.Unions
        "futures" -> Route.Futures
        "property" -> Route.Property
        "fine" -> if (alert.subject > 0) Route.FacilityDetail(alert.subject) else Route.Facilities
        else -> null
    }

    fun alerts(session: GameSession): List<AlertItem> = session.alerts().map {
        AlertItem("${it.code}:${it.subject}", it.title, it.detail, OverviewPresenter.severityTone(it.severity), it.severity, routeFor(it))
    }

    fun kindLabel(k: NewsKind): String = when (k) {
        NewsKind.WORLD -> "World"
        NewsKind.MARKET -> "Markets"
        NewsKind.COMPANY -> "Companies"
        NewsKind.RESEARCH -> "Research"
        NewsKind.FINANCE -> "Finance"
    }

    fun kindTone(k: NewsKind): Tone = when (k) {
        NewsKind.WORLD -> Tone.INFO
        NewsKind.MARKET -> Tone.NEUTRAL
        NewsKind.COMPANY -> Tone.NEUTRAL
        NewsKind.RESEARCH -> Tone.GOOD
        NewsKind.FINANCE -> Tone.WARN
    }

    fun news(session: GameSession, only: NewsKind? = null, limit: Int = 60): List<NewsLine> =
        session.news.items().filter { only == null || it.kind == only }.take(limit).map {
            NewsLine(Formatting.date(app.many.economytycoon.core.Tick(it.tick)), it.kind, kindLabel(it.kind), it.headline, kindTone(it.kind))
        }

    fun goals(session: GameSession): List<GoalGroup> {
        val company = session.selectedCompany ?: return emptyList()
        val progress = Objectives.evaluate(session.economy, company)
        return Objectives.groups().map { group ->
            val items = progress.filter { it.objective.group == group }.map(::goal)
            GoalGroup(group, items, items.count { it.done })
        }
    }

    fun goal(p: ObjectiveProgress) = GoalItem(
        p.objective.id, p.objective.title, p.objective.description, p.ppm / 1_000_000.0,
        if (p.done) "Done" else "${Formatting.grouped(p.value.coerceAtLeast(0L))} of ${Formatting.grouped(p.target)}", p.done,
    )
}
