package app.many.economytycoon.session

import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.scenarios.ScenarioEvent
import app.many.economytycoon.simulation.Economy

/** The wording of scenario events in the news. */
object ScenarioNews {
    fun headline(e: Economy, ev: ScenarioEvent): String {
        val def = ev.let { event ->
            val id = when (event) { is ScenarioEvent.GoalReached -> event.scenario; is ScenarioEvent.Won -> event.scenario; is ScenarioEvent.Lost -> event.scenario }
            ScenarioCatalog.byId(id)
        }
        val title = def?.title ?: "Scenario"
        return when (ev) {
            is ScenarioEvent.GoalReached -> "$title: ${def?.goals?.getOrNull(ev.index)?.describe() ?: "goal"} - done"
            is ScenarioEvent.Won -> "$title won in ${Formatting.plural(ev.days, "day")} (${ev.medal.label} medal)"
            is ScenarioEvent.Lost -> "$title lost: ${ev.reason.label.replaceFirstChar { it.lowercase() }}"
        }
    }
}
