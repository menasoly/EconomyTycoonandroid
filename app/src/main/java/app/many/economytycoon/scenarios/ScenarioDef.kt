package app.many.economytycoon.scenarios

import app.many.economytycoon.core.Money
import app.many.economytycoon.world.WorldSize

/** How well a scenario was played, from how many days the win took. */
enum class Medal(val label: String) { NONE("Not won"), BRONZE("Bronze"), SILVER("Silver"), GOLD("Gold") }

/**
 * A ready-made game with a point to it: a starting position, targets to reach and a time to reach them in.
 * Everything is data; the scenario service checks the targets as the game runs.
 */
data class ScenarioDef(
    val id: String,
    val title: String,
    val tagline: String,
    val story: String,
    val capital: Money,
    val aiCompanies: Int,
    val worldSize: WorldSize,
    val seed: Long,
    val countryIndex: Int,
    val goals: List<Goal>,
    /** The game is lost when the time runs out. */
    val years: Int,
    /** Winning within these many days earns the medal. */
    val goldDays: Int,
    val silverDays: Int,
    /** The game is lost if net worth falls below this (whole units), if set. */
    val ruinBelow: Long? = null,
    val tips: List<String> = emptyList(),
) {
    val deadlineDays: Int get() = years * 360
    val bronzeDays: Int get() = deadlineDays

    fun medalFor(daysTaken: Long): Medal = when {
        daysTaken <= goldDays -> Medal.GOLD
        daysTaken <= silverDays -> Medal.SILVER
        daysTaken <= bronzeDays -> Medal.BRONZE
        else -> Medal.NONE
    }

    /** Problems with the definition itself; empty for a sound one. */
    fun problems(): List<String> {
        val out = ArrayList<String>()
        if (id.isBlank() || !id.matches(Regex("[a-z0-9-]+"))) out += "id must be lowercase letters, digits and dashes"
        if (title.length !in 3..40) out += "title must be three to forty characters"
        if (goals.isEmpty()) out += "needs at least one goal"
        if (goals.map { it.kind }.distinct().size != goals.size) out += "a goal kind may appear only once"
        if (goals.any { it.kind == GoalKind.MARKET_LEADER && it.target != 1L }) out += "market leadership has a target of one"
        if (years < 1) out += "needs at least a year"
        if (goldDays <= 0 || silverDays < goldDays || deadlineDays < silverDays) out += "medal times must grow from gold to the deadline"
        if (capital.raw <= 0L) out += "needs starting capital"
        if (aiCompanies < 0) out += "rivals cannot be negative"
        return out
    }
}
