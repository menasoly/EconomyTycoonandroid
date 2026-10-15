package app.many.economytycoon.guide

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.simulation.Economy

/** Where in the app a step is done. The text form matches a route key so the screen can open it. */
enum class GuideArea(val routeKey: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    BUILD("facilities", "Facilities"),
    PEOPLE("people", "People"),
    MARKET("market", "Market"),
    RULES("rules", "Automation"),
    RESEARCH("research", "Research"),
    FINANCE("finance", "Finance"),
    LOANS("loans", "Loans"),
}

/**
 * One thing a new player is asked to do. Whether it has been done is read from the game, never stored, so a
 * loaded save shows the right progress and a step cannot be ticked off without really doing it.
 */
class GuideStep(
    val id: String,
    val title: String,
    /** Why this matters, in a sentence or two. */
    val why: String,
    /** What to press, in the order to press it. */
    val how: String,
    val area: GuideArea,
    private val done: (Economy, CompanyId) -> Boolean,
) {
    fun isDone(e: Economy, company: CompanyId): Boolean = e.companies.company(company)?.status == app.many.economytycoon.companies.CompanyStatus.ACTIVE && done(e, company)
}

enum class StepState { DONE, CURRENT, LATER }

/** A step together with where it stands for one company. */
data class StepStatus(val step: GuideStep, val state: StepState)
