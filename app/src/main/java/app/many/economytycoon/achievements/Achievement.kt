package app.many.economytycoon.achievements

import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.scenarios.Medal

enum class Tier(val label: String, val points: Int) { BRONZE("Bronze", 1), SILVER("Silver", 2), GOLD("Gold", 3) }

enum class Category(val label: String) {
    WEALTH("Wealth"),
    GROWTH("Growth"),
    FINANCE("Finance"),
    PROPERTY("Property"),
    COMMERCE("Commerce"),
    CAMPAIGN("Campaign"),
    ENDURANCE("Endurance"),
}

/** How an achievement is earned. */
sealed class Check {
    /** A measure of the running game reaches [target]. Earned in any one game. */
    data class Meter(val kind: GoalKind, val target: Long) : Check()

    /** A lifetime total reaches [target]. */
    data class Total(val stat: Stat, val target: Long) : Check()

    /** At least [count] scenarios hold a medal of [medal] or better. */
    data class Medals(val medal: Medal, val count: Int) : Check()
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val category: Category,
    val tier: Tier,
    val check: Check,
) {
    val points: Int get() = tier.points
}
