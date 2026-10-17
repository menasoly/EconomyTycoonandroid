package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.scenarios.DefEvent
import app.many.economytycoon.scenarios.DefEventKind
import app.many.economytycoon.scenarios.DefFailKind
import app.many.economytycoon.scenarios.ScenarioDef
import app.many.economytycoon.scenarios.GoalMeter
import app.many.economytycoon.scenarios.GoalProgress
import app.many.economytycoon.scenarios.LossReason
import app.many.economytycoon.scenarios.ScenarioEvent
import app.many.economytycoon.scenarios.ScenarioRun

/** Checks the running scenario's targets and deadline every few days and reports what changed. */
class ScenarioRunner(private val e: Economy) {
    fun progress(): List<GoalProgress> {
        val run = e.scenario.run ?: return emptyList()
        val def = e.scenario.def() ?: return emptyList()
        return GoalMeter.progress(e, run.playerId, def.goals)
    }

    fun run(tick: Tick): List<ScenarioEvent> {
        val run = e.scenario.run ?: return emptyList()
        if (!run.isActive) return emptyList()
        val def = e.scenario.def() ?: return emptyList()
        val out = ArrayList<ScenarioEvent>()
        if (def.events.isNotEmpty()) scripted(run, def, tick, out)
        if (tick.value % CHECK_EVERY != 0L) return out
        val progress = GoalMeter.progress(e, run.playerId, def.goals)
        for ((i, p) in progress.withIndex()) {
            if (p.done && !run.reached(i)) { e.scenario.markReached(run, i); out += ScenarioEvent.GoalReached(run.id, i, tick) }
        }
        if (progress.all { it.done }) {
            val days = (tick.value - run.startedAt.value).coerceAtLeast(0L)
            val medal = def.medalFor(days)
            e.scenario.win(run, tick, medal)
            out += ScenarioEvent.Won(run.id, days, medal, tick)
            return out
        }
        loss(run, def, tick)?.let { reason ->
            e.scenario.lose(run, tick, reason)
            out += ScenarioEvent.Lost(run.id, reason, tick)
        }
        return out
    }

    private fun loss(run: ScenarioRun, def: ScenarioDef, tick: Tick): LossReason? {
        val mine = GoalMeter.playerCompanies(e, run.playerId)
        val day = tick.value - run.startedAt.value
        val ruinBelow = def.ruinBelow
        if (mine.isEmpty()) return LossReason.NO_COMPANY
        if (ruinBelow != null && day >= def.ruinAfterDay && mine.all { GoalMeter.netWorthUnits(e, it) < ruinBelow }) return LossReason.RUINED
        for (f in def.fails) {
            if (day < f.afterDay) continue
            val hit = when (f.kind) {
                DefFailKind.NET_WORTH_BELOW -> mine.all { GoalMeter.netWorthUnits(e, it) < f.threshold }
                DefFailKind.CASH_BELOW -> mine.sumOf { e.finance.ledger.cash(e.companies.require(it).owner).wholeUnits() } < f.threshold
                DefFailKind.FACILITIES_BELOW -> mine.sumOf { e.production.facilitiesOf(it).size.toLong() } < f.threshold
            }
            if (hit) return f.kind.reason
        }
        return if (day >= def.deadlineDays) LossReason.DEADLINE else null
    }

    /** Carries out the events whose day has come, on the first company the player has. */
    private fun scripted(run: ScenarioRun, def: ScenarioDef, tick: Tick, out: MutableList<ScenarioEvent>) {
        val day = tick.value - run.startedAt.value
        while (run.eventsDone < def.events.size && def.events[run.eventsDone].day <= day) {
            val index = run.eventsDone
            run.restoreEvents(index + 1)
            val text = apply(run, def.events[index], index)
            if (text.isNotBlank()) out += ScenarioEvent.Scripted(run.id, text, tick)
        }
    }

    private fun apply(run: ScenarioRun, ev: DefEvent, index: Int): String {
        val company = GoalMeter.playerCompanies(e, run.playerId).firstOrNull()
        val units = Money.units(ev.amount)
        val note = ev.text.trim()
        return when (ev.kind) {
            DefEventKind.ANNOUNCE -> note
            DefEventKind.GRANT -> {
                if (company == null || units.raw <= 0L) return note
                e.finance.mint(e.tick, e.companies.require(company).owner, units, "scenario:grant:$index")
                note.ifEmpty { "A grant of ${ev.amount} arrives." }
            }
            DefEventKind.LEVY -> {
                if (company == null) return note
                val owner = e.companies.require(company).owner
                val take = Money.min(units, e.finance.ledger.cash(owner))
                if (take.raw > 0L) {
                    val gov = e.governmentOfCompany(company)
                    e.finance.ledger.post(
                        TransactionDraft(
                            "scenario:levy:$index", e.tick, ReasonCode.FEE,
                            listOf(Entry(AccountRef(owner, Chart.CASH), -take), Entry(AccountRef(gov, Chart.CASH), take)),
                            "scripted levy",
                        ),
                    )
                }
                note.ifEmpty { "A levy of ${take.wholeUnits()} is taken." }
            }
            DefEventKind.RIVAL_GRANT -> {
                if (units.raw <= 0L) return note
                for (r in e.companies.ofKind(CompanyKind.AI).sortedBy { it.id }) e.finance.mint(e.tick, r.owner, units, "scenario:rivals:$index:${r.id.value}")
                note.ifEmpty { "Rivals receive ${ev.amount} each." }
            }
        }
    }

    companion object { const val CHECK_EVERY = 5L }
}
