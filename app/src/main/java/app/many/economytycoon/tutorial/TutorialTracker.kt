package app.many.economytycoon.tutorial

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.persistence.SaveStore
import app.many.economytycoon.simulation.Economy

/** Reads the facts the tutorial needs from the live economy. */
object TutorialFactsReader {
    fun read(e: Economy, company: CompanyId, visited: Set<String>): TutorialFacts {
        val c = e.companies.company(company) ?: return TutorialFacts(day = e.tick.value, visited = visited)
        val facilities = e.production.facilitiesOf(company)
        val from = app.many.economytycoon.core.Tick((e.tick.value - 30L).coerceAtLeast(0L))
        val income = e.finance.statements.income(c.owner, from, e.tick)
        return TutorialFacts(
            day = e.tick.value,
            cash = e.finance.statements.balance(c.owner, e.tick).cash,
            facilities = facilities.size,
            runningFacilities = facilities.count { it.isOperating() && it.lines.isNotEmpty() },
            employees = e.workforce.employedBy(company),
            loans = e.finance.loans.activeFor(c.owner).size,
            activeContracts = e.contracts.activeOf(company).size,
            vehicles = e.logistics.vehiclesOf(company).size,
            techsUnlocked = e.research.peek(company)?.let { r -> e.content.allTechnologies.count { r.isUnlocked(it.id) } } ?: 0,
            listed = e.exchange.isListed(company),
            monthlyRevenue = income.revenue,
            monthlyProfit = income.netIncome,
            visited = visited,
        )
    }
}

/** Keeps a player's tutorial progress current as the game runs, and saves it when asked to. */
class TutorialTracker(
    private val engine: TutorialEngine = TutorialEngine(),
    private val store: SaveStore? = null,
    private val key: String = "tutorial.progress",
) {
    var progress: TutorialProgress = store?.get(key).let { TutorialProgress.fromBytes(it) }
        private set
    private val visited = LinkedHashSet<String>()

    val visitedScreens: Set<String> get() = visited

    fun noteVisit(route: String) { visited += route }

    fun update(facts: TutorialFacts): TutorialProgress {
        progress = engine.update(progress, facts.copy(visited = facts.visited + visited))
        persist()
        return progress
    }

    fun view(facts: TutorialFacts): TutorialView = engine.view(progress, facts.copy(visited = facts.visited + visited))

    fun acknowledge(id: String) { progress = engine.acknowledge(progress, id); persist() }
    fun skipCurrent() { progress = engine.skipCurrent(progress); persist() }
    fun dismiss() { progress = engine.dismiss(progress); persist() }
    fun restart() { progress = engine.restart(); persist() }

    private fun persist() { store?.put(key, progress.toBytes()) }
}
