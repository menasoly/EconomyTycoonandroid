package app.many.economytycoon.scenarios

import app.many.economytycoon.core.Money
import app.many.economytycoon.world.WorldSize

/** How well a scenario was played, from how many days the win took. */
enum class Medal(val label: String) { NONE("Not won"), BRONZE("Bronze"), SILVER("Silver"), GOLD("Gold") }

/** What a scripted event does when its day comes. */
enum class DefEventKind { GRANT, LEVY, RIVAL_GRANT, ANNOUNCE }

/** Something that happens on [day], counted from the day the scenario began. [amount] is in whole units. */
data class DefEvent(val day: Int, val kind: DefEventKind, val amount: Long = 0L, val text: String = "")

/** A way to lose beyond the deadline: the figure measured must not fall under [DefFail.threshold]. */
enum class DefFailKind(val reason: LossReason) {
    NET_WORTH_BELOW(LossReason.RUINED),
    CASH_BELOW(LossReason.OUT_OF_CASH),
    FACILITIES_BELOW(LossReason.TOO_FEW_SITES),
}

/** The condition is only checked from day [afterDay] on. */
data class DefFail(val kind: DefFailKind, val threshold: Long, val afterDay: Int = 0)

/** A site the player starts with: a recipe and the position of its region among the home country's regions. */
data class DefSite(val recipe: String, val regionIndex: Int = 0)

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
    /** Days that pass before [ruinBelow] is checked. */
    val ruinAfterDay: Int = 0,
    /** Scripted events, in day order. Only scenarios made in the workshop have any. */
    val events: List<DefEvent> = emptyList(),
    /** Further ways to lose, besides [ruinBelow]. */
    val fails: List<DefFail> = emptyList(),
    /** Sites the player's company begins with. */
    val starters: List<DefSite> = emptyList(),
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
        if (ruinAfterDay < 0) out += "the grace period cannot be negative"
        if (events.any { it.day < 0 || it.amount < 0L }) out += "events need a day and an amount of zero or more"
        if (events.zipWithNext().any { (a, b) -> b.day < a.day }) out += "events must be in day order"
        if (fails.any { it.afterDay < 0 }) out += "a failure condition cannot start before the first day"
        if (starters.any { it.recipe.isBlank() || it.regionIndex < 0 }) out += "a starting site needs a recipe and a region"
        return out
    }
}
