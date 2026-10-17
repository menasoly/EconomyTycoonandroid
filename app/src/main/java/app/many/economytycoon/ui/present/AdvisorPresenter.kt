package app.many.economytycoon.ui.present

import app.many.economytycoon.advisor.Advisor
import app.many.economytycoon.advisor.AdvisorFacts
import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.advisor.Topic
import app.many.economytycoon.ui.nav.Route

data class AdviceItem(val code: String, val topic: String, val title: String, val detail: String, val tone: Tone, val route: Route?, val actionLabel: String?)

data class AdviceModel(val items: List<AdviceItem>, val hidden: Int, val headline: String)

object AdvisorPresenter {
    /** Where the player goes to act on a suggestion. */
    fun routeFor(code: String): Route? = when (code) {
        "no-facilities" -> Route.Facilities
        "loss-making", "thin-margin", "tight-liquidity", "idle-cash", "expensive-debt" -> Route.Finance
        "go-public" -> Route.Exchange
        "idle-facility", "blocked-facility", "worn-facility" -> Route.Facilities
        "overstock" -> Route.Orders
        "understaffed", "low-morale", "underpaid", "heavy-payroll" -> Route.People
        "idle-research" -> Route.Research
        "concentration" -> Route.Facilities
        "idle-land" -> Route.Property
        "contract-offers" -> Route.Contracts
        else -> if (code.startsWith("mkt-")) Route.Marketing else if (code.startsWith("carbon.")) Route.Carbon else if (code.startsWith("ip-")) Route.Ip else if (code.startsWith("tourism.")) Route.Tourism else null
    }

    private fun actionLabel(route: Route?): String? = when (route) {
        null -> null
        Route.Facilities -> "Open facilities"
        Route.Finance -> "Open finance"
        Route.Exchange -> "Open the exchange"
        Route.Orders -> "Open orders"
        Route.People -> "Open people"
        Route.Research -> "Open research"
        Route.Property -> "Open property"
        Route.Contracts -> "Open contracts"
        Route.Marketing -> "Open marketing"
        Route.Carbon -> "Open carbon"
        Route.Ip -> "Open patents and secrets"
        Route.Tourism -> "Open tourism"
        else -> "Open"
    }

    fun tone(priority: Int): Tone = when {
        priority >= 70 -> Tone.BAD
        priority >= 50 -> Tone.WARN
        priority >= 35 -> Tone.INFO
        else -> Tone.NEUTRAL
    }

    fun item(i: Insight): AdviceItem {
        val r = routeFor(i.code)
        return AdviceItem(i.code, i.topic.label, i.title, i.detail, tone(i.priority), r, actionLabel(r))
    }

    fun headline(count: Int, top: Insight?): String = when {
        count == 0 -> "Nothing needs your attention."
        count == 1 -> "One suggestion: ${top?.title}"
        else -> "$count suggestions. Most pressing: ${top?.title}"
    }

    fun build(advisor: Advisor, facts: AdvisorFacts, limit: Int = 12, extra: List<Insight> = emptyList()): AdviceModel {
        val byPriority = compareByDescending<Insight> { it.priority }.thenBy { it.code }
        val more = extra.filter { !advisor.isHidden(it.code, facts.tick) }
        val shown = (advisor.advise(facts) + more).sortedWith(byPriority)
        val hidden = advisor.all(facts).size + extra.size - shown.size
        return AdviceModel(shown.take(limit).map { item(it) }, hidden, headline(shown.size, shown.firstOrNull()))
    }

    /** Suggestions for the selected company of a running game; null before one is chosen. */
    fun build(session: app.many.economytycoon.session.GameSession, limit: Int = 12): AdviceModel? =
        session.adviceFacts()?.let { build(session.advisor, it, limit, session.marketingInsights() + session.carbonInsights() + session.ipInsights() + session.tourismInsights()) }

    fun topics(): List<String> = Topic.values().map { it.label }
}
