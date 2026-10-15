package app.many.economytycoon.achievements

import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.scenarios.Medal
import app.many.economytycoon.scenarios.PlayerProfile

/** How far an achievement is along: [value] against [target]. */
data class AchievementProgress(val value: Long, val target: Long) {
    val done: Boolean get() = value >= target
    val ratio: Double get() = if (target <= 0L) 1.0 else (value.coerceAtLeast(0L).toDouble() / target).coerceIn(0.0, 1.0)
}

object AchievementEvaluator {
    /** Scenarios holding at least [medal]. */
    fun medalsAtLeast(profile: PlayerProfile, medal: Medal): Int =
        app.many.economytycoon.scenarios.ScenarioCatalog.all.count { profile.medal(it.id) >= medal }

    /**
     * Progress of [a]. [meters] reads the running game for a goal kind; a lifetime or medal check never asks
     * it, so a profile can be judged with no game at all by passing a reader that returns zero.
     */
    fun progress(a: Achievement, profile: PlayerProfile, meters: (GoalKind) -> Long): AchievementProgress = when (val c = a.check) {
        is Check.Meter -> AchievementProgress(meters(c.kind), c.target)
        is Check.Total -> AchievementProgress(profile.stats[c.stat], c.target)
        is Check.Medals -> AchievementProgress(medalsAtLeast(profile, c.medal).toLong(), c.count.toLong())
    }

    /** Achievements not yet unlocked whose condition now holds. */
    fun earnable(profile: PlayerProfile, meters: (GoalKind) -> Long): List<Achievement> =
        AchievementCatalog.all.filter { !profile.achievements.isUnlocked(it.id) && progress(it, profile, meters).done }
}
