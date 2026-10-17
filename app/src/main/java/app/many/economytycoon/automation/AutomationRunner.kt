package app.many.economytycoon.automation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.simulation.Economy

/** What a manager did, for the player to read afterwards. */
data class ManagerAction(val day: Long, val kind: ManagerKind, val note: String)

/**
 * Decides, each day, which managers are due and what they would do. It never touches the economy itself: it returns
 * commands, and the caller issues them through the ordinary command path, so everything a manager does is
 * recorded, validated and replayed like the player's own actions.
 */
class AutomationRunner(private val e: Economy, private val logSize: Int = 100) {
    private val lastRun = HashMap<Pair<Long, ManagerKind>, Long>()
    private val log = ArrayDeque<ManagerAction>()

    fun recent(): List<ManagerAction> = log.toList().reversed()

    fun due(kind: ManagerKind, company: CompanyId, day: Long): Boolean {
        val last = lastRun[company.value to kind] ?: return true
        return day - last >= kind.everyDays
    }

    /** Commands for every enabled manager that is due today. */
    fun plan(company: CompanyId, settings: ManagerSettings, day: Long): List<Planned> {
        if (!settings.anyOn || e.companies.company(company)?.isActive != true) return emptyList()
        val ctx = AutomationContext(e, company, settings)
        val out = ArrayList<Planned>()
        for (kind in ManagerKind.values()) {
            if (!settings.isOn(kind) || !due(kind, company, day)) continue
            lastRun[company.value to kind] = day
            out += Managers.plan(kind, ctx)
        }
        return out
    }

    fun record(day: Long, planned: Planned) {
        log.addLast(ManagerAction(day, planned.kind, planned.note))
        while (log.size > logSize) log.removeFirst()
    }

    fun forget(company: CompanyId) { lastRun.keys.removeAll { it.first == company.value } }
}
