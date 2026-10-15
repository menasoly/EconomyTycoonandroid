package app.many.economytycoon.tutorial

import app.many.economytycoon.core.Money

/** What the tutorial can see of the game: a plain snapshot, so lessons can be checked without a running game. */
data class TutorialFacts(
    val day: Long = 0L,
    val cash: Money = Money.ZERO,
    val facilities: Int = 0,
    val runningFacilities: Int = 0,
    val employees: Long = 0L,
    val loans: Int = 0,
    val activeContracts: Int = 0,
    val vehicles: Int = 0,
    val techsUnlocked: Int = 0,
    val listed: Boolean = false,
    val monthlyRevenue: Money = Money.ZERO,
    val monthlyProfit: Money = Money.ZERO,
    /** Route keys of screens the player has opened. */
    val visited: Set<String> = emptySet(),
)

/** A number in [TutorialFacts] a lesson can ask about. */
enum class Gauge(val label: String) {
    DAY("days played"),
    FACILITIES("facilities"),
    RUNNING("running facilities"),
    EMPLOYEES("employees"),
    LOANS("loans"),
    CONTRACTS("active contracts"),
    VEHICLES("vehicles"),
    TECHS("technologies"),
    CASH("cash"),
    REVENUE("monthly revenue"),
    PROFIT("monthly profit"),
    ;

    fun read(f: TutorialFacts): Long = when (this) {
        DAY -> f.day
        FACILITIES -> f.facilities.toLong()
        RUNNING -> f.runningFacilities.toLong()
        EMPLOYEES -> f.employees
        LOANS -> f.loans.toLong()
        CONTRACTS -> f.activeContracts.toLong()
        VEHICLES -> f.vehicles.toLong()
        TECHS -> f.techsUnlocked.toLong()
        CASH -> f.cash.wholeUnits()
        REVENUE -> f.monthlyRevenue.wholeUnits()
        PROFIT -> f.monthlyProfit.wholeUnits()
    }
}

/** How a lesson knows the player has done what it asked. */
sealed class Goal {
    /** The player taps to say they have read it. */
    object Acknowledge : Goal()

    /** The player opens the screen with this route key. */
    data class Visit(val route: String) : Goal()

    /** A gauge reaches a value. */
    data class Reach(val gauge: Gauge, val atLeast: Long) : Goal()

    /** The company is listed on the exchange. */
    object Listed : Goal()

    /** Every one of these. */
    data class All(val goals: List<Goal>) : Goal()

    fun met(f: TutorialFacts, acknowledged: Boolean = false): Boolean = when (this) {
        Acknowledge -> acknowledged
        is Visit -> route in f.visited
        is Reach -> gauge.read(f) >= atLeast
        Listed -> f.listed
        is All -> goals.all { it.met(f, acknowledged) }
    }

    /** How far along, in ppm, for goals that count; others are all or nothing. */
    fun progressPpm(f: TutorialFacts, acknowledged: Boolean = false): Long = when (this) {
        is Reach -> if (atLeast <= 0L) 1_000_000L else (gauge.read(f).coerceIn(0L, atLeast) * 1_000_000L / atLeast)
        is All -> if (goals.isEmpty()) 1_000_000L else goals.sumOf { it.progressPpm(f, acknowledged) } / goals.size
        else -> if (met(f, acknowledged)) 1_000_000L else 0L
    }

    fun describe(): String = when (this) {
        Acknowledge -> "Read this and carry on."
        is Visit -> "Open the $route screen."
        is Reach -> "Get $atLeast ${gauge.label}."
        Listed -> "List the company on the exchange."
        is All -> goals.joinToString(" ") { it.describe() }
    }
}
