package app.many.economytycoon.ui.present

import app.many.economytycoon.scenarios.GoalProgress
import app.many.economytycoon.scenarios.LossReason
import app.many.economytycoon.scenarios.Medal
import app.many.economytycoon.scenarios.PlayerProfile
import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.scenarios.ScenarioDef
import app.many.economytycoon.scenarios.ScenarioStatus
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class ScenarioItem(
    val id: String,
    val title: String,
    val tagline: String,
    val locked: Boolean,
    val medal: Medal,
    val medalLabel: String,
    val record: String?,
    val tone: Tone,
)

data class ScenarioDetail(
    val id: String,
    val title: String,
    val story: String,
    val facts: List<Fact>,
    val goals: List<String>,
    val medals: String,
    val tips: List<String>,
)

data class ScenarioListModel(val items: List<ScenarioItem>, val detail: ScenarioDetail?, val stars: String, val note: String)

data class GoalLine(val text: String, val done: Boolean, val ratio: Double, val progress: String)

data class ScenarioStatusModel(
    val title: String,
    val statusLabel: String,
    val tone: Tone,
    val goals: List<GoalLine>,
    val timeLeft: String,
    val timeRatio: Double,
    val medalNote: String?,
)

object ScenariosPresenter {
    fun medalTone(m: Medal) = when (m) { Medal.GOLD -> Tone.GOOD; Medal.SILVER -> Tone.INFO; Medal.BRONZE -> Tone.WARN; Medal.NONE -> Tone.NEUTRAL }

    private fun deadlineText(d: ScenarioDef) = "${d.years} years"

    fun item(def: ScenarioDef, profile: PlayerProfile): ScenarioItem {
        val rec = profile.record(def.id)
        val medal = profile.medal(def.id)
        val locked = !profile.isUnlocked(def.id)
        val record = rec?.takeIf { it.attempted }?.let {
            val best = if (it.bestDays > 0L) "best ${Formatting.plural(it.bestDays, "day")}" else "not yet won"
            "$best · ${Formatting.plural(it.wins.toLong(), "win")}, ${Formatting.plural(it.losses.toLong(), "loss")}"
        }
        return ScenarioItem(def.id, def.title, if (locked) "Win the one before it to unlock." else def.tagline, locked, medal, medal.label, record, if (locked) Tone.NEUTRAL else medalTone(medal))
    }

    fun detail(def: ScenarioDef): ScenarioDetail = ScenarioDetail(
        def.id, def.title, def.story,
        listOf(
            Fact("Starting capital", Formatting.compactMoney(def.capital)), Fact("Rivals", def.aiCompanies.toString()),
            Fact("World", def.worldSize.name.lowercase().replaceFirstChar { it.uppercase() }), Fact("Time allowed", deadlineText(def)),
        ),
        def.goals.map { it.describe() },
        "Gold within ${Formatting.plural(def.goldDays.toLong(), "day")}, silver within ${def.silverDays}, bronze within ${def.bronzeDays}.",
        def.tips,
    )

    fun list(profile: PlayerProfile, selected: String?): ScenarioListModel {
        val items = ScenarioCatalog.all.map { item(it, profile) }
        val chosen = selected?.let { ScenarioCatalog.byId(it) }?.takeIf { profile.isUnlocked(it.id) }
        val max = ScenarioCatalog.all.size * 3
        return ScenarioListModel(items, chosen?.let { detail(it) }, "${profile.stars()} of $max stars", "Each scenario is a fixed world with a goal and a deadline. Win one to open the next.")
    }

    fun goalLine(session: GameSession, p: GoalProgress): GoalLine {
        val money = p.goal.kind.name.let { it == "NET_WORTH" || it == "CASH" || it == "MONTHLY_PROFIT" || it == "RENT_INCOME" || it == "INVESTED" }
        val value = if (money) Formatting.compactMoney(app.many.economytycoon.core.Money.units(p.value.coerceAtLeast(0L))) else Formatting.grouped(p.value.coerceAtLeast(0L))
        val target = if (money) Formatting.compactMoney(app.many.economytycoon.core.Money.units(p.goal.target)) else Formatting.grouped(p.goal.target)
        return GoalLine(p.goal.describe(), p.done, p.ppm / 1_000_000.0, if (p.goal.target == 1L && !money) (if (p.done) "Yes" else "Not yet") else "$value of $target")
    }

    /** The running scenario of a session, or null for a game without one. */
    fun status(session: GameSession): ScenarioStatusModel? {
        val e = session.economy
        val run = e.scenario.run ?: return null
        val def = e.scenario.def() ?: return null
        val goals = e.scenarioRunner.progress().map { goalLine(session, it) }
        val played = run.daysPlayed(session.tick)
        val left = (def.deadlineDays - played).coerceAtLeast(0L)
        val (label, tone) = when (run.status) {
            ScenarioStatus.ACTIVE -> (if (left <= 90L) "Running out of time" else "In progress") to (if (left <= 90L) Tone.WARN else Tone.INFO)
            ScenarioStatus.WON -> "Won with a ${run.medal.label.lowercase()} medal" to Tone.GOOD
            ScenarioStatus.LOST -> (run.reason?.label ?: "Lost") to Tone.BAD
        }
        val note = if (run.custom != null) "Made in the workshop, so it earns no medal." else if (run.isActive) {
            val m = def.medalFor(played)
            if (m == Medal.NONE) null else "A win now earns a ${m.label.lowercase()} medal."
        } else "You can keep playing as a sandbox."
        return ScenarioStatusModel(
            def.title, label, tone, goals,
            if (run.isActive) "${Formatting.plural(left, "day")} left" else "Finished after ${Formatting.plural(played, "day")}",
            (played.toDouble() / def.deadlineDays).coerceIn(0.0, 1.0), note,
        )
    }

    fun lossLabel(r: LossReason) = r.label
}
