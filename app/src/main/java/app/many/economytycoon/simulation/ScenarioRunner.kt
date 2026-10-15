package app.many.economytycoon.simulation

import app.many.economytycoon.core.Tick
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
        if (!run.isActive || tick.value % CHECK_EVERY != 0L) return emptyList()
        val def = e.scenario.def() ?: return emptyList()
        val out = ArrayList<ScenarioEvent>()
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
        loss(run, def.ruinBelow, def.deadlineDays, tick)?.let { reason ->
            e.scenario.lose(run, tick, reason)
            out += ScenarioEvent.Lost(run.id, reason, tick)
        }
        return out
    }

    private fun loss(run: ScenarioRun, ruinBelow: Long?, deadlineDays: Int, tick: Tick): LossReason? {
        val mine = GoalMeter.playerCompanies(e, run.playerId)
        return when {
            mine.isEmpty() -> LossReason.NO_COMPANY
            ruinBelow != null && mine.all { GoalMeter.netWorthUnits(e, it) < ruinBelow } -> LossReason.RUINED
            tick.value - run.startedAt.value >= deadlineDays -> LossReason.DEADLINE
            else -> null
        }
    }

    companion object { const val CHECK_EVERY = 5L }
}
